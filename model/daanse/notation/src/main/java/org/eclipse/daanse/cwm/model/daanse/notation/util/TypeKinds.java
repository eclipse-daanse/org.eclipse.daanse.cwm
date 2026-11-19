/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * What a type holds, as far as a notation is concerned: by its name (SQL, CWM, Ecore), else by its JDBC type
 * code (an SQL type of the relational model), else by the Java class the tag {@code daanse.ecore.instanceClassName}
 * names. A type known by none of them is {@link Kind#UNKNOWN}.
 */
final class TypeKinds {

    /** The kinds of values a type holds. */
    enum Kind {
        TEXT, NUMBER, BOOLEAN, TEMPORAL, BINARY, UNKNOWN
    }

    /**
     * What a type holds; {@code storage} where it is an SQL type of a column (what is stored), else the type of a
     * value; {@code instant} for a time with a zone (an instant), {@code localTime} for a date and time or a time
     * known to be local.
     */
    record Info(Kind kind, boolean storage, boolean instant, boolean localTime, String name) {
    }

    private static final Set<String> TEXT = Set.of("char", "character", "varchar", "character varying", "varchar2",
            "nchar", "nvarchar", "nvarchar2", "national character varying", "text", "tinytext", "mediumtext",
            "longtext", "string", "clob", "nclob", "longvarchar", "longnvarchar", "name", "bpchar", "java.lang.string");
    private static final Set<String> NUMBER = Set.of("integer", "int", "long", "short", "byte", "float", "double",
            "real", "decimal", "numeric", "number", "bigint", "smallint", "tinyint", "money", "biginteger",
            "bigdecimal", "unlimitedinteger", "double precision", "hugeint", "int2", "int4", "int8", "float4",
            "float8", "mediumint");
    private static final Set<String> BOOLEAN = Set.of("boolean", "bool", "bit", "logical");
    private static final Set<String> INSTANT = Set.of("timestamp with time zone", "timestamptz", "timestamp_tz",
            "timestamp with local time zone", "datetimeoffset", "time with time zone", "timetz", "instant",
            "offsetdatetime", "zoneddatetime", "offsettime");
    private static final Set<String> LOCAL_TIME = Set.of("timestamp", "timestamp without time zone", "datetime",
            "datetime2", "smalldatetime", "time", "time without time zone", "localdatetime", "localtime");
    private static final Set<String> DATE = Set.of("date", "localdate", "year", "yearmonth");
    private static final Set<String> BINARY = Set.of("binary", "varbinary", "longvarbinary", "blob", "tinyblob",
            "mediumblob", "longblob", "bytea", "bytes", "raw", "image", "bytearray");

    /** JDBC type codes. */
    private static final Map<Long, Kind> JDBC = Map.ofEntries(Map.entry(1L, Kind.TEXT), Map.entry(12L, Kind.TEXT),
            Map.entry(-1L, Kind.TEXT), Map.entry(-15L, Kind.TEXT), Map.entry(-9L, Kind.TEXT),
            Map.entry(-16L, Kind.TEXT), Map.entry(2005L, Kind.TEXT), Map.entry(2011L, Kind.TEXT),
            Map.entry(-6L, Kind.NUMBER), Map.entry(5L, Kind.NUMBER), Map.entry(4L, Kind.NUMBER),
            Map.entry(-5L, Kind.NUMBER), Map.entry(6L, Kind.NUMBER), Map.entry(7L, Kind.NUMBER),
            Map.entry(8L, Kind.NUMBER), Map.entry(2L, Kind.NUMBER), Map.entry(3L, Kind.NUMBER),
            Map.entry(16L, Kind.BOOLEAN), Map.entry(-7L, Kind.BOOLEAN), Map.entry(91L, Kind.TEMPORAL),
            Map.entry(92L, Kind.TEMPORAL), Map.entry(93L, Kind.TEMPORAL), Map.entry(2013L, Kind.TEMPORAL),
            Map.entry(2014L, Kind.TEMPORAL), Map.entry(-2L, Kind.BINARY), Map.entry(-3L, Kind.BINARY),
            Map.entry(-4L, Kind.BINARY), Map.entry(2004L, Kind.BINARY));
    /** JDBC TIME_WITH_TIMEZONE and TIMESTAMP_WITH_TIMEZONE. */
    private static final Set<Long> JDBC_INSTANT = Set.of(2013L, 2014L);
    /** JDBC TIME and TIMESTAMP. */
    private static final Set<Long> JDBC_LOCAL_TIME = Set.of(92L, 93L);

    private TypeKinds() {
    }

    /** What {@code type} holds. */
    static Info of(Classifier type) {
        EStructuralFeature typeNumber = type.eClass().getEStructuralFeature("typeNumber");
        boolean storage = typeNumber != null;
        String name = type.getName() == null ? "" : plain(type.getName());
        Optional<Info> byName = byName(name, storage, type.getName());
        if (byName.isPresent()) {
            return byName.get();
        }
        if (storage && type.eIsSet(typeNumber) && type.eGet(typeNumber) instanceof Number n) {
            long code = n.longValue();
            Kind kind = JDBC.get(code);
            if (kind != null) {
                return new Info(kind, true, JDBC_INSTANT.contains(code), JDBC_LOCAL_TIME.contains(code),
                        type.getName());
            }
        }
        Optional<String> javaClass = TaggedValues.value(type, "daanse.ecore.instanceClassName");
        if (javaClass.isPresent()) {
            String c = javaClass.get();
            String simple = plain(c.substring(c.lastIndexOf('.') + 1));
            if (c.equals("java.lang.String")) {
                return new Info(Kind.TEXT, storage, false, false, type.getName());
            }
            if (c.startsWith("java.time.") || c.equals("java.util.Date") || c.startsWith("java.sql.")) {
                boolean instant = INSTANT.contains(simple) || c.equals("java.util.Date");
                boolean local = LOCAL_TIME.contains(simple) || c.equals("java.sql.Timestamp")
                        || c.equals("java.sql.Time");
                return new Info(Kind.TEMPORAL, storage, instant, local, type.getName());
            }
            Optional<Info> bySimple = byName(simple, storage, type.getName());
            if (bySimple.isPresent()) {
                return bySimple.get();
            }
        }
        return new Info(Kind.UNKNOWN, storage, false, false, type.getName());
    }

    private static Optional<Info> byName(String name, boolean storage, String written) {
        Kind kind = null;
        if (TEXT.contains(name)) {
            kind = Kind.TEXT;
        } else if (NUMBER.contains(name)) {
            kind = Kind.NUMBER;
        } else if (BOOLEAN.contains(name)) {
            kind = Kind.BOOLEAN;
        } else if (INSTANT.contains(name) || LOCAL_TIME.contains(name) || DATE.contains(name)) {
            kind = Kind.TEMPORAL;
        } else if (BINARY.contains(name)) {
            kind = Kind.BINARY;
        }
        return kind == null ? Optional.empty()
                : Optional.of(new Info(kind, storage, INSTANT.contains(name), LOCAL_TIME.contains(name), written));
    }

    /**
     * A type name in lower case without its length or precision ({@code VARCHAR(40)} is {@code varchar}), an Ecore
     * type name in its plain form ({@code EString} is {@code string}).
     */
    static String plain(String name) {
        String n = name.trim();
        int paren = n.indexOf('(');
        if (paren > 0) {
            String after = n.substring(n.indexOf(')', paren) + 1).trim();
            n = (n.substring(0, paren).trim() + (after.isEmpty() ? "" : " " + after)).trim();
        }
        if (n.length() > 1 && n.charAt(0) == 'E' && Character.isUpperCase(n.charAt(1))) {
            n = n.substring(1);
            if (n.endsWith("Object")) {
                n = n.substring(0, n.length() - "Object".length());
            }
        }
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
