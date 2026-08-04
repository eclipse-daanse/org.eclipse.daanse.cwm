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

import org.eclipse.daanse.sql.dialect.api.Dialect;

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

    /**
     * Whether to gather optimizer statistics for {@code tableName} as soon as
     * that table has finished loading, rather than waiting for the rest.
     *
     * <p>
     * Off by default. Analysing per table is faster than one database-wide
     * statement, but slows the loading by about as much, because both draw on the
     * same connections and cores. It is a per-table decision because the trade
     * depends on the dataset: one dominant table finishing early has nothing to
     * overlap with, many mid-sized ones do.
     *
     * <p>
     * A table analysed here is analysed <em>before</em> the keys and indexes
     * exist, so the statistics cannot describe them; where that matters, rely on
     * {@link #analyzeAfterAll()}.
     */
    default boolean analyzeAfterTable(String tableName) {
        return false;
    }

    /**
     * Whether to gather statistics for the whole database once everything is
     * loaded and indexed. On by default: a planner with nothing to go on turns
     * quick queries into slow ones.
     *
     * <p>
     * Turn it off for a dataset small enough that the planner cannot go wrong,
     * or when every table is already covered by {@link #analyzeAfterTable}.
     */
    default boolean analyzeAfterAll() {
        return true;
    }
}
