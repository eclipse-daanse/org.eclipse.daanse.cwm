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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.ClassifierFeatureMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.ClassifierMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.DataObjectSet;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.FeatureMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationFactory;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationUse;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ResourceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/**
 * Exports a provider mapping as plain CWM transformation metadata, so that lineage, process and
 * documentation tools that read transformation maps see digital-twin mappings too.
 *
 * <p>A {@code TransformationMap} for the provider mapping (source and target data object sets
 * next to it: the source classes, the provider), a {@code ClassifierMap} per service mapping
 * (source: the class the service reads from and the owners of every feature its paths cross;
 * target: the service), a {@code FeatureMap} per resource mapping that reads a path (source: the
 * features of the path; target: the resource) and a {@code ClassifierFeatureMap} per resource
 * mapping that gives a literal; every function is the text of the value source in the language
 * {@value SensinactBindings#FUNCTION_LANGUAGE}. A {@code TransformationUse} names the provider
 * mapping as the model that performs the transformation (OMG CWM 1.1, 10.3.13).</p>
 *
 * <p>What a map does is said in words by a {@code Description} of type {@code documentation} that
 * the transformation map owns — the place of every description in CWM; a classifier map owns
 * nothing but its feature maps (C-4, C-5). The attribute {@code functionDescription}, which CWM
 * requires once per map, is the empty string.</p>
 */
public final class Transformations {

    private static final TransformationFactory TF = TransformationFactory.eINSTANCE;

    /** The type of the descriptions the export writes. */
    public static final String DOCUMENTATION = "documentation";

    private Transformations() {
    }

    /** Writes the transformation metadata of {@code mapping} into {@code target}; returns the transformation map. */
    public static TransformationMap export(ProviderMapping mapping, Namespace target) {
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(target, "target");
        String provider = SensinactBindings.modelName(mapping);
        TransformationMap map = TF.createTransformationMap();
        map.setName(mapping.getName());
        map.setFunction(Expressions.procedure(SensinactBindings.FUNCTION_LANGUAGE,
                "provider " + provider + " id " + SensinactBindings.render(mapping.getId())));
        map.setFunctionDescription("");
        map.setIsPrimary(true);
        target.getOwnedElement().add(map);
        List<Description> descriptions = new ArrayList<>();
        descriptions.add(describe(map, mapping.getName(), "instances of " + names(mapping.getSourceClass())
                + " become instances of the provider " + provider));
        map.getSource().add(dataObjectSet(target, mapping.getName() + "-source", List.copyOf(mapping.getSourceClass())));
        map.getTarget().add(dataObjectSet(target, mapping.getName() + "-target", List.of(mapping.getProvider())));

        for (ServiceMapping sm : SensinactBindings.serviceMappings(mapping)) {
            ClassifierMap cm = TF.createClassifierMap();
            cm.setName(sm.getName());
            String reads = sm.getSelector() == null ? "the source instance" : SensinactBindings.render(sm.getSelector());
            cm.setFunction(Expressions.procedure(SensinactBindings.FUNCTION_LANGUAGE,
                    sm.getSelector() == null ? "this" : SensinactBindings.render(sm.getSelector())));
            cm.setFunctionDescription("");
            Set<Classifier> sources = new LinkedHashSet<>(SensinactBindings.contextClasses(sm));
            if (sm.getSelector() != null) {
                sources.addAll(owners(sm.getSelector()));
            }
            for (ResourceMapping rm : SensinactBindings.resourceMappings(sm)) {
                sources.addAll(owners(rm.getValue()));
            }
            cm.getSource().addAll(sources);
            cm.getTarget().add(sm.getService());
            map.getOwnedElement().add(cm);
            descriptions.add(describe(cm, cm.getName(), "the service " + sm.getService().getName() + " reads from "
                    + reads));
            for (ResourceMapping rm : SensinactBindings.resourceMappings(sm)) {
                ValueSource value = rm.getValue();
                String text = SensinactBindings.render(value);
                Set<StructuralFeature> features = SensinactBindings.features(value);
                if (features.isEmpty()) {
                    ClassifierFeatureMap cfm = TF.createClassifierFeatureMap();
                    cfm.setName(rm.getName());
                    cfm.setFunction(Expressions.procedure(SensinactBindings.FUNCTION_LANGUAGE, text));
                    cfm.setFunctionDescription("");
                    cfm.setClassifierToFeature(true);
                    cfm.getClassifier().addAll(SensinactBindings.contextClasses(sm));
                    cfm.getFeature().add(rm.getResource());
                    cm.getCfMap().add(cfm);
                    descriptions.add(describe(cfm, cm.getName() + "." + cfm.getName(),
                            "the value of " + rm.getResource().getName() + " is the literal " + text));
                } else {
                    FeatureMap fm = TF.createFeatureMap();
                    fm.setName(rm.getName());
                    fm.setFunction(Expressions.procedure(SensinactBindings.FUNCTION_LANGUAGE, text));
                    fm.setFunctionDescription("");
                    fm.getSource().addAll(features);
                    fm.getTarget().add(rm.getResource());
                    cm.getFeatureMap().add(fm);
                    descriptions.add(describe(fm, cm.getName() + "." + fm.getName(),
                            "the value of " + rm.getResource().getName() + " is " + text));
                }
            }
        }

        map.getOwnedElement().addAll(descriptions);

        TransformationUse use = TF.createTransformationUse();
        use.setName(mapping.getName() + "-use");
        use.setKind("sensinact");
        use.setType("sensinact provider mapping");
        use.getClient().add(map);
        use.getSupplier().add(mapping);
        target.getOwnedElement().add(use);
        return map;
    }

    private static DataObjectSet dataObjectSet(Namespace owner, String name, List<? extends ModelElement> elements) {
        DataObjectSet set = TF.createDataObjectSet();
        set.setName(name);
        set.getElement().addAll(elements);
        owner.getOwnedElement().add(set);
        return set;
    }

    /** The description of what {@code element} does, for the transformation map to own. */
    private static Description describe(ModelElement element, String name, String text) {
        Description description = BusinessinformationFactory.eINSTANCE.createDescription();
        description.setName(name + "_" + DOCUMENTATION + "_en");
        description.setType(DOCUMENTATION);
        description.setLanguage("en");
        description.setBody(text);
        description.getModelElement().add(element);
        return description;
    }

    /** The owners of the features a value source reads: classes for attributes, associations for association ends. */
    private static Set<Classifier> owners(ValueSource source) {
        Set<Classifier> owners = new LinkedHashSet<>();
        for (StructuralFeature f : SensinactBindings.features(source)) {
            if (f.getOwner() != null) {
                owners.add(f.getOwner());
            }
        }
        return owners;
    }

    private static String names(List<? extends ModelElement> elements) {
        return String.join(", ", elements.stream().map(ModelElement::getName).toList());
    }
}
