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
import org.junit.jupiter.api.Test;

class ForeignKeysTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void columns_filtersToColumnInstances() {
        ForeignKey fk = RF.createForeignKey();
        Column a = RF.createColumn();
        a.setName("A");
        fk.getFeature().add(a);
        assertThat(ForeignKeys.columns(fk)).containsExactly(a);
        assertThat(ForeignKeys.columnStream(fk)).containsExactly(a);
    }

    @Test
    void targetTable_resolvesViaUniqueKey() {
        Table parent = RF.createTable();
        parent.setName("PARENT");
        Column pkCol = RF.createColumn();
        pkCol.setName("ID");
        parent.getFeature().add(pkCol);
        PrimaryKey pk = RF.createPrimaryKey();
        pk.getFeature().add(pkCol);

        ForeignKey fk = RF.createForeignKey();
        fk.setUniqueKey(pk);

        assertThat(ForeignKeys.targetUniqueKey(fk)).hasValue(pk);
        assertThat(ForeignKeys.targetTable(fk)).hasValueSatisfying(t -> assertThat(t.getName()).isEqualTo("PARENT"));
    }

    @Test
    void nullSafe() {
        assertThat(ForeignKeys.columns(null)).isEmpty();
        assertThat(ForeignKeys.columnStream(null)).isEmpty();
        assertThat(ForeignKeys.targetUniqueKey(null)).isEmpty();
        assertThat(ForeignKeys.targetTable(null)).isEmpty();
    }
}
