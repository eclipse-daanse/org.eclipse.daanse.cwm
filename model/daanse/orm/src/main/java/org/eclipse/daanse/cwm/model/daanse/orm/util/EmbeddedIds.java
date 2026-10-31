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
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.EmbeddedId;
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;

/** Creates {@link EmbeddedId} mappings; the name is the attribute name. */
public final class EmbeddedIds {

    private EmbeddedIds() {
    }

    /** The embedded id of {@code owner} for {@code attribute}; columns follow as overrides. */
    public static EmbeddedId create(IdentifiableType owner, Attribute attribute) {
        EmbeddedId id = CWMORMFactory.eINSTANCE.createEmbeddedId();
        id.setName(attribute.getName());
        id.setCwmAttribute(attribute);
        owner.setEmbeddedId(id);
        return id;
    }
}
