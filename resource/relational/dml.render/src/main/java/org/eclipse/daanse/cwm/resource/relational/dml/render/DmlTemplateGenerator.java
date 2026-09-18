/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.dml.render;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * Generates prepared DML statement templates from a CWM relational
 * {@link Table} for one SQL dialect: SELECT (by pk / all / paged), INSERT,
 * UPDATE by pk and DELETE by pk. Kinds that don't apply (no primary key,
 * pk-only table) are simply absent from the result.
 *
 * <p>All statements are built as text directly over the dialect API
 * ({@code IdentifierQuoter}, {@code ParameterPlaceholderGenerator}) —
 * deliberately self-contained, no query-model dependency, and only
 * dialect-portable statement shapes. Offline-instantiable like
 * {@code CwmDdlRenderer}.</p>
 */
public final class DmlTemplateGenerator {

    private final Dialect dialect;

    public DmlTemplateGenerator(Dialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    public Dialect dialect() {
        return dialect;
    }

    public DmlTemplates templates(Table table) {
        return templates(table, DmlSettings.defaults());
    }

    public DmlTemplates templates(Table table, DmlSettings settings) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(settings, "settings");
        List<Column> all = columns(table);
        List<Column> pk = pkColumns(table);
        List<Column> nonPk = all.stream().filter(c -> !pk.contains(c)).toList();

        List<DmlTemplate> out = new ArrayList<>();
        out.add(selectAll(table, all, settings));
        if (!pk.isEmpty()) {
            out.add(selectByPk(table, all, pk, settings));
        }
        out.add(selectPage(table, all, pk, settings));
        out.add(insert(table, all, settings));
        if (!pk.isEmpty() && !nonPk.isEmpty()) {
            out.add(update(table, pk, nonPk, settings));
        }
        if (!pk.isEmpty()) {
            out.add(delete(table, pk, settings));
        }
        return new DmlTemplates(table, out);
    }

    // ------------------------------------------------------------ SELECT side

    private DmlTemplate selectAll(Table table, List<Column> all, DmlSettings settings) {
        String sql = "SELECT " + columnCsv(all) + " FROM " + qualifiedTable(table, settings);
        return new DmlTemplate(TemplateKind.SELECT_ALL, sql, List.of());
    }

    private DmlTemplate selectByPk(Table table, List<Column> all, List<Column> pk, DmlSettings settings) {
        StringBuilder sql = new StringBuilder("SELECT ").append(columnCsv(all))
                .append(" FROM ").append(qualifiedTable(table, settings));
        List<ParameterSpec> params = new ArrayList<>();
        appendPkWhere(sql, params, pk, settings);
        return new DmlTemplate(TemplateKind.SELECT_BY_PK, sql.toString(), params);
    }

    /**
     * Deterministic paging: ORDER BY over the pk columns (first column when
     * there is no pk), then the ANSI parameterized form
     * {@code OFFSET ? ROWS FETCH NEXT ? ROWS ONLY}.
     */
    private DmlTemplate selectPage(Table table, List<Column> all, List<Column> pk, DmlSettings settings) {
        List<Column> orderCols = pk.isEmpty() ? List.of(all.get(0)) : pk;
        StringBuilder sql = new StringBuilder("SELECT ").append(columnCsv(all))
                .append(" FROM ").append(qualifiedTable(table, settings))
                .append(" ORDER BY ").append(columnCsv(orderCols));
        List<ParameterSpec> params = new ArrayList<>();
        sql.append(" OFFSET ").append(placeholder(params, "offset", null, settings)).append(" ROWS")
                .append(" FETCH NEXT ").append(placeholder(params, "limit", null, settings))
                .append(" ROWS ONLY");
        return new DmlTemplate(TemplateKind.SELECT_PAGE, sql.toString(), params);
    }

    // ------------------------------------------------------------- write side

    private DmlTemplate insert(Table table, List<Column> all, DmlSettings settings) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(qualifiedTable(table, settings));
        List<ParameterSpec> params = new ArrayList<>();
        sql.append(" (").append(columnCsv(all)).append(") VALUES (");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(placeholder(params, all.get(i), settings));
        }
        sql.append(')');
        return new DmlTemplate(TemplateKind.INSERT, sql.toString(), params);
    }

    private DmlTemplate update(Table table, List<Column> pk, List<Column> nonPk, DmlSettings settings) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(qualifiedTable(table, settings))
                .append(" SET ");
        List<ParameterSpec> params = new ArrayList<>();
        boolean first = true;
        for (Column c : nonPk) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(quote(c)).append(" = ").append(placeholder(params, c, settings));
        }
        appendPkWhere(sql, params, pk, settings);
        return new DmlTemplate(TemplateKind.UPDATE_BY_PK, sql.toString(), params);
    }

    private DmlTemplate delete(Table table, List<Column> pk, DmlSettings settings) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(qualifiedTable(table, settings));
        List<ParameterSpec> params = new ArrayList<>();
        appendPkWhere(sql, params, pk, settings);
        return new DmlTemplate(TemplateKind.DELETE_BY_PK, sql.toString(), params);
    }

    // ------------------------------------------------------------------ parts

    private void appendPkWhere(StringBuilder sql, List<ParameterSpec> params,
            List<Column> pk, DmlSettings settings) {
        sql.append(" WHERE ");
        boolean first = true;
        for (Column k : pk) {
            if (!first) {
                sql.append(" AND ");
            }
            first = false;
            sql.append(quote(k)).append(" = ").append(placeholder(params, k, settings));
        }
    }

    private String columnCsv(List<Column> cols) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Column c : cols) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(quote(c));
        }
        return sb.toString();
    }

    private String placeholder(List<ParameterSpec> params, Column c, DmlSettings settings) {
        return placeholder(params, c.getName(), jdbcType(c), settings);
    }

    private String placeholder(List<ParameterSpec> params, String name, JDBCType type,
            DmlSettings settings) {
        int pos = params.size() + 1;
        params.add(new ParameterSpec(pos, name, type));
        return switch (settings.parameterStyle()) {
            case POSITIONAL -> dialect.parameterPlaceholderGenerator().placeholder(pos);
            case NAMED -> ":" + name;
        };
    }

    private String quote(Column c) {
        return dialect.quoteIdentifier(c.getName());
    }

    private String qualifiedTable(Table table, DmlSettings settings) {
        String schema = schemaName(table);
        String name = dialect.quoteIdentifier(table.getName());
        return settings.includeSchema() && schema != null
                ? dialect.quoteIdentifier(schema) + "." + name
                : name;
    }

    private static String schemaName(Table table) {
        return table.getNamespace() instanceof Schema s && s.getName() != null && !s.getName().isBlank()
                ? s.getName()
                : null;
    }

    private static List<Column> columns(Table table) {
        return table.getFeature().stream()
                .filter(Column.class::isInstance).map(Column.class::cast).toList();
    }

    private static List<Column> pkColumns(Table table) {
        return table.getOwnedElement().stream()
                .filter(PrimaryKey.class::isInstance).map(PrimaryKey.class::cast)
                .findFirst()
                .map(pk -> pk.getFeature().stream()
                        .filter(Column.class::isInstance).map(Column.class::cast).toList())
                .orElse(List.of());
    }

    private static JDBCType jdbcType(Column c) {
        if (c.getType() instanceof SQLSimpleType st) {
            try {
                return JDBCType.valueOf((int) st.getTypeNumber());
            } catch (IllegalArgumentException notAJdbcType) {
                return null;
            }
        }
        return null;
    }
}
