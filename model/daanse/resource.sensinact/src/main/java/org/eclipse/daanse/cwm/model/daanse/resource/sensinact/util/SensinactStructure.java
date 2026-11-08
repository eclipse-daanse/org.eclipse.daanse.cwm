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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;

/**
 * What the structure of a provider says without storing it: its services and their resources in
 * order, the extra metadata and the description of a resource. Next to it the reading of a CWM
 * class model that deriving resources from a class needs: the ancestors of a classifier, its
 * attributes, whether a feature holds many values.
 */
public final class SensinactStructure {

    private SensinactStructure() {
    }

    /** The services of a provider, in their order. */
    public static List<Service> services(Provider provider) {
        return provider.getOwnedElement().stream().filter(Service.class::isInstance).map(Service.class::cast).toList();
    }

    /** The resources of a service, in their order. */
    public static List<Resource> resources(Service service) {
        return service.getFeature().stream().filter(Resource.class::isInstance).map(Resource.class::cast).toList();
    }

    /** The tagged values of a resource as extra metadata, without the {@code daanse.} tags. */
    public static Map<String, String> extraMetadata(Resource resource) {
        Map<String, String> extra = new LinkedHashMap<>();
        for (TaggedValue tv : resource.getTaggedValue()) {
            if (tv.getTag() != null && !tv.getTag().startsWith("daanse.")) {
                extra.put(tv.getTag(), tv.getValue());
            }
        }
        return extra;
    }

    /** The description of an element: the body of its Description of type {@code documentation}. */
    public static Optional<String> description(ModelElement element) {
        return Descriptions.find(element, "documentation").map(Description::getBody);
    }

    /** Whether a feature holds many values: a range of its multiplicity above 1 or unbounded. */
    public static boolean isMany(StructuralFeature feature) {
        if (feature.getMultiplicity() == null) {
            return false;
        }
        for (MultiplicityRange r : feature.getMultiplicity().getRange()) {
            if (r.getUpper() < 0 || r.getUpper() > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * A classifier and its ancestors, found through the generalizations owned next to their
     * child (the CWM convention).
     */
    public static Set<Classifier> lineage(Classifier start) {
        Set<Classifier> lineage = new LinkedHashSet<>();
        Deque<Classifier> todo = new ArrayDeque<>(List.of(start));
        while (!todo.isEmpty()) {
            Classifier c = todo.pop();
            if (!lineage.add(c) || c.getNamespace() == null) {
                continue;
            }
            for (ModelElement owned : c.getNamespace().getOwnedElement()) {
                if (owned instanceof Generalization g && g.getChild() == c && g.getParent() != null) {
                    todo.addLast(g.getParent());
                }
            }
        }
        return lineage;
    }

    /** The attributes of a class and of its ancestors: own ones first. */
    public static List<Attribute> allAttributes(Classifier classifier) {
        List<Attribute> all = new ArrayList<>();
        for (Classifier c : lineage(classifier)) {
            for (Feature f : c.getFeature()) {
                if (f instanceof Attribute a) {
                    all.add(a);
                }
            }
        }
        return all;
    }
}
