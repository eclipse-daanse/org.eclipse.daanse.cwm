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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionTable;
import org.eclipse.daanse.cwm.model.daanse.orm.ElementCollection;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;

/** Creates {@link ElementCollection} mappings; the name is the attribute name. */
public final class ElementCollections {

    private ElementCollections() {
    }

    /**
     * An element collection of {@code attribute} in {@code table}; {@code column} holds a basic
     * element, {@code null} for embeddable elements.
     */
    public static ElementCollection create(ManagedType owner, Attribute attribute, CollectionTable table,
            Column column) {
        ElementCollection ec = CWMORMFactory.eINSTANCE.createElementCollection();
        ec.setName(attribute.getName());
        ec.setCwmAttribute(attribute);
        ec.setCollectionTable(table);
        if (column != null) {
            ec.setColumn(Columns.of(column));
        }
        owner.getElementCollection().add(ec);
        return ec;
    }
}
