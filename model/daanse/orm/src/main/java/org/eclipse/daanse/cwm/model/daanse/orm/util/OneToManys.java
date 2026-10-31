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
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;

/** Creates {@link OneToMany} mappings; the name is the association end name. */
public final class OneToManys {

    private OneToManys() {
    }

    /** The unidirectional owning side over {@code join}, a foreign key in the target table. */
    public static OneToMany create(ManagedType owner, AssociationEnd end, Join join) {
        OneToMany m = base(owner, end);
        m.setJoin(join);
        return m;
    }

    /** The owning side over a join table. */
    public static OneToMany create(ManagedType owner, AssociationEnd end, JoinTable joinTable) {
        OneToMany m = base(owner, end);
        m.setJoinTable(joinTable);
        return m;
    }

    /** The inverse side of a many-to-one mapped on the other class. */
    public static OneToMany inverse(ManagedType owner, AssociationEnd end) {
        OneToMany m = base(owner, end);
        m.setOwningSide(false);
        return m;
    }

    private static OneToMany base(ManagedType owner, AssociationEnd end) {
        OneToMany m = CWMORMFactory.eINSTANCE.createOneToMany();
        m.setName(end.getName());
        m.setCwmAssociationEnd(end);
        owner.getOneToMany().add(m);
        return m;
    }
}
