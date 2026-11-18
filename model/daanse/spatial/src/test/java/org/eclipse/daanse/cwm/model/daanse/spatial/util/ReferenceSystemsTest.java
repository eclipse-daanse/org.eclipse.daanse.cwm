/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.junit.jupiter.api.Test;

/** A reference system's OGC URI, its EPSG number and when two systems are one. */
class ReferenceSystemsTest {

    private final Land land = new Land();

    @Test
    void theUriNamesAuthorityVersionAndCode() {
        assertThat(ReferenceSystems.uri(land.utm32)).isEqualTo("http://www.opengis.net/def/crs/EPSG/0/25832");
        ReferenceSystem crs84 = SpatialElements.referenceSystem(land.geodata, "crs84", ReferenceSystemAuthority.OGC,
                "CRS84", ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH);
        crs84.setVersion("1.3");
        assertThat(ReferenceSystems.uri(crs84)).isEqualTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84");
        crs84.setVersion(" ");
        assertThat(ReferenceSystems.uri(crs84)).as("a blank version is none").isEqualTo(
                "http://www.opengis.net/def/crs/OGC/0/CRS84");
        assertThat(ReferenceSystems.name(land.utm32)).isEqualTo("EPSG 25832");
    }

    @Test
    void onlyAnEpsgSystemHasTheNumberADatabaseTakes() {
        assertThat(ReferenceSystems.epsg(land.utm32)).hasValue(25832);
        assertThat(ReferenceSystems.epsg(SpatialElements.referenceSystem(land.geodata, "crs84",
                ReferenceSystemAuthority.OGC, "CRS84", ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH)))
                .isEmpty();
        assertThat(ReferenceSystems.epsg(SpatialElements.referenceSystem(land.geodata, "named",
                ReferenceSystemAuthority.EPSG, "UTM32", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH)))
                .isEmpty();
        assertThat(ReferenceSystems.epsg(SpatialElements.referenceSystem(land.geodata, "zero",
                ReferenceSystemAuthority.EPSG, "0", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH))).isEmpty();
        assertThat(ReferenceSystems.epsg(null)).isEmpty();
    }

    @Test
    void twoElementsOfOneUriAreOneSystem() {
        ReferenceSystem again = SpatialElements.referenceSystem(land.geodata, "again", ReferenceSystemAuthority.EPSG,
                " 25832 ", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
        assertThat(ReferenceSystems.same(land.utm32, again)).isTrue();
        assertThat(ReferenceSystems.same(land.utm32, land.utm32)).isTrue();
        assertThat(ReferenceSystems.same(land.utm32, land.laea)).isFalse();
        again.setVersion("9.8");
        assertThat(ReferenceSystems.same(land.utm32, again)).as("another version").isFalse();
        assertThat(ReferenceSystems.same(land.utm32, null)).isFalse();
        assertThat(ReferenceSystems.same(null, null)).isTrue();
    }
}
