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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.util.resource.relational.Tables;
import org.eclipse.daanse.jdbc.db.api.meta.IndexInfo;
import org.eclipse.daanse.jdbc.db.api.meta.IndexInfoItem;
import org.eclipse.daanse.jdbc.db.api.meta.MetaInfo;
import org.eclipse.daanse.jdbc.db.api.meta.StructureInfo;
import org.eclipse.daanse.jdbc.db.api.schema.CheckConstraint;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnMetaData;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnReference;
import org.eclipse.daanse.jdbc.db.api.schema.ImportedKey;
import org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey;
import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.api.schema.UniqueConstraint;
import org.eclipse.daanse.jdbc.db.api.schema.ViewDefinition;
import org.osgi.service.component.annotations.Component;

/**
 * Builds a CWM relational {@link Catalog} from a {@link MetaInfo} snapshot
 * produced by {@code DatabaseService.createMetaInfo}. Pass a {@code Dialect} as
 * the {@code MetadataProvider} so the snapshot includes UNIQUE/CHECK
 * constraints, indexes and triggers.
 *
 * <p>
 * Inverse of {@code org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator}
 * — together they enable a JDBC → CWM → SQL → JDBC round trip.
 */
@Component(service = CwmLoader.class)
public final class CwmLoaderImpl implements CwmLoader {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;

    @Override
    public Catalog load(MetaInfo info, JdbcToCwmConfig config) {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }
        if (config == null) {
            config = JdbcToCwmConfig.all();
        }
        StructureInfo si = info.structureInfo();

        Catalog catalog = RF.createCatalog();
        catalog.setName(pickCatalogName(info, config));

        Map<String, Schema> schemasByName = collectSchemas(catalog, si, config);
        Map<String, NamedColumnSet> tableByFqn = collectTablesAndViews(si, config, schemasByName);

        attachViewBodies(si, config, tableByFqn);

        Map<String, Map<String, Column>> columnsByTable = attachColumns(si, tableByFqn);

        attachPrimaryKeys(si, tableByFqn, columnsByTable);

        if (config.includeUniqueConstraints()) {
            attachUniqueConstraints(si, tableByFqn, columnsByTable);
        }
        if (config.includeCheckConstraints()) {
            attachCheckConstraints(si, tableByFqn);
        }
        if (config.includeForeignKeys()) {
            attachForeignKeys(si, tableByFqn, columnsByTable);
        }
        if (config.includeIndexes()) {
            attachIndexes(info, schemasByName, tableByFqn, columnsByTable);
        }
        if (config.includeTriggers()) {
            attachTriggers(si, tableByFqn);
        }

        return catalog;
    }

    private static String pickCatalogName(MetaInfo info, JdbcToCwmConfig config) {
        if (config.catalogName() != null && !config.catalogName().isBlank()) {
            return config.catalogName();
        }
        if (info.databaseInfo() != null && info.databaseInfo().databaseProductName() != null) {
            return info.databaseInfo().databaseProductName();
        }
        return "DEFAULT";
    }

    private static Map<String, Schema> collectSchemas(Catalog catalog, StructureInfo si, JdbcToCwmConfig config) {
        Map<String, Schema> out = new LinkedHashMap<>();
        for (SchemaReference sr : si.schemas()) {
            if (!isSchemaAccepted(sr.name(), config))
                continue;
            out.computeIfAbsent(sr.name(), n -> ownedSchema(catalog, n));
        }
        // Drivers that don't list every schema separately — fall back to schemas
        // mentioned by tables.
        for (TableDefinition td : si.tables()) {
            String sname = td.table().schema().map(SchemaReference::name).orElse(null);
            if (sname == null)
                continue;
            if (!isSchemaAccepted(sname, config))
                continue;
            out.computeIfAbsent(sname, n -> ownedSchema(catalog, n));
        }
        return out;
    }

    private static Schema ownedSchema(Catalog catalog, String name) {
        Schema s = RF.createSchema();
        s.setName(name);
        catalog.getOwnedElement().add(s);
        return s;
    }

    private static boolean isSchemaAccepted(String schemaName, JdbcToCwmConfig config) {
        return config.schemaFilter().isEmpty() || config.schemaFilter().contains(schemaName);
    }

    private static Map<String, NamedColumnSet> collectTablesAndViews(StructureInfo si, JdbcToCwmConfig config,
            Map<String, Schema> schemasByName) {
        Map<String, NamedColumnSet> out = new LinkedHashMap<>();
        for (TableDefinition td : si.tables()) {
            TableReference tr = td.table();
            // Some MetadataProviders (PostgreSQL) report indexes and constraints
            // alongside actual relations — filter to the table/view kinds we map.
            String type = tr.type();
            boolean isView = TableReference.TYPE_VIEW.equals(type);
            // Accept the SQL-standard INFORMATION_SCHEMA type name "BASE TABLE"
            // (H2, and other standards-compliant drivers) alongside "TABLE".
            boolean isTable = TableReference.TYPE_TABLE.equals(type) || "BASE TABLE".equals(type);
            if (!isTable && !isView)
                continue;
            String sname = tr.schema().map(SchemaReference::name).orElse(null);
            Schema cwmSchema = schemasByName.get(sname);
            if (cwmSchema == null)
                continue;
            if (!config.tableFilter().test(sname, tr.name()))
                continue;
            if (isView && !config.includeViews())
                continue;

            NamedColumnSet ncs;
            if (isView) {
                View v = RF.createView();
                v.setName(tr.name());
                ncs = v;
            } else {
                Table t = RF.createTable();
                t.setName(tr.name());
                ncs = t;
            }
            cwmSchema.getOwnedElement().add(ncs);
            out.put(fqn(sname, tr.name()), ncs);
        }
        return out;
    }

    private static void attachViewBodies(StructureInfo si, JdbcToCwmConfig config,
            Map<String, NamedColumnSet> tableByFqn) {
        if (!config.includeViews())
            return;
        for (ViewDefinition vd : si.viewDefinitions()) {
            String sname = vd.view().schema().map(SchemaReference::name).orElse(null);
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, vd.view().name()));
            if (!(ncs instanceof View view))
                continue;
            vd.viewBody().ifPresent(body -> {
                QueryExpression qe = DF.createQueryExpression();
                qe.setLanguage("SQL");
                qe.setBody(body);
                view.setQueryExpression(qe);
            });
        }
    }

    private static Map<String, Map<String, Column>> attachColumns(StructureInfo si,
            Map<String, NamedColumnSet> tableByFqn) {
        Map<String, Map<String, Column>> out = new LinkedHashMap<>();
        for (ColumnDefinition cd : si.columns()) {
            ColumnReference cr = cd.column();
            if (cr.table().isEmpty())
                continue;
            TableReference table = cr.table().get();
            String sname = table.schema().map(SchemaReference::name).orElse(null);
            String tname = table.name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (ncs == null)
                continue;

            Column col = RF.createColumn();
            col.setName(cr.name());
            ColumnMetaData md = cd.columnMetaData();
            col.setIsNullable(toCwmNullability(md.nullability()));
            col.setType(SqlSimpleTypes.toCwmType(md.typeName(), md.dataType(), md.columnSize(), md.decimalDigits()));
            md.columnSize().ifPresent(s -> col.setLength(s));
            md.decimalDigits().ifPresent(d -> col.setScale(d));
            md.columnDefault().filter(d -> !d.isBlank()).ifPresent(d -> {
                Expression init = CF.createExpression();
                init.setLanguage("SQL");
                init.setBody(d);
                col.setInitialValue(init);
            });
            ncs.getFeature().add(col);
            out.computeIfAbsent(fqn(sname, tname), k -> new LinkedHashMap<>()).put(cr.name(), col);
        }
        return out;
    }

    private static void attachPrimaryKeys(StructureInfo si, Map<String, NamedColumnSet> tableByFqn,
            Map<String, Map<String, Column>> columnsByTable) {
        for (PrimaryKey pk : si.primaryKeys()) {
            String sname = pk.table().schema().map(SchemaReference::name).orElse(null);
            String tname = pk.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;
            Map<String, Column> cmap = columnsByTable.getOrDefault(fqn(sname, tname), Map.of());

            org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey cwmPk = RF.createPrimaryKey();
            cwmPk.setName(pk.constraintName().orElse("pk_" + tname));
            for (ColumnReference cr : pk.columns()) {
                Column c = cmap.get(cr.name());
                if (c == null)
                    continue;
                cwmPk.getFeature().add(c);
                c.getUniqueKey().add(cwmPk);
            }
            cwmTable.getOwnedElement().add(cwmPk);
        }
    }

    private static void attachUniqueConstraints(StructureInfo si, Map<String, NamedColumnSet> tableByFqn,
            Map<String, Map<String, Column>> columnsByTable) {
        for (UniqueConstraint uc : si.uniqueConstraints()) {
            String sname = uc.table().schema().map(SchemaReference::name).orElse(null);
            String tname = uc.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;
            Map<String, Column> cmap = columnsByTable.getOrDefault(fqn(sname, tname), Map.of());

            // Skip when every listed column is already in the PK — that
            // "UNIQUE" entry is just the underlying index of the PK.
            if (Tables.findPrimaryKey(cwmTable)
                    .map(pk -> uc.columns().stream()
                            .allMatch(cr -> pk.getFeature().stream().anyMatch(f -> cr.name().equals(f.getName()))))
                    .orElse(false)) {
                continue;
            }

            org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint cwmUc = RF.createUniqueConstraint();
            cwmUc.setName(uc.name() == null || uc.name().isBlank() ? "uc_" + tname : uc.name());
            for (ColumnReference cr : uc.columns()) {
                Column c = cmap.get(cr.name());
                if (c == null)
                    continue;
                cwmUc.getFeature().add(c);
                c.getUniqueKey().add(cwmUc);
            }
            if (cwmUc.getFeature().isEmpty())
                continue;
            cwmTable.getOwnedElement().add(cwmUc);
        }
    }

    private static void attachCheckConstraints(StructureInfo si, Map<String, NamedColumnSet> tableByFqn) {
        for (CheckConstraint cc : si.checkConstraints()) {
            String sname = cc.table().schema().map(SchemaReference::name).orElse(null);
            String tname = cc.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;

            org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint cwmCc = RF.createCheckConstraint();
            cwmCc.setName(cc.name() == null || cc.name().isBlank() ? "ck_" + tname : cc.name());
            BooleanExpression be = CF.createBooleanExpression();
            be.setLanguage("SQL");
            be.setBody(unwrapCheckBody(cc.checkClause()));
            cwmCc.setBody(be);
            cwmTable.getOwnedElement().add(cwmCc);
        }
    }

    /**
     * Strip the {@code CHECK (...)} wrapper that some providers (notably PG's
     * {@code pg_get_constraintdef}) return, leaving just the boolean expression.
     */
    private static String unwrapCheckBody(String raw) {
        if (raw == null)
            return "";
        String s = raw.strip();
        if (s.regionMatches(true, 0, "CHECK", 0, 5)) {
            s = s.substring(5).stripLeading();
            if (s.startsWith("(") && s.endsWith(")")) {
                String inner = s.substring(1, s.length() - 1).strip();
                if (isBalanced(inner)) {
                    return inner;
                }
            }
        }
        return s;
    }

    private static boolean isBalanced(String s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'')
                inString = !inString;
            if (inString)
                continue;
            if (c == '(')
                depth++;
            else if (c == ')') {
                depth--;
                if (depth < 0)
                    return false;
            }
        }
        return depth == 0;
    }

    private static void attachForeignKeys(StructureInfo si, Map<String, NamedColumnSet> tableByFqn,
            Map<String, Map<String, Column>> columnsByTable) {
        // Group by (schema, table, fk-name) so multi-column FKs become one CWM
        // ForeignKey.
        Map<String, List<ImportedKey>> groups = new LinkedHashMap<>();
        int anonCounter = 0;
        for (ImportedKey ik : si.importedKeys()) {
            if (ik.foreignKeyColumn().table().isEmpty())
                continue;
            TableReference tr = ik.foreignKeyColumn().table().get();
            String sname = tr.schema().map(SchemaReference::name).orElse(null);
            String fkName = ik.name() == null || ik.name().isBlank() ? "fk_" + tr.name() + "_anon_" + (anonCounter++)
                    : ik.name();
            String key = sname + "." + tr.name() + "#" + fkName;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(ik);
        }

        for (List<ImportedKey> group : groups.values()) {
            group.sort(Comparator.comparingInt(ImportedKey::keySequence));
            ImportedKey first = group.get(0);

            TableReference fkTable = first.foreignKeyColumn().table().get();
            String fkSchemaName = fkTable.schema().map(SchemaReference::name).orElse(null);
            NamedColumnSet srcNcs = tableByFqn.get(fqn(fkSchemaName, fkTable.name()));
            if (!(srcNcs instanceof Table srcTable))
                continue;

            if (first.primaryKeyColumn().table().isEmpty())
                continue;
            TableReference pkTable = first.primaryKeyColumn().table().get();
            String pkSchemaName = pkTable.schema().map(SchemaReference::name).orElse(null);
            NamedColumnSet dstNcs = tableByFqn.get(fqn(pkSchemaName, pkTable.name()));
            if (!(dstNcs instanceof Table dstTable))
                continue;

            UniqueKey targetUk = findTargetUniqueKey(dstTable, group);
            if (targetUk == null)
                continue;

            ForeignKey cwmFk = RF.createForeignKey();
            String fkName = first.name();
            if (fkName == null || fkName.isBlank())
                fkName = "fk_" + fkTable.name();
            cwmFk.setName(fkName);
            cwmFk.setUniqueKey(targetUk);
            cwmFk.setDeleteRule(mapReferentialAction(first.deleteRule()));
            cwmFk.setUpdateRule(mapReferentialAction(first.updateRule()));

            Map<String, Column> srcCols = columnsByTable.getOrDefault(fqn(fkSchemaName, fkTable.name()), Map.of());
            for (ImportedKey k : group) {
                Column fc = srcCols.get(k.foreignKeyColumn().name());
                if (fc == null)
                    continue;
                cwmFk.getFeature().add(fc);
                fc.getKeyRelationship().add(cwmFk);
            }
            srcTable.getOwnedElement().add(cwmFk);
        }
    }

    private static UniqueKey findTargetUniqueKey(Table dstTable, List<ImportedKey> group) {
        // Prefer the PK when its columns equal the referenced set; otherwise a
        // matching unique constraint; otherwise fall back to the PK.
        List<String> refCols = new ArrayList<>();
        for (ImportedKey k : group)
            refCols.add(k.primaryKeyColumn().name());

        Optional<org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey> pk = Tables.findPrimaryKey(dstTable);
        if (pk.isPresent() && featureNames(pk.get()).equals(refCols)) {
            return pk.get();
        }
        for (org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint uc : Tables
                .findUniqueConstraints(dstTable)) {
            if (featureNames(uc).equals(refCols))
                return uc;
        }
        return pk.orElse(null);
    }

    private static List<String> featureNames(UniqueKey uk) {
        List<String> out = new ArrayList<>();
        for (org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature f : uk.getFeature()) {
            out.add(f.getName());
        }
        return out;
    }

    private static void attachIndexes(MetaInfo info, Map<String, Schema> schemasByName,
            Map<String, NamedColumnSet> tableByFqn, Map<String, Map<String, Column>> columnsByTable) {
        for (IndexInfo ii : info.indexInfos()) {
            TableReference tr = ii.tableReference();
            String sname = tr.schema().map(SchemaReference::name).orElse(null);
            String tname = tr.name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;
            Schema cwmSchema = schemasByName.get(sname);
            if (cwmSchema == null)
                continue;
            Map<String, Column> cmap = columnsByTable.getOrDefault(fqn(sname, tname), Map.of());

            Map<String, List<IndexInfoItem>> byIndex = new LinkedHashMap<>();
            for (IndexInfoItem item : ii.indexInfoItems()) {
                if (item.type() == IndexInfoItem.IndexType.TABLE_INDEX_STATISTIC)
                    continue;
                String iname = item.indexName().orElse("");
                if (iname.isBlank())
                    continue;
                byIndex.computeIfAbsent(iname, k -> new ArrayList<>()).add(item);
            }
            for (Map.Entry<String, List<IndexInfoItem>> entry : byIndex.entrySet()) {
                if (matchesPrimaryKey(cwmTable, entry.getValue()))
                    continue;

                SQLIndex idx = RF.createSQLIndex();
                idx.setName(entry.getKey());
                idx.setSpannedClass(cwmTable);
                idx.setIsUnique(entry.getValue().get(0).unique());
                entry.getValue().sort(Comparator.comparingInt(IndexInfoItem::ordinalPosition));
                for (IndexInfoItem item : entry.getValue()) {
                    if (item.column().isEmpty())
                        continue;
                    Column col = cmap.get(item.column().get().name());
                    if (col == null)
                        continue;
                    SQLIndexColumn ic = RF.createSQLIndexColumn();
                    ic.setFeature(col);
                    idx.getIndexedFeature().add(ic);
                }
                if (!idx.getIndexedFeature().isEmpty()) {
                    cwmSchema.getOwnedElement().add(idx);
                }
            }
        }
    }

    private static void attachTriggers(StructureInfo si, Map<String, NamedColumnSet> tableByFqn) {
        for (org.eclipse.daanse.jdbc.db.api.schema.Trigger trg : si.triggers()) {
            String sname = trg.table().schema().map(SchemaReference::name).orElse(null);
            String tname = trg.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;

            org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger cwmTrg = RF.createTrigger();
            cwmTrg.setName(trg.name());
            cwmTrg.setTable(cwmTable);
            cwmTrg.setConditionTiming(mapTiming(trg.timing()));
            cwmTrg.setEventManipulation(mapEvent(trg.event()));
            cwmTrg.setActionOrientation(mapOrientation(trg.orientation().orElse(null)));

            // Prefer the procedural source (PG: pg_proc.prosrc); fall back to the
            // full CREATE TRIGGER definition when the provider can't separate them.
            String body = trg.body().orElse(trg.fullDefinition().orElse(null));
            if (body != null && !body.isBlank()) {
                ProcedureExpression action = CF.createProcedureExpression();
                action.setLanguage("SQL");
                action.setBody(body);
                cwmTrg.setActionStatement(action);
            }

            cwmTable.getTrigger().add(cwmTrg);
        }
    }

    private static ConditionTimingType mapTiming(org.eclipse.daanse.jdbc.db.api.schema.Trigger.TriggerTiming t) {
        if (t == null)
            return null;
        return switch (t) {
        case BEFORE -> ConditionTimingType.BEFORE;
        case AFTER -> ConditionTimingType.AFTER;
        // CWM 1.1's relational metamodel only has BEFORE/AFTER —
        // INSTEAD OF maps to BEFORE as the closest match.
        case INSTEAD_OF -> ConditionTimingType.BEFORE;
        };
    }

    private static EventManipulationType mapEvent(org.eclipse.daanse.jdbc.db.api.schema.Trigger.TriggerEvent e) {
        if (e == null)
            return null;
        return switch (e) {
        case INSERT -> EventManipulationType.INSERT;
        case UPDATE -> EventManipulationType.UPDATE;
        case DELETE -> EventManipulationType.DELETE;
        };
    }

    private static ActionOrientationType mapOrientation(String s) {
        if (s == null)
            return ActionOrientationType.STATEMENT;
        return switch (s.toUpperCase()) {
        case "ROW" -> ActionOrientationType.ROW;
        default -> ActionOrientationType.STATEMENT;
        };
    }

    private static boolean matchesPrimaryKey(Table table, List<IndexInfoItem> items) {
        Optional<org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey> pkOpt = Tables.findPrimaryKey(table);
        if (pkOpt.isEmpty())
            return false;
        List<String> pkCols = featureNames(pkOpt.get());
        List<IndexInfoItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(IndexInfoItem::ordinalPosition));
        if (sorted.size() != pkCols.size())
            return false;
        for (int i = 0; i < sorted.size(); i++) {
            String name = sorted.get(i).column().map(ColumnReference::name).orElse(null);
            if (!pkCols.get(i).equals(name))
                return false;
        }
        return true;
    }

    private static ReferentialRuleType mapReferentialAction(ImportedKey.ReferentialAction a) {
        if (a == null)
            return null;
        return switch (a) {
        case CASCADE -> ReferentialRuleType.IMPORTED_KEY_CASCADE;
        case NO_ACTION -> ReferentialRuleType.IMPORTED_KEY_NO_ACTION;
        case SET_NULL -> ReferentialRuleType.IMPORTED_KEY_SET_NULL;
        case SET_DEFAULT -> ReferentialRuleType.IMPORTED_KEY_SET_DEFAULT;
        case RESTRICT -> ReferentialRuleType.IMPORTED_KEY_RESTRICT;
        };
    }

    /** Map a jdbc.db {@link ColumnMetaData.Nullability} to a CWM {@link NullableType}. */
    private static NullableType toCwmNullability(ColumnMetaData.Nullability n) {
        if (n == null) {
            return NullableType.COLUMN_NULLABLE_UNKNOWN;
        }
        return switch (n) {
        case NO_NULLS -> NullableType.COLUMN_NO_NULLS;
        case NULLABLE -> NullableType.COLUMN_NULLABLE;
        case UNKNOWN -> NullableType.COLUMN_NULLABLE_UNKNOWN;
        };
    }

    private static String fqn(String schema, String table) {
        return (schema == null ? "" : schema) + "." + table;
    }
}
