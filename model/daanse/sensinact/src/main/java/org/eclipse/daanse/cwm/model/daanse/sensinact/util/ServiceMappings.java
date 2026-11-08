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

import org.eclipse.daanse.cwm.model.daanse.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/** Creates service mappings. */
public final class ServiceMappings {

    private ServiceMappings() {
    }

    /** The mapping of {@code service} in {@code mapping}, named after the service; it reads from the source instance. */
    public static ServiceMapping create(ProviderMapping mapping, Service service) {
        Objects.requireNonNull(mapping, "mapping");
        ServiceMapping sm = SensinactFactory.eINSTANCE.createServiceMapping();
        sm.setService(Objects.requireNonNull(service, "service"));
        sm.setName(service.getName());
        mapping.getOwnedElement().add(sm);
        return sm;
    }

    /** The mapping of {@code service} reading from the element {@code selector} picks. */
    public static ServiceMapping create(ProviderMapping mapping, Service service, PathSource selector) {
        ServiceMapping sm = create(mapping, service);
        sm.setSelector(Objects.requireNonNull(selector, "selector"));
        return sm;
    }

    /** Dates the values of the service with {@code timestamp}. */
    public static ServiceMapping timestamp(ServiceMapping mapping, ValueSource timestamp) {
        mapping.setTimestamp(Objects.requireNonNull(timestamp, "timestamp"));
        return mapping;
    }
}
