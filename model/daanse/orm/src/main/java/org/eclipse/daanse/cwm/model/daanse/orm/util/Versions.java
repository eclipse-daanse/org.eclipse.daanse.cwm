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
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;
import org.eclipse.daanse.cwm.model.daanse.orm.Version;

/** Creates {@link Version} mappings; the name is the attribute name. */
public final class Versions {

    private Versions() {
    }

    /** A version mapping of {@code attribute} onto {@code column}, added to {@code owner}. */
    public static Version create(IdentifiableType owner, Attribute attribute, Column column) {
        Version v = CWMORMFactory.eINSTANCE.createVersion();
        v.setName(attribute.getName());
        v.setCwmAttribute(attribute);
        v.setColumn(Columns.of(column));
        owner.getVersion().add(v);
        return v;
    }
}
