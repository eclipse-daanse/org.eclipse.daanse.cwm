/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;

/**
 * One row of cross-dialect test parameterization: the {@code DatabaseProvider}
 * id plus the per-dialect knobs that the otherwise-identical test bodies need
 * (schema name + scoping, the settings a script lands with, and the
 * trigger profile). Activating yields a live {@link ActiveDatabase} from the
 * {@code jdbc.datasource.testkit} ServiceLoader, or for DuckDB a file of its
 * own; H2 and DuckDB need no Docker, the container ones throw on
 * {@link #activate()} when Docker is unavailable so the caller can skip.
 */
public enum DialectProfile {

    /** In-process; no triggers (H2 wants Java class bodies). */
    H2("h2", "RT_H2", true, false, null, null, null, null, null),

    POSTGRES("postgres", "rt_pg", true, true,
            "BEGIN RAISE NOTICE 'audit'; RETURN NEW; END;", "CREATE OR REPLACE FUNCTION", "EXECUTE FUNCTION",
            ConditionTimingType.BEFORE, EventManipulationType.INSERT),

    /** Schema == database "rt" (pre-created by the provider); never dropped. */
    MARIADB("mariadb", "rt", false, true,
            "BEGIN SET @audit_count = COALESCE(@audit_count, 0) + 1; END", "CREATE PROCEDURE", "CALL ",
            ConditionTimingType.BEFORE, EventManipulationType.INSERT),

    /**
     * Schema == database: the provider binds its connection to a database of its own and lets the
     * user create others, so the test schema is created and dropped as a database.
     */
    MYSQL("mysql", "rt_mysql", true, true,
            "BEGIN SET @audit_count = COALESCE(@audit_count, 0) + 1; END", "CREATE PROCEDURE", "CALL ",
            ConditionTimingType.BEFORE, EventManipulationType.INSERT),

    /** MSSQL has no BEFORE triggers — AFTER INSERT. */
    MSSQL("mssql", "rt_mssql", true, true,
            "BEGIN SET NOCOUNT ON; END", "CREATE OR ALTER PROCEDURE", "AS EXEC ",
            ConditionTimingType.AFTER, EventManipulationType.INSERT),

    /** Schema == user "RT"; CREATE SCHEMA is skipped (Oracle ties it to a user). */
    ORACLE("oracle", "RT", false, true,
            "BEGIN NULL; END;", "CREATE OR REPLACE PROCEDURE", null,
            ConditionTimingType.BEFORE, EventManipulationType.INSERT),

    /**
     * In-process, in a file; no triggers. Its foreign keys go into CREATE TABLE, as it cannot add
     * one to an existing table.
     */
    DUCKDB("duckdb", "RT_DUCKDB", true, false, null, null, null, null, null);

    private final String providerId;
    private final String schemaName;
    private final boolean ownsSchemaNamespace; // can CREATE/DROP the schema as a namespace
    private final boolean supportsTriggers;
    private final String triggerBody;
    private final String triggerProcPrefix;
    private final String triggerCallSnippet; // null = no shared-procedure CALL form (Oracle)
    private final ConditionTimingType triggerTiming;
    private final EventManipulationType triggerEvent;

    DialectProfile(String providerId, String schemaName, boolean ownsSchemaNamespace, boolean supportsTriggers,
            String triggerBody, String triggerProcPrefix, String triggerCallSnippet,
            ConditionTimingType triggerTiming, EventManipulationType triggerEvent) {
        this.providerId = providerId;
        this.schemaName = schemaName;
        this.ownsSchemaNamespace = ownsSchemaNamespace;
        this.supportsTriggers = supportsTriggers;
        this.triggerBody = triggerBody;
        this.triggerProcPrefix = triggerProcPrefix;
        this.triggerCallSnippet = triggerCallSnippet;
        this.triggerTiming = triggerTiming;
        this.triggerEvent = triggerEvent;
    }

    public String providerId() {
        return providerId;
    }

    public String schemaName() {
        return schemaName;
    }

    public boolean supportsTriggers() {
        return supportsTriggers;
    }

    /**
     * Whether the database keeps a schema in the catalog slot of JDBC, as MariaDB and MySQL do: a
     * schema there is a database.
     */
    public boolean schemasAreCatalogs() {
        return this == MARIADB || this == MYSQL;
    }

    /**
     * Whether the database keeps a comment on a column of a view where its dialect writes comments on
     * views: H2 takes {@code COMMENT ON VIEW} and rejects {@code COMMENT ON COLUMN} for a column of a
     * view.
     */
    public boolean keepsCommentsOnViewColumns() {
        return this != H2;
    }

    /**
     * Whether a migration can add a constraint to a table with ALTER TABLE or drop one. DuckDB can do
     * neither: it declares its constraints in CREATE TABLE only.
     */
    public boolean altersConstraints() {
        return this != DUCKDB;
    }

    public String triggerBody() {
        return triggerBody;
    }

    public String triggerProcPrefix() {
        return triggerProcPrefix;
    }

    public String triggerCallSnippet() {
        return triggerCallSnippet;
    }

    public ConditionTimingType triggerTiming() {
        return triggerTiming;
    }

    public EventManipulationType triggerEvent() {
        return triggerEvent;
    }

    /** Activate the underlying provider (throws when its Docker engine is absent). */
    public ActiveDatabase activate() {
        if (this == DUCKDB) {
            return DuckDbDatabase.activate();
        }
        return DatabaseProvider.byId(providerId).activate();
    }

    /**
     * The settings a script for this database is written with: no {@code CREATE SCHEMA} where the
     * dialect owns no schema namespace (Oracle ties a schema to a user, MariaDB to a database).
     */
    public DdlSettings settings() {
        return DdlSettings.defaults().withCreateSchema(ownsSchemaNamespace);
    }

    /** The create script of {@code model} for this database. */
    public List<String> create(Dialect dialect, Schema model) {
        return new DdlGeneratorFactoryImpl().create(dialect, settings()).createSchema(model);
    }

    /** Scope the connection before metadata reads, where the dialect would list more than the test schema. */
    public void prepareForMetadata(Connection c) throws SQLException {
        if (schemasAreCatalogs()) {
            // MariaDB and MySQL put the database in the catalog slot; without scoping,
            // the snapshot also returns performance_schema/information_schema tables.
            c.setCatalog(schemaName);
        }
    }

    /** Snapshot the live structure through the database's metadata provider, as the CWM import does. */
    public MetaInfo snapshot(DatabaseService service, Connection c, Dialect dialect) throws SQLException {
        var provider = org.eclipse.daanse.sql.jdbc.metadata.MetadataProviders.forConnection(c);
        return provider.isPresent() ? service.createMetaInfo(c, provider.get()) : service.createMetaInfo(c);
    }

    /**
     * Best-effort teardown: reverse the model via the generator (dialect- and
     * model-correct), then drop the schema namespace where the dialect owns one.
     * All failures are swallowed — each dialect runs against its own database so
     * leftovers never cross-contaminate, and fixtures emit {@code IF NOT EXISTS}.
     */
    public void cleanup(Connection c, Schema model, Dialect dialect) {
        try {
            List<String> drop = new DdlGeneratorFactoryImpl().create(dialect, settings().withCascade(true))
                    .dropSchema(model);
            try (Statement s = c.createStatement()) {
                for (String stmt : drop) {
                    try {
                        s.execute(stmt);
                    } catch (SQLException ignored) {
                        // best effort
                    }
                }
            }
        } catch (SQLException ignored) {
            // best effort
        }
        if (ownsSchemaNamespace) {
            try (Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
            } catch (SQLException ignored) {
                // H2/PG honour this; SQL Server rejects CASCADE — the model-drop above already cleared it.
            }
        }
    }

    @Override
    public String toString() {
        return providerId;
    }
}
