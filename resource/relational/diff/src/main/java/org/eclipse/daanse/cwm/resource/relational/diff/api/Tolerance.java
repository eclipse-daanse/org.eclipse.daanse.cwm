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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * One way in which two models may differ without counting as a difference. A comparison takes a set
 * of them in {@link DiffSettings#tolerances()}; each is applied in the one place where its kind of
 * value is compared, for every database alike. Without any, everything the differ looks at counts.
 *
 * <p>
 * The tolerances exist for comparing a model with what a database made of it: a database keeps the
 * structure, but spells some of it its own way or does not keep it. {@link #roundTrip(String)} gives
 * the set for one database, each member there because a round trip through that database showed the
 * need.
 * </p>
 */
public enum Tolerance {

    /**
     * Column and parameter types compare by their JDBC type code, {@code DECIMAL} and {@code NUMERIC}
     * as one, instead of by their names: a database reports its own name for the type it was given,
     * PostgreSQL {@code int4} for {@code INTEGER} and {@code numeric} for {@code DECIMAL}, H2, MariaDB,
     * MySQL, SQL Server and DuckDB {@code CHARACTER VARYING} for {@code VARCHAR}, Oracle {@code VARCHAR2}
     * and {@code NUMBER}. Where a side has no code, the names compare.
     */
    TYPE_BY_JDBC_CODE,

    /**
     * The integer types ({@code TINYINT}, {@code SMALLINT}, {@code INTEGER}, {@code BIGINT}) and
     * {@code DECIMAL} and {@code NUMERIC} compare as one exact numeric type, an integer type as one
     * without precision and scale: Oracle keeps every integer type as {@code NUMBER} without
     * precision and reports it as {@code NUMERIC}. Integer types of different width are the same then.
     */
    INTEGER_AS_NUMERIC,

    /**
     * The text of check conditions, column defaults, view queries, trigger bodies and conditions,
     * routine bodies and index filters does not count, only whether there is one: a database stores
     * such text as it parsed it, PostgreSQL {@code id > 0} as {@code (id > 0)} and {@code 'NEW'} as
     * {@code 'NEW'::character varying}, SQL Server a default as {@code ('NEW')}, MySQL {@code `ID` > 0}
     * as {@code (`ID` > 0)}, and a view query or a trigger body with its own qualification, casts and
     * wrapping, MySQL every column of a view as {@code `schema`.`table`.`column` AS `column`}.
     */
    IGNORE_EXPRESSION_TEXT,

    /**
     * The length of character and binary columns does not count: DuckDB's {@code VARCHAR} has no
     * length and reports none.
     */
    IGNORE_CHARACTER_LENGTH,

    /**
     * The name of a primary key does not count, only its columns: DuckDB names every primary key
     * {@code <table>_<columns>_pkey}, MariaDB and MySQL every one {@code PRIMARY}, whatever
     * {@code CREATE TABLE} calls it.
     */
    IGNORE_PRIMARY_KEY_NAMES,

    /**
     * The names of unique constraints, checks and foreign keys do not count: a constraint is matched
     * by what it constrains. DuckDB names each itself ({@code <table>_<columns>_key}, {@code _check},
     * {@code _fkey}), whatever {@code CREATE TABLE} calls it.
     */
    IGNORE_CONSTRAINT_NAMES,

    /**
     * The delete and update rules of foreign keys do not count: DuckDB does not keep the rule a
     * foreign key was created with.
     */
    IGNORE_FK_RULES,

    /**
     * The grantor of a privilege does not count: a model names none, the database names the user who
     * ran the {@code GRANT}.
     */
    IGNORE_GRANTOR,

    /**
     * Parties only the second model holds, and their privileges, do not count: a database reports the
     * user who created everything, its own predefined roles and {@code PUBLIC}.
     */
    IGNORE_EXTRA_PARTIES,

    /**
     * Names compare without regard to case. In no round trip: the DDL generator quotes every name, so
     * a database keeps its case, and {@code "Orders"} and {@code "orders"} are two tables. For models
     * of a database that folds unquoted names.
     */
    CASE_INSENSITIVE_NAMES;

    /** What every round trip needs, and so what a database the comparison does not know gets. */
    private static final Set<Tolerance> BASE = EnumSet.of(TYPE_BY_JDBC_CODE, IGNORE_EXPRESSION_TEXT, IGNORE_GRANTOR,
            IGNORE_EXTRA_PARTIES);

    /**
     * The tolerances for comparing a model with the model read back from the database of
     * {@code dialectId} after its DDL ran there:
     *
     * <table>
     * <caption>Tolerances of a round trip</caption>
     * <tr><th>Dialect</th><th>Tolerances</th></tr>
     * <tr><td>every one</td><td>{@link #TYPE_BY_JDBC_CODE}, {@link #IGNORE_EXPRESSION_TEXT},
     * {@link #IGNORE_GRANTOR}, {@link #IGNORE_EXTRA_PARTIES}</td></tr>
     * <tr><td>{@code postgresql}, {@code h2}, {@code sqlserver}</td><td>no more</td></tr>
     * <tr><td>{@code mariadb}, {@code mysql}</td><td>and {@link #IGNORE_PRIMARY_KEY_NAMES}</td></tr>
     * <tr><td>{@code oracle}</td><td>and {@link #INTEGER_AS_NUMERIC}</td></tr>
     * <tr><td>{@code duckdb}</td><td>and {@link #IGNORE_CHARACTER_LENGTH},
     * {@link #IGNORE_PRIMARY_KEY_NAMES}, {@link #IGNORE_CONSTRAINT_NAMES}, {@link #IGNORE_FK_RULES}</td></tr>
     * </table>
     *
     * The ids are those of the DDL dialects ({@code postgresql}, {@code duckdb}, {@code h2},
     * {@code mariadb}, {@code mysql}, {@code oracle}, {@code sqlserver}, {@code ansi}) and their
     * aliases ({@code postgres}, {@code pg}, {@code mssql}, {@code mssqlserver}), in any case. An id
     * no round trip has shown more for, {@code ansi} among them, and an unknown id or {@code null} get
     * what every database needs.
     *
     * @return a new set the caller may change
     */
    public static Set<Tolerance> roundTrip(String dialectId) {
        Set<Tolerance> tolerances = EnumSet.copyOf(BASE);
        String id = dialectId == null ? "" : dialectId.trim().toLowerCase(Locale.ROOT);
        switch (id) {
        case "duckdb" -> tolerances.addAll(
                EnumSet.of(IGNORE_CHARACTER_LENGTH, IGNORE_PRIMARY_KEY_NAMES, IGNORE_CONSTRAINT_NAMES, IGNORE_FK_RULES));
        case "mariadb", "mysql" -> tolerances.add(IGNORE_PRIMARY_KEY_NAMES);
        case "oracle" -> tolerances.add(INTEGER_AS_NUMERIC);
        default -> {
            // postgresql, h2, sqlserver, ansi and their aliases: what every database needs
        }
        }
        return tolerances;
    }
}
