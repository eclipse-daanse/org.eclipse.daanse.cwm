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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.sensinact.AdminMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ArrivalTimeSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.LiteralSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ResourceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;
import org.junit.jupiter.api.Test;

/** The create helpers: owner, name, required values, the explicit forms of generating resources. */
class SensinactHelpersTest {

    private final Station s = new Station();

    @Test
    void providersServicesAndResourcesAreOwnedAndNamed() {
        assertThat(s.weather.getNamespace()).isSameAs(s.providers);
        assertThat(s.climate.getNamespace()).isSameAs(s.weather);
        assertThat(s.climate.isRequired()).isTrue();
        assertThat(s.temperature.getOwner()).isSameAs(s.climate);
        assertThat(s.temperature.getType()).isSameAs(s.decimal);
        assertThat(s.temperature.isRequired()).isTrue();
        assertThat(s.stateResource.isRequired()).isFalse();
        assertThat(Services.optional(s.weather, "status").isRequired()).isFalse();
    }

    @Test
    void mappingsAreNamedAfterWhatTheyMap() {
        assertThat(s.mapping.getName()).isEqualTo("m");
        assertThat(s.mapping.getProvider()).isSameAs(s.weather);
        assertThat(s.mapping.getSourceClass()).containsExactly(s.station);
        assertThat(s.climateMapping.getName()).isEqualTo("climate");
        assertThat(s.climateMapping.getNamespace()).isSameAs(s.mapping);
        ResourceMapping rm = SensinactBindings.resourceMappings(s.climateMapping).get(0);
        assertThat(rm.getName()).isEqualTo("temperature");
        assertThat(ValueSources.steps(rm.getValue())).containsExactly(s.value);
        AdminMapping admin = AdminMappings.location(AdminMappings.create(s.mapping), ValueSources.path(s.locationRef, s.lat),
                ValueSources.path(s.locationRef, s.lon));
        assertThat(admin.getName()).isEqualTo("admin");
        assertThat(s.mapping.getAdmin()).isSameAs(admin);
    }

    @Test
    void resourcesFromTheAttributesOfAClass() {
        Service measured = Services.create(s.weather, "measured");
        List<Resource> created = Resources.fromAttributes(measured, s.gauge, s.state);
        assertThat(created).extracting(Resource::getName).containsExactly("level", "value", "takenAt");
        assertThat(created.get(0).getType()).isSameAs(s.decimal);
        // class-typed and many-valued attributes are left out
        assertThat(Resources.fromAttributes(Services.create(s.weather, "station"), s.station))
                .extracting(Resource::getName).containsExactly("name");
    }

    @Test
    void resourceMappingsForTheAttributesOfTheSameName() {
        Service measured = Services.create(s.weather, "measured");
        Resources.fromAttributes(measured, s.gauge);
        ServiceMapping sm = ServiceMappings.create(s.mapping, measured,
                ValueSources.at(ValueSources.path(s.readings), 0));
        List<ResourceMapping> created = ResourceMappings.mapAttributes(sm, s.gauge, s.takenAt);
        assertThat(created).extracting(ResourceMapping::getName).containsExactly("level", "value", "state");
        assertThat(ValueSources.steps(created.get(1).getValue())).containsExactly(s.value);
    }

    @Test
    void valueSourcesAndTheirModifiers() {
        PathSource v = ValueSources.format(ValueSources.orElse(ValueSources.at(
                ValueSources.path(s.readings, s.takenAt), 2), "2026-01-01T00:00:00Z"), "yyyy-MM-dd'T'HH:mm:ssX");
        assertThat(v.getIndex()).isEqualTo(2);
        assertThat(v.isSetIndex()).isTrue();
        assertThat(v.getFallback()).isInstanceOfSatisfying(LiteralSource.class,
                l -> assertThat(l.getLiteral()).isEqualTo("2026-01-01T00:00:00Z"));
        assertThat(v.getFormat()).isEqualTo("yyyy-MM-dd'T'HH:mm:ssX");
        assertThat(ValueSources.chain(v)).containsExactly(v, v.getFallback());
        assertThat(ValueSources.steps(v)).containsExactly(s.readings, s.takenAt);
        assertThat(ValueSources.steps(ValueSources.literal("x"))).isEmpty();
        assertThat(ValueSources.orElse(ValueSources.path(s.takenAt), ValueSources.arrivalTime()).getFallback())
                .isInstanceOf(ArrivalTimeSource.class);
        PathSource filtered = ValueSources.where(ValueSources.path(s.readings, s.value), "state=OK");
        assertThat(filtered.getFilter().getLanguage()).isEqualTo(SensinactBindings.FILTER_LANGUAGE);
        assertThat(filtered.getFilter().getBody()).isEqualTo("state=OK");
        assertThat(ValueSources.path(s.name).isSetIndex()).isFalse();
    }

    @Test
    void wrongArgumentsFailAtOnce() {
        assertThatThrownBy(() -> ValueSources.path()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderMappings.create(s.mappings, "x", s.weather, ValueSources.literal("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Resources.create(s.climate, "x", null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> Providers.create(null, "x")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parent");
    }
}
