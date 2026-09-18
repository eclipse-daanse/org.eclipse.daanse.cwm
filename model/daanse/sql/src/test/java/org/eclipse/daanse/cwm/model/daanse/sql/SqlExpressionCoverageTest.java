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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.math.BigDecimal;

import org.eclipse.daanse.cwm.model.daanse.sql.select.DerivedColumn;
import org.eclipse.daanse.cwm.model.daanse.sql.select.FromClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.OrderByClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QueryExpression;
import org.eclipse.daanse.cwm.model.daanse.sql.select.ProjectionReference;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QuerySpecification;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SelectFactory;
import org.eclipse.daanse.cwm.model.daanse.sql.select.UnresolvedTableReference;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deckt viele Phase-2/3-Konstrukte in einer Anfrage ab und prüft den XMI-Round-Trip:
 * CASE, SUBSTRING, Fensterfunktion (OVER + Frame), EXISTS-Subquery, GROUP BY, ORDER BY mit
 * OFFSET/FETCH WITH TIES.
 */
class SqlExpressionCoverageTest {

    private final SqlFactory sql = SqlFactory.eINSTANCE;
    private final SelectFactory sel = SelectFactory.eINSTANCE;

    @Test
    void richQueryRoundTrips(@TempDir File tmp) throws Exception {
        QuerySpecification spec = sel.createQuerySpecification();
        FromClause from = sel.createFromClause();
        from.getTableReferences().add(unresolved("orders"));
        spec.setFrom(from);

        // 1) CASE WHEN <true> THEN 'a' ELSE 'b' END
        CaseExpression caseExpr = sql.createCaseExpression();
        WhenClause when = sql.createWhenClause();
        BooleanLiteral cond = sql.createBooleanLiteral();
        cond.setValue(TruthValueKind.TRUE);
        when.setCondition(cond);
        when.setResult(str("a"));
        caseExpr.getWhenClauses().add(when);
        caseExpr.setElseResult(str("b"));
        spec.getSelectList().add(col(caseExpr));

        // 2) SUBSTRING(name FROM 1 FOR 3)
        SubstringExpression sub = sql.createSubstringExpression();
        sub.setSource(columnRef("name"));
        sub.setStartPosition(num(1));
        sub.setStringLength(num(3));
        spec.getSelectList().add(col(sub));

        // 3) Fensterfunktion: ROW_NUMBER() OVER (PARTITION BY region ORDER BY ts ROWS ...)
        WindowFunction rowNumber = sql.createWindowFunction();
        rowNumber.setKind(WindowFunctionKind.ROW_NUMBER);
        WindowSpecification win = sql.createWindowSpecification();
        win.getPartitionBy().add(columnRef("region"));
        SortSpecification sortTs = sql.createSortSpecification();
        sortTs.setSortKey(columnRef("ts"));
        sortTs.setOrdering(SortDirectionKind.ASC);
        win.getOrderBy().add(sortTs);
        WindowFrame frame = sql.createWindowFrame();
        frame.setUnits(FrameUnitsKind.ROWS);
        WindowFrameBound start = sql.createWindowFrameBound();
        start.setBoundType(FrameBoundKind.UNBOUNDED_PRECEDING);
        frame.setStart(start);
        win.setFrame(frame);
        rowNumber.setWindow(win);
        spec.getSelectList().add(col(rowNumber));

        // 4) WHERE EXISTS (SELECT * FROM audit)
        ExistsPredicate exists = sql.createExistsPredicate();
        QuerySpecification subSpec = sel.createQuerySpecification();
        subSpec.getSelectList().add(sel.createAsterisk());
        FromClause subFrom = sel.createFromClause();
        subFrom.getTableReferences().add(unresolved("audit"));
        subSpec.setFrom(subFrom);
        QueryExpression subQuery = sel.createQueryExpression();
        subQuery.setBody(subSpec);
        exists.setSubquery(subQuery);
        spec.setWhere(exists);

        // 5) GROUP BY region
        var groupBy = sel.createGroupByClause();
        var grouping = sel.createOrdinaryGroupingSet();
        grouping.getElements().add(columnRef("region"));
        groupBy.getElements().add(grouping);
        spec.setGroupBy(groupBy);

        // 6) ORDER BY ts OFFSET 10 ROWS FETCH FIRST 5 ROWS WITH TIES
        QueryExpression query = sel.createQueryExpression();
        query.setBody(spec);
        OrderByClause orderBy = sel.createOrderByClause();
        SortSpecification ssort = sql.createSortSpecification();
        ssort.setSortKey(columnRef("ts"));
        ssort.setNullOrdering(NullOrderingKind.NULLS_LAST);
        orderBy.getSortSpecifications().add(ssort);
        var offsetFetch = sel.createResultOffsetFetch();
        offsetFetch.setOffset(num(10));
        offsetFetch.setOffsetRows(RowKind.ROWS);
        offsetFetch.setFetchFirst(num(5));
        offsetFetch.setWithTies(true);
        orderBy.setOffsetFetch(offsetFetch);
        query.setOrderBy(orderBy);

        File file = new File(tmp, "rich.xmi");
        ResourceSet rs = newXmi();
        Resource res = rs.createResource(URI.createFileURI(file.getAbsolutePath()));
        res.getContents().add(query);
        res.save(null);

        ResourceSet rs2 = newXmi();
        QueryExpression reloaded =
                (QueryExpression) rs2.getResource(URI.createFileURI(file.getAbsolutePath()), true)
                        .getContents().get(0);

        QuerySpecification rspec = (QuerySpecification) reloaded.getBody();
        assertThat(rspec.getSelectList()).hasSize(3);
        assertThat(((DerivedColumn) rspec.getSelectList().get(0)).getExpression())
                .isInstanceOf(CaseExpression.class);
        assertThat(((DerivedColumn) rspec.getSelectList().get(1)).getExpression())
                .isInstanceOf(SubstringExpression.class);
        WindowFunction wf = (WindowFunction) ((DerivedColumn) rspec.getSelectList().get(2)).getExpression();
        assertThat(wf.getKind()).isEqualTo(WindowFunctionKind.ROW_NUMBER);
        assertThat(wf.getWindow().getFrame().getUnits()).isEqualTo(FrameUnitsKind.ROWS);
        assertThat(rspec.getWhere()).isInstanceOf(ExistsPredicate.class);
        assertThat(rspec.getGroupBy().getElements()).hasSize(1);
        assertThat(reloaded.getOrderBy().getOffsetFetch().isWithTies()).isTrue();
    }

    @Test
    void canonicalUnificationsRoundTrip(@TempDir File tmp) throws Exception {
        QuerySpecification spec = sel.createQuerySpecification();
        FromClause from = sel.createFromClause();
        from.getTableReferences().add(unresolved("t"));
        spec.setFrom(from);

        // U1: CAST(x AS INTEGER) mit kanonischem DataTypeKind
        CastSpecification cast = sql.createCastSpecification();
        cast.setOperand(columnRef("x"));
        DataTypeReference dtr = sql.createDataTypeReference();
        dtr.setKind(DataTypeKind.INTEGER);
        cast.setTargetType(dtr);
        DerivedColumn c0 = col(cast);
        c0.setAliasDefinition(sql.createAliasDefinition()); // anonymer Alias, per Handle referenzierbar
        spec.getSelectList().add(c0);

        // U4: STRING_AGG(name, ',')
        AggregateFunction agg = sql.createAggregateFunction();
        agg.setKind(AggregateKind.STRING_AGG);
        agg.getArguments().add(columnRef("name"));
        agg.setSeparator(str(","));
        spec.getSelectList().add(col(agg));

        // U2: Binärliteral
        BinaryLiteral bin = sql.createBinaryLiteral();
        bin.setHexValue("DEADBEEF");
        bin.setDataType(DataTypeKind.BINARY);
        spec.getSelectList().add(col(bin));

        // U5: WHERE name ~ '^a' (Regex)
        RegexMatchPredicate rx = sql.createRegexMatchPredicate();
        rx.setValue(columnRef("name"));
        rx.setPattern(str("^a"));
        rx.setFlags("i");
        spec.setWhere(rx);

        // U3: ORDER BY <Projektions-Handle auf c0>
        QueryExpression query = sel.createQueryExpression();
        query.setBody(spec);
        OrderByClause orderBy = sel.createOrderByClause();
        var ss = sql.createSortSpecification();
        ProjectionReference pref = sel.createProjectionReference();
        pref.setProjection(c0);
        ss.setSortKey(pref);
        orderBy.getSortSpecifications().add(ss);
        query.setOrderBy(orderBy);

        File file = new File(tmp, "canon.xmi");
        ResourceSet rs = newXmi();
        Resource res = rs.createResource(URI.createFileURI(file.getAbsolutePath()));
        res.getContents().add(query);
        res.save(null);

        ResourceSet rs2 = newXmi();
        QueryExpression reloaded =
                (QueryExpression) rs2.getResource(URI.createFileURI(file.getAbsolutePath()), true)
                        .getContents().get(0);
        QuerySpecification rspec = (QuerySpecification) reloaded.getBody();

        CastSpecification rcast = (CastSpecification) ((DerivedColumn) rspec.getSelectList().get(0)).getExpression();
        assertThat(rcast.getTargetType().getKind()).isEqualTo(DataTypeKind.INTEGER);
        AggregateFunction ragg = (AggregateFunction) ((DerivedColumn) rspec.getSelectList().get(1)).getExpression();
        assertThat(ragg.getKind()).isEqualTo(AggregateKind.STRING_AGG);
        BinaryLiteral rbin = (BinaryLiteral) ((DerivedColumn) rspec.getSelectList().get(2)).getExpression();
        assertThat(rbin.getHexValue()).isEqualTo("DEADBEEF");
        assertThat(rspec.getWhere()).isInstanceOf(RegexMatchPredicate.class);

        // U3: der Projektions-Handle löst nach dem Laden objektidentisch auf die erste Projektion auf.
        ProjectionReference rpref = (ProjectionReference) reloaded.getOrderBy().getSortSpecifications().get(0).getSortKey();
        assertThat(rpref.getProjection()).isSameAs(rspec.getSelectList().get(0));
    }

    private DerivedColumn col(Expression e) {
        DerivedColumn dc = sel.createDerivedColumn();
        dc.setExpression(e);
        return dc;
    }

    private UnresolvedTableReference unresolved(String name) {
        UnresolvedTableReference t = sel.createUnresolvedTableReference();
        t.setQualifiedName(qname(name));
        return t;
    }

    private ColumnReference columnRef(String name) {
        ColumnReference c = sql.createColumnReference();
        c.setQualifiedName(qname(name));
        return c;
    }

    private QualifiedName qname(String name) {
        QualifiedName q = sql.createQualifiedName();
        q.getParts().add(name);
        return q;
    }

    private CharacterStringLiteral str(String v) {
        CharacterStringLiteral l = sql.createCharacterStringLiteral();
        l.setValue(v);
        return l;
    }

    private NumericLiteral num(long v) {
        NumericLiteral l = sql.createNumericLiteral();
        l.setValue(BigDecimal.valueOf(v));
        return l;
    }

    private static ResourceSet newXmi() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        return rs;
    }
}
