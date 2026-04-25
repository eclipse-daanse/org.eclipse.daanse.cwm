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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.junit.jupiter.api.Test;

class NamedColumnSetsTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private static Table table(String name, String... cols) {
        Table t = RF.createTable();
        t.setName(name);
        for (String cn : cols) {
            Column c = RF.createColumn();
            c.setName(cn);
            t.getFeature().add(c);
        }
        return t;
    }

    @Test
    void findSchemaAndCatalog_viaNamespaceWalk() {
        Catalog cat = RF.createCatalog();
        cat.setName("MYCAT");
        Schema sch = RF.createSchema();
        sch.setName("PUBLIC");
        cat.getOwnedElement().add(sch);
        Table t = table("EMP", "ID");
        sch.getOwnedElement().add(t);

        assertThat(NamedColumnSets.findSchema(t)).hasValueSatisfying(s -> assertThat(s.getName()).isEqualTo("PUBLIC"));
        assertThat(NamedColumnSets.findCatalog(t)).hasValueSatisfying(c -> assertThat(c.getName()).isEqualTo("MYCAT"));
        assertThat(NamedColumnSets.qualifiedName(t)).isEqualTo("MYCAT.PUBLIC.EMP");
    }

    @Test
    void qualifiedName_detached_onlyTableName() {
        Table t = table("ORPHAN");
        assertThat(NamedColumnSets.findSchema(t)).isEmpty();
        assertThat(NamedColumnSets.findCatalog(t)).isEmpty();
        assertThat(NamedColumnSets.qualifiedName(t)).isEqualTo("ORPHAN");
    }

    @Test
    void nullSafe() {
        assertThat(NamedColumnSets.qualifiedName(null)).isNull();
    }
}
