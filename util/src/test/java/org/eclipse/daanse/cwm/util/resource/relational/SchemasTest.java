/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.util.resource.relational;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.junit.jupiter.api.Test;

class SchemasTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void tablesAndViews_areTypeFiltered() {
        Schema sch = RF.createSchema();
        sch.setName("PUBLIC");
        Table t = RF.createTable();
        t.setName("EMP");
        sch.getOwnedElement().add(t);
        sch.getOwnedElement().add(RF.createView());

        assertThat(Schemas.tables(sch)).singleElement().extracting(Table::getName).isEqualTo("EMP");
        assertThat(Schemas.views(sch)).hasSize(1);
        assertThat(Schemas.columnSets(sch)).hasSize(2);
    }

    @Test
    void findCatalog_viaNamespaceWalk() {
        Catalog cat = RF.createCatalog();
        cat.setName("MYCAT");
        Schema sch = RF.createSchema();
        sch.setName("PUBLIC");
        cat.getOwnedElement().add(sch);

        assertThat(Schemas.findCatalog(sch)).hasValueSatisfying(c -> assertThat(c.getName()).isEqualTo("MYCAT"));
    }

    @Test
    void findCatalog_detached_empty() {
        Schema sch = RF.createSchema();
        assertThat(Schemas.findCatalog(sch)).isEmpty();
    }

    @Test
    void findTableAndView_byName() {
        Schema sch = RF.createSchema();
        Table t = RF.createTable();
        t.setName("EMP");
        sch.getOwnedElement().add(t);

        assertThat(Schemas.findTable(sch, "EMP")).isPresent();
        assertThat(Schemas.findTable(sch, "MISSING")).isEmpty();
        assertThat(Schemas.findView(sch, "EMP")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Schemas.tables(null)).isEmpty();
        assertThat(Schemas.findCatalog(null)).isEmpty();
        assertThat(Schemas.findTable(null, "X")).isEmpty();
        assertThat(Schemas.findTable(RF.createSchema(), null)).isEmpty();
    }

    @Test
    void qualifiedName_includesCatalog() {
        Catalog cat = RF.createCatalog();
        cat.setName("CAT");
        Schema sch = RF.createSchema();
        sch.setName("SCH");
        cat.getOwnedElement().add(sch);
        assertThat(Schemas.qualifiedName(sch)).isEqualTo("CAT.SCH");
    }

    @Test
    void qualifiedName_detachedReturnsBareName() {
        Schema sch = RF.createSchema();
        sch.setName("ORPHAN");
        assertThat(Schemas.qualifiedName(sch)).isEqualTo("ORPHAN");
    }

}
