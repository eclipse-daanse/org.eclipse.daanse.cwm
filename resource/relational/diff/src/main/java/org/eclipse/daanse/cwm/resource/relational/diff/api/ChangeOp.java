/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

/**
 * The complete catalog of schema change operations — the single intermediate
 * form between {@code SchemaDiff} and every output (dialect SQL via
 * {@code MigrationEmitter}, Liquibase changelog via {@code liquibase.render}).
 * Ordering rules live in {@link ChangePlanner}, not here.
 *
 * <p>Referenced elements are the live CWM objects of the NEW schema (of the
 * OLD one for pure drops). Renames carry the old name as a String so a plan
 * remains expressible without the old model in memory (artifact case).</p>
 */
public sealed interface ChangeOp {

    // tables
    record CreateTable(Table table) implements ChangeOp {
    }

    record DropTable(Table table) implements ChangeOp {
    }

    record RenameTable(Table table, String oldName) implements ChangeOp {
    }

    // columns
    record AddColumn(Table table, Column column) implements ChangeOp {
    }

    record DropColumn(Table table, Column column) implements ChangeOp {
    }

    record RenameColumn(Table table, Column column, String oldName) implements ChangeOp {
    }

    /** Type/size change; {@code oldColumn} may be null in the artifact case. */
    record AlterColumnType(Table table, Column oldColumn, Column newColumn) implements ChangeOp {
    }

    record AlterColumnNullability(Table table, Column column, boolean nullable) implements ChangeOp {
    }

    /** {@code defaultValue == null} drops the default. */
    record AlterColumnDefault(Table table, Column column, String defaultValue) implements ChangeOp {
    }

    // keys
    record AddPrimaryKey(Table table, PrimaryKey primaryKey) implements ChangeOp {
    }

    record DropPrimaryKey(Table table, PrimaryKey primaryKey) implements ChangeOp {
    }

    record AddUniqueConstraint(Table table, UniqueConstraint constraint) implements ChangeOp {
    }

    record DropUniqueConstraint(Table table, UniqueConstraint constraint) implements ChangeOp {
    }

    record AddCheckConstraint(Table table, CheckConstraint constraint) implements ChangeOp {
    }

    record DropCheckConstraint(Table table, CheckConstraint constraint) implements ChangeOp {
    }

    record AddForeignKey(Table table, ForeignKey foreignKey) implements ChangeOp {
    }

    record DropForeignKey(Table table, ForeignKey foreignKey) implements ChangeOp {
    }

    // indexes
    record CreateIndex(SQLIndex index) implements ChangeOp {
    }

    record DropIndex(SQLIndex index) implements ChangeOp {
    }

    record RenameIndex(SQLIndex index, String oldName) implements ChangeOp {
    }

    // constraint renames
    /** Primary key, unique, check or foreign key {@code constraint} (new side) renamed from {@code oldName}. */
    record RenameConstraint(Table table, ModelElement constraint, String oldName) implements ChangeOp {
    }

    //  views
    record CreateView(View view) implements ChangeOp {
    }

    record DropView(View view) implements ChangeOp {
    }

    record ReplaceView(View oldView, View newView) implements ChangeOp {
    }

    // triggers
    record CreateTrigger(Table table, Trigger trigger) implements ChangeOp {
    }

    record DropTrigger(Table table, Trigger trigger) implements ChangeOp {
    }
}
