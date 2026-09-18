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

import static org.eclipse.daanse.cwm.resource.relational.liquibase.render.LiquibaseXml.defaultAttribute;
import static org.eclipse.daanse.cwm.resource.relational.liquibase.render.LiquibaseXml.liquibaseType;
import static org.eclipse.daanse.cwm.resource.relational.liquibase.render.LiquibaseXml.notNull;
import static org.eclipse.daanse.cwm.resource.relational.liquibase.render.LiquibaseXml.schemaNameOf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.Dialects;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * Delta writer: turns an ordered {@link ChangeOp} plan into a Liquibase
 * changelog — one {@code <changeSet>} per operation, deterministic ids
 * (running number + content hash), rollback blocks where the inverse is
 * computable, an intentionally empty {@code <rollback/>} where it is not.
 * Pure output format, parallel to the dialect SQL of the migration emitter;
 * no liquibase classes involved.
 *
 * <p>The changelog is dialect-neutral (Liquibase translates the generic
 * types itself). Only CHECK constraints — which Liquibase OSS has no change
 * type for — fall back to {@code <sql dbms="…">} per dialect, with the text
 * coming from the jdbc.db DdlGenerator.</p>
 */
public final class LiquibaseChangelogWriter {

    public String write(List<ChangeOp> ops) {
        return write(ops, ChangelogSettings.defaults());
    }

    public String write(List<ChangeOp> ops, ChangelogSettings settings) {
        Objects.requireNonNull(ops, "ops");
        Objects.requireNonNull(settings, "settings");
        LiquibaseXml x = LiquibaseXml.document(settings.logicalFilePath());
        for (ChangeOp op : ops) {
            writeOp(x, op, settings);
        }
        return x.closeDocument();
    }

    private void writeOp(LiquibaseXml x, ChangeOp op, ChangelogSettings settings) {
        switch (op) {
            case ChangeOp.CreateTable o -> createTable(x, o, settings);
            case ChangeOp.DropTable o -> {
                x.openChangeSet(settings.author(), "DropTable|" + qualified(o.table()));
                x.emptyElement("dropTable", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName());
                if (settings.includeRollback()) {
                    x.emptyRollback();
                }
                x.closeChangeSet();
            }
            case ChangeOp.RenameTable o -> {
                String schema = schemaNameOf(o.table());
                x.openChangeSet(settings.author(),
                        "RenameTable|" + schema + "|" + o.oldName() + "->" + o.table().getName());
                x.emptyElement("renameTable", "schemaName", schema,
                        "oldTableName", o.oldName(), "newTableName", o.table().getName());
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("renameTable", "schemaName", schema,
                            "oldTableName", o.table().getName(), "newTableName", o.oldName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.AddColumn o -> {
                x.openChangeSet(settings.author(),
                        "AddColumn|" + qualified(o.table()) + "|" + o.column().getName());
                x.startElement("addColumn", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName());
                column(x, o.column(), false);
                x.endElement("addColumn");
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("dropColumn", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(), "columnName", o.column().getName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.DropColumn o -> {
                x.openChangeSet(settings.author(),
                        "DropColumn|" + qualified(o.table()) + "|" + o.column().getName());
                x.emptyElement("dropColumn", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(), "columnName", o.column().getName());
                if (settings.includeRollback()) {
                    x.emptyRollback();
                }
                x.closeChangeSet();
            }
            case ChangeOp.RenameColumn o -> {
                String schema = schemaNameOf(o.table());
                String type = liquibaseType(o.column());
                x.openChangeSet(settings.author(), "RenameColumn|" + qualified(o.table())
                        + "|" + o.oldName() + "->" + o.column().getName());
                x.emptyElement("renameColumn", "schemaName", schema,
                        "tableName", o.table().getName(),
                        "oldColumnName", o.oldName(), "newColumnName", o.column().getName(),
                        "columnDataType", type);
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("renameColumn", "schemaName", schema,
                            "tableName", o.table().getName(),
                            "oldColumnName", o.column().getName(), "newColumnName", o.oldName(),
                            "columnDataType", type);
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.AlterColumnType o -> {
                String type = liquibaseType(o.newColumn());
                x.openChangeSet(settings.author(), "AlterColumnType|" + qualified(o.table())
                        + "|" + o.newColumn().getName() + "|" + type);
                x.emptyElement("modifyDataType", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(),
                        "columnName", o.newColumn().getName(), "newDataType", type);
                // modifyDataType loses NOT NULL on some databases — restate it.
                if (notNull(o.newColumn())) {
                    x.emptyElement("addNotNullConstraint", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(),
                            "columnName", o.newColumn().getName(), "columnDataType", type);
                }
                if (settings.includeRollback()) {
                    if (o.oldColumn() != null) {
                        x.startElement("rollback");
                        x.emptyElement("modifyDataType", "schemaName", schemaNameOf(o.table()),
                                "tableName", o.table().getName(),
                                "columnName", o.oldColumn().getName(),
                                "newDataType", liquibaseType(o.oldColumn()));
                        x.endElement("rollback");
                    } else {
                        x.emptyRollback();
                    }
                }
                x.closeChangeSet();
            }
            case ChangeOp.AlterColumnNullability o -> {
                String type = liquibaseType(o.column());
                String element = o.nullable() ? "dropNotNullConstraint" : "addNotNullConstraint";
                String inverse = o.nullable() ? "addNotNullConstraint" : "dropNotNullConstraint";
                x.openChangeSet(settings.author(), "AlterColumnNullability|" + qualified(o.table())
                        + "|" + o.column().getName() + "|" + o.nullable());
                x.emptyElement(element, "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(),
                        "columnName", o.column().getName(), "columnDataType", type);
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement(inverse, "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(),
                            "columnName", o.column().getName(), "columnDataType", type);
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.AlterColumnDefault o -> {
                x.openChangeSet(settings.author(), "AlterColumnDefault|" + qualified(o.table())
                        + "|" + o.column().getName() + "|" + o.defaultValue());
                if (o.defaultValue() == null || o.defaultValue().isBlank()) {
                    x.emptyElement("dropDefaultValue", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(), "columnName", o.column().getName());
                } else {
                    String[] attr = defaultAttribute(o.defaultValue());
                    x.emptyElement("addDefaultValue", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(), "columnName", o.column().getName(),
                            "columnDataType", liquibaseType(o.column()), attr[0], attr[1]);
                }
                if (settings.includeRollback()) {
                    x.emptyRollback(); // old default value is not carried on the op
                }
                x.closeChangeSet();
            }
            case ChangeOp.AddPrimaryKey o -> {
                x.openChangeSet(settings.author(), "AddPrimaryKey|" + qualified(o.table()));
                x.emptyElement("addPrimaryKey", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(),
                        "columnNames", featureCsv(o.primaryKey().getFeature()),
                        "constraintName", o.primaryKey().getName());
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("dropPrimaryKey", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(),
                            "constraintName", o.primaryKey().getName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.DropPrimaryKey o -> {
                x.openChangeSet(settings.author(), "DropPrimaryKey|" + qualified(o.table()));
                x.emptyElement("dropPrimaryKey", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(),
                        "constraintName", o.primaryKey().getName());
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("addPrimaryKey", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(),
                            "columnNames", featureCsv(o.primaryKey().getFeature()),
                            "constraintName", o.primaryKey().getName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.AddUniqueConstraint o -> {
                x.openChangeSet(settings.author(), "AddUniqueConstraint|" + qualified(o.table())
                        + "|" + o.constraint().getName());
                x.emptyElement("addUniqueConstraint", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(),
                        "columnNames", String.join(", ", UniqueConstraints.columns(o.constraint())
                                .stream().map(Column::getName).toList()),
                        "constraintName", o.constraint().getName());
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("dropUniqueConstraint", "schemaName", schemaNameOf(o.table()),
                            "tableName", o.table().getName(),
                            "constraintName", o.constraint().getName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.DropUniqueConstraint o -> {
                x.openChangeSet(settings.author(), "DropUniqueConstraint|" + qualified(o.table())
                        + "|" + o.constraint().getName());
                x.emptyElement("dropUniqueConstraint", "schemaName", schemaNameOf(o.table()),
                        "tableName", o.table().getName(),
                        "constraintName", o.constraint().getName());
                if (settings.includeRollback()) {
                    x.emptyRollback();
                }
                x.closeChangeSet();
            }
            case ChangeOp.AddCheckConstraint o ->
                checkConstraintSql(x, o.table(), o.constraint().getName(),
                        o.constraint().getBody() == null ? null : o.constraint().getBody().getBody(),
                        true, settings);
            case ChangeOp.DropCheckConstraint o ->
                checkConstraintSql(x, o.table(), o.constraint().getName(), null, false, settings);
            case ChangeOp.AddForeignKey o -> addForeignKey(x, o, settings);
            case ChangeOp.DropForeignKey o -> {
                x.openChangeSet(settings.author(), "DropForeignKey|" + qualified(o.table())
                        + "|" + o.foreignKey().getName());
                x.emptyElement("dropForeignKeyConstraint",
                        "baseTableSchemaName", schemaNameOf(o.table()),
                        "baseTableName", o.table().getName(),
                        "constraintName", o.foreignKey().getName());
                if (settings.includeRollback()) {
                    x.emptyRollback();
                }
                x.closeChangeSet();
            }
            case ChangeOp.CreateIndex o -> {
                Table t = (Table) o.index().getSpannedClass();
                x.openChangeSet(settings.author(), "CreateIndex|" + o.index().getName());
                x.startElement("createIndex", "schemaName", schemaNameOf(t),
                        "tableName", t.getName(), "indexName", o.index().getName(),
                        "unique", o.index().isIsUnique() ? "true" : null);
                for (var ifc : o.index().getIndexedFeature()) {
                    if (ifc.getFeature() instanceof Column c) {
                        x.emptyElement("column", "name", c.getName());
                    }
                }
                x.endElement("createIndex");
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("dropIndex", "schemaName", schemaNameOf(t),
                            "tableName", t.getName(), "indexName", o.index().getName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.DropIndex o -> {
                Table t = (Table) o.index().getSpannedClass();
                x.openChangeSet(settings.author(), "DropIndex|" + o.index().getName());
                x.emptyElement("dropIndex", "schemaName", schemaNameOf(t),
                        "tableName", t.getName(), "indexName", o.index().getName());
                if (settings.includeRollback()) {
                    x.emptyRollback();
                }
                x.closeChangeSet();
            }
            case ChangeOp.RenameIndex o -> {
                Table t = (Table) o.index().getSpannedClass();
                x.openChangeSet(settings.author(), "RenameIndex|" + o.oldName()
                        + "->" + o.index().getName());
                // no OSS change type — raw SQL both ways
                x.textElement("sql", "ALTER INDEX " + o.oldName() + " RENAME TO " + o.index().getName());
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.textElement("sql", "ALTER INDEX " + o.index().getName() + " RENAME TO " + o.oldName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.CreateView o -> {
                x.openChangeSet(settings.author(), "CreateView|" + qualified(o.view()));
                x.textElement("createView", Views.queryBody(o.view()).orElse(""),
                        "schemaName", schemaNameOf(o.view()), "viewName", o.view().getName(),
                        "replaceIfExists", "true");
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.emptyElement("dropView", "schemaName", schemaNameOf(o.view()),
                            "viewName", o.view().getName());
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.DropView o -> {
                x.openChangeSet(settings.author(), "DropView|" + qualified(o.view()));
                x.emptyElement("dropView", "schemaName", schemaNameOf(o.view()),
                        "viewName", o.view().getName());
                if (settings.includeRollback()) {
                    x.emptyRollback();
                }
                x.closeChangeSet();
            }
            case ChangeOp.ReplaceView o -> {
                x.openChangeSet(settings.author(), "ReplaceView|" + qualified(o.newView()));
                x.textElement("createView", Views.queryBody(o.newView()).orElse(""),
                        "schemaName", schemaNameOf(o.newView()), "viewName", o.newView().getName(),
                        "replaceIfExists", "true");
                if (settings.includeRollback()) {
                    x.startElement("rollback");
                    x.textElement("createView", Views.queryBody(o.oldView()).orElse(""),
                            "schemaName", schemaNameOf(o.oldView()), "viewName", o.oldView().getName(),
                            "replaceIfExists", "true");
                    x.endElement("rollback");
                }
                x.closeChangeSet();
            }
            case ChangeOp.CreateTrigger o -> throw new UnsupportedOperationException(
                    "trigger ops are not implemented in the changelog writer yet");
            case ChangeOp.DropTrigger o -> throw new UnsupportedOperationException(
                    "trigger ops are not implemented in the changelog writer yet");
        }
    }

    // big blocks

    private void createTable(LiquibaseXml x, ChangeOp.CreateTable o, ChangelogSettings settings) {
        Table t = o.table();
        x.openChangeSet(settings.author(), "CreateTable|" + qualified(t));
        x.startElement("createTable", "schemaName", schemaNameOf(t), "tableName", t.getName());
        List<Column> pkCols = pkColumns(t);
        boolean singlePk = pkCols.size() == 1;
        for (Object f : t.getFeature()) {
            if (f instanceof Column c) {
                column(x, c, singlePk && pkCols.contains(c));
            }
        }
        x.endElement("createTable");
        if (pkCols.size() > 1) {
            x.emptyElement("addPrimaryKey", "schemaName", schemaNameOf(t),
                    "tableName", t.getName(),
                    "columnNames", String.join(", ", pkCols.stream().map(Column::getName).toList()),
                    "constraintName", pkName(t));
        }
        if (settings.includeRollback()) {
            x.startElement("rollback");
            x.emptyElement("dropTable", "schemaName", schemaNameOf(t), "tableName", t.getName());
            x.endElement("rollback");
        }
        x.closeChangeSet();
    }

    private void column(LiquibaseXml x, Column c, boolean inlinePk) {
        String defaultValue = LiquibaseXml.defaultValueOf(c);
        List<String> attrs = new ArrayList<>(List.of("name", c.getName(), "type", liquibaseType(c)));
        if (defaultValue != null && !defaultValue.isBlank()) {
            String[] attr = defaultAttribute(defaultValue);
            attrs.add(attr[0]);
            attrs.add(attr[1]);
        }
        boolean needsConstraints = inlinePk || notNull(c);
        if (!needsConstraints) {
            x.emptyElement("column", attrs.toArray(String[]::new));
            return;
        }
        x.startElement("column", attrs.toArray(String[]::new));
        x.emptyElement("constraints",
                "primaryKey", inlinePk ? "true" : null,
                "nullable", notNull(c) ? "false" : null);
        x.endElement("column");
    }

    private void addForeignKey(LiquibaseXml x, ChangeOp.AddForeignKey o, ChangelogSettings settings) {
        ForeignKey fk = o.foreignKey();
        Table target = ForeignKeys.targetTable(fk).orElse(null);
        if (target == null || fk.getUniqueKey() == null) {
            return;
        }
        x.openChangeSet(settings.author(), "AddForeignKey|" + qualified(o.table())
                + "|" + fk.getName());
        x.emptyElement("addForeignKeyConstraint",
                "constraintName", fk.getName(),
                "baseTableSchemaName", schemaNameOf(o.table()),
                "baseTableName", o.table().getName(),
                "baseColumnNames", String.join(", ",
                        ForeignKeys.columns(fk).stream().map(Column::getName).toList()),
                "referencedTableSchemaName", schemaNameOf(target),
                "referencedTableName", target.getName(),
                "referencedColumnNames", featureCsv(fk.getUniqueKey().getFeature()),
                "onDelete", referentialAction(fk.getDeleteRule() == null ? null : fk.getDeleteRule().getName()),
                "onUpdate", referentialAction(fk.getUpdateRule() == null ? null : fk.getUpdateRule().getName()));
        if (settings.includeRollback()) {
            x.startElement("rollback");
            x.emptyElement("dropForeignKeyConstraint",
                    "baseTableSchemaName", schemaNameOf(o.table()),
                    "baseTableName", o.table().getName(),
                    "constraintName", fk.getName());
            x.endElement("rollback");
        }
        x.closeChangeSet();
    }

    /**
     * CHECK constraints have no OSS change type — {@code <sql dbms="…">} per
     * dialect, identical texts folded into one element.
     */
    private void checkConstraintSql(LiquibaseXml x, Table table, String name, String body,
            boolean add, ChangelogSettings settings) {
        if (add && (body == null || body.isBlank())) {
            return;
        }
        x.openChangeSet(settings.author(),
                (add ? "AddCheckConstraint|" : "DropCheckConstraint|") + qualified(table) + "|" + name);
        Map<String, List<String>> byText = new LinkedHashMap<>();
        for (Dialects.NamedDialect nd : Dialects.defaults()) {
            String dbms = dbmsOf(nd.name());
            if (dbms == null) {
                continue;
            }
            String sql = add
                    ? nd.dialect().ddlGenerator().addCheckConstraint(tableRef(table), name, body)
                    : nd.dialect().ddlGenerator().dropConstraint(tableRef(table), name, true);
            byText.computeIfAbsent(sql, k -> new ArrayList<>()).add(dbms);
        }
        for (Map.Entry<String, List<String>> e : byText.entrySet()) {
            x.textElement("sql", e.getKey(), "dbms", String.join(",", e.getValue()));
        }
        if (settings.includeRollback()) {
            if (add) {
                x.startElement("rollback");
                x.textElement("sql", "ALTER TABLE " + qualified(table) + " DROP CONSTRAINT " + name);
                x.endElement("rollback");
            } else {
                x.emptyRollback();
            }
        }
        x.closeChangeSet();
    }

    // misc

    private static TableReference tableRef(Table t) {
        String schema = schemaNameOf(t);
        return new TableReference(
                schema == null ? java.util.Optional.empty()
                        : java.util.Optional.of(new SchemaReference(schema)),
                t.getName(), TableReference.TYPE_TABLE);
    }

    private static String dbmsOf(String dialectName) {
        return switch (dialectName) {
            case "PostgreSQL" -> "postgresql";
            case "MySQL" -> "mysql";
            case "MariaDB" -> "mariadb";
            case "Oracle" -> "oracle";
            case "SQL Server" -> "mssql";
            case "H2" -> "h2";
            default -> null; // ANSI has no dbms id — it is everyone's fallback anyway
        };
    }

    private static String referentialAction(String literal) {
        if (literal == null) {
            return null;
        }
        String upper = literal.toUpperCase();
        if (upper.contains("CASCADE")) return "CASCADE";
        if (upper.contains("SET_NULL") || upper.contains("SETNULL")) return "SET NULL";
        if (upper.contains("SET_DEFAULT") || upper.contains("SETDEFAULT")) return "SET DEFAULT";
        if (upper.contains("RESTRICT")) return "RESTRICT";
        if (upper.contains("NO_ACTION") || upper.contains("NOACTION")) return "NO ACTION";
        return null;
    }

    private static String qualified(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs) {
        String schema = schemaNameOf(ncs);
        return schema == null ? ncs.getName() : schema + "." + ncs.getName();
    }

    private static List<Column> pkColumns(Table t) {
        return t.getOwnedElement().stream()
                .filter(org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey.class::isInstance)
                .map(org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey.class::cast)
                .findFirst()
                .map(pk -> pk.getFeature().stream()
                        .filter(Column.class::isInstance).map(Column.class::cast).toList())
                .orElse(List.of());
    }

    private static String pkName(Table t) {
        return t.getOwnedElement().stream()
                .filter(org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey.class::isInstance)
                .map(org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey.class::cast)
                .findFirst()
                .map(pk -> pk.getName() == null || pk.getName().isBlank()
                        ? "pk_" + t.getName() : pk.getName())
                .orElse("pk_" + t.getName());
    }

    private static String featureCsv(List<? extends StructuralFeature> features) {
        return String.join(", ", features.stream().map(StructuralFeature::getName).toList());
    }
}
