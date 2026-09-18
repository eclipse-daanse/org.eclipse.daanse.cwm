/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.liquibase.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.MigrationEmitter;

/**
 * Snapshot writer: serializes the STATE of a CWM Schema as a Liquibase
 * create-changelog — createTable (PK/NOT NULL/default inline) per table,
 * then indexes, then all foreign keys collected after every table exists
 * (FK cycles), then views, then triggers, then the table and column
 * comments ({@link DdlSettings#COMMENT_TYPE_JDBC_REMARKS} Descriptions).
 * Delegates the per-element rendering to the same change mapping the delta
 * writer uses, so both outputs stay consistent.
 */
public final class LiquibaseSnapshotWriter {

    private final MigrationEmitter emitter;

    public LiquibaseSnapshotWriter(MigrationEmitter emitter) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
    }

    public String write(Schema schema) {
        return write(schema, ChangelogSettings.defaults().withAuthor("cwm-snapshot"));
    }

    public String write(Schema schema, ChangelogSettings settings) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(settings, "settings");

        List<ChangeOp> ops = new ArrayList<>();
        for (Table t : Schemas.tables(schema)) {
            ops.add(new ChangeOp.CreateTable(t));
        }
        Namespaces.ownedElementStream(schema, SQLIndex.class)
                .forEach(ix -> ops.add(new ChangeOp.CreateIndex(ix)));
        for (Table t : Schemas.tables(schema)) {
            Tables.foreignKeys(t).forEach(fk -> ops.add(new ChangeOp.AddForeignKey(t, fk)));
        }
        for (View v : Schemas.views(schema)) {
            ops.add(new ChangeOp.CreateView(v));
        }
        for (Table t : Schemas.tables(schema)) {
            t.getTrigger().forEach(trg -> ops.add(new ChangeOp.CreateTrigger(t, trg)));
        }
        for (Table t : Schemas.tables(schema)) {
            comment(t, t, ops);
            ColumnSets.columns(t).forEach(c -> comment(t, c, ops));
        }
        return new LiquibaseChangelogWriter(emitter).write(ops, settings);
    }

    private static void comment(Table table, ModelElement element, List<ChangeOp> ops) {
        Descriptions.find(element, DdlSettings.COMMENT_TYPE_JDBC_REMARKS)
                .ifPresent(d -> ops.add(new ChangeOp.SetComment(table, element, d.getBody())));
    }
}
