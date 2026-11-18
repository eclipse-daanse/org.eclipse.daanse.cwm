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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * The queryables of a collection (OGC API Features Part 3, {@code GET /collections/{id}/queryables}) as a structure: a
 * JSON Schema (draft 2020-12) of maps, lists, texts, numbers and truth values, in order, every element derived from
 * the model. A JSON text is written only at the edge, by the server.
 *
 * <ul>
 * <li>{@code $schema}: draft 2020-12; {@code $id}: the URL of the resource, which the server knows (left out where it
 * gives none); {@code type}: {@code object}; {@code title}: the collection's display name, else its name;
 * {@code description}: its Description of type documentation.</li>
 * <li>{@code properties}: the queryable properties in order, each with its {@code title}, {@code description},
 * {@code type} and {@code format} (by {@link TypeFamily}; a geometry has no type and the format
 * {@code geometry-<kind>} from its geometry feature), {@code enum} (the literals of an enumeration type, else the
 * allowed values, typed as the property), {@code minimum}, {@code maximum}, {@code maxLength} (the length of an SQL
 * type) and {@code pattern}.</li>
 * <li>{@code additionalProperties}: the collection's {@code openProperties}.</li>
 * </ul>
 * No {@code required}; a unit is no schema keyword and is left out.
 */
public final class Queryables {

    /** The JSON Schema dialect of the document. */
    public static final String SCHEMA = "https://json-schema.org/draft/2020-12/schema";

    private Queryables() {
    }

    /** The queryables of {@code collection}; {@code id} is the URL of the document, {@code null} to leave it out. */
    public static Map<String, Object> document(FeatureCollection collection, String id) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("$schema", SCHEMA);
        if (id != null) {
            doc.put("$id", id);
        }
        doc.put("type", "object");
        doc.put("title", FeatureCollections.title(collection));
        FeatureCollections.description(collection).ifPresent(d -> doc.put("description", d));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (FeatureProperty property : FeatureCollections.properties(collection)) {
            if (property.isQueryable()) {
                properties.put(property.getName(), property(property));
            }
        }
        doc.put("properties", Collections.unmodifiableMap(properties));
        doc.put("additionalProperties", collection.isOpenProperties());
        return Collections.unmodifiableMap(doc);
    }

    /** The schema of one property. */
    public static Map<String, Object> property(FeatureProperty property) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", FeatureProperties.title(property));
        FeatureCollections.description(property).ifPresent(d -> schema.put("description", d));
        TypeFamily family = FeatureProperties.family(property);
        if (FeatureProperties.isGeometry(property)) {
            FeatureCollections.geometryFeature(property).ifPresent(f -> schema.put("format", format(f)));
            return Collections.unmodifiableMap(schema);
        }
        family.jsonType().ifPresent(t -> schema.put("type", t));
        family.format().ifPresent(f -> schema.put("format", f));
        List<Object> values = values(property, family);
        if (!values.isEmpty()) {
            schema.put("enum", values);
        }
        number(property.getMinimum()).ifPresent(m -> schema.put("minimum", m));
        number(property.getMaximum()).ifPresent(m -> schema.put("maximum", m));
        if (family == TypeFamily.STRING) {
            maxLength(property.getType()).ifPresent(l -> schema.put("maxLength", l));
        }
        if (property.getPattern() != null) {
            schema.put("pattern", property.getPattern());
        }
        return Collections.unmodifiableMap(schema);
    }

    /**
     * The format of a geometry: {@code geometry-} and the kind, single or multi or either ({@code point},
     * {@code multipoint}, {@code point-or-multipoint}, the same of {@code linestring} and {@code polygon}); a
     * {@code MIXED} geometry is {@code geometry-any}.
     */
    public static String format(GeometryFeature feature) {
        String kind = switch (feature.getKind()) {
        case POINT -> "point";
        case LINE -> "linestring";
        case POLYGON -> "polygon";
        case MIXED -> "any";
        };
        if (feature.getKind() == GeometryKind.MIXED) {
            return "geometry-any";
        }
        return switch (feature.getMultiplicity()) {
        case SINGLE -> "geometry-" + kind;
        case MULTI -> "geometry-multi" + kind;
        case EITHER -> "geometry-" + kind + "-or-multi" + kind;
        };
    }

    /**
     * Whether the property is a simple queryable, which is also a query parameter of {@code /items} with the same
     * schema: queryable, no geometry, of a family with a JSON Schema type.
     */
    public static boolean simple(FeatureProperty property) {
        return property.isQueryable() && !FeatureProperties.isGeometry(property)
                && FeatureProperties.family(property).jsonType().isPresent();
    }

    /** The values of an enumeration type, else the allowed values, typed as the family reads them. */
    static List<Object> values(FeatureProperty property, TypeFamily family) {
        List<Object> values = new ArrayList<>();
        for (String literal : literals(property.getType())) {
            values.add(literal);
        }
        if (values.isEmpty()) {
            for (String value : property.getAllowedValues()) {
                values.add(typed(value, family));
            }
        }
        return Collections.unmodifiableList(values);
    }

    /** The names of the literals of an enumeration type, in order; none for another type. */
    static List<String> literals(Classifier type) {
        List<String> names = new ArrayList<>();
        if (!TypeFamily.isEnumeration(type)) {
            return names;
        }
        EStructuralFeature literal = type.eClass().getEStructuralFeature("literal");
        if (type.eGet(literal) instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof ModelElement e && e.getName() != null) {
                    names.add(e.getName());
                }
            }
        }
        return names;
    }

    private static Object typed(String value, TypeFamily family) {
        try {
            return switch (family) {
            case INTEGER -> Long.valueOf(value.trim());
            case NUMBER -> new BigDecimal(value.trim());
            default -> value;
            };
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static Optional<BigDecimal> number(String literal) {
        if (literal == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(literal.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The largest length of an SQL type of text, where it says one. */
    private static Optional<Long> maxLength(Classifier type) {
        if (type == null) {
            return Optional.empty();
        }
        EStructuralFeature length = type.eClass().getEStructuralFeature("characterMaximumLength");
        if (length != null && type.eIsSet(length) && type.eGet(length) instanceof Number n && n.longValue() > 0) {
            return Optional.of(n.longValue());
        }
        return Optional.empty();
    }
}
