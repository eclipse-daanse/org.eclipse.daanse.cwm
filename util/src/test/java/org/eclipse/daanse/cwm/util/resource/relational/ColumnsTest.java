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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.junit.jupiter.api.Test;

class ColumnsTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void ownerVariants() {
        Table t = RF.createTable();
        t.setName("T");
        Column c = RF.createColumn();
        c.setName("X");
        t.getFeature().add(c);

        assertThat(Columns.owner(c)).isPresent();
        assertThat(Columns.namedOwner(c)).hasValueSatisfying(ncs -> assertThat(ncs.getName()).isEqualTo("T"));
        assertThat(Columns.tableOwner(c)).hasValueSatisfying(tab -> assertThat(tab.getName()).isEqualTo("T"));
    }

    @Test
    void tableOwner_empty_whenOwnerIsView() {
        View v = RF.createView();
        v.setName("V");
        Column c = RF.createColumn();
        c.setName("X");
        v.getFeature().add(c);

        assertThat(Columns.tableOwner(c)).isEmpty();
        assertThat(Columns.namedOwner(c)).isPresent();
    }

    @Test
    void owner_detached_empty() {
        Column c = RF.createColumn();
        c.setName("X");
        assertThat(Columns.owner(c)).isEmpty();
        assertThat(Columns.namedOwner(c)).isEmpty();
        assertThat(Columns.tableOwner(c)).isEmpty();
    }

    @Test
    void qualifiedName_fullChain() {
        Catalog cat = RF.createCatalog();
        cat.setName("CAT");
        Schema sch = RF.createSchema();
        sch.setName("SCH");
        cat.getOwnedElement().add(sch);
        Table t = RF.createTable();
        t.setName("T");
        sch.getOwnedElement().add(t);
        Column c = RF.createColumn();
        c.setName("X");
        t.getFeature().add(c);

        assertThat(Columns.qualifiedName(c)).isEqualTo("CAT.SCH.T.X");
    }

    @Test
    void qualifiedName_detached_onlyColumnName() {
        Column c = RF.createColumn();
        c.setName("X");
        assertThat(Columns.qualifiedName(c)).isEqualTo("X");
    }

    @Test
    void isValid() {
        Column named = RF.createColumn();
        named.setName("X");
        assertThat(Columns.isValid(named)).isTrue();
        assertThat(Columns.isValid(RF.createColumn())).isFalse();
        assertThat(Columns.isValid(null)).isFalse();
    }
}
