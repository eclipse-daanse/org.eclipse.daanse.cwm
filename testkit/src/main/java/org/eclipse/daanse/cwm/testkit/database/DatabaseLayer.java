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
package org.eclipse.daanse.cwm.testkit.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * Creates the tables (and keys, indexes, views) of a CWM {@link Schema} in a
 * JDBC {@link DataSource} using the dialect's DDL generator.
 */
public final class DatabaseLayer {

    private DatabaseLayer() {
    }

    /**
     * Creates the full schema (all features).
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema schema) throws SQLException {
        apply(dataSource, dialect, schema, Feature.ALL);
    }

    /**
     * Creates only the listed features, e.g. {@code SCHEMA, TABLE,
     * PRIMARY_KEY} to skip indexes or foreign keys.
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema schema, Set<Feature> features)
            throws SQLException {
        apply(dataSource, dialect, schema, features, DdlSettings.defaults());
    }

    /**
     * Creates the listed features with explicit {@link DdlSettings}, e.g. to drop
     * schema qualification for connection-scoped databases.
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema schema, Set<Feature> features,
            DdlSettings settings) throws SQLException {
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect, settings).createSchema(schema, features);
        if (ddl.isEmpty()) {
            return;
        }
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }
}
