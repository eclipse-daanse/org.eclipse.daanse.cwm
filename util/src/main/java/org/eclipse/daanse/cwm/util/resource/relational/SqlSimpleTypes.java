/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.util.resource.relational;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Factories and inspection helpers for {@link SQLSimpleType}. SQL-99 spec types
 * live on the nested {@link Sql99} class. Every factory method ends in
 * {@code Type} and emits a fresh instance.
 */
public final class SqlSimpleTypes {

    private SqlSimpleTypes() {
    }

    /** {@code VARCHAR(length)}. */
    public static SQLSimpleType varcharType(int length) {
        SQLSimpleType t = Sql99.characterVaryingType();
        t.setCharacterMaximumLength(length);
        return t;
    }

    /** {@code NVARCHAR(length)}. */
    public static SQLSimpleType nvarcharType(int length) {
        SQLSimpleType t = Sql99.nationalCharacterVaryingType();
        t.setCharacterMaximumLength(length);
        return t;
    }

    /** {@code CHARACTER(length)}. */
    public static SQLSimpleType characterType(int length) {
        SQLSimpleType t = Sql99.characterType();
        t.setCharacterMaximumLength(length);
        return t;
    }

    /** {@code NCHAR(length)}. */
    public static SQLSimpleType nationalCharacterType(int length) {
        SQLSimpleType t = Sql99.nationalCharacterType();
        t.setCharacterMaximumLength(length);
        return t;
    }

    /** {@code DECIMAL(precision, scale)}. */
    public static SQLSimpleType decimalType(int precision, int scale) {
        SQLSimpleType t = Sql99.decimalType();
        t.setNumericPrecision(precision);
        t.setNumericScale(scale);
        return t;
    }

    /** {@code NUMERIC(precision, scale)}. */
    public static SQLSimpleType numericType(int precision, int scale) {
        SQLSimpleType t = Sql99.numericType();
        t.setNumericPrecision(precision);
        t.setNumericScale(scale);
        return t;
    }

    /** {@code FLOAT(precision)} — precision in bits. */
    public static SQLSimpleType floatType(int precision) {
        SQLSimpleType t = Sql99.floatType();
        t.setNumericPrecision(precision);
        return t;
    }

    private static final SQLSimpleType BIGINT_T = Templates.integral("BIGINT", Types.BIGINT);
    private static final SQLSimpleType TINYINT_T = Templates.integral("TINYINT", Types.TINYINT);

    public static SQLSimpleType bigintType() {
        return EcoreUtil.copy(BIGINT_T);
    }

    public static SQLSimpleType tinyintType() {
        return EcoreUtil.copy(TINYINT_T);
    }

    /** Resolve a type by SQL-99 name (case-insensitive, whitespace collapsed). */
    public static Optional<SQLSimpleType> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String key = name.trim().toUpperCase().replaceAll("\\s+", " ");
        Supplier<SQLSimpleType> s = BY_NAME.get(key);
        return s == null ? Optional.empty() : Optional.of(s.get());
    }

    private static final Map<String, Supplier<SQLSimpleType>> BY_NAME = byNameMap();

    private static Map<String, Supplier<SQLSimpleType>> byNameMap() {
        Map<String, Supplier<SQLSimpleType>> m = new LinkedHashMap<>();
        m.put("BIT", Sql99::bitType);
        m.put("BIT VARYING", Sql99::bitVaryingType);
        m.put("BINARY LARGE OBJECT", Sql99::binaryLargeObjectType);
        m.put("CHARACTER", Sql99::characterType);
        m.put("CHARACTER VARYING", Sql99::characterVaryingType);
        m.put("CHARACTER LARGE OBJECT", Sql99::characterLargeObjectType);
        m.put("NATIONAL CHARACTER", Sql99::nationalCharacterType);
        m.put("NATIONAL CHARACTER VARYING", Sql99::nationalCharacterVaryingType);
        m.put("NATIONAL CHARACTER LARGE OBJECT", Sql99::nationalCharacterLargeObjectType);
        m.put("NUMERIC", Sql99::numericType);
        m.put("DECIMAL", Sql99::decimalType);
        m.put("INTEGER", Sql99::integerType);
        m.put("SMALLINT", Sql99::smallintType);
        m.put("FLOAT", Sql99::floatType);
        m.put("REAL", Sql99::realType);
        m.put("DOUBLE PRECISION", Sql99::doublePrecisionType);
        m.put("BOOLEAN", Sql99::booleanType);
        m.put("DATE", Sql99::dateType);
        m.put("TIME", Sql99::timeType);
        m.put("TIME WITH TIMEZONE", Sql99::timeWithTimezoneType);
        m.put("TIMESTAMP", Sql99::timestampType);
        m.put("TIMESTAMP WITH TIMEZONE", Sql99::timestampWithTimezoneType);
        m.put("INTERVAL", Sql99::intervalType);
        m.put("BIGINT", SqlSimpleTypes::bigintType);
        m.put("TINYINT", SqlSimpleTypes::tinyintType);
        m.put("BLOB", Sql99::blobType);
        m.put("CLOB", Sql99::clobType);
        m.put("NCLOB", Sql99::nclobType);
        m.put("CHAR", Sql99::characterType);
        m.put("VARCHAR", Sql99::varcharType);
        m.put("NCHAR", Sql99::nationalCharacterType);
        m.put("NVARCHAR", Sql99::nationalCharacterVaryingType);
        m.put("INT", Sql99::integerType);
        return m;
    }

    /** JDBC type code, or {@link Types#OTHER} when null. */
    public static int jdbcType(SQLSimpleType type) {
        return type == null ? Types.OTHER : (int) type.getTypeNumber();
    }

    public static boolean isNumeric(SQLSimpleType type) {
        switch (jdbcType(type)) {
        case Types.TINYINT:
        case Types.SMALLINT:
        case Types.INTEGER:
        case Types.BIGINT:
        case Types.REAL:
        case Types.FLOAT:
        case Types.DOUBLE:
        case Types.DECIMAL:
        case Types.NUMERIC:
            return true;
        default:
            return false;
        }
    }

    public static boolean isText(SQLSimpleType type) {
        switch (jdbcType(type)) {
        case Types.CHAR:
        case Types.VARCHAR:
        case Types.LONGVARCHAR:
        case Types.NCHAR:
        case Types.NVARCHAR:
        case Types.LONGNVARCHAR:
        case Types.CLOB:
        case Types.NCLOB:
            return true;
        default:
            return false;
        }
    }

    public static boolean isTemporal(SQLSimpleType type) {
        switch (jdbcType(type)) {
        case Types.DATE:
        case Types.TIME:
        case Types.TIMESTAMP:
        case Types.TIME_WITH_TIMEZONE:
        case Types.TIMESTAMP_WITH_TIMEZONE:
            return true;
        default:
            return false;
        }
    }

    /** Short form like {@code "VARCHAR(255)"} or {@code "DECIMAL(10,2)"}. */
    public static String describe(SQLSimpleType type) {
        if (type == null) {
            return null;
        }
        String name = type.getName();
        if (isNumeric(type) && type.getNumericPrecision() > 0) {
            if (type.getNumericScale() > 0) {
                return name + "(" + type.getNumericPrecision() + "," + type.getNumericScale() + ")";
            }
            return name + "(" + type.getNumericPrecision() + ")";
        }
        if (isText(type) && type.getCharacterMaximumLength() > 0) {
            return name + "(" + type.getCharacterMaximumLength() + ")";
        }
        return name;
    }

    /** Factories for the SQL-99 §19.5 data types. */
    public static final class Sql99 {

        private static final SQLSimpleType BIT = Templates.bare("BIT", Types.BIT);
        private static final SQLSimpleType BIT_VARYING = Templates.bare("BIT VARYING", Types.VARBINARY);
        private static final SQLSimpleType BINARY_LARGE_OBJECT = Templates.bare("BINARY LARGE OBJECT", Types.BLOB);
        private static final SQLSimpleType CHARACTER = Templates.bare("CHARACTER", Types.CHAR);
        private static final SQLSimpleType CHARACTER_VARYING = Templates.bare("CHARACTER VARYING", Types.VARCHAR);
        private static final SQLSimpleType CHARACTER_LARGE_OBJECT = Templates.bare("CHARACTER LARGE OBJECT",
                Types.CLOB);
        private static final SQLSimpleType NATIONAL_CHARACTER = Templates.bare("NATIONAL CHARACTER", Types.NCHAR);
        private static final SQLSimpleType NATIONAL_CHARACTER_VARYING = Templates.bare("NATIONAL CHARACTER VARYING",
                Types.NVARCHAR);
        private static final SQLSimpleType NATIONAL_CHARACTER_LARGE_OBJECT = Templates
                .bare("NATIONAL CHARACTER LARGE OBJECT", Types.NCLOB);

        // Numeric
        private static final SQLSimpleType NUMERIC = Templates.numericRadix("NUMERIC", Types.NUMERIC, 10);
        private static final SQLSimpleType DECIMAL = Templates.numericRadix("DECIMAL", Types.DECIMAL, 10);
        private static final SQLSimpleType INTEGER = Templates.integral("INTEGER", Types.INTEGER);
        private static final SQLSimpleType SMALLINT = Templates.integral("SMALLINT", Types.SMALLINT);
        private static final SQLSimpleType FLOAT = Templates.numericRadix("FLOAT", Types.FLOAT, 2);
        private static final SQLSimpleType REAL = Templates.numericRadix("REAL", Types.REAL, 2);
        private static final SQLSimpleType DOUBLE_PRECISION = Templates.numericRadix("DOUBLE PRECISION", Types.DOUBLE,
                2);
        // Boolean
        private static final SQLSimpleType BOOLEAN = Templates.bare("BOOLEAN", Types.BOOLEAN);

        // Date and time
        private static final SQLSimpleType DATE = Templates.bare("DATE", Types.DATE);
        private static final SQLSimpleType TIME = Templates.bare("TIME", Types.TIME);
        private static final SQLSimpleType TIME_WITH_TIMEZONE = Templates.bare("TIME WITH TIMEZONE",
                Types.TIME_WITH_TIMEZONE);
        private static final SQLSimpleType TIMESTAMP = Templates.bare("TIMESTAMP", Types.TIMESTAMP);
        private static final SQLSimpleType TIMESTAMP_WITH_TIMEZONE = Templates.bare("TIMESTAMP WITH TIMEZONE",
                Types.TIMESTAMP_WITH_TIMEZONE);
        private static final SQLSimpleType INTERVAL = Templates.bare("INTERVAL", Types.OTHER);

        private Sql99() {
        }

        public static SQLSimpleType bitType() {
            return EcoreUtil.copy(BIT);
        }

        public static SQLSimpleType bitVaryingType() {
            return EcoreUtil.copy(BIT_VARYING);
        }

        public static SQLSimpleType binaryLargeObjectType() {
            return EcoreUtil.copy(BINARY_LARGE_OBJECT);
        }

        /** Alias for {@link #binaryLargeObjectType()}. */
        public static SQLSimpleType blobType() {
            return binaryLargeObjectType();
        }

        public static SQLSimpleType characterType() {
            return EcoreUtil.copy(CHARACTER);
        }

        public static SQLSimpleType characterVaryingType() {
            return EcoreUtil.copy(CHARACTER_VARYING);
        }

        /**
         * Alias for {@link #characterVaryingType()}. Prefer
         * {@link SqlSimpleTypes#varcharType(int)} for a sized type.
         */
        public static SQLSimpleType varcharType() {
            return characterVaryingType();
        }

        public static SQLSimpleType characterLargeObjectType() {
            return EcoreUtil.copy(CHARACTER_LARGE_OBJECT);
        }

        /** Alias for {@link #characterLargeObjectType()}. */
        public static SQLSimpleType clobType() {
            return characterLargeObjectType();
        }

        public static SQLSimpleType nationalCharacterType() {
            return EcoreUtil.copy(NATIONAL_CHARACTER);
        }

        public static SQLSimpleType nationalCharacterVaryingType() {
            return EcoreUtil.copy(NATIONAL_CHARACTER_VARYING);
        }

        public static SQLSimpleType nationalCharacterLargeObjectType() {
            return EcoreUtil.copy(NATIONAL_CHARACTER_LARGE_OBJECT);
        }

        /** Alias for {@link #nationalCharacterLargeObjectType()}. */
        public static SQLSimpleType nclobType() {
            return nationalCharacterLargeObjectType();
        }

        public static SQLSimpleType numericType() {
            return EcoreUtil.copy(NUMERIC);
        }

        public static SQLSimpleType decimalType() {
            return EcoreUtil.copy(DECIMAL);
        }

        public static SQLSimpleType integerType() {
            return EcoreUtil.copy(INTEGER);
        }

        public static SQLSimpleType smallintType() {
            return EcoreUtil.copy(SMALLINT);
        }

        public static SQLSimpleType floatType() {
            return EcoreUtil.copy(FLOAT);
        }

        public static SQLSimpleType realType() {
            return EcoreUtil.copy(REAL);
        }

        public static SQLSimpleType doublePrecisionType() {
            return EcoreUtil.copy(DOUBLE_PRECISION);
        }

        public static SQLSimpleType booleanType() {
            return EcoreUtil.copy(BOOLEAN);
        }

        public static SQLSimpleType dateType() {
            return EcoreUtil.copy(DATE);
        }

        public static SQLSimpleType timeType() {
            return EcoreUtil.copy(TIME);
        }

        public static SQLSimpleType timeWithTimezoneType() {
            return EcoreUtil.copy(TIME_WITH_TIMEZONE);
        }

        public static SQLSimpleType timestampType() {
            return EcoreUtil.copy(TIMESTAMP);
        }

        public static SQLSimpleType timestampWithTimezoneType() {
            return EcoreUtil.copy(TIMESTAMP_WITH_TIMEZONE);
        }

        public static SQLSimpleType intervalType() {
            return EcoreUtil.copy(INTERVAL);
        }
    }

    private static final class Templates {

        private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

        private Templates() {
        }

        static SQLSimpleType bare(String name, int typeNumber) {
            SQLSimpleType t = RF.createSQLSimpleType();
            t.setName(name);
            t.setTypeNumber(typeNumber);
            return t;
        }

        static SQLSimpleType integral(String name, int typeNumber) {
            // SQL-99: numericScale = 0 for integral types.
            SQLSimpleType t = bare(name, typeNumber);
            t.setNumericScale(0);
            return t;
        }

        static SQLSimpleType numericRadix(String name, int typeNumber, long radix) {
            SQLSimpleType t = bare(name, typeNumber);
            t.setNumericPrecisionRadix(radix);
            return t;
        }
    }
}
