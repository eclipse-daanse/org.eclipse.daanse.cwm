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
package org.eclipse.daanse.cwm.testkit;

import java.util.Locale;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place that writes the timing lines a run is measured by.
 *
 * <pre>
 * DBTIMING db=&lt;id&gt; phase=&lt;name&gt; ms=&lt;duration&gt; [detail=...]
 * </pre>
 *
 * <p>
 * The format is grepped by the harness collector, so it is fixed: one line per
 * phase occurrence, fields in this order, no line breaks inside a line.
 */
public final class DbTiming {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbTiming.class);

    private DbTiming() {
    }

    /**
     * Writes one line, taking the duration as now minus {@code startNanos}.
     *
     * <p>
     * Logged at warn, which is not what the level means. It is what makes the
     * line survive the default configuration of a test run, and a measurement
     * the collector never sees is no measurement.
     *
     * @param detail optional trailing {@code detail=} value; omitted when null
     */
    public static void log(Dialect dialect, String phase, long startNanos, String detail) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        if (detail == null) {
            LOGGER.warn("DBTIMING db={} phase={} ms={}", dbId(dialect), phase, ms);
        } else {
            LOGGER.warn("DBTIMING db={} phase={} ms={} detail={}", dbId(dialect), phase, ms, detail);
        }
    }

    /**
     * Lowercase database id, matching
     * {@code org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider#id()}
     * so the collector can join timings to the database that produced them.
     */
    public static String dbId(Dialect dialect) {
        return dialect == null || dialect.name() == null ? "unknown" : dialect.name().toLowerCase(Locale.ROOT);
    }
}
