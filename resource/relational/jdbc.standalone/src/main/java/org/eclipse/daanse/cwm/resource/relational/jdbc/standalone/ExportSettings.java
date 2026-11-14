/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import java.util.Objects;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.Dialects;

/**
 * How {@link CwmJdbcExport} writes a model into a database. Never changes; start from
 * {@link #defaults()} and derive with the {@code with} methods.
 *
 * @param ddl       how the statements are written: schema-qualified names, {@code CREATE SCHEMA},
 *                  {@code IF NOT EXISTS}, {@code CASCADE}, the type of the descriptions written as
 *                  comments, and whether the export fails before anything runs where the dialect
 *                  cannot write what the model holds ({@link DdlSettings#strict()}), rather than
 *                  write it plainer or leave it out
 * @param dropFirst whether what the model creates is dropped first, so an export runs again and
 *                  again: the drop script of the same model, written with {@code IF EXISTS}, runs
 *                  before the create script, each statement only where the database holds what it
 *                  works on (see {@link CwmJdbcExport})
 * @param dialect   the id of the dialect the statements are written in, one of {@link Dialects#IDS}
 *                  (an alias is taken as the id it stands for); {@code null}: the dialect of the
 *                  database the export writes into, {@link Dialects#forConnection}
 */
public record ExportSettings(DdlSettings ddl, boolean dropFirst, String dialect) {

    /**
     * @throws IllegalArgumentException for an unknown dialect, naming the known ones
     */
    public ExportSettings {
        Objects.requireNonNull(ddl, "ddl");
        dialect = dialect == null || dialect.isBlank() ? null : Dialects.id(dialect);
    }

    /** {@link DdlSettings#defaults()}, nothing dropped, the dialect of the database. */
    public static ExportSettings defaults() {
        return new ExportSettings(DdlSettings.defaults(), false, null);
    }

    public ExportSettings withDdl(DdlSettings value) {
        return new ExportSettings(value, dropFirst, dialect);
    }

    public ExportSettings withDropFirst(boolean value) {
        return new ExportSettings(ddl, value, dialect);
    }

    /** @param id a dialect id or alias; {@code null}: the dialect of the database */
    public ExportSettings withDialect(String id) {
        return new ExportSettings(ddl, dropFirst, id);
    }
}
