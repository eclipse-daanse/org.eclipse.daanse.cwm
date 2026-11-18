/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.examples.City;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryMultiplicity;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialFactory;
import org.junit.jupiter.api.Test;

/** The queryables of the city's collections, compared as structures, never as text. */
class QueryablesTest {

    private static final String ID = "https://example.org/collections/schools/queryables";

    private final City city = new City();

    /** The schools: the key is not queryable, the kind lists its values, the location is a point. */
    @Test
    void theQueryablesOfTheSchools() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("label", Map.of("title", "label", "type", "string"));
        properties.put("kind", Map.of("title", "kind", "type", "string", "enum",
                List.of("primary", "secondary", "vocational")));
        properties.put("pupils", Map.of("title", "pupils", "type", "integer", "minimum", new BigDecimal("0")));
        properties.put("opened", Map.of("title", "opened", "type", "string", "format", "date"));
        properties.put("district_id", Map.of("title", "district_id", "description",
                "The district the school lies in.", "type", "string"));
        properties.put("location", Map.of("title", "location", "format", "geometry-point"));
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        expected.put("$id", ID);
        expected.put("type", "object");
        expected.put("title", "Schools");
        expected.put("properties", properties);
        expected.put("additionalProperties", false);

        Map<String, Object> document = Queryables.document(city.schools, ID);
        assertThat(document).as("the document").isEqualTo(expected);
        assertThat(document.keySet()).as("in the order of the schema").containsExactlyElementsOf(expected.keySet());
        assertThat(properties(document).keySet()).as("in the order of the properties")
                .containsExactlyElementsOf(properties.keySet());
    }

    /** The districts: a title and a description of their own, a decimal area, two dates, a multi-polygon. */
    @Test
    void theQueryablesOfTheDistricts() {
        Map<String, Object> document = Queryables.document(city.districts, null);
        assertThat(document).doesNotContainKey("$id").containsEntry("title", "City districts")
                .containsEntry("description", "The districts of the city, each valid from a day until a day.");
        Map<String, Object> properties = properties(document);
        assertThat(properties.keySet()).containsExactly("label", "residents", "area_km2", "valid_from", "valid_to",
                "outline");
        assertThat(properties.get("area_km2")).isEqualTo(Map.of("title", "area_km2", "type", "number"));
        assertThat(properties.get("valid_to")).isEqualTo(Map.of("title", "valid_to", "type", "string", "format",
                "date"));
        assertThat(properties.get("outline")).isEqualTo(Map.of("title", "outline", "format",
                "geometry-multipolygon"));
    }

    /** A display name is the title; an open collection allows other properties; a text type with a length. */
    @Test
    void titleOpennessAndLength() {
        city.schoolLabel.setDisplayName("Name of the school");
        city.schools.setOpenProperties(true);
        SQLSimpleType varchar = RelationalFactory.eINSTANCE.createSQLSimpleType();
        varchar.setName("VARCHAR");
        varchar.setTypeNumber(12);
        varchar.setCharacterMaximumLength(80L);
        city.types.getOwnedElement().add(varchar);
        city.schoolLabel.setType(varchar);
        Map<String, Object> document = Queryables.document(city.schools, null);
        assertThat(document).containsEntry("additionalProperties", true);
        assertThat(properties(document).get("label")).isEqualTo(Map.of("title",
                "Name of the school", "type", "string", "maxLength", 80L));
    }

    /** Every kind and multiplicity of a geometry has its format; a mixed column any. */
    @Test
    void theFormatOfEachGeometry() {
        Map<String, String> formats = new LinkedHashMap<>();
        for (GeometryKind kind : GeometryKind.values()) {
            for (GeometryMultiplicity multiplicity : GeometryMultiplicity.values()) {
                GeometryFeature f = SpatialFactory.eINSTANCE.createGeometryFeature();
                f.setKind(kind);
                f.setMultiplicity(multiplicity);
                formats.put(kind + " " + multiplicity, Queryables.format(f));
            }
        }
        assertThat(formats).isEqualTo(Map.ofEntries(
                Map.entry("POINT EITHER", "geometry-point-or-multipoint"), Map.entry("POINT SINGLE", "geometry-point"),
                Map.entry("POINT MULTI", "geometry-multipoint"),
                Map.entry("LINE EITHER", "geometry-linestring-or-multilinestring"),
                Map.entry("LINE SINGLE", "geometry-linestring"), Map.entry("LINE MULTI", "geometry-multilinestring"),
                Map.entry("POLYGON EITHER", "geometry-polygon-or-multipolygon"),
                Map.entry("POLYGON SINGLE", "geometry-polygon"), Map.entry("POLYGON MULTI", "geometry-multipolygon"),
                Map.entry("MIXED EITHER", "geometry-any"), Map.entry("MIXED SINGLE", "geometry-any"),
                Map.entry("MIXED MULTI", "geometry-any")));
    }

    /** A simple queryable is a query parameter too: not the geometry, not a property that is not queryable. */
    @Test
    void theSimpleQueryablesAreTheParametersOfItems() {
        List<String> simple = FeatureCollections.properties(city.schools).stream().filter(Queryables::simple)
                .map(FeatureProperty::getName).toList();
        assertThat(simple).containsExactly("label", "kind", "pupils", "opened", "district_id");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> document) {
        return (Map<String, Object>) document.get("properties");
    }
}
