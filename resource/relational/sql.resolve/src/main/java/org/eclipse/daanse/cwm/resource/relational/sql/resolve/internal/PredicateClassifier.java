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

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.PredicateKind;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;

/**
 * Best-effort {@link PredicateKind} classification of the WHERE and JOIN-ON
 * predicates of an already parsed statement. Walks the JSQLParser AST; a bare
 * column compared with {@code = / IN / IS NULL} is {@code EQUALITY}, with
 * {@code < > <= >= / BETWEEN / LIKE 'x%'} is {@code RANGE}, and a column that
 * only appears wrapped (function, arithmetic, cast), under a leading-wildcard
 * {@code LIKE}, or with {@code <>} is {@code NON_SARGABLE}.
 * <p>
 * AST column references are mapped back to CWM columns by name: an alias or
 * table qualifier is resolved against the FROM clause, an unqualified name only
 * when it is unique among the query's resolved columns. Anything ambiguous or
 * unsupported simply gets no entry — callers must treat absent columns as
 * <em>unknown</em>, never as sargable.
 */
final class PredicateClassifier {

    private final Map<String, Column> qualified = new HashMap<>();
    private final Map<String, Column> unqualified = new HashMap<>();
    private final Set<String> ambiguous = new HashSet<>();
    private final Map<Column, EnumSet<PredicateKind>> kinds = new LinkedHashMap<>();

    private PredicateClassifier(Collection<Column> candidates) {
        for (Column c : candidates) {
            String col = lower(c.getName());
            if (col == null) {
                continue;
            }
            String table = Columns.namedOwner(c).map(t -> lower(t.getName())).orElse(null);
            if (table != null) {
                qualified.putIfAbsent(table + "." + col, c);
            }
            if (unqualified.putIfAbsent(col, c) != null && unqualified.get(col) != c) {
                ambiguous.add(col);
            }
        }
    }

    /**
     * Classify {@code statement}'s WHERE / JOIN-ON predicates over the given
     * resolved {@code candidates}. Never throws — on any surprise the result is
     * simply missing entries.
     */
    static Map<Column, EnumSet<PredicateKind>> classify(Statement statement, Collection<Column> candidates) {
        PredicateClassifier pc = new PredicateClassifier(candidates);
        try {
            if (statement instanceof Select select) {
                pc.select(select);
            }
        } catch (RuntimeException ignored) {
            // best-effort: partial (or no) classification is acceptable
        }
        return pc.kinds;
    }

    /* ------------------------------------------------------------------ */

    private void select(Select s) {
        if (s instanceof PlainSelect ps) {
            plainSelect(ps);
        } else if (s instanceof SetOperationList sol) {
            sol.getSelects().forEach(this::select);
        } else if (s instanceof ParenthesedSelect p) {
            select(p.getSelect());
        }
    }

    private void plainSelect(PlainSelect ps) {
        Map<String, String> aliases = new HashMap<>();
        collectFromItem(ps.getFromItem(), aliases);
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                collectFromItem(j.getRightItem(), aliases);
            }
        }
        condition(ps.getWhere(), aliases);
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                for (Expression on : j.getOnExpressions()) {
                    condition(on, aliases);
                }
            }
        }
    }

    /** Record table aliases; recurse into sub-selects used as FROM items. */
    private void collectFromItem(Object item, Map<String, String> aliases) {
        if (item instanceof net.sf.jsqlparser.schema.Table t) {
            String table = lower(t.getName());
            String alias = t.getAlias() != null ? lower(t.getAlias().getName()) : null;
            if (alias != null && table != null) {
                aliases.put(alias, table);
            }
        } else if (item instanceof ParenthesedSelect sub) {
            select(sub.getSelect());
        }
    }

    private void condition(Expression e, Map<String, String> aliases) {
        switch (e) {
            case null -> { /* no predicate */ }
            case AndExpression and -> {
                condition(and.getLeftExpression(), aliases);
                condition(and.getRightExpression(), aliases);
            }
            case OrExpression or -> {
                condition(or.getLeftExpression(), aliases);
                condition(or.getRightExpression(), aliases);
            }
            case XorExpression xor -> {
                condition(xor.getLeftExpression(), aliases);
                condition(xor.getRightExpression(), aliases);
            }
            case Parenthesis p -> condition(p.getExpression(), aliases);
            case NotExpression not -> condition(not.getExpression(), aliases);
            case LikeExpression like -> like(like, aliases);
            case EqualsTo eq -> comparison(eq, PredicateKind.EQUALITY, aliases);
            case NotEqualsTo ne -> comparison(ne, PredicateKind.NON_SARGABLE, aliases);
            case GreaterThan c -> comparison(c, PredicateKind.RANGE, aliases);
            case GreaterThanEquals c -> comparison(c, PredicateKind.RANGE, aliases);
            case MinorThan c -> comparison(c, PredicateKind.RANGE, aliases);
            case MinorThanEquals c -> comparison(c, PredicateKind.RANGE, aliases);
            case Between b -> side(b.getLeftExpression(), PredicateKind.RANGE, aliases);
            case InExpression in -> {
                side(in.getLeftExpression(), PredicateKind.EQUALITY, aliases);
                if (in.getRightExpression() instanceof Select sub) {
                    select(sub);
                }
            }
            case IsNullExpression isNull -> side(isNull.getLeftExpression(), PredicateKind.EQUALITY, aliases);
            case ExistsExpression ex -> {
                if (ex.getRightExpression() instanceof Select sub) {
                    select(sub);
                }
            }
            default -> { /* unsupported predicate shape: leave unclassified */ }
        }
    }

    /**
     * {@code LIKE 'x%'} navigates a b-tree like a range; a leading wildcard,
     * a non-literal pattern with unknown shape, or {@code NOT LIKE} does not.
     */
    private void like(LikeExpression like, Map<String, String> aliases) {
        if (like.isNot()) {
            side(like.getLeftExpression(), PredicateKind.NON_SARGABLE, aliases);
            return;
        }
        if (like.getRightExpression() instanceof StringValue sv) {
            String pattern = sv.getValue();
            boolean leadingWildcard = pattern != null && !pattern.isEmpty()
                    && (pattern.charAt(0) == '%' || pattern.charAt(0) == '_');
            side(like.getLeftExpression(),
                    leadingWildcard ? PredicateKind.NON_SARGABLE : PredicateKind.RANGE, aliases);
        }
        // non-literal pattern (parameter, expression): shape unknown, no entry
    }

    /** Both sides of a comparison: bare columns get {@code kind}, wrapped columns NON_SARGABLE. */
    private void comparison(BinaryExpression cmp, PredicateKind kind, Map<String, String> aliases) {
        side(cmp.getLeftExpression(), kind, aliases);
        side(cmp.getRightExpression(), kind, aliases);
    }

    private void side(Expression e, PredicateKind kind, Map<String, String> aliases) {
        switch (e) {
            case null -> { /* nothing */ }
            case net.sf.jsqlparser.schema.Column astCol -> mark(astCol, kind, aliases);
            case Select sub -> select(sub);
            default -> {
                // a column inside a function / arithmetic / cast cannot use its b-tree
                for (net.sf.jsqlparser.schema.Column wrapped : columnsWithin(e)) {
                    mark(wrapped, PredicateKind.NON_SARGABLE, aliases);
                }
            }
        }
    }

    /** All bare column references nested anywhere inside {@code e}. */
    private Set<net.sf.jsqlparser.schema.Column> columnsWithin(Expression e) {
        Set<net.sf.jsqlparser.schema.Column> out = new HashSet<>();
        collectColumns(e, out);
        return out;
    }

    private void collectColumns(Expression e, Set<net.sf.jsqlparser.schema.Column> out) {
        switch (e) {
            case null -> { /* nothing */ }
            case net.sf.jsqlparser.schema.Column c -> out.add(c);
            case Function f -> collectColumns(f.getParameters(), out);
            case CastExpression c -> collectColumns(c.getLeftExpression(), out);
            case Parenthesis p -> collectColumns(p.getExpression(), out);
            case ExpressionList<?> list -> list.forEach(x -> collectColumns(x, out));
            case BinaryExpression b -> {
                collectColumns(b.getLeftExpression(), out);
                collectColumns(b.getRightExpression(), out);
            }
            default -> { /* literals, parameters, subselects: no bare column */ }
        }
    }

    private void mark(net.sf.jsqlparser.schema.Column astCol, PredicateKind kind, Map<String, String> aliases) {
        Column resolved = lookup(astCol, aliases);
        if (resolved != null) {
            kinds.computeIfAbsent(resolved, k -> EnumSet.noneOf(PredicateKind.class)).add(kind);
        }
    }

    private Column lookup(net.sf.jsqlparser.schema.Column astCol, Map<String, String> aliases) {
        String col = lower(astCol.getColumnName());
        if (col == null) {
            return null;
        }
        String qualifier = astCol.getTable() != null ? lower(astCol.getTable().getName()) : null;
        if (qualifier != null) {
            String table = aliases.getOrDefault(qualifier, qualifier);
            return qualified.get(table + "." + col);
        }
        return ambiguous.contains(col) ? null : unqualified.get(col);
    }

    private static String lower(String s) {
        return s == null || s.isBlank() ? null : s.toLowerCase();
    }
}
