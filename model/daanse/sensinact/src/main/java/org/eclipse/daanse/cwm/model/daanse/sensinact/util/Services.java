/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.daanse.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Service;

/** Creates services. */
public final class Services {

    private Services() {
    }

    /** A required service named {@code name} (the service id) in the {@code ownedElement} of {@code provider}. */
    public static Service create(Provider provider, String name) {
        Objects.requireNonNull(provider, "provider");
        Service service = SensinactFactory.eINSTANCE.createService();
        service.setName(Objects.requireNonNull(name, "name"));
        provider.getOwnedElement().add(service);
        return service;
    }

    /** An optional service: a mapping onto the provider may leave it out. */
    public static Service optional(Provider provider, String name) {
        Service service = create(provider, name);
        service.setRequired(false);
        return service;
    }
}
