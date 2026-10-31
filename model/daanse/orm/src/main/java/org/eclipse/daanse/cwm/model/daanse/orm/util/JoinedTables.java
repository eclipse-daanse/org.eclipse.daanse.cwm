/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionTable;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.SecondaryTable;

/**
 * Creates the tables reached over a join. The table is not set: it is the table of the join's
 * columns, and the name is that table's name.
 */
public final class JoinedTables {

    private JoinedTables() {
    }

    /** A secondary table of {@code entity}, joined to the primary table by {@code join}. */
    public static SecondaryTable secondary(Entity entity, Join join) {
        SecondaryTable st = CWMORMFactory.eINSTANCE.createSecondaryTable();
        st.setJoin(join);
        st.setName(name(join));
        entity.getSecondaryTable().add(st);
        return st;
    }

    /** A collection table, joined to the owner's table by {@code join}. */
    public static CollectionTable collection(Join join) {
        CollectionTable ct = CWMORMFactory.eINSTANCE.createCollectionTable();
        ct.setJoin(join);
        ct.setName(name(join));
        return ct;
    }

    /** A join table: {@code join} towards the owning side, {@code inverseJoin} towards the target. */
    public static JoinTable join(Join join, Join inverseJoin) {
        JoinTable jt = CWMORMFactory.eINSTANCE.createJoinTable();
        jt.setJoin(join);
        jt.setInverseJoin(inverseJoin);
        jt.setName(name(join));
        return jt;
    }

    private static String name(Join join) {
        var table = OrmBindings.tableOf(join);
        return table == null ? null : table.getName();
    }
}
