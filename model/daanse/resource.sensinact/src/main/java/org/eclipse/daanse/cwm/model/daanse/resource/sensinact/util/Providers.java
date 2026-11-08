/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.SensinactFactory;

/** Creates providers. */
public final class Providers {

    private Providers() {
    }

    /** A provider named {@code name} in the {@code ownedElement} of {@code parent}. */
    public static Provider create(Namespace parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Provider provider = SensinactFactory.eINSTANCE.createProvider();
        provider.setName(Objects.requireNonNull(name, "name"));
        parent.getOwnedElement().add(provider);
        return provider;
    }
}
