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
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;

/** Creates {@link Basic} mappings; the name is the attribute name. */
public final class Basics {

    private Basics() {
    }

    /** A basic mapping of {@code attribute} onto {@code column}, added to {@code owner}. */
    public static Basic create(ManagedType owner, Attribute attribute, Column column) {
        Basic b = create(owner, attribute);
        b.setColumn(Columns.of(column));
        return b;
    }

    /**
     * A basic mapping without column — only in a mapped superclass or embeddable whose users set
     * the column by an attribute override.
     */
    public static Basic create(ManagedType owner, Attribute attribute) {
        Basic b = CWMORMFactory.eINSTANCE.createBasic();
        b.setName(attribute.getName());
        b.setCwmAttribute(attribute);
        owner.getBasic().add(b);
        return b;
    }
}
