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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.junit.jupiter.api.Test;

class TablesTest {

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
    void findPrimaryKey_viaColumnBackReference() {
        Table t = table("EMP", "ID", "NAME");
        Column idCol = (Column) t.getFeature().get(0);

        PrimaryKey pk = RF.createPrimaryKey();
        pk.setName("PK_EMP");
        pk.getFeature().add(idCol);

        assertThat(Tables.findPrimaryKey(t)).hasValueSatisfying(p -> assertThat(p.getName()).isEqualTo("PK_EMP"));
    }

    @Test
    void findPrimaryKey_noPk_empty() {
        Table t = table("EMP", "ID", "NAME");
        assertThat(Tables.findPrimaryKey(t)).isEmpty();
    }

    @Test
    void findUniqueConstraints_dedups() {
        Table t = table("EMP", "A", "B");
        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.setName("UC");
        uc.getFeature().add((Column) t.getFeature().get(0));
        uc.getFeature().add((Column) t.getFeature().get(1));

        assertThat(Tables.findUniqueConstraints(t)).containsExactly(uc);
    }

    @Test
    void findForeignKeys_viaColumnBackReference() {
        Table t = table("ORDER_ITEMS", "ORDER_ID", "PRODUCT_ID");
        Column orderCol = (Column) t.getFeature().get(0);

        ForeignKey fk = RF.createForeignKey();
        fk.setName("FK_ORDER");
        fk.getFeature().add(orderCol);

        assertThat(Tables.findForeignKeys(t)).singleElement()
                .satisfies(f -> assertThat(f.getName()).isEqualTo("FK_ORDER"));
    }

    @Test
    void isValid() {
        assertThat(Tables.isValid(table("T", "C"))).isTrue();
        assertThat(Tables.isValid(table("", "C"))).isFalse();
        assertThat(Tables.isValid(table("T"))).isFalse();
        assertThat(Tables.isValid(null)).isFalse();
    }

    @Test
    void streamVariants_forUniqueAndForeignKeys() {
        Table t = table("EMP", "ID", "DEPT_ID");
        Column id = (Column) t.getFeature().get(0);
        Column dept = (Column) t.getFeature().get(1);

        PrimaryKey pk = RF.createPrimaryKey();
        pk.getFeature().add(id);

        ForeignKey fk = RF.createForeignKey();
        fk.getFeature().add(dept);

        // PrimaryKey is also a UniqueConstraint subtype — it's counted once.
        assertThat(Tables.uniqueConstraintStream(t).count()).isEqualTo(1);
        assertThat(Tables.foreignKeyStream(t).count()).isEqualTo(1);
    }
}
