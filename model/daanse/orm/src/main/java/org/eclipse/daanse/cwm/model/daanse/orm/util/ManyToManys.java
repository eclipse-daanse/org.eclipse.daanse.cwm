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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToMany;

/** Creates {@link ManyToMany} mappings; the name is the association end name. */
public final class ManyToManys {

    private ManyToManys() {
    }

    /** The owning side over {@code joinTable}. */
    public static ManyToMany create(ManagedType owner, AssociationEnd end, JoinTable joinTable) {
        ManyToMany m = base(owner, end);
        m.setJoinTable(joinTable);
        return m;
    }

    /** The inverse side; the opposite end owns the join table. */
    public static ManyToMany inverse(ManagedType owner, AssociationEnd end) {
        ManyToMany m = base(owner, end);
        m.setOwningSide(false);
        return m;
    }

    private static ManyToMany base(ManagedType owner, AssociationEnd end) {
        ManyToMany m = CWMORMFactory.eINSTANCE.createManyToMany();
        m.setName(end.getName());
        m.setCwmAssociationEnd(end);
        owner.getManyToMany().add(m);
        return m;
    }
}
