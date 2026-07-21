/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
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
    void findPrimaryKey_viaOwnedElement() {
        Table t = table("EMP", "ID", "NAME");
        Column idCol = (Column) t.getFeature().get(0);

        PrimaryKey pk = RF.createPrimaryKey();
        pk.setName("PK_EMP");
        pk.getFeature().add(idCol);
        t.getOwnedElement().add(pk);

        assertThat(Tables.findPrimaryKey(t)).hasValueSatisfying(p -> assertThat(p.getName()).isEqualTo("PK_EMP"));
    }

    @Test
    void findPrimaryKey_noPk_empty() {
        Table t = table("EMP", "ID", "NAME");
        assertThat(Tables.findPrimaryKey(t)).isEmpty();
    }

    @Test
    void uniqueConstraints_dedups() {
        Table t = table("EMP", "A", "B");
        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.setName("UC");
        uc.getFeature().add((Column) t.getFeature().get(0));
        uc.getFeature().add((Column) t.getFeature().get(1));
        t.getOwnedElement().add(uc);

        assertThat(Tables.uniqueConstraints(t)).containsExactly(uc);
    }

    @Test
    void foreignKeys_viaOwnedElement() {
        Table t = table("ORDER_ITEMS", "ORDER_ID", "PRODUCT_ID");
        Column orderCol = (Column) t.getFeature().get(0);

        ForeignKey fk = RF.createForeignKey();
        fk.setName("FK_ORDER");
        fk.getFeature().add(orderCol);
        t.getOwnedElement().add(fk);

        assertThat(Tables.foreignKeys(t)).singleElement()
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
    void singleColumnUniqueColumns_viaKeysAndUniqueIndexes() {
        Schema schema = RF.createSchema();
        schema.setName("S");
        Table t = table("EMP", "ID", "EMAIL", "BADGE", "FIRST", "LAST");
        schema.getOwnedElement().add(t);
        Column id = (Column) t.getFeature().get(0);
        Column email = (Column) t.getFeature().get(1);
        Column badge = (Column) t.getFeature().get(2);
        Column first = (Column) t.getFeature().get(3);
        Column last = (Column) t.getFeature().get(4);

        // single-column PK -> unique
        PrimaryKey pk = RF.createPrimaryKey();
        pk.getFeature().add(id);
        t.getOwnedElement().add(pk);

        // single-column unique constraint -> unique
        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.getFeature().add(email);
        t.getOwnedElement().add(uc);

        // multi-column unique constraint -> NOT single-column unique
        UniqueConstraint ucMulti = RF.createUniqueConstraint();
        ucMulti.getFeature().add(first);
        ucMulti.getFeature().add(last);
        t.getOwnedElement().add(ucMulti);

        // single-column unique index -> unique
        addIndex(schema, t, true, badge);
        // single-column NON-unique index -> not unique
        addIndex(schema, t, false, first);
        // multi-column unique index -> not single-column unique
        addIndex(schema, t, true, first, last);
        // duplicate coverage (unique index on the PK column) -> deduplicated
        addIndex(schema, t, true, id);

        assertThat(Tables.singleColumnUniqueColumns(t)).containsExactly(id, email, badge);
    }

    @Test
    void singleColumnUniqueColumns_noSchema_keysStillFound() {
        Table t = table("EMP", "ID");
        Column id = (Column) t.getFeature().get(0);
        PrimaryKey pk = RF.createPrimaryKey();
        pk.getFeature().add(id);
        t.getOwnedElement().add(pk);

        assertThat(Tables.singleColumnUniqueColumns(t)).containsExactly(id);
    }

    private static void addIndex(Schema schema, Table t, boolean unique, Column... cols) {
        SQLIndex idx = RF.createSQLIndex();
        idx.setIsUnique(unique);
        idx.setSpannedClass(t);
        for (Column c : cols) {
            SQLIndexColumn ic = RF.createSQLIndexColumn();
            ic.setFeature(c);
            idx.getIndexedFeature().add(ic);
        }
        schema.getOwnedElement().add(idx);
    }

    @Test
    void streamVariants_forUniqueAndForeignKeys() {
        Table t = table("EMP", "ID", "DEPT_ID");
        Column id = (Column) t.getFeature().get(0);
        Column dept = (Column) t.getFeature().get(1);

        PrimaryKey pk = RF.createPrimaryKey();
        pk.getFeature().add(id);
        t.getOwnedElement().add(pk);

        ForeignKey fk = RF.createForeignKey();
        fk.getFeature().add(dept);
        t.getOwnedElement().add(fk);

        // PrimaryKey is also a UniqueConstraint subtype — it's counted once.
        assertThat(Tables.uniqueConstraintStream(t).count()).isEqualTo(1);
        assertThat(Tables.foreignKeyStream(t).count()).isEqualTo(1);
    }
}
