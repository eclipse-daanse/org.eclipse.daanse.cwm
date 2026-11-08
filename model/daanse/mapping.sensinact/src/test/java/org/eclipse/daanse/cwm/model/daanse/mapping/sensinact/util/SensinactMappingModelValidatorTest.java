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

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ResourceMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ValueSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.SensinactMappingModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.SensinactMappingModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Providers;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Resources;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Services;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/** A positive and at least one negative case per rule of the validator. */
class SensinactMappingModelValidatorTest {

    private final Station s = new Station();

    @Test
    void theExamplesHaveNoError() {
        for (Examples.Case c : Examples.all()) {
            assertThat(SensinactMappingModelValidator.validate(c.model())).as(c.name()).isEmpty();
        }
    }

    @Test
    void theExamplesWarnOnlyOfDowncastsAndUnreadAttributes() {
        for (Examples.Case c : Examples.all()) {
            for (Finding f : SensinactMappingModelValidator.check(c.model())) {
                assertThat(f.rule() + " " + f.message()).as(c.name())
                        .matches("S8 .*|S2 .* is a feature of MOSMIXSWeatherReport, a subclass of WeatherReport.*");
            }
        }
    }

    @Test
    void theStationHasOnlyTheUnreadAttributes() {
        assertThat(rules(SensinactMappingModelValidator.check(s.model))).containsExactly("S8");
    }

    @Test
    void s1_requiredServicesAndResourcesAreMapped() {
        Service status = Services.create(s.weather, "status");
        EcoreUtil.delete(SensinactBindings.resourceMappings(s.climateMapping).get(0));
        assertThat(messages(errors(), "S1")).containsExactly(
                "ProviderMapping 'm' does not map the required resource sensors.providers.weather.climate.temperature.",
                "ProviderMapping 'm' does not map the required service sensors.providers.weather.status.");
        status.setRequired(false);
        ResourceMappings.create(s.climateMapping, s.temperature, ValueSources.path(s.value));
        assertThat(errors()).isEmpty();
    }

    @Test
    void s1_mappingsOnlyOntoTheProviderAndEachOnce() {
        Service other = Services.create(Providers.create(s.providers, "other"), "elsewhere");
        Resource foreign = Resources.create(other, "foreign", s.decimal);
        ServiceMapping twice = ServiceMappings.create(s.mapping, s.climate);
        ResourceMappings.create(twice, s.temperature, ValueSources.path(s.name));
        ResourceMappings.create(twice, s.temperature, ValueSources.path(s.name));
        ResourceMappings.create(twice, foreign, ValueSources.path(s.name));
        ServiceMappings.create(s.mapping, other);
        List<String> s1 = messages(errors(), "S1");
        assertThat(s1).anyMatch(m -> m.contains("maps service climate twice"));
        assertThat(s1).anyMatch(m -> m.contains("maps resource sensors.providers.weather.climate.temperature twice"));
        assertThat(s1).anyMatch(m -> m.contains("resource sensors.providers.other.elsewhere.foreign is no resource of"));
        assertThat(s1).anyMatch(m -> m.contains("service sensors.providers.other.elsewhere is no service of weather"));
    }

    @Test
    void s2_eachStepIsAFeatureOfTheTypeBefore() {
        ProviderMappings.timestamp(s.mapping, ValueSources.path(s.value));
        assertThat(messages(errors(), "S2")).containsExactly(
                "ProviderMapping 'm': timestamp: value is no feature of Station.");
        s.mapping.setTimestamp(ValueSources.at(ValueSources.path(s.readings, s.takenAt), 0));
        assertThat(errors()).isEmpty();
        s.mapping.setTimestamp(ValueSources.at(ValueSources.path(s.readings, s.takenAt, s.lat), 0));
        assertThat(messages(errors(), "S2")).containsExactly(
                "ProviderMapping 'm': timestamp: lat is no feature of Timestamp.");
    }

    @Test
    void s2_aNavigableEndIsAStepFromTheOtherEnd() {
        AdminMappings.friendlyName(AdminMappings.create(s.mapping), ValueSources.path(s.ownerEnd, s.ownerName));
        assertThat(errors()).isEmpty();
        s.ownerEnd.setIsNavigable(false);
        assertThat(messages(errors(), "S2")).containsExactly(
                "ProviderMapping 'm': admin friendlyName: owner is no feature of Station.");
    }

    @Test
    void s2_aFeatureOfASubclassIsADowncast() {
        Resource level = Resources.optional(s.climate, "level", s.decimal);
        ResourceMappings.create(s.climateMapping, level, ValueSources.path(s.level));
        List<Finding> findings = SensinactMappingModelValidator.check(s.model);
        assertThat(findings).filteredOn(f -> f.rule().equals("S2")).singleElement()
                .satisfies(f -> assertThat(f.severity()).isEqualTo(Severity.WARNING))
                .satisfies(f -> assertThat(f.message()).contains("level is a feature of Gauge, a subclass of Reading"));
    }

    @Test
    void s2_oneElementIsPickedAtTheOneStepThatHoldsManyValues() {
        s.mapping.setTimestamp(ValueSources.path(s.readings, s.takenAt));
        assertThat(messages(errors(), "S2")).singleElement().asString().contains("without an index or a filter");
        s.mapping.setTimestamp(ValueSources.at(ValueSources.path(s.name), 0));
        assertThat(messages(errors(), "S2")).singleElement().asString().contains("no step holds many values");
        s.mapping.setTimestamp(ValueSources.where(ValueSources.path(s.readings, s.takenAt), "not a filter"));
        assertThat(messages(errors(), "S2")).singleElement().asString().contains("the filter is no 'path=literal'");
        s.mapping.setTimestamp(ValueSources.at(ValueSources.path(s.readings, s.takenAt), -1));
        assertThat(messages(errors(), "S2")).singleElement().asString().contains("the index is negative");
        s.mapping.setTimestamp(ValueSources.where(ValueSources.path(s.readings, s.takenAt), "state=OK"));
        assertThat(messages(errors(), "S2")).isEmpty();
        // a path may end at the step that holds many values: the element picked there is the value
        s.mapping.setTimestamp(ValueSources.at(ValueSources.path(s.readings), 0));
        assertThat(messages(errors(), "S2")).isEmpty();
    }

    @Test
    void s3_theValueConvertsIntoTheTypeOfTheResource() {
        ResourceMapping rm = SensinactBindings.resourceMappings(s.climateMapping).get(0);
        rm.setValue(ValueSources.path(s.takenAt));
        assertThat(warnings("S3")).containsExactly(
                "ProviderMapping 'm': temperature reads a Timestamp into a resource of type Double.");
        rm.setValue(ValueSources.literal("warm"));
        assertThat(warnings("S3")).containsExactly(
                "ProviderMapping 'm': temperature gives the literal 'warm' to a resource of type Double.");
        rm.setValue(ValueSources.literal("21.5"));
        assertThat(warnings("S3")).isEmpty();
    }

    @Test
    void s4_theIdIsGiven() {
        s.mapping.setId(null);
        assertThat(messages(errors(), "S4")).containsExactly(
                "ProviderMapping 'm' does not say where the id of an instance comes from.");
    }

    @Test
    void s5_timestamps() {
        ServiceMappings.timestamp(s.climateMapping, ValueSources.path(s.value));
        assertThat(warnings("S5")).containsExactly("ProviderMapping 'm': timestamp of climate ends at a Double, "
                + "neither a time, a whole number of milliseconds nor a text.");
        s.climateMapping.setTimestamp(ValueSources.literal("yesterday"));
        assertThat(warnings("S5")).singleElement().asString().contains("is no ISO-8601 instant");
        s.climateMapping.setTimestamp(ValueSources.format(ValueSources.path(s.state), "yyyy-MM-dd HH:mm"));
        assertThat(warnings("S5")).singleElement().asString().contains("has no zone");
        s.climateMapping.setTimestamp(ValueSources.format(ValueSources.path(s.state), "yyyy-MM-dd'T'HH:mmX"));
        assertThat(warnings("S5")).isEmpty();
        s.climateMapping.setTimestamp(ValueSources.format(ValueSources.path(s.state), "yyyy-MM-dd {"));
        assertThat(messages(errors(), "S5")).singleElement().asString().contains("is no DateTimeFormatter pattern");
    }

    @Test
    void s6_admin() {
        s.weather.setFriendlyNameRequired(true);
        s.weather.setLocationRequired(true);
        assertThat(messages(errors(), "S6")).containsExactly(
                "ProviderMapping 'm': provider weather requires a friendly name.",
                "ProviderMapping 'm': provider weather requires a location.");
        var admin = AdminMappings.friendlyName(AdminMappings.create(s.mapping), ValueSources.path(s.name));
        admin.setLatitude(ValueSources.path(s.locationRef, s.lat));
        assertThat(messages(errors(), "S6")).containsExactly(
                "ProviderMapping 'm': a location needs latitude and longitude.",
                "ProviderMapping 'm': provider weather requires a location.");
        admin.setLongitude(ValueSources.path(s.locationRef, s.lon));
        assertThat(errors()).isEmpty();
    }

    @Test
    void s7_mappingNames() {
        ProviderMappings.create(s.mappings, "m", s.weather, ValueSources.path(s.name), s.station);
        assertThat(messages(errors(), "S7")).singleElement().asString().contains("has two provider mappings named m");
    }

    @Test
    void theFindingsOfTheProviderStructureComeAlong() {
        Services.create(s.weather, "climate");
        Resource untyped = Resources.optional(s.climate, "untyped", s.decimal);
        untyped.setType(null);
        Services.optional(s.weather, "air quality");
        assertThat(messages(errors(), "P1")).singleElement().asString().contains("has two services named climate");
        assertThat(messages(errors(), "P2")).singleElement().asString().contains("untyped has no type");
        assertThat(warnings("P3")).singleElement().asString().contains("'air quality' becomes an id");
    }

    @Test
    void s9_attributesNoPathReads() {
        List<Finding> s8 = SensinactMappingModelValidator.check(s.model).stream().filter(f -> f.rule().equals("S8")).toList();
        assertThat(s8).extracting(Finding::message).containsExactly(
                "ProviderMapping 'm' reads no path from Reading.takenAt.");
        assertThat(s8).hasSize(1);
    }

    private List<Finding> errors() {
        return SensinactMappingModelValidator.check(s.model).stream().filter(f -> f.severity() == Severity.ERROR).toList();
    }

    private List<String> warnings(String rule) {
        return SensinactMappingModelValidator.check(s.model).stream()
                .filter(f -> f.severity() == Severity.WARNING && f.rule().equals(rule)).map(Finding::message).toList();
    }

    private static List<String> messages(List<Finding> findings, String rule) {
        return findings.stream().filter(f -> f.rule().equals(rule)).map(Finding::message).toList();
    }

    private static List<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::rule).toList();
    }
}
