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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.daanse.sql.select.DerivedColumn;
import org.eclipse.daanse.cwm.model.daanse.sql.select.FromClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.Join;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QueryExpression;
import org.eclipse.daanse.cwm.model.daanse.sql.select.QuerySpecification;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SelectFactory;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SelectPackage;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SetOperation;
import org.eclipse.daanse.cwm.model.daanse.sql.select.UnresolvedTableReference;
import org.eclipse.daanse.cwm.model.daanse.sql.select.WithClause;
import org.eclipse.daanse.cwm.model.daanse.sql.select.WithListElement;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Baut SELECT-Modellinstanzen ausschliesslich ueber die generierten EMF-Factories,
 * serialisiert sie als XMI, laedt sie zurueck und prueft den Inhalt.
 *
 * <p>Das Modell ist semantisch (nicht SQL-syntaktisch): der {@code Join} ist ein Ueberbegriff
 * mit Typ + allgemeiner Bedingung, Aliase sind anonyme Identitaeten (Name optional).</p>
 *
 * <pre>
 * WITH recent AS (SELECT * FROM orders)
 * SELECT &lt;anon&gt;   FROM JOIN(INNER, orders o, customers c; condition)
 * UNION ALL
 * SELECT &lt;anon&gt;   FROM recent
 * </pre>
 */
class SqlFixtureTest {

    private final SqlFactory sql = SqlFactory.eINSTANCE;
    private final SelectFactory sel = SelectFactory.eINSTANCE;

    @Test
    void buildSerializeAndReload(@TempDir File tmp) throws Exception {
        QueryExpression query = buildQuery();

        File file = new File(tmp, "fixture.xmi");
        ResourceSet rs = newXmiResourceSet();
        Resource res = rs.createResource(URI.createFileURI(file.getAbsolutePath()));
        res.getContents().add(query);
        res.save(null);

        assertThat(file).isFile();
        assertThat(file.length()).isPositive();

        ResourceSet rs2 = newXmiResourceSet();
        Resource loaded = rs2.getResource(URI.createFileURI(file.getAbsolutePath()), true);
        assertThat(loaded.getContents()).hasSize(1);

        QueryExpression reloaded = (QueryExpression) loaded.getContents().get(0);
        assertThat(reloaded.getWithClause()).isNotNull();
        assertThat(reloaded.getWithClause().getElements()).hasSize(1);
        assertThat(reloaded.getBody()).isInstanceOf(SetOperation.class);

        SetOperation setOp = (SetOperation) reloaded.getBody();
        assertThat(setOp.getOperator()).isEqualTo(SetOperatorKind.UNION);
        assertThat(setOp.getSetQuantifier()).isEqualTo(SetQuantifierKind.ALL);
        assertThat(setOp.getLeft()).isInstanceOf(QuerySpecification.class);

        QuerySpecification left = (QuerySpecification) setOp.getLeft();
        assertThat(left.getFrom().getTableReferences()).hasSize(1);
        assertThat(left.getFrom().getTableReferences().get(0)).isInstanceOf(Join.class);

        Join join = (Join) left.getFrom().getTableReferences().get(0);
        assertThat(join.getJoinType()).isEqualTo(JoinTypeKind.INNER);
        assertThat(join.getCondition()).isNotNull();

        // Anonymer Alias: vorhanden, aber ohne Namen (Serializer erzeugt ihn spaeter).
        DerivedColumn col = (DerivedColumn) left.getSelectList().get(0);
        assertThat(col.getAliasDefinition()).isNotNull();
        assertThat(col.getAliasDefinition().getAlias()).isNull();
    }

    @Test
    void joinCanCarryCwmDescription(@TempDir File tmp) throws Exception {
        // Dank Vererbung SqlElement -> CWM ModelElement traegt jeder Knoten (auch Join) die
        // mehrwertige CWM-description-Referenz auf Description-Objekte.
        Join join = sel.createJoin();
        join.setJoinType(JoinTypeKind.LEFT);
        join.setLeft(unresolved("a"));
        join.setRight(unresolved("b"));

        Description desc = BusinessinformationFactory.eINSTANCE.createDescription();
        desc.setBody("Linker Verbund von a und b");
        desc.getModelElement().add(join);

        File file = new File(tmp, "join.xmi");
        ResourceSet rs = newXmiResourceSet();
        Resource res = rs.createResource(URI.createFileURI(file.getAbsolutePath()));
        // 'description' ist non-containment; das Description-Objekt braucht ein eigenes Zuhause.
        res.getContents().add(join);
        res.getContents().add(desc);
        res.save(null);

        ResourceSet rs2 = newXmiResourceSet();
        Resource loaded = rs2.getResource(URI.createFileURI(file.getAbsolutePath()), true);
        Join reloaded = (Join) loaded.getContents().get(0);
        // ModelElement has no `description` back-reference (MOF Reference Closure);
        // assert from the Description side, which is the direction CWM declares.
        Description reloadedDesc = (Description) loaded.getContents().get(1);
        assertThat(reloadedDesc.getModelElement()).containsExactly(reloaded);
        assertThat(reloadedDesc.getBody()).isEqualTo("Linker Verbund von a und b");
    }

    @Test
    void packageNamespacesAreRegistered() {
        assertThat(SqlPackage.eNS_URI)
                .isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.sql");
        assertThat(SelectPackage.eNS_URI)
                .isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.sql/select");
    }

    // ---------------------------------------------------------------------

    private QueryExpression buildQuery() {
        QuerySpecification leftSpec = sel.createQuerySpecification();

        UnresolvedTableReference orders = unresolved("orders");
        orders.setAliasDefinition(namedAlias("o"));
        UnresolvedTableReference customers = unresolved("customers");
        customers.setAliasDefinition(namedAlias("c"));

        Join join = sel.createJoin();
        join.setJoinType(JoinTypeKind.INNER);
        join.setLeft(orders);
        join.setRight(customers);
        join.setCondition(trueCondition());

        FromClause from = sel.createFromClause();
        from.getTableReferences().add(join);
        leftSpec.setFrom(from);
        leftSpec.getSelectList().add(anonymousColumn("o"));

        QuerySpecification rightSpec = sel.createQuerySpecification();
        FromClause rightFrom = sel.createFromClause();
        rightFrom.getTableReferences().add(unresolved("recent"));
        rightSpec.setFrom(rightFrom);
        rightSpec.getSelectList().add(anonymousColumn("o"));

        SetOperation union = sel.createSetOperation();
        union.setOperator(SetOperatorKind.UNION);
        union.setSetQuantifier(SetQuantifierKind.ALL);
        union.setLeft(leftSpec);
        union.setRight(rightSpec);

        QuerySpecification cteSpec = sel.createQuerySpecification();
        cteSpec.getSelectList().add(sel.createAsterisk());
        FromClause cteFrom = sel.createFromClause();
        cteFrom.getTableReferences().add(unresolved("orders"));
        cteSpec.setFrom(cteFrom);
        QueryExpression cteQuery = sel.createQueryExpression();
        cteQuery.setBody(cteSpec);

        WithListElement cte = sel.createWithListElement();
        cte.setQueryName(namedAlias("recent"));
        cte.setQuery(cteQuery);
        WithClause with = sel.createWithClause();
        with.getElements().add(cte);

        QueryExpression query = sel.createQueryExpression();
        query.setWithClause(with);
        query.setBody(union);
        return query;
    }

    private UnresolvedTableReference unresolved(String name) {
        UnresolvedTableReference t = sel.createUnresolvedTableReference();
        t.setQualifiedName(qname(name));
        return t;
    }

    private QualifiedName qname(String... parts) {
        QualifiedName q = sql.createQualifiedName();
        for (String p : parts) {
            q.getParts().add(p);
        }
        return q;
    }

    /** Alias mit explizitem Namen (z.B. Korrelationsname o/c). */
    private AliasDefinition namedAlias(String name) {
        AliasDefinition a = sql.createAliasDefinition();
        a.setAlias(name);
        return a;
    }

    /** Projektierte Spalte mit anonymem Alias (Name wird erst beim Serialisieren erzeugt). */
    private DerivedColumn anonymousColumn(String columnName) {
        ColumnReference ref = sql.createColumnReference();
        ref.setQualifiedName(qname(columnName));
        DerivedColumn dc = sel.createDerivedColumn();
        dc.setExpression(ref);
        dc.setAliasDefinition(sql.createAliasDefinition()); // anonym: kein Name gesetzt
        return dc;
    }

    private Expression trueCondition() {
        BooleanLiteral lit = sql.createBooleanLiteral();
        lit.setValue(TruthValueKind.TRUE);
        return lit;
    }

    private static ResourceSet newXmiResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        return rs;
    }
}
