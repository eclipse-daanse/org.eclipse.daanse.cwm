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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;

/** Creates {@link ManyToOne} mappings; the name is the association end name. */
public final class ManyToOnes {

    private ManyToOnes() {
    }

    /** A many-to-one over {@code join}; {@code join} is {@code null} in a mapped superclass. */
    public static ManyToOne create(ManagedType owner, AssociationEnd end, Join join) {
        ManyToOne m = CWMORMFactory.eINSTANCE.createManyToOne();
        m.setName(end.getName());
        m.setCwmAssociationEnd(end);
        m.setJoin(join);
        owner.getManyToOne().add(m);
        return m;
    }

    /**
     * A many-to-one providing the embedded id: {@code idAttribute} is the attribute of the
     * embedded-id class it provides, {@code null} for the whole id.
     */
    public static ManyToOne mapsId(ManagedType owner, AssociationEnd end, Attribute idAttribute) {
        ManyToOne m = create(owner, end, null);
        m.setMapsId(true);
        m.setCwmMapsIdAttribute(idAttribute);
        return m;
    }
}
