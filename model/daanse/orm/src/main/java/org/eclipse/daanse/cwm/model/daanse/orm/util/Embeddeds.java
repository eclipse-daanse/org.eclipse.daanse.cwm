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
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;

/** Creates {@link Embedded} mappings; the name is the attribute name. */
public final class Embeddeds {

    private Embeddeds() {
    }

    /** An embedded mapping of {@code attribute}; columns follow as overrides when needed. */
    public static Embedded create(ManagedType owner, Attribute attribute) {
        Embedded e = CWMORMFactory.eINSTANCE.createEmbedded();
        e.setName(attribute.getName());
        e.setCwmAttribute(attribute);
        owner.getEmbedded().add(e);
        return e;
    }
}
