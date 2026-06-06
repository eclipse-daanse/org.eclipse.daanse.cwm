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
package org.eclipse.daanse.cwm.testkit.api;

import java.net.URL;
import java.sql.Connection;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * Supplies the rows to load into the tables created from the
 * {@link DatabaseSupplier} schema. Both entry points are optional; CSVs load
 * first, then {@link #load(Connection, Dialect)}.
 */
public interface DataSupplier {

    /**
     * CSV resources keyed by target table name (optionally {@code "schema.table"}).
     * Iteration order is the load order — use a {@link java.util.LinkedHashMap} to
     * load parent rows before child rows. CSVs are header-only; column types come
     * from the CWM table.
     */
    default Map<String, URL> csvResources() {
        return Map.of();
    }

    /**
     * Loads rows programmatically, after the CSVs. Use the given connection and
     * dialect to issue your own INSERTs.
     */
    default void load(Connection connection, Dialect dialect) throws Exception {
        // default: nothing
    }
}
