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
import org.eclipse.daanse.cwm.model.daanse.orm.Converter;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;

/** Creates {@link Converter} registrations; the name is the converter class name. */
public final class Converters {

    private Converters() {
    }

    /** Registers {@code cls} as converter in {@code em}. */
    public static Converter create(EntityMappings em, Class cls, boolean autoApply) {
        Converter c = CWMORMFactory.eINSTANCE.createConverter();
        c.setName(cls.getName());
        c.setCwmClass(cls);
        c.setAutoApply(autoApply);
        em.getConverter().add(c);
        return c;
    }
}
