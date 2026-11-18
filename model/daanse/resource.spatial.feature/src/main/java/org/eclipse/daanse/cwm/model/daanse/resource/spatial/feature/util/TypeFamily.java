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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * The family of the type of a property, and the JSON Schema type and format the queryables give it. A type is read by
 * its JDBC type code where it has one (an SQL type of a relational model), else by its name (an SQL type name or a CWM
 * data type name, without length or precision, in any case); the data type {@code Geometry} is a geometry, an
 * enumeration a text whose literals are its values.
 *
 * <table>
 * <caption>Type family, JSON Schema type and format</caption>
 * <tr><th>Family</th><th>Names</th><th>JDBC codes</th><th>type</th><th>format</th></tr>
 * <tr><td>{@link #STRING}</td><td>string, varchar, char, character, character varying, nvarchar, nchar, text, clob,
 * varchar2, nvarchar2, longvarchar, uuid, an enumeration</td><td>1, 12, -1, -15, -9, -16, 2005, 2011</td><td>string</td>
 * <td></td></tr>
 * <tr><td>{@link #INTEGER}</td><td>integer, int, bigint, smallint, tinyint, long, short, byte, int2, int4, int8,
 * hugeint</td><td>-6, 5, 4, -5</td><td>integer</td><td></td></tr>
 * <tr><td>{@link #NUMBER}</td><td>float, double, double precision, real, decimal, numeric, number, money, float4,
 * float8</td><td>6, 7, 8, 2, 3</td><td>number</td><td></td></tr>
 * <tr><td>{@link #BOOLEAN}</td><td>boolean, bool, bit</td><td>16, -7</td><td>boolean</td><td></td></tr>
 * <tr><td>{@link #DATE}</td><td>date</td><td>91</td><td>string</td><td>date</td></tr>
 * <tr><td>{@link #TIME}</td><td>time, time without time zone</td><td>92, 2013</td><td>string</td><td>time</td></tr>
 * <tr><td>{@link #TIMESTAMP}</td><td>timestamp, datetime, timestamp without time zone</td><td>93</td><td>string</td>
 * <td>date-time</td></tr>
 * <tr><td>{@link #TIMESTAMP_WITH_ZONE}</td><td>timestamptz, timestamp with time zone, datetimeoffset</td><td>2014</td>
 * <td>string</td><td>date-time</td></tr>
 * <tr><td>{@link #GEOMETRY}</td><td>the data type Geometry; geometry, geography</td><td></td><td>(none)</td>
 * <td>geometry-... from the geometry feature</td></tr>
 * <tr><td>{@link #OTHER}</td><td>anything else</td><td></td><td>(none)</td><td></td></tr>
 * </table>
 */
public enum TypeFamily {

    /** Text. */
    STRING("string", null),
    /** Whole numbers. */
    INTEGER("integer", null),
    /** Decimal and floating-point numbers. */
    NUMBER("number", null),
    /** True or false. */
    BOOLEAN("boolean", null),
    /** A day. */
    DATE("string", "date"),
    /** A time of day. */
    TIME("string", "time"),
    /** A day and a time without a zone: read in the collection's time zone. */
    TIMESTAMP("string", "date-time"),
    /** A day and a time with a zone or an offset. */
    TIMESTAMP_WITH_ZONE("string", "date-time"),
    /** A geometry: no JSON Schema type, a format from the geometry feature. */
    GEOMETRY(null, null),
    /** A type not known: no JSON Schema type. */
    OTHER(null, null);

    private static final Map<String, TypeFamily> BY_NAME = Map.ofEntries(
            Map.entry("string", STRING), Map.entry("varchar", STRING), Map.entry("char", STRING),
            Map.entry("character", STRING), Map.entry("character varying", STRING), Map.entry("nvarchar", STRING),
            Map.entry("nchar", STRING), Map.entry("text", STRING), Map.entry("clob", STRING),
            Map.entry("varchar2", STRING), Map.entry("nvarchar2", STRING), Map.entry("longvarchar", STRING),
            Map.entry("uuid", STRING),
            Map.entry("integer", INTEGER), Map.entry("int", INTEGER), Map.entry("bigint", INTEGER),
            Map.entry("smallint", INTEGER), Map.entry("tinyint", INTEGER), Map.entry("long", INTEGER),
            Map.entry("short", INTEGER), Map.entry("byte", INTEGER), Map.entry("int2", INTEGER),
            Map.entry("int4", INTEGER), Map.entry("int8", INTEGER), Map.entry("hugeint", INTEGER),
            Map.entry("float", NUMBER), Map.entry("double", NUMBER), Map.entry("double precision", NUMBER),
            Map.entry("real", NUMBER), Map.entry("decimal", NUMBER), Map.entry("numeric", NUMBER),
            Map.entry("number", NUMBER), Map.entry("money", NUMBER), Map.entry("float4", NUMBER),
            Map.entry("float8", NUMBER),
            Map.entry("boolean", BOOLEAN), Map.entry("bool", BOOLEAN), Map.entry("bit", BOOLEAN),
            Map.entry("date", DATE),
            Map.entry("time", TIME), Map.entry("time without time zone", TIME),
            Map.entry("timestamp", TIMESTAMP), Map.entry("datetime", TIMESTAMP),
            Map.entry("timestamp without time zone", TIMESTAMP),
            Map.entry("timestamptz", TIMESTAMP_WITH_ZONE), Map.entry("timestamp with time zone", TIMESTAMP_WITH_ZONE),
            Map.entry("datetimeoffset", TIMESTAMP_WITH_ZONE),
            Map.entry("geometry", GEOMETRY), Map.entry("geography", GEOMETRY));

    private static final Map<Long, TypeFamily> BY_JDBC = Map.ofEntries(
            Map.entry(1L, STRING), Map.entry(12L, STRING), Map.entry(-1L, STRING), Map.entry(-15L, STRING),
            Map.entry(-9L, STRING), Map.entry(-16L, STRING), Map.entry(2005L, STRING), Map.entry(2011L, STRING),
            Map.entry(-6L, INTEGER), Map.entry(5L, INTEGER), Map.entry(4L, INTEGER), Map.entry(-5L, INTEGER),
            Map.entry(6L, NUMBER), Map.entry(7L, NUMBER), Map.entry(8L, NUMBER), Map.entry(2L, NUMBER),
            Map.entry(3L, NUMBER),
            Map.entry(16L, BOOLEAN), Map.entry(-7L, BOOLEAN),
            Map.entry(91L, DATE), Map.entry(92L, TIME), Map.entry(2013L, TIME), Map.entry(93L, TIMESTAMP),
            Map.entry(2014L, TIMESTAMP_WITH_ZONE));

    private final String jsonType;
    private final String format;

    TypeFamily(String jsonType, String format) {
        this.jsonType = jsonType;
        this.format = format;
    }

    /** The JSON Schema {@code type}; none for a geometry and a type not known. */
    public Optional<String> jsonType() {
        return Optional.ofNullable(jsonType);
    }

    /** The JSON Schema {@code format} of a temporal type; none otherwise (a geometry's comes from its feature). */
    public Optional<String> format() {
        return Optional.ofNullable(format);
    }

    /** Whether a value of the family is a day or a day and a time: a type the time of a feature can have. */
    public boolean temporal() {
        return this == DATE || this == TIMESTAMP || this == TIMESTAMP_WITH_ZONE;
    }

    /** Whether the family is a number. */
    public boolean numeric() {
        return this == INTEGER || this == NUMBER;
    }

    /** The family of {@code type}; {@link #OTHER} for none. */
    public static TypeFamily of(Classifier type) {
        if (type == null || type.eIsProxy()) {
            return OTHER;
        }
        if (SpatialElements.isGeometryType(type)) {
            return GEOMETRY;
        }
        if (isEnumeration(type)) {
            return STRING;
        }
        EStructuralFeature typeNumber = type.eClass().getEStructuralFeature("typeNumber");
        if (typeNumber != null && type.eIsSet(typeNumber) && type.eGet(typeNumber) instanceof Number n
                && BY_JDBC.containsKey(n.longValue())) {
            return BY_JDBC.get(n.longValue());
        }
        return type.getName() == null ? OTHER : BY_NAME.getOrDefault(plain(type.getName()), OTHER);
    }

    /** Whether {@code type} is an enumeration of CWM data types (its literals are its values). */
    public static boolean isEnumeration(Classifier type) {
        return type != null && type.eClass().getEStructuralFeature("literal") != null
                && "Enumeration".equals(type.eClass().getName());
    }

    /**
     * A type name in lower case without its length or precision ({@code VARCHAR(40)} is {@code varchar}), an Ecore
     * type name in its plain form ({@code EString} is {@code string}).
     */
    static String plain(String name) {
        String n = name.trim();
        int paren = n.indexOf('(');
        if (paren > 0) {
            n = n.substring(0, paren).trim();
        }
        if (n.length() > 1 && n.charAt(0) == 'E' && Character.isUpperCase(n.charAt(1))) {
            n = n.substring(1);
            if (n.endsWith("Object")) {
                n = n.substring(0, n.length() - "Object".length());
            }
        }
        return n.toLowerCase(Locale.ROOT);
    }
}
