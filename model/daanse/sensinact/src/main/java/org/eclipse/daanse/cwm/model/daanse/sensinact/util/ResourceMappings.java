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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ResourceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/** Creates resource mappings. */
public final class ResourceMappings {

    private ResourceMappings() {
    }

    /** The mapping of {@code resource} in {@code mapping}, named after the resource, its value from {@code value}. */
    public static ResourceMapping create(ServiceMapping mapping, Resource resource, ValueSource value) {
        Objects.requireNonNull(mapping, "mapping");
        ResourceMapping rm = SensinactFactory.eINSTANCE.createResourceMapping();
        rm.setResource(Objects.requireNonNull(resource, "resource"));
        rm.setName(resource.getName());
        rm.setValue(Objects.requireNonNull(value, "value"));
        mapping.getOwnedElement().add(rm);
        return rm;
    }

    /**
     * One mapping per resource of the mapped service that has an attribute of the same name in
     * {@code source} (own ones first, then inherited), reading that attribute; attributes in
     * {@code exclude} are left out. The explicit form of generating resource values from a class.
     */
    public static List<ResourceMapping> mapAttributes(ServiceMapping mapping, Classifier source, Attribute... exclude) {
        List<Attribute> excluded = List.of(exclude);
        List<ResourceMapping> created = new ArrayList<>();
        for (Resource resource : SensinactBindings.resources(mapping.getService())) {
            for (Attribute a : SensinactBindings.allAttributes(source)) {
                if (!excluded.contains(a) && a.getName().equals(resource.getName())) {
                    created.add(create(mapping, resource, ValueSources.path(a)));
                    break;
                }
            }
        }
        return created;
    }
}
