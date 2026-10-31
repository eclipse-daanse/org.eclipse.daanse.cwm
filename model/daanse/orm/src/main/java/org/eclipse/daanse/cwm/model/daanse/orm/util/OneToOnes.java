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
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToOne;

/** Creates {@link OneToOne} mappings; the name is the association end name. */
public final class OneToOnes {

    private OneToOnes() {
    }

    /** The owning side over {@code join} (foreign key in the owner's table). */
    public static OneToOne create(ManagedType owner, AssociationEnd end, Join join) {
        OneToOne m = base(owner, end);
        m.setJoin(join);
        return m;
    }

    /** The owning side over the shared primary key. */
    public static OneToOne primaryKeyJoin(ManagedType owner, AssociationEnd end, Join join) {
        OneToOne m = base(owner, end);
        m.setPrimaryKeyJoin(join);
        return m;
    }

    /** The inverse side; the opposite end is mapped as owning side on the other class. */
    public static OneToOne inverse(ManagedType owner, AssociationEnd end) {
        OneToOne m = base(owner, end);
        m.setOwningSide(false);
        return m;
    }

    private static OneToOne base(ManagedType owner, AssociationEnd end) {
        OneToOne m = CWMORMFactory.eINSTANCE.createOneToOne();
        m.setName(end.getName());
        m.setCwmAssociationEnd(end);
        owner.getOneToOne().add(m);
        return m;
    }
}
