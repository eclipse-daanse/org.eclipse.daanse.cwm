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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;

/** Creates resources. */
public final class Resources {

    private Resources() {
    }

    /** A required resource named {@code name} (the resource id) of type {@code type}, a feature of {@code service}. */
    public static Resource create(Service service, String name, Classifier type) {
        Objects.requireNonNull(service, "service");
        Resource resource = SensinactFactory.eINSTANCE.createResource();
        resource.setName(Objects.requireNonNull(name, "name"));
        resource.setType(Objects.requireNonNull(type, "type"));
        service.getFeature().add(resource);
        return resource;
    }

    /** An optional resource: a mapping of the service may leave it out. */
    public static Resource optional(Service service, String name, Classifier type) {
        Resource resource = create(service, name, type);
        resource.setRequired(false);
        return resource;
    }

    /**
     * One resource per single-valued attribute of {@code source} (own ones first, then inherited)
     * that is typed by a data type, named and typed like the attribute — the explicit form of
     * generating resources from a class. Attributes in {@code exclude} are left out.
     */
    public static List<Resource> fromAttributes(Service service, Classifier source, Attribute... exclude) {
        List<Attribute> excluded = List.of(exclude);
        List<Resource> created = new ArrayList<>();
        for (Attribute a : SensinactStructure.allAttributes(source)) {
            if (!excluded.contains(a) && a.getType() instanceof DataType && !SensinactStructure.isMany(a)) {
                created.add(create(service, a.getName(), a.getType()));
            }
        }
        return created;
    }
}
