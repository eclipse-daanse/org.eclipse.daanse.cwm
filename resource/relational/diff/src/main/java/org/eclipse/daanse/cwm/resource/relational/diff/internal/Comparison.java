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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.NamedColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.Tolerance;

/**
 * What of a value the differs compare, under the {@link Tolerance}s of one comparison: every
 * tolerance is applied here and nowhere else. Each method gives a key; two values are the same where
 * their keys are equal.
 */
final class Comparison {

    private final DiffSettings settings;

    Comparison(DiffSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    DiffSettings settings() {
        return settings;
    }

    /** A name as it is compared: as written, or without case under {@link Tolerance#CASE_INSENSITIVE_NAMES}. */
    String name(String name) {
        if (name == null) {
            return null;
        }
        return settings.tolerates(Tolerance.CASE_INSENSITIVE_NAMES) ? name.toLowerCase(Locale.ROOT) : name;
    }

    boolean sameName(String a, String b) {
        return Objects.equals(name(a), name(b));
    }

    /** The key under which {@link Tolerance#INTEGER_AS_NUMERIC} compares integer and exact numeric types. */
    private static final String EXACT_NUMERIC = "exact numeric";

    /**
     * The type of a column or parameter as it is compared: its JDBC type code, {@code DECIMAL} and
     * {@code NUMERIC} as one, under {@link Tolerance#TYPE_BY_JDBC_CODE} where the type has a code;
     * otherwise its name, without case and with single spaces. Under
     * {@link Tolerance#INTEGER_AS_NUMERIC} the integer types and {@code DECIMAL} and {@code NUMERIC}
     * are one.
     */
    Object type(Classifier type) {
        JDBCType code = jdbcType(type);
        if (settings.tolerates(Tolerance.INTEGER_AS_NUMERIC) && (isInteger(code) || isExactNumeric(code))) {
            return EXACT_NUMERIC;
        }
        if (settings.tolerates(Tolerance.TYPE_BY_JDBC_CODE) && code != JDBCType.OTHER) {
            return code == JDBCType.NUMERIC ? JDBCType.DECIMAL : code;
        }
        return type == null ? null : text(type.getName());
    }

    /**
     * The size of a column as it is compared, where its type takes one in DDL: the length of character
     * and binary types (not under {@link Tolerance#IGNORE_CHARACTER_LENGTH}), precision and scale of
     * {@code DECIMAL} and {@code NUMERIC}. A type without size, such as {@code INTEGER} or {@code DATE},
     * has none to differ in: DDL does not write one, a database may still report its own. Under
     * {@link Tolerance#INTEGER_AS_NUMERIC} an integer type has the size of a {@code NUMERIC} without
     * precision and scale.
     */
    Object size(Column column) {
        JDBCType code = jdbcType(column.getType());
        if (isInteger(code)) {
            return settings.tolerates(Tolerance.INTEGER_AS_NUMERIC) ? List.of(0L, 0L) : null;
        }
        if (isExactNumeric(code)) {
            return List.of(precision(column), scale(column));
        }
        return switch (code) {
        case CHAR, VARCHAR, NCHAR, NVARCHAR, LONGVARCHAR, LONGNVARCHAR, BINARY, VARBINARY, LONGVARBINARY ->
            settings.tolerates(Tolerance.IGNORE_CHARACTER_LENGTH) ? null : length(column);
        default -> null;
        };
    }

    private static boolean isInteger(JDBCType code) {
        return code == JDBCType.TINYINT || code == JDBCType.SMALLINT || code == JDBCType.INTEGER
                || code == JDBCType.BIGINT;
    }

    private static boolean isExactNumeric(JDBCType code) {
        return code == JDBCType.DECIMAL || code == JDBCType.NUMERIC;
    }

    /** The length of a character or binary column: on the column, or else on its type. */
    static long length(Column column) {
        long typed = column.getType() instanceof SQLSimpleType s ? s.getCharacterMaximumLength() : 0;
        return Math.max(column.getLength(), typed);
    }

    static long precision(Column column) {
        long typed = column.getType() instanceof SQLSimpleType s ? s.getNumericPrecision() : 0;
        return Math.max(column.getPrecision(), typed);
    }

    static long scale(Column column) {
        if (column.getScale() != 0) {
            return column.getScale();
        }
        return column.getType() instanceof SQLSimpleType s ? s.getNumericScale() : 0;
    }

    /**
     * The text of a condition, query, body, default or filter as it is compared: whether there is one
     * under {@link Tolerance#IGNORE_EXPRESSION_TEXT}, otherwise the text without case and with single
     * spaces.
     */
    Object expression(String text) {
        boolean present = text != null && !text.isBlank();
        if (settings.tolerates(Tolerance.IGNORE_EXPRESSION_TEXT)) {
            return present;
        }
        return present ? text(text) : null;
    }

    Object expression(Expression expression) {
        return expression(expression == null ? null : expression.getBody());
    }

    /** The name of a primary key as compared, or nothing under {@link Tolerance#IGNORE_PRIMARY_KEY_NAMES}. */
    Object primaryKeyName(String name) {
        return settings.tolerates(Tolerance.IGNORE_PRIMARY_KEY_NAMES) ? null : name(name);
    }

    /**
     * Whether the names of unique constraints, checks and foreign keys count, as they do but under
     * {@link Tolerance#IGNORE_CONSTRAINT_NAMES}; where they do not, a constraint is matched by what it
     * constrains.
     */
    boolean constraintNamesCount() {
        return !settings.tolerates(Tolerance.IGNORE_CONSTRAINT_NAMES);
    }

    /** The delete and update rule of a foreign key, or nothing under {@link Tolerance#IGNORE_FK_RULES}. */
    Object rules(ForeignKey fk) {
        return settings.tolerates(Tolerance.IGNORE_FK_RULES) ? null
                : Arrays.asList(fk.getDeleteRule(), fk.getUpdateRule());
    }

    /** The grantor of a privilege by name, or nothing under {@link Tolerance#IGNORE_GRANTOR}. */
    Object grantor(Privilege privilege) {
        return settings.tolerates(Tolerance.IGNORE_GRANTOR) || privilege.getGrantor() == null ? null
                : name(privilege.getGrantor().getName());
    }

    /** Whether parties only the second model holds count, as they do but under {@link Tolerance#IGNORE_EXTRA_PARTIES}. */
    boolean countsExtraParties() {
        return !settings.tolerates(Tolerance.IGNORE_EXTRA_PARTIES);
    }

    /** A table or view by schema and name, as compared, so the same name in another schema is another table. */
    Object table(NamedColumnSet table) {
        if (table == null) {
            return null;
        }
        String schema = NamedColumnSets.findSchema(table).map(Schema::getName).orElse(null);
        return Arrays.asList(name(schema), name(table.getName()));
    }

    /**
     * What tells a routine from another of its schema: its name and the kinds and types of its
     * parameters, so each overload is a routine of its own.
     */
    Object signature(Procedure procedure) {
        List<Object> parameters = new ArrayList<>();
        for (Parameter parameter : procedure.getParameter()) {
            if (parameter.getKind() != ParameterDirectionKind.PDK_RETURN) {
                parameters.add(Arrays.asList(parameter.getKind(), type(parameter.getType())));
            }
        }
        return Arrays.asList(name(procedure.getName()), parameters);
    }

    /**
     * The type a function returns as it is compared, as {@link #type}; empty where it states none, as
     * PostgreSQL's metadata do not.
     */
    Optional<Object> returnType(Procedure procedure) {
        return procedure.getParameter().stream().filter(p -> p.getKind() == ParameterDirectionKind.PDK_RETURN)
                .findFirst().map(p -> type(p.getType()));
    }

    private static JDBCType jdbcType(Classifier type) {
        return type instanceof SQLDataType sql ? SQLSimpleTypes.toJdbcType(sql) : JDBCType.OTHER;
    }

    private static String text(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
