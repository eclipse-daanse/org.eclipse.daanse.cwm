/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.sql.resolve.internal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.ColumnUsage;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Failure;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.ProducedColumn;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Resolution;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.SqlResolver;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Validation;
import org.eclipse.daanse.cwm.util.resource.relational.Columns;

import ai.starlake.transpiler.CatalogNotFoundException;
import ai.starlake.transpiler.ColumnNotFoundException;
import ai.starlake.transpiler.JSQLColumResolver;
import ai.starlake.transpiler.JSQLResolver;
import ai.starlake.transpiler.SchemaNotFoundException;
import ai.starlake.transpiler.TableNotDeclaredException;
import ai.starlake.transpiler.TableNotFoundException;
import ai.starlake.transpiler.schema.JdbcColumn;
import ai.starlake.transpiler.schema.JdbcMetaData;
import ai.starlake.transpiler.schema.JdbcResultSetMetaData;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Default {@link SqlResolver} implementation. Resolves a SQL query against one
 * or more CWM Schemas, returning the columns referenced by the query and
 * the columns produced by its SELECT projection, mapped back to the original CWM
 * {@link Column} EObjects. Instances are created by
 * {@link SqlResolverFactoryImpl}.
 */
public final class SqlResolverImpl implements SqlResolver {

    private final CwmJdbcMetaData metaData;

    public SqlResolverImpl(CwmJdbcMetaData metaData) {
        this.metaData = metaData;
    }

    /** Resolve the query body of a {@link QueryColumnSet}. */
    @Override
    public Resolution resolve(QueryColumnSet qcs) {
        if (qcs == null)
            return failure(Failure.EMPTY, "QueryColumnSet is null");
        return resolve(qcs.getQuery());
    }

    /** Resolve the defining query body of a {@link View}. */
    public Resolution resolve(View view) {
        if (view == null)
            return failure(Failure.EMPTY, "View is null");
        return resolve(view.getQueryExpression());
    }

    /**
     * Resolve a {@link QueryExpression} (also produced by
     * {@code View.getQueryExpression()}).
     */
    public Resolution resolve(QueryExpression qe) {
        if (qe == null)
            return failure(Failure.EMPTY, "QueryExpression is null");
        String lang = qe.getLanguage();
        if (lang != null && !lang.isBlank() && !"SQL".equalsIgnoreCase(lang)) {
            return failure(Failure.NON_SQL_LANGUAGE,
                    "QueryExpression language is " + lang + " — only SQL is supported");
        }
        return resolve(qe.getBody());
    }

    /** Resolve a raw SQL string. */
    public Resolution resolve(String sql) {
        if (sql == null || sql.isBlank())
            return failure(Failure.EMPTY, "SQL is empty");

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            return failure(Failure.PARSE, ex.getMessage());
        }

        // JSQLResolver mutates its own JdbcMetaData while walking the
        // statement. Pass a copy so repeated resolve() calls stay isolated.
        JSQLResolver resolver = new JSQLResolver(JdbcMetaData.copyOf(metaData.jdbcMetaData()));
        Set<JdbcColumn> resolved;
        try {
            resolved = resolver.resolve(statement);
        } catch (CatalogNotFoundException ex) {
            return failure(Failure.UNKNOWN_CATALOG, ex.getMessage());
        } catch (SchemaNotFoundException ex) {
            return failure(Failure.UNKNOWN_SCHEMA, ex.getMessage());
        } catch (TableNotFoundException | TableNotDeclaredException ex) {
            return failure(Failure.UNKNOWN_TABLE, ex.getMessage());
        } catch (ColumnNotFoundException ex) {
            return failure(Failure.UNKNOWN_COLUMN, ex.getMessage());
        } catch (RuntimeException ex) {
            return failure(Failure.OTHER, ex.getMessage());
        }

        // Collect column usages by clause, mapping each JdbcColumn back to
        // its CWM Column EObject. The clause getters (getWhereColumns etc.)
        // can return expression-wrapping JdbcColumns when the clause carries
        // a non-trivial expression (e.g. {@code col LIKE '%@x'}); those get
        // flattened to the underlying column references via flatten(...).
        // {@code resolver.resolve(...)}'s return value is already the flat
        // union — we use it as the authoritative columnsUsed set.
        Map<Column, EnumSet<ColumnUsage>> usage = new LinkedHashMap<>();
        EnumMap<ColumnUsage, List<Column>> ordered = new EnumMap<>(ColumnUsage.class);
        for (ColumnUsage cu : ColumnUsage.values())
            ordered.put(cu, List.of());
        ordered.put(ColumnUsage.SELECT,
                recordAll(resolver.flatten(resolver.getSelectColumns()), ColumnUsage.SELECT, usage));
        ordered.put(ColumnUsage.WHERE, recordAll(resolver.getFlattendedWhereColumns(), ColumnUsage.WHERE, usage));
        ordered.put(ColumnUsage.JOIN, recordAll(resolver.getFlattenedJoinedOnColumns(), ColumnUsage.JOIN, usage));
        ordered.put(ColumnUsage.GROUP_BY,
                recordAll(resolver.flatten(resolver.getGroupByColumns()), ColumnUsage.GROUP_BY, usage));
        ordered.put(ColumnUsage.HAVING,
                recordAll(resolver.flatten(resolver.getHavingColumns()), ColumnUsage.HAVING, usage));
        ordered.put(ColumnUsage.ORDER_BY,
                recordAll(resolver.flatten(resolver.getOrderByColumns()), ColumnUsage.ORDER_BY, usage));

        Set<Column> columns = new LinkedHashSet<>();
        if (resolved != null) {
            for (JdbcColumn jc : resolved) {
                Column c = metaData.resolve(jc);
                if (c != null)
                    columns.add(c);
            }
        }
        // Make sure every clause-classified column also appears in the set
        // (and conversely make sure every used column has at least an empty
        // usage entry so columnUsage.keySet() ⊆ columnsUsed).
        for (Column c : columns)
            usage.computeIfAbsent(c, k -> EnumSet.noneOf(ColumnUsage.class));
        columns.addAll(usage.keySet());
        Set<NamedColumnSet> tables = collectTables(columns);
        Set<String> functions = resolver.getFlatFunctionNames();

        // Produced columns — the SELECT projection. Use a fresh metadata copy
        // so the projection-resolution doesn't see leftover state from
        // JSQLResolver's mutation.
        List<ProducedColumn> produced = collectProducedColumns(statement);

        String rewritten = null;
        try {
            JSQLColumResolver crr = new JSQLColumResolver(JdbcMetaData.copyOf(metaData.jdbcMetaData()));
            crr.setCommentFlag(false);
            rewritten = crr.getResolvedStatementText(sql);
        } catch (RuntimeException | JSQLParserException ignored) {
            // rewrite is best-effort — leave null on failure
        }

        return new Resolution(true, Optional.empty(), summary(columns, tables, produced), columns, usage, ordered,
                tables, produced, functions == null ? Set.of() : functions, rewritten);
    }

    /**
     * Compare the columns declared on {@code qcs} (its {@code Feature}s) with the
     * columns the resolved query produces. Returns
     * {@link Validation#ok}{@code = true} iff the declared list and the produced
     * list match by name and order.
     *
     * <p>
     * If {@code resolution.ok()} is false (the query didn't parse / didn't
     * resolve), the validation reports every declared column as missing from the
     * produced list and is {@code ok = false}.
     * </p>
     */
    public Validation validate(QueryColumnSet qcs, Resolution resolution) {
        Objects.requireNonNull(qcs, "qcs");
        Objects.requireNonNull(resolution, "resolution");

        List<String> declared = new ArrayList<>();
        for (Feature f : qcs.getFeature()) {
            if (f instanceof Column c && c.getName() != null) {
                declared.add(c.getName());
            }
        }

        List<String> produced = new ArrayList<>();
        for (ProducedColumn pc : resolution.producedColumns()) {
            if (pc.name() != null)
                produced.add(pc.name());
        }

        List<String> missingDeclared = new ArrayList<>(); // produced but not declared
        for (String p : produced)
            if (!containsIgnoreCase(declared, p))
                missingDeclared.add(p);

        List<String> missingProduced = new ArrayList<>(); // declared but not produced
        for (String d : declared)
            if (!containsIgnoreCase(produced, d))
                missingProduced.add(d);

        List<String> outOfOrder = new ArrayList<>();
        int max = Math.min(declared.size(), produced.size());
        for (int i = 0; i < max; i++) {
            if (!declared.get(i).equalsIgnoreCase(produced.get(i))) {
                outOfOrder.add(declared.get(i) + " ↔ " + produced.get(i));
            }
        }

        boolean ok = missingDeclared.isEmpty() && missingProduced.isEmpty() && outOfOrder.isEmpty();
        return new Validation(ok, missingDeclared, missingProduced, outOfOrder);
    }

    /* ------------------------------------------------------------------ */

    private List<ProducedColumn> collectProducedColumns(Statement statement) {
        if (!(statement instanceof Select select))
            return List.of();
        try {
            JSQLColumResolver crr = new JSQLColumResolver(JdbcMetaData.copyOf(metaData.jdbcMetaData()));
            crr.setCommentFlag(false);
            JdbcResultSetMetaData rsmd = crr.getResultSetMetaData(select);
            if (rsmd == null)
                return List.of();
            List<JdbcColumn> jcs = rsmd.getColumns();
            List<String> labels = rsmd.getLabels();
            int n = rsmd.getColumnCount();
            List<ProducedColumn> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                JdbcColumn jc = i < jcs.size() ? jcs.get(i) : null;
                String label = i < labels.size() ? labels.get(i) : null;
                String name = label != null && !label.isBlank() ? label : (jc != null ? jc.columnName : null);
                Column source = metaData.resolve(jc);
                out.add(new ProducedColumn(name, source));
            }
            return out;
        } catch (SQLException | RuntimeException ex) {
            return List.of();
        }
    }

    /**
     * Record the clause's columns into {@code out} (flag-set per Column) AND return
     * the per-clause list in encounter order, identity-deduped, mapped back to CWM
     * Columns.
     */
    private List<Column> recordAll(java.util.Collection<JdbcColumn> jcs, ColumnUsage usage,
            Map<Column, EnumSet<ColumnUsage>> out) {
        if (jcs == null)
            return List.of();
        LinkedHashSet<Column> ordered = new LinkedHashSet<>();
        for (JdbcColumn jc : jcs) {
            Column c = metaData.resolve(jc);
            if (c == null)
                continue;
            out.computeIfAbsent(c, k -> EnumSet.noneOf(ColumnUsage.class)).add(usage);
            ordered.add(c);
        }
        return List.copyOf(ordered);
    }

    private Set<NamedColumnSet> collectTables(Set<Column> columns) {
        Set<NamedColumnSet> tables = new LinkedHashSet<>();
        for (Column c : columns) {
            Columns.namedOwner(c).ifPresent(tables::add);
        }
        return tables;
    }

    private static Resolution failure(Failure f, String msg) {
        return new Resolution(false, Optional.of(f), safe(msg), Set.of(), Map.of(), Map.of(), Set.of(), List.of(),
                Set.of(), null);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String summary(Set<Column> columns, Set<NamedColumnSet> tables, List<ProducedColumn> produced) {
        return "ok: " + columns.size() + " column(s) used across " + tables.size() + " table(s); produces "
                + produced.size() + " column(s)";
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list)
            if (s.equalsIgnoreCase(value))
                return true;
        return false;
    }

}
