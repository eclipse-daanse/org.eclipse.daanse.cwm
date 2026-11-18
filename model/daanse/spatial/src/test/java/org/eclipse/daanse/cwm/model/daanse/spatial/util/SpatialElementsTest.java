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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.DistanceBand;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryMultiplicity;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridLevel;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.ResultForm;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPredicate;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialRelation;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialWeight;
import org.eclipse.daanse.cwm.model.daanse.spatial.TieBreak;
import org.eclipse.daanse.cwm.model.daanse.spatial.UnmatchedPolicy;
import org.junit.jupiter.api.Test;

/** The create helpers hang each element on its owner and the finders find it from a column or a feature. */
class SpatialElementsTest {

    private final Land land = new Land();

    @Test
    void aFeatureIsOwnedByItsNamespaceAndFoundFromItsColumn() {
        assertThat(land.communes.getNamespace()).isSameAs(land.geodata);
        assertThat(land.communes.getModelElement()).containsExactly(land.communeShape);
        assertThat(land.communes.getKind()).isEqualTo(GeometryKind.POLYGON);
        assertThat(land.communes.getReferenceSystem()).isSameAs(land.utm32);
        assertThat(land.communes.getStorage()).isEqualTo(GeometryStorage.NATIVE);
        assertThat(land.communes.getMultiplicity()).as("single or multi, unless said").isEqualTo(
                GeometryMultiplicity.EITHER);
        assertThat(land.communes.isHasHeight()).isFalse();
        assertThat(land.communes.getAxisOrder()).as("stored east first unless said").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(land.communes.getCoordinateEpoch()).isNull();
        assertThat(SpatialElements.features(land.communeShape)).containsExactly(land.communes);
        assertThat(SpatialElements.features(land.communeId)).isEmpty();
        assertThatThrownBy(() -> SpatialElements.feature(land.geodata, "none", GeometryKind.POINT, land.wgs84,
                GeometryStorage.NATIVE)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("column");
    }

    @Test
    void aReferenceSystemIsOwnedByItsNamespace() {
        assertThat(land.utm32.getNamespace()).isSameAs(land.geodata);
        assertThat(land.utm32.getName()).isEqualTo("EPSG_25832");
        assertThat(land.utm32.getAuthority()).isEqualTo(ReferenceSystemAuthority.EPSG);
        assertThat(land.utm32.getCode()).isEqualTo("25832");
        assertThat(land.utm32.getKind()).isEqualTo(ReferenceSystemKind.PROJECTED);
        assertThat(land.utm32.getAxes()).as("two axes unless said").isEqualTo(2);
        assertThat(land.utm32.getAxisOrder()).as("east first, as stated").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(land.wgs84.getAxisOrder()).as("north first, as stated").isEqualTo(AxisOrder.NORTH_EAST);
        assertThat(land.utm32.isSetAxisOrder()).isTrue();
        assertThat(land.utm32.isDynamic()).isFalse();
        assertThat(land.utm32.getVersion()).as("no version is 0").isEqualTo("0");
        ReferenceSystem crs84 = SpatialElements.referenceSystem(land.geodata, "crs84", ReferenceSystemAuthority.OGC,
                "CRS84", ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH);
        assertThat(crs84.getCode()).isEqualTo("CRS84");
        assertThat(land.geodata.getOwnedElement()).contains(crs84);
        assertThatThrownBy(() -> SpatialElements.epsg(land.geodata, 4258, ReferenceSystemKind.GEOGRAPHIC, null))
                .as("the axis order is stated, there is no default").isInstanceOf(NullPointerException.class)
                .hasMessageContaining("axisOrder");
    }

    /** CRS84 and CRS84h with the versions their OGC URIs write, so that a model need not know them. */
    @Test
    void theTwoDefaultsOfOgcApiFeatures() {
        ReferenceSystem crs84 = SpatialElements.crs84(land.geodata);
        ReferenceSystem crs84h = SpatialElements.crs84h(land.geodata);
        assertThat(ReferenceSystems.uri(crs84)).isEqualTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84");
        assertThat(ReferenceSystems.uri(crs84h)).isEqualTo("http://www.opengis.net/def/crs/OGC/0/CRS84h");
        assertThat(crs84.getAxes()).isEqualTo(2);
        assertThat(crs84h.getAxes()).isEqualTo(3);
        assertThat(crs84.getAxisOrder()).as("longitude first").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(crs84h.getAxisOrder()).as("longitude first").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(crs84.isSetAxisOrder() && crs84h.isSetAxisOrder()).as("stated").isTrue();
        assertThat(crs84h.getKind()).isEqualTo(ReferenceSystemKind.GEOGRAPHIC);
        assertThat(crs84.getNamespace()).isSameAs(land.geodata);
        assertThat(SpatialModelValidator.check(land.model)).isEmpty();
    }

    /**
     * The logical type of a geometry is a GeometryType, made once per namespace; a plain CWM data type that only bears
     * the name is none.
     */
    @Test
    void theGeometryTypeIsAClassOfItsOwn() {
        assertThat(SpatialElements.geometryType(land.geodata)).isSameAs(SpatialElements.geometryType(land.geodata));
        assertThat(SpatialElements.geometryType(land.geodata).getName()).isEqualTo(SpatialElements.GEOMETRY);
        assertThat(SpatialElements.isGeometryType(SpatialElements.geometryType(land.geodata))).isTrue();
        DataType named = CoreFactory.eINSTANCE.createDataType();
        named.setName("Geometry");
        assertThat(SpatialElements.isGeometryType(named)).as("a name is no type").isFalse();
        assertThat(SpatialElements.isGeometryType(land.geometry)).as("the SQL type of a column").isFalse();
        assertThat(SpatialElements.isGeometryType(null)).isFalse();
    }

    @Test
    void aFeatureIsFoundAlsoThroughAnIndex() {
        org.eclipse.emf.ecore.resource.Resource resource = new org.eclipse.emf.ecore.resource.impl.ResourceImpl();
        resource.getContents().add(land.model);
        InverseReferences.install(resource);
        assertThat(SpatialElements.features(land.stationPosition)).containsExactly(land.stations);
    }

    @Test
    void aRelationNamesItsFeaturesKeysAndPolicies() {
        SpatialRelation r = land.communesInCounties();
        assertThat(r.getNamespace()).isSameAs(land.geodata);
        assertThat(r.getChild()).isSameAs(land.communes);
        assertThat(r.getChildKey()).isSameAs(land.communeId);
        assertThat(r.getParent()).isSameAs(land.counties);
        assertThat(r.getParentKey()).isSameAs(land.countyId);
        assertThat(r.getPredicate()).isEqualTo(SpatialPredicate.CENTROID_WITHIN);
        assertThat(r.getUnmatched()).isEqualTo(UnmatchedPolicy.KEEP_NULL);
        assertThat(r.isSetWeight()).isFalse();
        assertThat(r.getTieBreak()).as("no tie break: a tie is an error").isEqualTo(TieBreak.ERROR);
        assertThat(r.getResultForm()).isEqualTo(ResultForm.TABLE);
        assertThat(SpatialElements.asChild(land.communes)).containsExactly(r);
        assertThat(SpatialElements.asParent(land.counties)).containsExactly(r);
        assertThat(SpatialElements.asParent(land.communes)).isEmpty();
    }

    @Test
    void theOptionalPartsOfARelation() {
        SpatialRelation lakes = SpatialElements.workingSystem(land.lakesInCountries(), land.laea);
        assertThat(lakes.getWeight()).isEqualTo(SpatialWeight.AREA);
        assertThat(lakes.getMinimumShare()).isNull();
        assertThat(lakes.getWorkingSystem()).isSameAs(land.laea);
        assertThat(land.communesInCounties().getWorkingSystem()).isNull();
        SpatialRelation near = SpatialElements.withinDistance(land.communesInCounties(), "500");
        assertThat(near.getPredicate()).isEqualTo(SpatialPredicate.WITHIN_DISTANCE);
        assertThat(near.getDistance()).isEqualTo("500");
        assertThat(SpatialElements.explicitKey(near, land.communeCounty).getExplicitKey())
                .isSameAs(land.communeCounty);
        assertThat(SpatialElements.result(near, land.county).getResult()).isSameAs(land.county);
    }

    @Test
    void aGridLevelAndADistanceBand() {
        GridLevel regions = SpatialElements.grid(land.geodata, "regions", land.stations, land.stationId, land.h3(),
                5, null);
        GridLevel cells = SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.h3(), 7,
                regions);
        assertThat(cells.getParent()).isSameAs(regions);
        assertThat(cells.getResolution()).isEqualTo(7);
        assertThat(cells.getSourceKey()).isSameAs(land.stationId);
        assertThat(cells.getResultForm()).as("a table until said otherwise").isEqualTo(ResultForm.TABLE);
        assertThat(regions.getParent()).isNull();
        DistanceBand band = SpatialElements.band(land.geodata, "toTheRiver", land.stations, land.stationId,
                land.rivers, "1000", "5000");
        assertThat(band.getBound()).containsExactly("1000", "5000");
        assertThat(band.getSourceKey()).isSameAs(land.stationId);
        assertThat(band.getNamespace()).isSameAs(land.geodata);
        Table classes = land.table("station_band");
        assertThat(SpatialElements.result(band, classes).getResult()).isSameAs(classes);
        assertThat(SpatialElements.result(cells, classes).getResult()).isSameAs(classes);
        assertThatThrownBy(() -> SpatialElements.band(land.geodata, "none", land.stations, land.stationId,
                land.rivers)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bound");
        assertThatThrownBy(() -> SpatialElements.grid(land.geodata, "noKey", land.stations, null, land.h3(), 5,
                null)).isInstanceOf(NullPointerException.class).hasMessageContaining("sourceKey");
    }

    /**
     * The three grid systems the helpers know, each made once in a model and found again by its identifier wherever it
     * is owned; a system of one identifier with other resolutions is refused.
     */
    @Test
    void aGridSystemIsMadeOnceAndFoundAgainByItsIdentifier() {
        GridSystem geohash = land.geohash();
        GridSystem h3 = land.h3();
        GridSystem quadkey = land.quadkey();
        assertThat(List.of(geohash, h3, quadkey)).extracting(GridSystem::getName, GridSystem::getMinResolution,
                GridSystem::getMaxResolution, GridSystem::isNested).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("geohash", 1L, 12L, true),
                        org.assertj.core.groups.Tuple.tuple("h3", 0L, 15L, false),
                        org.assertj.core.groups.Tuple.tuple("quadkey", 1L, 23L, true));
        assertThat(geohash.getNamespace()).isSameAs(land.geodata);
        assertThat(SpatialElements.geohash(land.geo)).as("found again from another package of the model")
                .isSameAs(geohash);
        assertThat(SpatialElements.gridSystem(land.stationId, SpatialElements.H3)).contains(h3);
        assertThat(SpatialElements.gridSystem(land.stationId, "s2")).isEmpty();
        assertThat(SpatialElements.gridSystems(land.model)).containsExactly(geohash, h3, quadkey);
        GridSystem coarse = SpatialElements.gridSystem(land.geodata, "https://grids.example/coarse", 2, 4, true);
        assertThat(SpatialElements.gridSystem(land.geo, "https://grids.example/coarse", 2, 4, true)).isSameAs(coarse);
        assertThatThrownBy(() -> SpatialElements.gridSystem(land.geodata, "quadkey", 1, 22, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the model holds the grid system quadkey with the resolutions 1 to 23, nested; asked for "
                        + "1 to 22, nested");
        assertThat(SpatialElements.gridSystems(new Land().model)).as("another model has its own").isEmpty();
    }

    @Test
    void featuresOfTwoTablesShareOneFeature() {
        GeometryFeature areas = SpatialElements.feature(land.geodata, "areas", GeometryKind.POLYGON, land.laea,
                GeometryStorage.NATIVE, Land.column(land.table("reserve"), "shape", land.geometry),
                Land.column(land.table("park"), "shape", land.geometry));
        assertThat(areas.getModelElement()).hasSize(2);
        assertThat(SpatialModelValidator.check(land.model)).isEmpty();
    }
}
