/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.sql.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.SynonymFactory;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Failure;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.ProducedColumn;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Resolution;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.SqlResolver;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.internal.SqlResolverFactoryImpl;
import org.junit.jupiter.api.Test;

/**
 * Queries through {@link Synonym}s resolve to the target's real columns and
 * tables; unresolvable synonyms fail like an unknown table.
 */
class SqlResolverSynonymTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private final Catalog catalog = catalog();
    private final Schema hr = schema("hr");
    private final Table employee = table(hr, "employee", "id", "name", "dept_id");
    private final Table dept = table(hr, "dept", "id", "title");

    @Test
    void selectThroughSynonymUsesTargetColumns() {
        synonym(hr, "emp", employee);

        Resolution res = resolver(hr).resolve("select e.name from emp e where e.id = 1");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(col(employee, "name"), col(employee, "id")), res.columnsUsed());
        assertEquals(Set.of(employee), res.tablesUsed());
        assertEquals(List.of(new ProducedColumn("name", col(employee, "name"))), res.producedColumns());
    }

    @Test
    void joinOfSynonymAndTable() {
        synonym(hr, "emp", employee);

        Resolution res = resolver(hr).resolve("select e.name, d.title from emp e join dept d on e.dept_id = d.id");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(employee, dept), res.tablesUsed());
        assertTrue(res.columnsUsed().contains(col(employee, "dept_id")));
    }

    @Test
    void chainResolvesToFinalTable() {
        Synonym emp = synonym(hr, "emp", employee);
        synonym(hr, "staff", emp);

        Resolution res = resolver(hr).resolve("select name from staff");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(col(employee, "name")), res.columnsUsed());
    }

    @Test
    void synonymToView() {
        View v = RF.createView();
        v.setName("v_emp");
        v.getFeature().add(column("name"));
        hr.getOwnedElement().add(v);
        synonym(hr, "emp_view", v);

        Resolution res = resolver(hr).resolve("select name from emp_view");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(v), res.tablesUsed());
    }

    @Test
    void qualifiedSynonymInOtherSchema() {
        Schema app = schema("app");
        synonym(app, "emp", employee);

        Resolution res = resolver(hr, app).resolve("select name from app.emp");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(employee), res.tablesUsed());
    }

    @Test
    void targetSchemaNeedNotBePassed() {
        // Only the synonym's schema is in scope; the target table lives elsewhere.
        Schema app = schema("app");
        synonym(app, "emp", employee);

        Resolution res = resolver(app).resolve("select name from emp");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(col(employee, "name")), res.columnsUsed());
    }

    @Test
    void tableWinsOverSynonymOfSameName() {
        synonym(hr, "dept", employee);

        Resolution res = resolver(hr).resolve("select title from dept");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(dept), res.tablesUsed());
    }

    @Test
    void unresolvedSynonymFailsAsUnknownTable() {
        Synonym remote = synonym(hr, "emp_remote", null);
        remote.setDbLink("HQ");

        Resolution res = resolver(hr).resolve("select name from emp_remote");

        assertFalse(res.ok());
        assertEquals(Optional.of(Failure.UNKNOWN_TABLE), res.failure());
    }

    @Test
    void synonymToProcedureIsNotARelation() {
        Procedure fn = RF.createProcedure();
        fn.setName("fn_bonus");
        hr.getOwnedElement().add(fn);
        synonym(hr, "bonus", fn);

        Resolution res = resolver(hr).resolve("select x from bonus");

        assertFalse(res.ok());
        assertEquals(Optional.of(Failure.UNKNOWN_TABLE), res.failure());
    }

    @Test
    void cyclicSynonymsAreNotRegistered() {
        Synonym a = synonym(hr, "a", null);
        Synonym b = synonym(hr, "b", a);
        a.setTarget(b);

        Resolution res = resolver(hr).resolve("select name from a");

        assertFalse(res.ok());
        assertEquals(Optional.of(Failure.UNKNOWN_TABLE), res.failure());
    }

    @Test
    void publicSynonymIsVisibleUnqualifiedInCurrentSchema() {
        Schema pub = schema("PUBLIC");
        synonym(pub, "all_emp", employee).setIsPublic(true);
        Schema app = schema("app");

        // Only "app" is passed — PUBLIC synonyms of the catalog are visible anyway.
        Resolution res = resolver(app).resolve("select name from all_emp");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(employee), res.tablesUsed());
    }

    @Test
    void ownObjectWinsOverPublicSynonym() {
        Schema pub = schema("PUBLIC");
        synonym(pub, "dept", employee).setIsPublic(true);

        Resolution res = resolver(hr).resolve("select title from dept");

        assertTrue(res.ok(), res::message);
        assertEquals(Set.of(dept), res.tablesUsed());
    }

    @Test
    void nonPublicSynonymOfOtherSchemaIsNotVisibleUnqualified() {
        Schema app = schema("app");
        synonym(app, "emp", employee);

        Resolution res = resolver(hr).resolve("select name from emp");

        assertFalse(res.ok());
        assertEquals(Optional.of(Failure.UNKNOWN_TABLE), res.failure());
    }

    private static SqlResolver resolver(Schema... schemas) {
        return new SqlResolverFactoryImpl().create(List.of(schemas));
    }

    private static Catalog catalog() {
        Catalog c = RF.createCatalog();
        c.setName("db");
        return c;
    }

    private Schema schema(String name) {
        Schema s = RF.createSchema();
        s.setName(name);
        catalog.getOwnedElement().add(s);
        return s;
    }

    private static Table table(Schema schema, String name, String... columns) {
        Table t = RF.createTable();
        t.setName(name);
        for (String c : columns)
            t.getFeature().add(column(c));
        schema.getOwnedElement().add(t);
        return t;
    }

    private static Column column(String name) {
        Column c = RF.createColumn();
        c.setName(name);
        return c;
    }

    private static Column col(Table table, String name) {
        return table.getFeature().stream().filter(Column.class::isInstance).map(Column.class::cast)
                .filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    private static Synonym synonym(Schema schema, String name, ModelElement target) {
        Synonym s = SynonymFactory.eINSTANCE.createSynonym();
        s.setName(name);
        s.setTarget(target);
        s.setTargetName(target == null ? name : target.getName());
        schema.getOwnedElement().add(s);
        return s;
    }
}
