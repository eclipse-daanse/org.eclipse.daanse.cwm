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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.Transient;

/** Creates {@link Transient} mappings; the name is the feature name. */
public final class Transients {

    private Transients() {
    }

    /** Marks {@code feature} (an attribute or a navigable association end) as not persistent. */
    public static Transient create(ManagedType owner, StructuralFeature feature) {
        Transient t = CWMORMFactory.eINSTANCE.createTransient();
        t.setName(feature.getName());
        t.setCwmFeature(feature);
        owner.getTransient().add(t);
        return t;
    }
}
