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

import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.junit.jupiter.api.Test;

/** What the structure of a provider says without storing it, and the reading of a class model. */
class SensinactStructureTest {

    private final Weather w = new Weather();

    @Test
    void servicesAndResourcesInTheirOrder() {
        Service status = Services.optional(w.weather, "status");
        assertThat(SensinactStructure.services(w.weather)).containsExactly(w.climate, status);
        assertThat(SensinactStructure.resources(w.climate)).containsExactly(w.temperature, w.stateResource);
        assertThat(SensinactStructure.resources(status)).isEmpty();
    }

    @Test
    void extraMetadataAndDescriptionOfAResource() {
        TaggedValues.set(w.temperature, "sensorthings.unit.name", "degree Celsius");
        TaggedValues.set(w.temperature, "daanse.ecore.id", "true");
        Descriptions.describe(w.temperature, "documentation", "en", "Air temperature 2 m above ground");
        assertThat(SensinactStructure.extraMetadata(w.temperature))
                .containsExactly(Map.entry("sensorthings.unit.name", "degree Celsius"));
        assertThat(SensinactStructure.description(w.temperature)).contains("Air temperature 2 m above ground");
        assertThat(SensinactStructure.description(w.stateResource)).isEmpty();
    }

    @Test
    void ancestorsAndAttributesOwnFirst() {
        assertThat(SensinactStructure.lineage(w.gauge)).containsExactly(w.gauge, w.reading);
        assertThat(SensinactStructure.allAttributes(w.gauge)).containsExactly(w.level, w.value, w.takenAt, w.state);
    }

    @Test
    void manyValuedFeatures() {
        assertThat(SensinactStructure.isMany(w.readings)).isTrue();
        assertThat(SensinactStructure.isMany(w.value)).isFalse();
    }
}
