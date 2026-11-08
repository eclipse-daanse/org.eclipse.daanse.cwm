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

import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ValueSource;
import org.junit.jupiter.api.Test;

/** What the bindings derive from a mapping without storing it. */
class SensinactBindingsTest {

    private final Station s = new Station();

    @Test
    void mappingsInTheirOrder() {
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
    }

    @Test
    void typeAndManyValuedSteps() {
        ValueSource v = ValueSources.path(s.readings, s.value);
        assertThat(SensinactBindings.typeOf(v)).isSameAs(s.decimal);
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
}
