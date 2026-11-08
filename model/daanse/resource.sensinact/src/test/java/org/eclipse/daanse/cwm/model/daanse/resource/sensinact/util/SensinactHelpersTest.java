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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.junit.jupiter.api.Test;

/** The create helpers: owner, name, required values, the explicit form of generating resources. */
class SensinactHelpersTest {

    private final Weather w = new Weather();

    @Test
    void providersServicesAndResourcesAreOwnedAndNamed() {
        assertThat(w.weather.getNamespace()).isSameAs(w.providers);
        assertThat(w.climate.getNamespace()).isSameAs(w.weather);
        assertThat(w.climate.isRequired()).isTrue();
        assertThat(w.temperature.getOwner()).isSameAs(w.climate);
        assertThat(w.temperature.getType()).isSameAs(w.decimal);
        assertThat(w.temperature.isRequired()).isTrue();
        assertThat(w.stateResource.isRequired()).isFalse();
        assertThat(Services.optional(w.weather, "status").isRequired()).isFalse();
    }

    @Test
    void resourcesFromTheAttributesOfAClass() {
        Service measured = Services.create(w.weather, "measured");
        List<Resource> created = Resources.fromAttributes(measured, w.gauge, w.state);
        assertThat(created).extracting(Resource::getName).containsExactly("level", "value", "takenAt");
        assertThat(created.get(0).getType()).isSameAs(w.decimal);
        // class-typed and many-valued attributes are left out
        assertThat(Resources.fromAttributes(Services.create(w.weather, "station"), w.station))
                .extracting(Resource::getName).containsExactly("name");
    }

    @Test
    void wrongArgumentsFailAtOnce() {
        assertThatThrownBy(() -> Resources.create(w.climate, "x", null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> Providers.create(null, "x")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parent");
        assertThatThrownBy(() -> Services.create(null, "x")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }
}
