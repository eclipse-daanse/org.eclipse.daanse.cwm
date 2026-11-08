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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.daanse.sensinact.LiteralSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ResourceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/**
 * What a sensiNact mapping says without storing it: the services and resources of a provider, the
 * mappings of a mapping, the class a value source starts at, the type a path ends with, the text
 * of a path.
 *
 * <p>Paths follow CWM 1.1, 4.5: a step is an attribute of the current class (or of an ancestor),
 * or a navigable association end seen from the other end — the end {@code customer} of an
 * association between {@code Order} and {@code Customer} is a step from {@code Order} to
 * {@code Customer}. The type after a step is the type of the feature.</p>
 */
public final class SensinactBindings {

    /** The language of a {@link ValueSource#getFilter() filter}: {@code path=literal}. */
    public static final String FILTER_LANGUAGE = "event.atlas.collectionFilter";
    /** The language of the functions of an export to CWM transformation maps. */
    public static final String FUNCTION_LANGUAGE = "daanse.sensinact";

    private SensinactBindings() {
    }

    /** The services of a provider, in their order. */
    public static List<Service> services(Provider provider) {
        return provider.getOwnedElement().stream().filter(Service.class::isInstance).map(Service.class::cast).toList();
    }

    /** The resources of a service, in their order. */
    public static List<Resource> resources(Service service) {
        return service.getFeature().stream().filter(Resource.class::isInstance).map(Resource.class::cast).toList();
    }

    /** The service mappings of a provider mapping. */
    public static List<ServiceMapping> serviceMappings(ProviderMapping mapping) {
        return mapping.getOwnedElement().stream().filter(ServiceMapping.class::isInstance)
                .map(ServiceMapping.class::cast).toList();
    }

    /** The resource mappings of a service mapping. */
    public static List<ResourceMapping> resourceMappings(ServiceMapping mapping) {
        return mapping.getOwnedElement().stream().filter(ResourceMapping.class::isInstance)
                .map(ResourceMapping.class::cast).toList();
    }

    /** The provider mapping a service mapping belongs to, or {@code null}. */
    public static ProviderMapping providerMapping(ServiceMapping mapping) {
        return mapping.getNamespace() instanceof ProviderMapping pm ? pm : null;
    }

    /** The service mapping a resource mapping belongs to, or {@code null}. */
    public static ServiceMapping serviceMapping(ResourceMapping mapping) {
        return mapping.getNamespace() instanceof ServiceMapping sm ? sm : null;
    }

    /** The name of the provider model in the twin: the name of the provider. */
    public static String modelName(ProviderMapping mapping) {
        return mapping.getProvider() == null ? null : mapping.getProvider().getName();
    }

    /**
     * The classes the paths of a service start at: the class its selector ends at, else the
     * source classes of the provider mapping.
     */
    public static List<Class> contextClasses(ServiceMapping mapping) {
        if (mapping.getSelector() != null) {
            return typeOf(mapping.getSelector()) instanceof Class c ? List.of(c) : List.of();
        }
        ProviderMapping pm = providerMapping(mapping);
        return pm == null ? List.of() : List.copyOf(pm.getSourceClass());
    }

    /** The timestamp that dates the values of a service: its own, else that of its provider mapping. */
    public static ValueSource effectiveTimestamp(ServiceMapping mapping) {
        if (mapping.getTimestamp() != null) {
            return mapping.getTimestamp();
        }
        ProviderMapping pm = providerMapping(mapping);
        return pm == null ? null : pm.getTimestamp();
    }

    /** The type a value source ends with: the type of the last step of a path; {@code null} for any other source. */
    public static Classifier typeOf(ValueSource source) {
        List<StructuralFeature> path = ValueSources.steps(source);
        return path.isEmpty() ? null : path.get(path.size() - 1).getType();
    }

    /** Every feature a value source reads: the steps of its path and of the paths of its fallbacks, each once. */
    public static Set<StructuralFeature> features(ValueSource source) {
        Set<StructuralFeature> features = new LinkedHashSet<>();
        for (ValueSource s : ValueSources.chain(source)) {
            features.addAll(ValueSources.steps(s));
        }
        return features;
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

    /** The position of the first step of a path that holds many values, or -1. */
    public static int manyStep(ValueSource source) {
        List<StructuralFeature> path = ValueSources.steps(source);
        for (int i = 0; i < path.size(); i++) {
            if (isMany(path.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The class a feature is a step from: the owner of an attribute, the type of the other end
     * of an association end; {@code null} for an end without one other end.
     */
    public static Classifier holder(StructuralFeature feature) {
        if (feature instanceof AssociationEnd end) {
            AssociationEnd other = otherEnd(end);
            return other == null ? null : other.getType();
        }
        return feature.getOwner();
    }

    /** The other end of a binary association, or {@code null}. */
    public static AssociationEnd otherEnd(AssociationEnd end) {
        if (!(end.getOwner() instanceof Association a)) {
            return null;
        }
        AssociationEnd other = null;
        for (Feature f : a.getFeature()) {
            if (f instanceof AssociationEnd e && e != end) {
                if (other != null) {
                    return null;
                }
                other = e;
            }
        }
        return other;
    }

    /**
     * Whether a feature is a step from {@code context}: an attribute of it or of an ancestor, or a
     * navigable association end whose other end is typed by it or an ancestor.
     */
    public static boolean isStepFrom(StructuralFeature feature, Classifier context) {
        if (feature instanceof AssociationEnd end && !end.isIsNavigable()) {
            return false;
        }
        Classifier holder = holder(feature);
        return holder != null && lineage(context).contains(holder);
    }

    /** Whether a feature is a step from a subclass of {@code context} (a downcast). */
    public static boolean isDowncastFrom(StructuralFeature feature, Classifier context) {
        if (feature instanceof AssociationEnd end && !end.isIsNavigable()) {
            return false;
        }
        Classifier holder = holder(feature);
        return holder != null && !lineage(context).contains(holder) && lineage(holder).contains(context);
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

    /**
     * The text of a value source: the step names of a path with dots, the picked element in brackets
     * at the step that holds many values, a fallback after {@code ??}, a literal in quotes, the
     * arrival time as {@code arrival()} — {@code object.battery}, {@code reports[0].windSpeed},
     * {@code lines[key=line_5].value}, {@code name ?? 'Unknown'}.
     */
    public static String render(ValueSource source) {
        if (source instanceof LiteralSource literal) {
            return "'" + literal.getLiteral() + "'";
        }
        if (!(source instanceof PathSource path)) {
            return "arrival()";
        }
        StringBuilder sb = new StringBuilder();
        List<StructuralFeature> steps = path.getPath();
        int many = manyStep(path);
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(steps.get(i).getName());
            if (i == many) {
                if (path.getFilter() != null) {
                    sb.append('[').append(path.getFilter().getBody()).append(']');
                } else if (path.isSetIndex()) {
                    sb.append('[').append(path.getIndex()).append(']');
                }
            }
        }
        if (path.getFallback() != null) {
            sb.append(" ?? ").append(render(path.getFallback()));
        }
        return sb.toString();
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

    /** The description of a resource: the body of its Description of type {@code documentation}. */
    public static Optional<String> description(ModelElement element) {
        return Descriptions.find(element, "documentation").map(Description::getBody);
    }
}
