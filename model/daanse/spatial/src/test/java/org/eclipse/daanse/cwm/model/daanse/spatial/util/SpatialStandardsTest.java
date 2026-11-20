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
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reference systems, grid systems and the geometry type as a standard set: the checked-in file is what the code
 * writes, every element has a UUID of its own, the set keeps the rules of the model, and a model file refers to its
 * elements by URI and UUID.
 */
class SpatialStandardsTest {

    private static final Path DIR = Path.of(System.getProperty("user.dir"),
            "src/main/resources/org/eclipse/daanse/cwm/model/daanse/spatial/util");

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    /** Run with -Ddaanse.standard.write=true to write the file anew after a change to the code. */
    @Test
    void theCheckedInFileIsWhatTheCodeWrites() {
        assertThat(StandardSets.upToDate(SpatialStandards.standardSet(), DIR.resolve(SpatialStandards.SET_FILE)))
                .as(SpatialStandards.SET_FILE + " is what the code writes; run the test with -D" + StandardSets.WRITE
                        + "=true to write it again")
                .isTrue();
    }

    @Test
    void everyElementHasAUuidOfItsOwn() {
        Resource set = SpatialStandards.load(new ResourceSetImpl()).eResource();
        List<String> ids = new ArrayList<>();
        set.getAllContents().forEachRemaining(e -> ids.add(set.getURIFragment(e)));
        assertThat(ids).as("the package and its 17 elements").hasSize(18).doesNotHaveDuplicates()
                .allMatch(StandardSets.SHORT_UUID.asMatchPredicate());
    }

    @Test
    void theFileInTheJarHoldsTheSystemsAsTheirRegistersDefineThem() {
        Package spatial = SpatialStandards.load(new ResourceSetImpl());
        Resource set = spatial.eResource();
        assertThat(set.getURI().toString()).as("the URI of the set").isEqualTo(
                "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.spatial/spatial-standards.xmi");
        assertThat(spatial.getOwnedElement().stream().filter(ReferenceSystem.class::isInstance)
                .map(ReferenceSystem.class::cast).map(ReferenceSystems::uri)).as("the reference systems")
                .containsExactly(
                        "http://www.opengis.net/def/crs/OGC/1.3/CRS84", "http://www.opengis.net/def/crs/OGC/0/CRS84h",
                        "http://www.opengis.net/def/crs/EPSG/0/4326", "http://www.opengis.net/def/crs/EPSG/0/4979",
                        "http://www.opengis.net/def/crs/EPSG/0/3857", "http://www.opengis.net/def/crs/EPSG/0/4258",
                        "http://www.opengis.net/def/crs/EPSG/0/3035", "http://www.opengis.net/def/crs/EPSG/0/25832",
                        "http://www.opengis.net/def/crs/EPSG/0/25833", "http://www.opengis.net/def/crs/EPSG/0/31466",
                        "http://www.opengis.net/def/crs/EPSG/0/31467", "http://www.opengis.net/def/crs/EPSG/0/31468",
                        "http://www.opengis.net/def/crs/EPSG/0/31469");
        assertThat(SpatialStandards.epsg(spatial, 4326).orElseThrow()).as("WGS 84 latitude first")
                .extracting(ReferenceSystem::getAxisOrder, ReferenceSystem::getAxes)
                .containsExactly(AxisOrder.NORTH_EAST, 2L);
        assertThat(SpatialStandards.epsg(spatial, 4979).orElseThrow().getAxes()).as("with the height").isEqualTo(3);
        assertThat(SpatialStandards.epsg(spatial, 25832).orElseThrow().getAxisOrder()).as("UTM east first")
                .isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(SpatialStandards.referenceSystem(spatial, ReferenceSystemAuthority.OGC, "CRS84")).as("CRS84")
                .isPresent();
        assertThat(SpatialElements.gridSystems(spatial)).as("the grid systems").extracting(GridSystem::getName,
                GridSystem::getMinResolution, GridSystem::getMaxResolution, GridSystem::isNested).containsExactly(
                        tuple("geohash", 1L, 12L, true), tuple("h3", 0L, 15L, false), tuple("quadkey", 1L, 23L, true));
        assertThat(SpatialElements.geometryType(spatial).getName()).as("the geometry type").isEqualTo("Geometry");
    }

    @Test
    void theSetKeepsTheRulesOfTheModel() {
        Package spatial = SpatialStandards.load(new ResourceSetImpl());
        assertThat(SpatialModelValidator.check(spatial)).as("findings of the spatial rules").isEmpty();
        assertThat(Diagnostician.INSTANCE.validate(spatial).getChildren()).as("findings of the EMF check, OCL included")
                .isEmpty();
    }

    /**
     * A model file gives a property the geometry type and its geometries a reference system of the set, written as
     * {@code href="<uri>#<uuid>"}; read in a fresh resource set where the set is only mapped, both reach the set in
     * the jar.
     */
    @Test
    void aModelFileRefersToTheGeometryTypeAndAReferenceSystemByUriAndUuid(@TempDir Path dir) throws Exception {
        ResourceSet writing = new ResourceSetImpl();
        Package spatial = SpatialStandards.load(writing);
        Path file = dir.resolve("districts.xmi");
        Resource model = new XMIResourceImpl(URI.createFileURI(file.toString()));
        writing.getResources().add(model);
        Model districts = Models.create("districts");
        model.getContents().add(districts);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class district = CoreFactory.eINSTANCE.createClass();
        district.setName("district");
        districts.getOwnedElement().add(district);
        Attribute shape = CoreFactory.eINSTANCE.createAttribute();
        shape.setName("shape");
        shape.setType(SpatialElements.geometryType(spatial));
        district.getFeature().add(shape);
        ReferenceSystem utm32 = SpatialStandards.epsg(spatial, 25832).orElseThrow();
        SpatialElements.feature(districts, "districtShapes", GeometryKind.POLYGON, utm32, GeometryStorage.NATIVE,
                shape);
        model.save(Map.of());
        String geometryId = spatial.eResource().getURIFragment(shape.getType());
        String utm32Id = spatial.eResource().getURIFragment(utm32);
        assertThat(Files.readString(file)).as("the references by the URI of the set and the UUIDs").contains(
                "href=\"" + SpatialStandards.SET_URI + "#" + geometryId + "\"",
                "href=\"" + SpatialStandards.SET_URI + "#" + utm32Id + "\"");

        ResourceSet reading = new ResourceSetImpl();
        SpatialStandards.map(reading);
        Resource again = new XMIResourceImpl(URI.createFileURI(file.toString()));
        reading.getResources().add(again);
        again.load(Map.of());
        Model read = (Model) again.getContents().get(0);
        Attribute readShape = (Attribute) ((org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class) read
                .getOwnedElement().get(0)).getFeature().get(0);
        GeometryFeature shapes = (GeometryFeature) read.getOwnedElement().get(1);
        Resource set = reading.getResource(URI.createURI(SpatialStandards.SET_URI), false);
        assertThat(readShape.getType()).as("the geometry type of the set").isSameAs(set.getEObject(geometryId));
        assertThat(shapes.getReferenceSystem()).as("the reference system of the set")
                .isSameAs(set.getEObject(utm32Id));
        assertThat(SpatialModelValidator.check(read)).as("the model with the set's elements keeps the rules").isEmpty();
    }
}
