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

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/** Creates provider mappings. */
public final class ProviderMappings {

    private ProviderMappings() {
    }

    /**
     * A mapping named {@code name} (the mapping id) in the {@code ownedElement} of {@code owner}:
     * instances of {@code sources} become instances of {@code provider}, identified by {@code id}.
     */
    public static ProviderMapping create(Namespace owner, String name, Provider provider, ValueSource id,
            Class... sources) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(sources, "sources");
        if (sources.length == 0) {
            throw new IllegalArgumentException("a provider mapping has at least one source class");
        }
        ProviderMapping mapping = SensinactFactory.eINSTANCE.createProviderMapping();
        mapping.setName(Objects.requireNonNull(name, "name"));
        mapping.setProvider(Objects.requireNonNull(provider, "provider"));
        mapping.setId(Objects.requireNonNull(id, "id"));
        List.of(sources).forEach(s -> mapping.getSourceClass().add(Objects.requireNonNull(s, "source")));
        owner.getOwnedElement().add(mapping);
        return mapping;
    }

    /** Dates the values of a source instance with {@code timestamp}. */
    public static ProviderMapping timestamp(ProviderMapping mapping, ValueSource timestamp) {
        mapping.setTimestamp(Objects.requireNonNull(timestamp, "timestamp"));
        return mapping;
    }
}
