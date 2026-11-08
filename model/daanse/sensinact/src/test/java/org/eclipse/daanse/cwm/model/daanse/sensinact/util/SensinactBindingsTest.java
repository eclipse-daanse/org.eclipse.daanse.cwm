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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;
import org.junit.jupiter.api.Test;

/** What the bindings derive from a mapping without storing it. */
class SensinactBindingsTest {

    private final Station s = new Station();

    @Test
    void servicesResourcesAndMappingsInTheirOrder() {
        assertThat(SensinactBindings.services(s.weather)).containsExactly(s.climate);
        assertThat(SensinactBindings.resources(s.climate)).containsExactly(s.temperature, s.stateResource);
        assertThat(SensinactBindings.serviceMappings(s.mapping)).containsExactly(s.climateMapping);
        assertThat(SensinactBindings.resourceMappings(s.climateMapping)).extracting(rm -> rm.getResource())
                .containsExactly(s.temperature, s.stateResource);
        assertThat(SensinactBindings.providerMapping(s.climateMapping)).isSameAs(s.mapping);
        assertThat(SensinactBindings.modelName(s.mapping)).isEqualTo("weather");
    }

    @Test
    void aServiceReadsFromTheElementItsSelectorPicks() {
        assertThat(SensinactBindings.contextClasses(s.climateMapping)).containsExactly(s.reading);
        s.climateMapping.setSelector(null);
        assertThat(SensinactBindings.contextClasses(s.climateMapping)).containsExactly(s.station);
    }

    @Test
    void theTimestampOfAServiceFallsBackToItsProviderMapping() {
        assertThat(SensinactBindings.effectiveTimestamp(s.climateMapping)).isNull();
        ValueSource provider = ValueSources.path(s.readings, s.takenAt);
        ProviderMappings.timestamp(s.mapping, provider);
        assertThat(SensinactBindings.effectiveTimestamp(s.climateMapping)).isSameAs(provider);
        ValueSource own = ValueSources.path(s.takenAt);
        ServiceMappings.timestamp(s.climateMapping, own);
        assertThat(SensinactBindings.effectiveTimestamp(s.climateMapping)).isSameAs(own);
    }

    @Test
    void stepsAttributesOfTheClassOrAnAncestorAndNavigableEnds() {
        assertThat(SensinactBindings.isStepFrom(s.value, s.reading)).isTrue();
        assertThat(SensinactBindings.isStepFrom(s.value, s.gauge)).as("inherited").isTrue();
        assertThat(SensinactBindings.isStepFrom(s.level, s.reading)).isFalse();
        assertThat(SensinactBindings.isDowncastFrom(s.level, s.reading)).isTrue();
        assertThat(SensinactBindings.isStepFrom(s.ownerEnd, s.station)).as("the end seen from the other end").isTrue();
        assertThat(SensinactBindings.holder(s.ownerEnd)).isSameAs(s.station);
        assertThat(SensinactBindings.isStepFrom(s.stationEnd, s.owner)).as("not navigable").isFalse();
        assertThat(SensinactBindings.otherEnd(s.ownerEnd)).isSameAs(s.stationEnd);
        assertThat(SensinactBindings.lineage(s.gauge)).containsExactly(s.gauge, s.reading);
    }

    @Test
    void typeAndManyValuedSteps() {
        ValueSource v = ValueSources.path(s.readings, s.value);
        assertThat(SensinactBindings.typeOf(v)).isSameAs(s.decimal);
        assertThat(SensinactBindings.isMany(s.readings)).isTrue();
        assertThat(SensinactBindings.isMany(s.value)).isFalse();
        assertThat(SensinactBindings.manyStep(v)).isZero();
        assertThat(SensinactBindings.manyStep(ValueSources.path(s.value))).isEqualTo(-1);
        assertThat(SensinactBindings.typeOf(ValueSources.literal("x"))).isNull();
    }

    @Test
    void theTextOfAValueSource() {
        assertThat(SensinactBindings.render(ValueSources.path(s.locationRef, s.lat))).isEqualTo("location.lat");
        assertThat(SensinactBindings.render(ValueSources.at(ValueSources.path(s.readings, s.value), 0)))
                .isEqualTo("readings[0].value");
        assertThat(SensinactBindings.render(ValueSources.where(ValueSources.path(s.readings, s.value), "state=OK")))
                .isEqualTo("readings[state=OK].value");
        assertThat(SensinactBindings.render(ValueSources.orElse(ValueSources.path(s.name), "Unknown")))
                .isEqualTo("name ?? 'Unknown'");
        assertThat(SensinactBindings.render(ValueSources.literal("Weather"))).isEqualTo("'Weather'");
    }

    @Test
    void extraMetadataAndDescriptionOfAResource() {
        TaggedValues.set(s.temperature, "sensorthings.unit.name", "degree Celsius");
        TaggedValues.set(s.temperature, "daanse.ecore.id", "true");
        Descriptions.describe(s.temperature, "documentation", "en", "Air temperature 2 m above ground");
        assertThat(SensinactBindings.extraMetadata(s.temperature)).containsExactly(
                java.util.Map.entry("sensorthings.unit.name", "degree Celsius"));
        assertThat(SensinactBindings.description(s.temperature)).contains("Air temperature 2 m above ground");
        assertThat(SensinactBindings.description(s.stateResource)).isEmpty();
    }

    @Test
    void allAttributesOwnFirst() {
        assertThat(SensinactBindings.allAttributes(s.gauge)).containsExactly(s.level, s.value, s.takenAt, s.state);
    }
}
