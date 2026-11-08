/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.ClassifierFeatureMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.ClassifierMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.FeatureMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationMap;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationUse;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Resources;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;

/** A provider mapping as plain CWM transformation metadata. */
class TransformationsExportTest {

    @Test
    void theStationAsTransformationMap() {
        Station s = new Station();
        Package lineage = ClassModels.pkg(s.model, "lineage");
        TransformationMap map = Transformations.export(s.mapping, lineage);

        assertThat(map.getName()).isEqualTo("m");
        assertThat(map.getFunction().getLanguage()).isEqualTo("daanse.sensinact");
        assertThat(map.getFunction().getBody()).isEqualTo("provider weather id name");
        assertThat(map.getFunctionDescription()).isEmpty();
        assertThat(description(map, map).getBody()).isEqualTo("instances of Station become instances of the provider weather");
        assertThat(description(map, map).getType()).isEqualTo("documentation");
        assertThat(map.isIsPrimary()).isTrue();
        assertThat(map.getSource()).singleElement().satisfies(set -> assertThat(set.getElement()).containsExactly(s.station));
        assertThat(map.getTarget()).singleElement().satisfies(set -> assertThat(set.getElement()).containsExactly(s.weather));

        ClassifierMap cm = (ClassifierMap) map.getOwnedElement().get(0);
        assertThat(cm.getName()).isEqualTo("climate");
        assertThat(cm.getFunction().getBody()).isEqualTo("readings[0]");
        assertThat(cm.getFunctionDescription()).isEmpty();
        assertThat(description(map, cm).getBody()).isEqualTo("the service climate reads from readings[0]");
        assertThat(cm.getSource()).containsExactlyInAnyOrder(s.reading, s.station);
        assertThat(cm.getTarget()).containsExactly(s.climate);
        assertThat(cm.getFeatureMap()).extracting(FeatureMap::getName).containsExactlyInAnyOrder("temperature", "state");
        FeatureMap temperature = cm.getFeatureMap().stream().filter(f -> f.getName().equals("temperature")).findFirst()
                .orElseThrow();
        assertThat(temperature.getSource()).containsExactly(s.value);
        assertThat(temperature.getTarget()).containsExactly(s.temperature);
        assertThat(temperature.getFunction().getBody()).isEqualTo("value");
        assertThat(temperature.getFunctionDescription()).isEmpty();
        assertThat(description(map, temperature).getBody()).isEqualTo("the value of temperature is value");
        assertThat(description(map, temperature).getName()).isEqualTo("climate.temperature_documentation_en");

        TransformationUse use = lineage.getOwnedElement().stream().filter(TransformationUse.class::isInstance)
                .map(TransformationUse.class::cast).findFirst().orElseThrow();
        assertThat(use.getClient()).containsExactly(map);
        assertThat(use.getSupplier()).containsExactly(s.mapping);
        assertThat(use.getKind()).isEqualTo("sensinact");
    }

    @Test
    void aLiteralValueBecomesAClassifierFeatureMap() {
        Station s = new Station();
        Resource fixed = Resources.optional(s.climate, "fixed", s.string);
        ResourceMappings.create(s.climateMapping, fixed, ValueSources.literal("calibrated"));
        TransformationMap map = Transformations.export(s.mapping, ClassModels.pkg(s.model, "lineage"));
        ClassifierMap cm = (ClassifierMap) map.getOwnedElement().get(0);
        ClassifierFeatureMap cfm = cm.getCfMap().get(0);
        assertThat(cfm.getName()).isEqualTo("fixed");
        assertThat(cfm.isClassifierToFeature()).isTrue();
        assertThat(cfm.getClassifier()).containsExactly(s.reading);
        assertThat(cfm.getFeature()).containsExactly(fixed);
        assertThat(cfm.getFunction().getBody()).isEqualTo("'calibrated'");
        assertThat(description(map, cfm).getBody()).isEqualTo("the value of fixed is the literal 'calibrated'");
    }

    @Test
    void theExportsMeetTheConventionsOfTransformationMaps() {
        for (Examples.Case c : Examples.all()) {
            Package lineage = ClassModels.pkg(c.model(), "lineage");
            c.mappings().forEach(m -> Transformations.export(m, lineage));
            assertThat(conventions(lineage)).as(c.name()).isEmpty();
        }
    }

    /**
     * The checks the ETL conventions make of transformation maps: a classifier map is owned by a
     * transformation map, every source feature of a feature map belongs to a source classifier of
     * its classifier map, every target feature to a target classifier.
     */
    private static List<String> conventions(Package lineage) {
        List<String> findings = new ArrayList<>();
        for (var it = lineage.eAllContents(); it.hasNext();) {
            EObject o = it.next();
            if (o instanceof ClassifierMap cm && !(cm.eContainer() instanceof TransformationMap)) {
                findings.add(name(cm) + " is not owned by a transformation map");
            }
            if (o instanceof FeatureMap fm) {
                ClassifierMap cm = fm.getClassifierMap();
                for (Feature f : fm.getSource()) {
                    if (!cm.getSource().contains(f.getOwner())) {
                        findings.add(name(fm) + ": source " + f.getName() + " belongs to no source classifier");
                    }
                }
                for (Feature f : fm.getTarget()) {
                    if (!cm.getTarget().contains(f.getOwner())) {
                        findings.add(name(fm) + ": target " + f.getName() + " belongs to no target classifier");
                    }
                }
            }
        }
        return findings;
    }

    /** The one description the transformation map owns for {@code element}. */
    private static Description description(TransformationMap map, ModelElement element) {
        List<Description> found = map.getOwnedElement().stream().filter(Description.class::isInstance)
                .map(Description.class::cast).filter(d -> d.getModelElement().contains(element)).toList();
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private static String name(ModelElement e) {
        return e.eClass().getName() + " " + e.getName();
    }
}
