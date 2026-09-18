/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sql;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.sql.select.Asterisk;
import org.eclipse.daanse.cwm.model.daanse.sql.select.NamedTableReference;
import org.eclipse.daanse.cwm.model.daanse.sql.select.DerivedColumn;
import org.eclipse.daanse.cwm.model.daanse.sql.select.DerivedTable;
import org.eclipse.daanse.cwm.model.daanse.sql.select.FromClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.GroupByClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.Join;
import org.eclipse.daanse.cwm.model.daanse.sql.select.OrderByClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.OrdinaryGroupingSet;
import org.eclipse.daanse.cwm.model.daanse.sql.select.ProjectionReference;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QueryExpression;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QueryExpressionBody;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QuerySpecification;
import org.eclipse.daanse.cwm.model.daanse.sql.select.ResultOffsetFetch;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SelectFactory;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SetOperation;
import org.eclipse.daanse.cwm.model.daanse.sql.select.TableReference;
import org.eclipse.daanse.cwm.model.daanse.sql.select.UnresolvedTableReference;
import org.eclipse.daanse.cwm.model.daanse.sql.select.WithClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.WithListElement;

/**
 * Erzeugt Beispiel-Modellinstanzen ausschliesslich ueber die generierten EMF-Factories.
 *
 * <p>Eine „hat alles"-Instanz plus je eine fokussierte pro Konstrukt. Jede Methode liefert eine
 * eigenstaendige {@link QueryExpression}; {@link #all()} registriert sie unter einem Namen, der
 * als Dateiname (.sql) und als Testfall-Name dient. Das Modell ist dialekt-neutral/semantisch:
 * „minus" = ANSI EXCEPT, Aliase sind anonym (Korrelationsnamen optional).</p>
 */
public final class SqlExamples {

    private static final SqlFactory SQL = SqlFactory.eINSTANCE;
    private static final SelectFactory SEL = SelectFactory.eINSTANCE;
    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private SqlExamples() {
    }

    /** Alle Beispiele, Name → Instanz (Reihenfolge stabil). */
    public static Map<String, QueryExpression> all() {
        Map<String, QueryExpression> m = new LinkedHashMap<>();
        m.put("showcase", showcase());
        m.put("typed", typed());
        m.put("collab", collab());
        m.put("cte-symdiff", cteSymdiff());
        m.put("all-features", allFeatures());
        m.put("setop-union", setop(SetOperatorKind.UNION, SetQuantifierKind.DISTINCT));
        m.put("setop-union-all", setop(SetOperatorKind.UNION, SetQuantifierKind.ALL));
        m.put("setop-except", setop(SetOperatorKind.EXCEPT, null));
        m.put("setop-intersect", setop(SetOperatorKind.INTERSECT, null));
        m.put("join-inner", join(JoinTypeKind.INNER, true));
        m.put("join-left", join(JoinTypeKind.LEFT, true));
        m.put("join-right", join(JoinTypeKind.RIGHT, true));
        m.put("join-full", join(JoinTypeKind.FULL, true));
        m.put("join-cross", join(JoinTypeKind.CROSS, false));
        m.put("cte", cte(false));
        m.put("cte-recursive", cte(true));
        m.put("subselect-derived", subselectDerived());
        m.put("subselect-scalar", subselectScalar());
        m.put("subselect-in", subselectIn());
        m.put("subselect-exists", subselectExists());
        m.put("function-in-join", functionInJoin());
        m.put("where-count-order", whereCountOrder());
        return m;
    }

    // ---------------------------------------------------------------------
    // Beispiele
    // ---------------------------------------------------------------------

    /** WITH … SELECT mit COUNT(*)/Funktion/CASE/CAST/Fensterfunktion/Scalar-Subquery, JOIN+Subselect,
     *  WHERE (Vergleich AND IN-Subquery), GROUP BY/HAVING, ORDER BY (Handle) + OFFSET/FETCH. */
    public static QueryExpression allFeatures() {
        QuerySpecification spec = SEL.createQuerySpecification();

        // COUNT(*) AS cnt  (per Handle in ORDER BY referenziert)
        DerivedColumn cnt = derived(countStar(), "cnt");
        spec.getSelectList().add(cnt);
        // upper(c.name)
        spec.getSelectList().add(derived(func("upper", col("c", "name")), null));
        // CASE WHEN o.amount > 100 THEN 'big' ELSE 'small' END
        CaseExpression caseExpr = SQL.createCaseExpression();
        WhenClause when = SQL.createWhenClause();
        when.setCondition(cmp(ComparisonOperatorKind.GREATER_THAN, col("o", "amount"), num(100)));
        when.setResult(str("big"));
        caseExpr.getWhenClauses().add(when);
        caseExpr.setElseResult(str("small"));
        spec.getSelectList().add(derived(caseExpr, "bucket"));
        // CAST(o.amount AS INTEGER)
        CastSpecification cast = SQL.createCastSpecification();
        cast.setOperand(col("o", "amount"));
        DataTypeReference dtr = SQL.createDataTypeReference();
        dtr.setKind(DataTypeKind.INTEGER);
        cast.setTargetType(dtr);
        spec.getSelectList().add(derived(cast, "amount_i"));
        // ROW_NUMBER() OVER (PARTITION BY c.region ORDER BY o.ts)
        WindowFunction rn = SQL.createWindowFunction();
        rn.setKind(WindowFunctionKind.ROW_NUMBER);
        WindowSpecification win = SQL.createWindowSpecification();
        win.getPartitionBy().add(col("c", "region"));
        SortSpecification winSort = SQL.createSortSpecification();
        winSort.setSortKey(col("o", "ts"));
        winSort.setOrdering(SortDirectionKind.ASC);
        win.getOrderBy().add(winSort);
        rn.setWindow(win);
        spec.getSelectList().add(derived(rn, "rn"));
        // (SELECT max(amount) FROM orders) scalar subquery
        ScalarSubquery scalar = SQL.createScalarSubquery();
        QuerySpecification maxSpec = simpleSelect("orders", derived(agg(AggregateKind.MAX, col("amount")), null));
        scalar.setQuery(wrap(maxSpec));
        spec.getSelectList().add(derived(scalar, "max_amount"));

        // FROM orders o INNER JOIN (SELECT oid FROM lines) d ON o.id = d.oid
        UnresolvedTableReference orders = table("orders", "o");
        DerivedTable derived = SEL.createDerivedTable();
        derived.setQuery(wrap(simpleSelect("lines", derived(col("oid"), null))));
        derived.setAliasDefinition(alias("d"));
        Join join = SEL.createJoin();
        join.setJoinType(JoinTypeKind.INNER);
        join.setLeft(orders);
        join.setRight(derived);
        join.setCondition(cmp(ComparisonOperatorKind.EQUALS, col("o", "id"), col("d", "oid")));
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(join);
        spec.setFrom(from);

        // WHERE o.amount > 100 AND o.id IN (SELECT id FROM vip)
        BinaryLogicalExpression and = SQL.createBinaryLogicalExpression();
        and.setOperator(LogicalOperatorKind.AND);
        and.setLeft(cmp(ComparisonOperatorKind.GREATER_THAN, col("o", "amount"), num(100)));
        InPredicate in = SQL.createInPredicate();
        in.setValue(col("o", "id"));
        in.setSubquery(wrap(simpleSelect("vip", derived(col("id"), null))));
        and.setRight(in);
        spec.setWhere(and);

        // GROUP BY c.region HAVING COUNT(*) > 1
        GroupByClause groupBy = SEL.createGroupByClause();
        OrdinaryGroupingSet gs = SEL.createOrdinaryGroupingSet();
        gs.getElements().add(col("c", "region"));
        groupBy.getElements().add(gs);
        spec.setGroupBy(groupBy);
        spec.setHaving(cmp(ComparisonOperatorKind.GREATER_THAN, countStar(), num(1)));

        // WITH recent AS (SELECT * FROM orders)
        WithClause with = SEL.createWithClause();
        WithListElement cteEl = SEL.createWithListElement();
        cteEl.setQueryName(alias("recent"));
        cteEl.setQuery(wrap(selectStar("orders")));
        with.getElements().add(cteEl);

        // ORDER BY cnt (per Handle) OFFSET 0 ROWS FETCH FIRST 10 ROWS ONLY
        QueryExpression query = SEL.createQueryExpression();
        query.setWithClause(with);
        query.setBody(spec);
        OrderByClause orderBy = SEL.createOrderByClause();
        SortSpecification ss = SQL.createSortSpecification();
        ProjectionReference pref = SEL.createProjectionReference();
        pref.setProjection(cnt);
        ss.setSortKey(pref);
        ss.setOrdering(SortDirectionKind.DESC);
        orderBy.getSortSpecifications().add(ss);
        ResultOffsetFetch off = SEL.createResultOffsetFetch();
        off.setOffset(num(0));
        off.setOffsetRows(RowKind.ROWS);
        off.setFetchFirst(num(10));
        orderBy.setOffsetFetch(off);
        query.setOrderBy(orderBy);
        return query;
    }

    /** Zwei einfache SELECTs verbunden durch eine Mengenoperation. */
    public static QueryExpression setop(SetOperatorKind op, SetQuantifierKind quantifier) {
        SetOperation so = SEL.createSetOperation();
        so.setOperator(op);
        if (quantifier != null) {
            so.setSetQuantifier(quantifier);
        }
        so.setLeft(simpleSelect("t1", derived(col("a"), null)));
        so.setRight(simpleSelect("t2", derived(col("a"), null)));
        return wrap(so);
    }

    /** SELECT * FROM a &lt;join&gt; b [ON a.id = b.id]. */
    public static QueryExpression join(JoinTypeKind kind, boolean withCondition) {
        Join join = SEL.createJoin();
        join.setJoinType(kind);
        join.setLeft(table("a", "a"));
        join.setRight(table("b", "b"));
        if (withCondition) {
            join.setCondition(cmp(ComparisonOperatorKind.EQUALS, col("a", "id"), col("b", "id")));
        }
        QuerySpecification spec = SEL.createQuerySpecification();
        spec.getSelectList().add(SEL.createAsterisk());
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(join);
        spec.setFrom(from);
        return wrap(spec);
    }

    /** WITH t AS (SELECT * FROM base) SELECT * FROM t. */
    public static QueryExpression cte(boolean recursive) {
        WithClause with = SEL.createWithClause();
        with.setRecursive(recursive);
        WithListElement el = SEL.createWithListElement();
        el.setQueryName(alias("t"));
        el.setQuery(wrap(selectStar("base")));
        with.getElements().add(el);
        QueryExpression query = wrap(selectStar("t"));
        query.setWithClause(with);
        return query;
    }

    /** SELECT * FROM (SELECT a FROM base) d. */
    public static QueryExpression subselectDerived() {
        DerivedTable d = SEL.createDerivedTable();
        d.setQuery(wrap(simpleSelect("base", derived(col("a"), null))));
        d.setAliasDefinition(alias("d"));
        QuerySpecification spec = SEL.createQuerySpecification();
        spec.getSelectList().add(SEL.createAsterisk());
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(d);
        spec.setFrom(from);
        return wrap(spec);
    }

    /** SELECT (SELECT max(x) FROM other) AS m FROM base. */
    public static QueryExpression subselectScalar() {
        ScalarSubquery scalar = SQL.createScalarSubquery();
        scalar.setQuery(wrap(simpleSelect("other", derived(agg(AggregateKind.MAX, col("x")), null))));
        return wrap(simpleSelect("base", derived(scalar, "m")));
    }

    /** SELECT * FROM base WHERE id IN (SELECT id FROM allowed). */
    public static QueryExpression subselectIn() {
        InPredicate in = SQL.createInPredicate();
        in.setValue(col("id"));
        in.setSubquery(wrap(simpleSelect("allowed", derived(col("id"), null))));
        QuerySpecification spec = selectStar("base");
        spec.setWhere(in);
        return wrap(spec);
    }

    /** SELECT * FROM base b WHERE EXISTS (SELECT 1 FROM child c). */
    public static QueryExpression subselectExists() {
        ExistsPredicate exists = SQL.createExistsPredicate();
        exists.setSubquery(wrap(simpleSelect("child", derived(num(1), null))));
        QuerySpecification spec = selectStar("base");
        spec.setWhere(exists);
        return wrap(spec);
    }

    /** SELECT * FROM a INNER JOIN b ON upper(a.code) = b.code (Funktion in der Join-Bedingung). */
    public static QueryExpression functionInJoin() {
        Join join = SEL.createJoin();
        join.setJoinType(JoinTypeKind.INNER);
        join.setLeft(table("a", "a"));
        join.setRight(table("b", "b"));
        join.setCondition(cmp(ComparisonOperatorKind.EQUALS, func("upper", col("a", "code")), col("b", "code")));
        QuerySpecification spec = SEL.createQuerySpecification();
        spec.getSelectList().add(SEL.createAsterisk());
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(join);
        spec.setFrom(from);
        return wrap(spec);
    }

    /** SELECT region, COUNT(*) AS n FROM sales WHERE amount > 0 GROUP BY region ORDER BY n DESC. */
    public static QueryExpression whereCountOrder() {
        DerivedColumn n = derived(countStar(), "n");
        QuerySpecification spec = SEL.createQuerySpecification();
        spec.getSelectList().add(derived(col("region"), null));
        spec.getSelectList().add(n);
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(table("sales", null));
        spec.setFrom(from);
        spec.setWhere(cmp(ComparisonOperatorKind.GREATER_THAN, col("amount"), num(0)));
        GroupByClause groupBy = SEL.createGroupByClause();
        OrdinaryGroupingSet gs = SEL.createOrdinaryGroupingSet();
        gs.getElements().add(col("region"));
        groupBy.getElements().add(gs);
        spec.setGroupBy(groupBy);
        QueryExpression query = wrap(spec);
        OrderByClause orderBy = SEL.createOrderByClause();
        SortSpecification ss = SQL.createSortSpecification();
        ProjectionReference pref = SEL.createProjectionReference();
        pref.setProjection(n);
        ss.setSortKey(pref);
        ss.setOrdering(SortDirectionKind.DESC);
        orderBy.getSortSpecifications().add(ss);
        query.setOrderBy(orderBy);
        return query;
    }

    // ---------------------------------------------------------------------
    // Helfer
    // ---------------------------------------------------------------------

    /**
     * Gemeinsam entwickeltes Beispiel: mehrere Tabellen mit ALLEN Join-Arten
     * (INNER/LEFT/RIGHT/FULL/CROSS), ein Subselect (mit eigenem GROUP BY), nur
     * benannte Spalten (kein {@code SELECT *}), eine berechnete Spalte (CONCAT
     * mehrerer Quellspalten), GROUP BY und ein aeusseres ORDER BY.
     */
    public static QueryExpression collab() {
        // subselect: SELECT s.region AS region, COUNT(*) AS cnt FROM sales s GROUP BY s.region
        QuerySpecification sub = SEL.createQuerySpecification();
        DerivedColumn subRegion = derived(col("s", "region"), "region");
        DerivedColumn subCnt = derived(countStar(), "cnt");
        sub.getSelectList().add(subRegion);
        sub.getSelectList().add(subCnt);
        FromClause subFrom = SEL.createFromClause();
        subFrom.getTableReferences().add(table("sales", "s"));
        sub.setFrom(subFrom);
        GroupByClause subGb = SEL.createGroupByClause();
        OrdinaryGroupingSet subGs = SEL.createOrdinaryGroupingSet();
        subGs.getElements().add(refTo(subRegion));
        subGb.getElements().add(subGs);
        sub.setGroupBy(subGb);
        QueryExpression subQ = SEL.createQueryExpression();
        subQ.setBody(sub);
        DerivedTable subDt = SEL.createDerivedTable();
        subDt.setQuery(subQ);
        subDt.setAliasDefinition(alias("sub"));

        // FROM tree with all join types
        // INNER join with multiple AND conditions (each becomes a row in the join node).
        Join j1 = join(JoinTypeKind.INNER, table("a", "a"), table("b", "b"),
                and(cmp(ComparisonOperatorKind.EQUALS, col("a", "id"), col("b", "aid")),
                        cmp(ComparisonOperatorKind.EQUALS, col("a", "region"), col("b", "region"))));
        Join j2 = join(JoinTypeKind.LEFT, j1, table("c", "c"),
                cmp(ComparisonOperatorKind.EQUALS, col("b", "id"), col("c", "bid")));
        Join j3 = join(JoinTypeKind.RIGHT, j2, table("d", "d"),
                cmp(ComparisonOperatorKind.EQUALS, col("c", "id"), col("d", "cid")));
        Join j4 = join(JoinTypeKind.FULL, j3, table("e", "e"),
                cmp(ComparisonOperatorKind.EQUALS, col("d", "id"), col("e", "did")));
        Join j5 = join(JoinTypeKind.CROSS, j4, subDt, null);

        // outer SELECT: only named columns; one computed via CONCAT of two source columns
        QuerySpecification q = SEL.createQuerySpecification();
        DerivedColumn cCustomer = derived(col("a", "name"), "customer");
        DerivedColumn cFull = derived(concat(col("a", "first"), col("a", "last")), "fullname");
        DerivedColumn cCat = derived(col("c", "title"), "cat");
        DerivedColumn cN = derived(col("sub", "cnt"), "n");
        q.getSelectList().add(cCustomer);
        q.getSelectList().add(cFull);
        q.getSelectList().add(cCat);
        q.getSelectList().add(cN);
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(j5);
        q.setFrom(from);
        // WHERE as a boolean tree with AND and OR: (a.id > 0 AND a.region IN (...)) OR a.active = TRUE.
        q.setWhere(or(
                and(cmp(ComparisonOperatorKind.GREATER_THAN, col("a", "id"), num(0)),
                        inList(col("a", "region"), str("north"), str("south"))),
                cmp(ComparisonOperatorKind.EQUALS, col("a", "active"), boolLit(TruthValueKind.TRUE))));
        GroupByClause gb = SEL.createGroupByClause();
        OrdinaryGroupingSet gs = SEL.createOrdinaryGroupingSet();
        gs.getElements().add(refTo(cCustomer));
        gs.getElements().add(refTo(cCat));
        gb.getElements().add(gs);
        q.setGroupBy(gb);

        QueryExpression top = SEL.createQueryExpression();
        top.setBody(q);
        OrderByClause ob = SEL.createOrderByClause();
        ob.getSortSpecifications().add(sort(refTo(cN), SortDirectionKind.DESC));
        ob.getSortSpecifications().add(sort(refTo(cCustomer), SortDirectionKind.ASC));
        top.setOrderBy(ob);
        return top;
    }

    /**
     * Zwei Basis-CTEs (with1, with2), ein dritter CTE der beide – jeweils per Konstante
     * eingeschränkt – als {@code (with1 EXCEPT with2) UNION ALL (with2 EXCEPT with1)} kombiniert,
     * und ein Haupt-SELECT darunter, das den dritten CTE nutzt.
     */
    public static QueryExpression cteSymdiff() {
        WithListElement cte1 = cte("with1", selectKV("t1"));
        WithListElement cte2 = cte("with2", selectKV("t2"));

        SetOperation ex1 = setOp(SetOperatorKind.EXCEPT, null,
                selK("with1", num(10)), selK("with2", num(20)));
        SetOperation ex2 = setOp(SetOperatorKind.EXCEPT, null,
                selK("with2", num(20)), selK("with1", num(10)));
        SetOperation ua = setOp(SetOperatorKind.UNION, SetQuantifierKind.ALL, ex1, ex2);
        WithListElement cte3 = cte("combined", ua);

        QuerySpecification main = SEL.createQuerySpecification();
        main.getSelectList().add(derived(col("combined", "k"), "k"));
        FromClause mf = SEL.createFromClause();
        mf.getTableReferences().add(table("combined", "combined"));
        main.setFrom(mf);

        WithClause with = SEL.createWithClause();
        with.getElements().add(cte1);
        with.getElements().add(cte2);
        with.getElements().add(cte3);
        QueryExpression top = SEL.createQueryExpression();
        top.setWithClause(with);
        top.setBody(main);
        return top;
    }

    /**
     * Ein ganzheitliches Beispiel, das alles vereint: zwei Basis-CTEs, ein dritter CTE als
     * symmetrische Differenz (EXCEPT/UNION ALL mit Konstanten-WHERE), und ein Haupt-SELECT mit
     * allen Join-Arten (inkl. Multi-AND), einem Subselect, der CTE-Nutzung im Join, benannten
     * Spalten + Berechnung (CONCAT) + Aggregat (COUNT), WHERE mit '>' und 'IN', GROUP BY und ORDER BY.
     */
    public static QueryExpression showcase() {
        WithListElement cte1 = cte("with1", selectKV("t1"));
        WithListElement cte2 = cte("with2", selectKV("t2"));
        SetOperation ex1 = setOp(SetOperatorKind.EXCEPT, null, selK("with1", num(10)), selK("with2", num(20)));
        SetOperation ex2 = setOp(SetOperatorKind.EXCEPT, null, selK("with2", num(20)), selK("with1", num(10)));
        SetOperation ua = setOp(SetOperatorKind.UNION, SetQuantifierKind.ALL, ex1, ex2);
        WithListElement cte3 = cte("combined", ua);

        // subselect: SELECT region FROM sales GROUP BY region HAVING COUNT(*) > 5
        QuerySpecification sub = SEL.createQuerySpecification();
        DerivedColumn subRegion = derived(col("s", "region"), "region");
        sub.getSelectList().add(subRegion);
        FromClause subFrom = SEL.createFromClause();
        subFrom.getTableReferences().add(table("sales", "s"));
        sub.setFrom(subFrom);
        GroupByClause subGb = SEL.createGroupByClause();
        OrdinaryGroupingSet subGs = SEL.createOrdinaryGroupingSet();
        subGs.getElements().add(refTo(subRegion));
        subGb.getElements().add(subGs);
        sub.setGroupBy(subGb);
        // COUNT is not a projection — it is bound to HAVING.
        sub.setHaving(cmp(ComparisonOperatorKind.GREATER_THAN, countStar(), num(5)));
        DerivedTable subDt = SEL.createDerivedTable();
        subDt.setQuery(wrap(sub));
        subDt.setAliasDefinition(alias("sub"));

        // FROM with all join types + subselect + a CTE used in a join.
        Join j1 = join(JoinTypeKind.INNER, table("a", "a"), table("b", "b"),
                and(cmp(ComparisonOperatorKind.EQUALS, col("a", "id"), col("b", "aid")),
                        cmp(ComparisonOperatorKind.EQUALS, col("a", "region"), col("b", "region"))));
        Join j2 = join(JoinTypeKind.LEFT, j1, table("c", "c"),
                cmp(ComparisonOperatorKind.EQUALS, col("b", "id"), col("c", "bid")));
        Join j3 = join(JoinTypeKind.RIGHT, j2, table("d", "d"),
                cmp(ComparisonOperatorKind.EQUALS, col("c", "id"), col("d", "cid")));
        Join j4 = join(JoinTypeKind.FULL, j3, table("e", "e"),
                cmp(ComparisonOperatorKind.EQUALS, col("d", "id"), col("e", "did")));
        Join j5 = join(JoinTypeKind.CROSS, j4, subDt, null);
        Join j6 = join(JoinTypeKind.INNER, j5, table("combined", "cmb"),
                cmp(ComparisonOperatorKind.EQUALS, col("a", "k"), col("cmb", "k")));

        QuerySpecification main = SEL.createQuerySpecification();
        DerivedColumn cCustomer = derived(col("a", "name"), "customer");
        DerivedColumn cFull = derived(concat(col("a", "first"), col("a", "last")), "fullname");
        DerivedColumn cCat = derived(col("c", "title"), "cat");
        DerivedColumn cKey = derived(col("cmb", "k"), "key");
        main.getSelectList().add(cCustomer);
        main.getSelectList().add(cFull);
        main.getSelectList().add(cCat);
        main.getSelectList().add(cKey);
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(j6);
        main.setFrom(from);
        // WHERE as a boolean tree: top OR → (AND → '>','IN') and a leaf '='.
        main.setWhere(or(
                and(cmp(ComparisonOperatorKind.GREATER_THAN, col("a", "id"), num(0)),
                        inList(col("a", "region"), str("north"), str("south"))),
                cmp(ComparisonOperatorKind.EQUALS, col("a", "active"), boolLit(TruthValueKind.TRUE))));
        GroupByClause gb = SEL.createGroupByClause();
        OrdinaryGroupingSet gs = SEL.createOrdinaryGroupingSet();
        gs.getElements().add(refTo(cCustomer));
        gs.getElements().add(refTo(cCat));
        gb.getElements().add(gs);
        main.setGroupBy(gb);

        WithClause with = SEL.createWithClause();
        with.getElements().add(cte1);
        with.getElements().add(cte2);
        with.getElements().add(cte3);
        QueryExpression top = SEL.createQueryExpression();
        top.setWithClause(with);
        top.setBody(main);
        OrderByClause ob = SEL.createOrderByClause();
        ob.getSortSpecifications().add(sort(refTo(cCustomer), SortDirectionKind.ASC));
        top.setOrderBy(ob);
        return top;
    }

    /**
     * Typed example: base tables come from the CWM relational model (columns carry data types),
     * referenced via {@link NamedTableReference}. The projected columns inherit their source
     * column's type, so types flow from the tables to the SELECT column set.
     */
    public static QueryExpression typed() {
        Table customer = relTable("customer",
                relCol("id", "BIGINT"), relCol("name", "VARCHAR(100)"), relCol("active", "BOOLEAN"));
        Table orders = relTable("orders",
                relCol("cid", "BIGINT"), relCol("total", "DECIMAL(12,2)"), relCol("status", "VARCHAR(20)"));
        NamedTableReference c = named(customer, "c");
        NamedTableReference o = named(orders, "o");
        Join j = join(JoinTypeKind.INNER, c, o,
                cmp(ComparisonOperatorKind.EQUALS, col("c", "id"), col("o", "cid")));

        QuerySpecification q = SEL.createQuerySpecification();
        q.getSelectList().add(derived(col("c", "name"), "customer"));
        q.getSelectList().add(derived(col("c", "id"), "cid"));
        q.getSelectList().add(derived(col("o", "total"), "amount"));
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(j);
        q.setFrom(from);
        q.setWhere(cmp(ComparisonOperatorKind.GREATER_THAN, col("o", "total"), num(100)));
        return wrap(q);
    }

    private static Table relTable(String name, Column... cols) {
        Table t = RF.createTable();
        t.setName(name);
        for (Column c : cols) {
            t.getFeature().add(c);
        }
        return t;
    }

    private static Column relCol(String name, String type) {
        Column c = RF.createColumn();
        c.setName(name);
        SQLSimpleType st = RF.createSQLSimpleType();
        st.setName(type);
        c.setType(st);
        return c;
    }

    private static NamedTableReference named(Table table, String aliasName) {
        NamedTableReference n = SEL.createNamedTableReference();
        n.setTable(table);
        n.setAliasDefinition(alias(aliasName));
        return n;
    }

    private static WithListElement cte(String name, QueryExpressionBody body) {
        WithListElement e = SEL.createWithListElement();
        e.setQueryName(alias(name));
        e.setQuery(wrap(body));
        return e;
    }

    private static QuerySpecification selectKV(String fromName) {
        QuerySpecification s = SEL.createQuerySpecification();
        s.getSelectList().add(derived(col(fromName, "k"), "k"));
        s.getSelectList().add(derived(col(fromName, "v"), "v"));
        FromClause f = SEL.createFromClause();
        f.getTableReferences().add(table(fromName, fromName));
        s.setFrom(f);
        return s;
    }

    private static QuerySpecification selK(String fromName, NumericLiteral constVal) {
        QuerySpecification s = SEL.createQuerySpecification();
        s.getSelectList().add(derived(col(fromName, "k"), "k"));
        FromClause f = SEL.createFromClause();
        f.getTableReferences().add(table(fromName, fromName));
        s.setFrom(f);
        s.setWhere(cmp(ComparisonOperatorKind.GREATER_THAN, col(fromName, "v"), constVal));
        return s;
    }

    private static SetOperation setOp(SetOperatorKind op, SetQuantifierKind q,
            QueryExpressionBody left, QueryExpressionBody right) {
        SetOperation so = SEL.createSetOperation();
        so.setOperator(op);
        if (q != null) {
            so.setSetQuantifier(q);
        }
        so.setLeft(left);
        so.setRight(right);
        return so;
    }

    private static Join join(JoinTypeKind kind, TableReference left, TableReference right, Expression cond) {
        Join j = SEL.createJoin();
        j.setJoinType(kind);
        j.setLeft(left);
        j.setRight(right);
        if (cond != null) {
            j.setCondition(cond);
        }
        return j;
    }

    private static ProjectionReference refTo(DerivedColumn dc) {
        ProjectionReference p = SEL.createProjectionReference();
        p.setProjection(dc);
        return p;
    }

    private static SortSpecification sort(Expression key, SortDirectionKind dir) {
        SortSpecification s = SQL.createSortSpecification();
        s.setSortKey(key);
        s.setOrdering(dir);
        return s;
    }

    private static InPredicate inList(Expression value, Expression... items) {
        InPredicate in = SQL.createInPredicate();
        in.setValue(value);
        for (Expression it : items) {
            in.getList().add(it);
        }
        return in;
    }

    private static BooleanLiteral boolLit(TruthValueKind v) {
        BooleanLiteral b = SQL.createBooleanLiteral();
        b.setValue(v);
        return b;
    }

    /** Left-folded AND of two or more search conditions. */
    private static Expression and(Expression... parts) {
        Expression acc = parts[0];
        for (int i = 1; i < parts.length; i++) {
            BinaryLogicalExpression a = SQL.createBinaryLogicalExpression();
            a.setOperator(LogicalOperatorKind.AND);
            a.setLeft(acc);
            a.setRight(parts[i]);
            acc = a;
        }
        return acc;
    }

    /** Left-folded OR of two or more search conditions. */
    private static Expression or(Expression... parts) {
        Expression acc = parts[0];
        for (int i = 1; i < parts.length; i++) {
            BinaryLogicalExpression o = SQL.createBinaryLogicalExpression();
            o.setOperator(LogicalOperatorKind.OR);
            o.setLeft(acc);
            o.setRight(parts[i]);
            acc = o;
        }
        return acc;
    }

    /** Left-folded CONCAT (||) of two or more value expressions. */
    private static Expression concat(Expression... parts) {
        Expression acc = parts[0];
        for (int i = 1; i < parts.length; i++) {
            BinaryArithmeticExpression c = SQL.createBinaryArithmeticExpression();
            c.setOperator(ArithmeticOperatorKind.CONCAT);
            c.setLeft(acc);
            c.setRight(parts[i]);
            acc = c;
        }
        return acc;
    }

    private static QueryExpression wrap(QueryExpressionBody body) {
        QueryExpression qe = SEL.createQueryExpression();
        qe.setBody(body);
        return qe;
    }

    private static QuerySpecification simpleSelect(String tableName, DerivedColumn... items) {
        QuerySpecification spec = SEL.createQuerySpecification();
        for (DerivedColumn dc : items) {
            spec.getSelectList().add(dc);
        }
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(table(tableName, null));
        spec.setFrom(from);
        return spec;
    }

    private static QuerySpecification selectStar(String tableName) {
        QuerySpecification spec = SEL.createQuerySpecification();
        Asterisk star = SEL.createAsterisk();
        spec.getSelectList().add(star);
        FromClause from = SEL.createFromClause();
        from.getTableReferences().add(table(tableName, null));
        spec.setFrom(from);
        return spec;
    }

    private static UnresolvedTableReference table(String name, String aliasName) {
        UnresolvedTableReference t = SEL.createUnresolvedTableReference();
        t.setQualifiedName(qname(name));
        if (aliasName != null) {
            t.setAliasDefinition(alias(aliasName));
        }
        return t;
    }

    private static DerivedColumn derived(Expression expr, String aliasName) {
        DerivedColumn dc = SEL.createDerivedColumn();
        dc.setExpression(expr);
        dc.setAliasDefinition(aliasName == null ? SQL.createAliasDefinition() : alias(aliasName));
        return dc;
    }

    private static AliasDefinition alias(String name) {
        AliasDefinition a = SQL.createAliasDefinition();
        a.setAlias(name);
        return a;
    }

    private static QualifiedName qname(String... parts) {
        QualifiedName q = SQL.createQualifiedName();
        for (String p : parts) {
            q.getParts().add(p);
        }
        return q;
    }

    private static ColumnReference col(String... parts) {
        ColumnReference c = SQL.createColumnReference();
        c.setQualifiedName(qname(parts));
        return c;
    }

    private static ComparisonPredicate cmp(ComparisonOperatorKind op, Expression left, Expression right) {
        ComparisonPredicate p = SQL.createComparisonPredicate();
        p.setOperator(op);
        p.setLeft(left);
        p.setRight(right);
        return p;
    }

    private static RoutineInvocation func(String name, Expression... args) {
        RoutineInvocation ri = SQL.createRoutineInvocation();
        ri.setFunctionName(qname(name));
        for (Expression a : args) {
            ri.getArguments().add(a);
        }
        return ri;
    }

    private static AggregateFunction agg(AggregateKind kind, Expression... args) {
        AggregateFunction af = SQL.createAggregateFunction();
        af.setKind(kind);
        for (Expression a : args) {
            af.getArguments().add(a);
        }
        return af;
    }

    private static AggregateFunction countStar() {
        AggregateFunction af = SQL.createAggregateFunction();
        af.setKind(AggregateKind.COUNT_STAR);
        return af;
    }

    private static NumericLiteral num(long v) {
        NumericLiteral l = SQL.createNumericLiteral();
        l.setValue(BigDecimal.valueOf(v));
        return l;
    }

    private static CharacterStringLiteral str(String v) {
        CharacterStringLiteral l = SQL.createCharacterStringLiteral();
        l.setValue(v);
        return l;
    }
}
