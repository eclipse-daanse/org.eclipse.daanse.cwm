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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactModelValidator.Severity;
import org.junit.jupiter.api.Test;

/** A positive and a negative case per rule of the structure validator. */
class SensinactModelValidatorTest {

    private final Weather w = new Weather();

    @Test
    void theWeatherProviderHasNoFinding() {
        assertThat(SensinactModelValidator.check(w.model)).isEmpty();
        assertThat(SensinactModelValidator.check(w.weather)).isEmpty();
    }

    @Test
    void p1_servicesAndResourcesHaveUniqueNames() {
        Services.create(w.weather, "climate");
        Resources.create(w.climate, "temperature", w.decimal);
        assertThat(messages(errors(), "P1")).containsExactlyInAnyOrder(
                "provider weather has two services named climate.",
                "service sensors.providers.weather.climate has two resources named temperature.");
    }

    @Test
    void p2_everyResourceIsTyped() {
        Resource untyped = Resources.optional(w.climate, "untyped", w.decimal);
        untyped.setType(null);
        assertThat(messages(errors(), "P2"))
                .containsExactly("resource sensors.providers.weather.climate.untyped has no type.");
        assertThat(SensinactModelValidator.validate(w.model))
                .containsExactly("P2 resource sensors.providers.weather.climate.untyped has no type.");
    }

    @Test
    void p3_namesArePlainIds() {
        Services.optional(w.weather, "air quality");
        assertThat(errors()).isEmpty();
        assertThat(SensinactModelValidator.check(w.model)).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.rule()).isEqualTo("P3");
            assertThat(f.message()).contains("'air quality' becomes an id");
        });
    }

    private List<Finding> errors() {
        return SensinactModelValidator.check(w.model).stream().filter(f -> f.severity() == Severity.ERROR).toList();
    }

    private static List<String> messages(List<Finding> findings, String rule) {
        return findings.stream().filter(f -> f.rule().equals(rule)).map(Finding::message).toList();
    }
}
