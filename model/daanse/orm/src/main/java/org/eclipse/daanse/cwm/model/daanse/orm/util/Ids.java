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
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;

/** Creates {@link Id} mappings; the name is the attribute name. */
public final class Ids {

    private Ids() {
    }

    /** An id mapping of {@code attribute} onto the primary-key column {@code column}. */
    public static Id create(IdentifiableType owner, Attribute attribute, Column column) {
        Id id = create(owner, attribute);
        id.setColumn(Columns.of(column));
        return id;
    }

    /** An id mapping without column, for a mapped superclass whose entities override it. */
    public static Id create(IdentifiableType owner, Attribute attribute) {
        Id id = CWMORMFactory.eINSTANCE.createId();
        id.setName(attribute.getName());
        id.setCwmAttribute(attribute);
        owner.getId().add(id);
        return id;
    }
}
