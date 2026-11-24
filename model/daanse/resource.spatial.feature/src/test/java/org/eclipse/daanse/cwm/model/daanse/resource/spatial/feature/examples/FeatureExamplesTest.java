/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureCollections;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureModelValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureProperties;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.Queryables;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.TypeFamily;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.ListHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.Ordinate;
import org.eclipse.daanse.cwm.model.daanse.spatial.PartsHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialModelValidator;
import org.junit.jupiter.api.Test;

/**
 * The examples of the README, one test each, in the order of its sections and on its running scenario, the
 * {@link City}: the code of a section stands in the city or in its test here. {@link #everySnippetOfTheReadmeStands()}
 * fails where a line of code of the README is in neither, so that README and code cannot drift.
 */
class FeatureExamplesTest {

    private final City city = new City();

    /** Section "A collection and its key". */
    @Test
    void aCollectionAndItsKey() {
        FeatureCollection districts = city.districts;
        FeatureProperty districtId = city.districtId;
        FeatureProperty districtLabel = city.districtLabel;
        assertThat(FeatureCollections.properties(districts)).extracting(FeatureProperty::getName).containsExactly(
                "district_id", "label", "residents", "area_km2", "valid_from", "valid_to", "outline");
        assertThat(FeatureCollections.property(districts, "label")).containsSame(districtLabel);
        assertThat(FeatureCollections.title(districts)).isEqualTo("City districts");
        assertThat(FeatureCollections.description(districts))
                .contains("The districts of the city, each valid from a day until a day.");
        assertThat(FeatureProperties.required(districtId)).isTrue();
        assertThat(FeatureProperties.title(districtLabel)).isEqualTo("label");
        assertThat(districts.getFeatureKey()).isSameAs(districtId);
    }

    /** Section "A geometry in a property": the schools, each point in the property location. */
    @Test
    void aGeometryInAProperty() {
        FeatureCollection schools = city.schools;
        GeometryFeature schoolPoint = city.schoolPoint;
        FeatureProperty location = city.location;
        assertThat(FeatureCollections.holding(schoolPoint, schools)).get().isInstanceOfSatisfying(
                WholeHolding.class, whole -> assertThat(whole.getAttribute()).isSameAs(location));
        assertThat(FeatureCollections.geometryFeature(location)).contains(schoolPoint);
        assertThat(FeatureProperties.isGeometry(location)).isTrue();
        assertThat(schools.getGeometry()).isSameAs(schoolPoint);
        assertThat(location.getType()).isSameAs(SpatialElements.geometryType(city.types));
    }

    /** Section "A point as two numbers, with its envelope": the bus stops. */
    @Test
    void aPointAsTwoNumbersWithItsEnvelope() {
        FeatureCollection stops = city.stops;
        GeometryFeature stopPoint = city.stopPoint;
        assertThat(FeatureCollections.envelope(stops)).contains(city.stopBox);
        assertThat(FeatureCollections.holding(stopPoint, stops)).get().isInstanceOfSatisfying(PartsHolding.class,
                parts -> {
                    assertThat(SpatialElements.attribute(parts, Ordinate.EAST)).contains(city.east);
                    assertThat(SpatialElements.attribute(parts, Ordinate.NORTH)).contains(city.north);
                });
        assertThat(FeatureProperties.isGeometry(city.east)).as("a number").isFalse();
        assertThat(city.minEast.isReturnable()).isFalse();
        assertThat(FeatureModelValidator.check(city.model)).isEmpty();
    }

    /** Section "One point, a property and two numbers": the trees. */
    @Test
    void onePointAPropertyAndTwoNumbers() {
        FeatureCollection trees = city.trees;
        GeometryFeature treePoint = city.treePoint;
        assertThat(treePoint.getHolding()).hasSize(2);
        assertThat(FeatureCollections.holding(treePoint, trees)).get().isInstanceOf(WholeHolding.class);
        assertThat(FeatureCollections.holdings(treePoint, trees)).containsExactlyElementsOf(treePoint.getHolding());
        assertThat(SpatialElements.features(city.latitude)).containsExactly(treePoint);
        assertThat(FeatureModelValidator.check(city.model)).isEmpty();
    }

    /** Section "A point as one text": a collection whose point is the text 'lat,lon' of one property. */
    @Test
    void aPointAsOneText() {
        Package features = city.features;
        Package geodata = city.geodata;
        DataType string = city.string;
        ReferenceSystem crs84 = city.crs84;
        FeatureCollection stations = FeatureCollections.create(features, "stations");
        FeatureCollections.key(stations, FeatureProperties.required(stations, "station_id", string));
        FeatureProperty coordinates = FeatureProperties.create(stations, "coordinates", string);
        GeometryFeature stationPoint = SpatialElements.feature(geodata, "stationPoint", GeometryKind.POINT, crs84);
        SpatialElements.list(stationPoint, coordinates, ",", Ordinate.NORTH, Ordinate.EAST);
        FeatureCollections.geometry(stations, stationPoint);
        assertThat(FeatureCollections.holding(stationPoint, stations)).get().isInstanceOf(ListHolding.class);
        assertThat(FeatureModelValidator.check(city.model)).isEmpty();
        assertThat(SpatialModelValidator.check(city.model)).isEmpty();
    }

    /** Section "The time of a feature". */
    @Test
    void theTimeOfAFeature() {
        assertThat(city.districts.getIntervalStart()).isSameAs(city.validFrom);
        assertThat(city.districts.getIntervalEnd()).isSameAs(city.validTo);
        assertThat(city.schools.getInstant()).isSameAs(city.opened);
        FeatureProperty opened = city.opened;
        assertThat(TypeFamily.of(opened.getType()).temporal()).isTrue();
    }

    /** Section "What a property says of its values". */
    @Test
    void whatAPropertySaysOfItsValues() {
        assertThat(city.kind.getAllowedValues()).containsExactly("primary", "secondary", "vocational");
        assertThat(city.pupils.getMinimum()).isEqualTo("0");
        assertThat(city.schoolLabel.isSortable()).isTrue();
        assertThat(city.schoolId.isQueryable()).isFalse();
        FeatureProperty pupils = city.pupils;
        assertThat(FeatureProperties.family(pupils)).isEqualTo(TypeFamily.INTEGER);
        assertThat(TypeFamily.INTEGER.numeric()).isTrue();
        assertThat(TypeFamily.isEnumeration(pupils.getType())).isFalse();
    }

    /** Section "The queryables". */
    @Test
    void theQueryables() {
        FeatureCollection schools = city.schools;
        FeatureProperty location = city.location;
        FeatureProperty pupils = city.pupils;
        GeometryFeature schoolPoint = city.schoolPoint;
        Map<String, Object> document = Queryables.document(schools,
                "https://example.org/collections/schools/queryables");
        assertThat(document).containsEntry("$schema", Queryables.SCHEMA);
        assertThat(Queryables.property(location)).isEqualTo(Map.of("title", "location", "format", "geometry-point"));
        assertThat(Queryables.format(schoolPoint)).isEqualTo("geometry-point");
        assertThat(Queryables.simple(location)).isFalse();
        assertThat(Queryables.simple(pupils)).isTrue();
        assertThat(TypeFamily.of(pupils.getType())).isEqualTo(TypeFamily.INTEGER);
        assertThat(TypeFamily.DATE.jsonType()).contains("string");
        assertThat(TypeFamily.DATE.format()).contains("date");
    }

    /** Section "The reference systems of a collection". */
    @Test
    void theReferenceSystemsOfACollection() {
        FeatureCollection districts = city.districts;
        assertThat(districts.getReferenceSystems()).containsExactly(city.etrs89);
        assertThat(FeatureCollections.crs(districts)).containsExactly(FeatureCollections.CRS84,
                "http://www.opengis.net/def/crs/EPSG/0/25832", "http://www.opengis.net/def/crs/EPSG/0/4258");
        assertThat(FeatureCollections.storageCrs(districts)).contains("http://www.opengis.net/def/crs/EPSG/0/25832");
        assertThat(FeatureCollections.storageSystem(districts)).containsSame(city.utm32);
        assertThat(FeatureCollections.storageCrsCoordinateEpoch(districts)).isEmpty();
        assertThat(FeatureCollections.hasHeight(districts)).isFalse();
        assertThat(FeatureCollections.CRS84H).isEqualTo("http://www.opengis.net/def/crs/OGC/0/CRS84h");
    }

    /** Section "Rules". */
    @Test
    void rules() {
        Model model = city.model;
        assertThat(FeatureModelValidator.check(model)).isEmpty();
        assertThat(FeatureModelValidator.validate(model)).isEmpty();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Every line of code of the README stands in the city or in this test, so that README and code cannot drift. */
    @Test
    void everySnippetOfTheReadmeStands() throws IOException {
        Path module = Path.of(System.getProperty("user.dir"));
        Path examples = module.resolve(
                "src/test/java/org/eclipse/daanse/cwm/model/daanse/resource/spatial/feature/examples");
        List<String> lines = new ArrayList<>(Files.readAllLines(examples.resolve("City.java")));
        lines.addAll(Files.readAllLines(examples.resolve("FeatureExamplesTest.java")));
        String source = lines.stream().map(String::trim).collect(Collectors.joining(" "));
        List<String> missing = new ArrayList<>();
        int snippets = 0;
        boolean block = false;
        boolean java = false;
        for (String line : Files.readAllLines(module.resolve("README.md"))) {
            if (line.startsWith("```")) {
                java = !block && line.equals("```java");
                snippets += java ? 1 : 0;
                block = !block;
                continue;
            }
            String code = line.trim();
            int comment = code.indexOf(" // ");
            code = comment >= 0 ? code.substring(0, comment).trim() : code;
            code = code.endsWith(";") ? code.substring(0, code.length() - 1) : code;
            if (java && !code.isEmpty() && !code.startsWith("//") && !source.contains(code)) {
                missing.add(line);
            }
        }
        assertThat(snippets).as("the README shows its functions in code").isGreaterThan(5);
        assertThat(missing).as("lines of the README that no test runs").isEmpty();
    }
}
