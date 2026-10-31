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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;

/**
 * Creates {@link Entity} mappings. The entity name is the class name unless given; the table is
 * {@code null} only for a {@code SINGLE_TABLE} subclass.
 */
public final class Entities {

    private Entities() {
    }

    /** An entity for {@code cls} on {@code table}, added to {@code em}; entity name = class name. */
    public static Entity create(EntityMappings em, Class cls, NamedColumnSet table) {
        return create(em, cls.getName(), cls, table);
    }

    /** An entity with its own JPA entity name. */
    public static Entity create(EntityMappings em, String entityName, Class cls, NamedColumnSet table) {
        Entity e = CWMORMFactory.eINSTANCE.createEntity();
        e.setName(entityName);
        e.setCwmClass(cls);
        e.setCwmTable(table);
        em.getEntity().add(e);
        return e;
    }
}
