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
import org.eclipse.daanse.cwm.model.daanse.orm.Embeddable;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;

/** Creates {@link Embeddable} mappings; the name is the class name. */
public final class Embeddables {

    private Embeddables() {
    }

    /** An embeddable for {@code cls}, added to {@code em}. */
    public static Embeddable create(EntityMappings em, Class cls) {
        Embeddable e = CWMORMFactory.eINSTANCE.createEmbeddable();
        e.setName(cls.getName());
        e.setCwmClass(cls);
        em.getEmbeddable().add(e);
        return e;
    }
}
