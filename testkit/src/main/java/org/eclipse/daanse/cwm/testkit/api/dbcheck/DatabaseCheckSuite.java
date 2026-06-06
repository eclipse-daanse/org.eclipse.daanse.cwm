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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.api.dbcheck;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;

/**
 * A set of database-shape assertions to run against the loaded
 * {@code DataSource}: schema/table/column existence plus SQL queries.
 *
 * <p>
 * Build the records by hand, or call {@link #fromCwm(String, Schema)} to derive
 * existence checks for every table and column in a CWM {@link Schema}.
 *
 * @param name         display name for reports
 * @param schemaChecks schemas to assert present (or absent)
 * @param queryChecks  SQL queries to run against the DataSource
 */
public record DatabaseCheckSuite(String name, List<DatabaseSchemaCheck> schemaChecks,
        List<DatabaseQueryCheck> queryChecks) {

    public DatabaseCheckSuite {
        schemaChecks = List.copyOf(schemaChecks);
        queryChecks = List.copyOf(queryChecks);
    }

    public DatabaseCheckSuite(String name, List<DatabaseSchemaCheck> schemaChecks) {
        this(name, schemaChecks, List.of());
    }

    /**
     * Builds a suite asserting that every table and column in the CWM
     * {@code Schema} exists at its declared type.
     */
    public static DatabaseCheckSuite fromCwm(String name, Schema cwmSchema) {
        return new DatabaseCheckSuite(name, List.of(DatabaseSchemaCheck.fromCwm(cwmSchema)), List.of());
    }
}
