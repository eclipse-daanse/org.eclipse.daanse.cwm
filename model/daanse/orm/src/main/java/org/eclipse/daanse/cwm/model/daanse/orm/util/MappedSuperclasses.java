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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.MappedSuperclass;

/** Creates {@link MappedSuperclass} mappings; the name is the class name. */
public final class MappedSuperclasses {

    private MappedSuperclasses() {
    }

    /** A mapped superclass for {@code cls}, added to {@code em}. */
    public static MappedSuperclass create(EntityMappings em, Class cls) {
        MappedSuperclass m = CWMORMFactory.eINSTANCE.createMappedSuperclass();
        m.setName(cls.getName());
        m.setCwmClass(cls);
        em.getMappedSuperclass().add(m);
        return m;
    }
}
