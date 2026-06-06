/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.junit.jupiter.api.Test;

/** Unit cover for the immutable {@link DdlSettings} record and its withers. */
class DdlSettingsTest {

    @Test
    void defaults_qualify_schema_use_if_not_exists_and_no_cascade() {
        DdlSettings d = DdlSettings.defaults();
        assertThat(d.includeSchema()).isTrue();
        assertThat(d.ifNotExists()).isTrue();
        assertThat(d.cascade()).isFalse();
    }

    @Test
    void withers_change_one_field_and_leave_the_rest() {
        DdlSettings d = DdlSettings.defaults();
        assertThat(d.withIncludeSchema(false)).isEqualTo(new DdlSettings(false, true, false));
        assertThat(d.withIfNotExists(false)).isEqualTo(new DdlSettings(true, false, false));
        assertThat(d.withCascade(true)).isEqualTo(new DdlSettings(true, true, true));
        // original is untouched (immutable)
        assertThat(d).isEqualTo(DdlSettings.defaults());
    }

    @Test
    void withers_chain() {
        DdlSettings d = DdlSettings.defaults().withIncludeSchema(false).withCascade(true);
        assertThat(d).isEqualTo(new DdlSettings(false, true, true));
    }
}
