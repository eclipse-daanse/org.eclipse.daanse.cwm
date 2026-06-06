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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.jdbc.db.api.DatabaseService;
import org.eclipse.daanse.jdbc.db.api.meta.MetaInfo;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * One row of cross-dialect test parameterization: the {@code DatabaseProvider}
 * id plus the per-dialect knobs that the otherwise-identical test bodies need
 * (schema name + scoping, the feature subset that lands cleanly, and the
 * trigger profile). Activating yields a live {@link ActiveDatabase} from the
 * {@code jdbc.datasource.testkit} ServiceLoader; H2 needs no Docker, the
 * container ones throw on {@link #activate()} when Docker is unavailable so the
 * caller can skip.
 */
public enum DialectProfile {

    /** In-process; no triggers (H2 wants Java class bodies). */
    H2("h2", "RT_H2", true, true, false, null, null, null, null, null),

    POSTGRES("postgres", "rt_pg", true, true, true,
            "BEGIN RAISE NOTICE 'audit'; RETURN NEW; END;", "CREATE OR REPLACE FUNCTION", "EXECUTE FUNCTION",
            ConditionTimingType.BEFORE, EventManipulationType.INSERT),

    /** Schema == database "rt" (pre-created by the provider); never dropped. */
    MARIADB("mariadb", "rt", false, false, true,
            "BEGIN SET @audit_count = COALESCE(@audit_count, 0) + 1; END", "CREATE PROCEDURE", "CALL ",
            ConditionTimingType.BEFORE, EventManipulationType.INSERT),

    /** MSSQL has no BEFORE triggers — AFTER INSERT. createMetaInfo without dialect. */
    MSSQL("mssql", "rt_mssql", true, false, true,
            "BEGIN SET NOCOUNT ON; END", "CREATE OR ALTER PROCEDURE", "AS EXEC ",
            ConditionTimingType.AFTER, EventManipulationType.INSERT),

    /** Schema == user "RT"; CREATE SCHEMA is skipped (Oracle ties it to a user). */
    ORACLE("oracle", "RT", false, false, true,
            "BEGIN NULL; END;", "CREATE OR REPLACE PROCEDURE", null,
            ConditionTimingType.BEFORE, EventManipulationType.INSERT);

    private final String providerId;
    private final String schemaName;
    private final boolean ownsSchemaNamespace; // can CREATE/DROP the schema as a namespace
    private final boolean setSchemaBeforeMeta;
    private final boolean supportsTriggers;
    private final String triggerBody;
    private final String triggerProcPrefix;
    private final String triggerCallSnippet; // null = no shared-procedure CALL form (Oracle)
    private final ConditionTimingType triggerTiming;
    private final EventManipulationType triggerEvent;

    DialectProfile(String providerId, String schemaName, boolean ownsSchemaNamespace, boolean setSchemaBeforeMeta,
            boolean supportsTriggers, String triggerBody, String triggerProcPrefix, String triggerCallSnippet,
            ConditionTimingType triggerTiming, EventManipulationType triggerEvent) {
        this.providerId = providerId;
        this.schemaName = schemaName;
        this.ownsSchemaNamespace = ownsSchemaNamespace;
        this.setSchemaBeforeMeta = setSchemaBeforeMeta;
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
        return DatabaseProvider.byId(providerId).activate();
    }

    /** Features that create cleanly here: everything except triggers (+ SCHEMA where the dialect owns no namespace). */
    public Set<Feature> nonTriggerFeatures() {
        EnumSet<Feature> s = EnumSet.complementOf(EnumSet.of(Feature.TRIGGER));
        if (!ownsSchemaNamespace) {
            s.remove(Feature.SCHEMA);
        }
        return s;
    }

    /** Features for a triggered create: everything (minus SCHEMA where unsupported). */
    public Set<Feature> allFeatures() {
        EnumSet<Feature> s = EnumSet.allOf(Feature.class);
        if (!ownsSchemaNamespace) {
            s.remove(Feature.SCHEMA);
        }
        return s;
    }

    /** Point the connection at the test schema before metadata reads, where the dialect needs it. */
    public void prepareForMetadata(Connection c) throws SQLException {
        if (setSchemaBeforeMeta) {
            c.setSchema(schemaName);
        }
        if (this == MARIADB) {
            // MariaDB puts the database in the catalog slot; without scoping, the
            // snapshot also returns performance_schema/information_schema tables.
            c.setCatalog(schemaName);
        }
    }

    /** Snapshot the live structure; SQL Server's MetadataProvider is selected without an explicit dialect. */
    public MetaInfo snapshot(DatabaseService service, Connection c, Dialect dialect) throws SQLException {
        return this == MSSQL ? service.createMetaInfo(c) : service.createMetaInfo(c, dialect);
    }

    /**
     * Best-effort teardown: reverse the model via the generator (dialect- and
     * model-correct), then drop the schema namespace where the dialect owns one.
     * All failures are swallowed — each dialect runs against its own database so
     * leftovers never cross-contaminate, and fixtures emit {@code IF NOT EXISTS}.
     */
    public void cleanup(Connection c, Schema model, Dialect dialect, Set<Feature> features) {
        try {
            List<String> drop = new DdlGeneratorFactoryImpl().create(dialect, DdlSettings.defaults().withCascade(true))
                    .dropSchema(model, features);
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
