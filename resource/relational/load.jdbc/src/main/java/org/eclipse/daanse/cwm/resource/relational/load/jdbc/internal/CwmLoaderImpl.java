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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
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
        if (config.includeMaterializedViews()) {
            attachMaterializedViews(si, config, schemasByName, tableByFqn);
        }

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
        if (config.includeProcedures()) {
            attachProcedures(si, config, schemasByName);
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
            boolean isSystem = TableReference.TYPE_SYSTEM_TABLE.equals(type);
            boolean isGlobalTemporary = TableReference.TYPE_GLOBAL_TEMPORARY.equals(type);
            boolean isLocalTemporary = TableReference.TYPE_LOCAL_TEMPORARY.equals(type);
            // Accept the SQL-standard INFORMATION_SCHEMA type name "BASE TABLE"
            // (H2, and other standards-compliant drivers) alongside "TABLE".
            boolean isTable = TableReference.TYPE_TABLE.equals(type) || "BASE TABLE".equals(type) || isSystem
                    || isGlobalTemporary || isLocalTemporary;
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
                t.setIsSystem(isSystem);
                if (isGlobalTemporary || isLocalTemporary) {
                    t.setIsTemporary(true);
                    t.setTemporaryScope(isGlobalTemporary ? "GLOBAL" : "LOCAL");
                }
                ncs = t;
            }
            cwmSchema.getOwnedElement().add(ncs);
            td.tableMetaData().remarks().filter(r -> !r.isBlank())
                    .ifPresent(r -> attachJdbcRemarks(ncs, ncs, r));
            out.put(fqn(sname, tr.name()), ncs);
        }
        return out;
    }

    /**
     * Creates a JDBC-REMARKS {@link Description} attached to {@code described},
     * owned by {@code owner}. Containment only matters for serialization —
     * lookup goes through the element's own {@code description} reference (see
     * {@link Descriptions#find}).
     */
    private static void attachJdbcRemarks(Namespace owner, ModelElement described, String remarks) {
        Description description = BusinessinformationFactory.eINSTANCE.createDescription();
        description.setType(CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS);
        description.setBody(remarks);
        description.getModelElement().add(described);
        owner.getOwnedElement().add(description);
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
            vd.viewBody().ifPresent(body -> view.setQueryExpression(sqlQuery(body)));
        }
    }

    private static QueryExpression sqlQuery(String body) {
        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody(body);
        return qe;
    }

    /**
     * Materialized views have no CWM 1.1 class of their own — they map to
     * {@link View} with {@code isReadOnly=true} plus the marker tag
     * {@link org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader#TAG_MATERIALIZED}.
     * Providers that also report the mview's backing container as a table
     * (Oracle) get the tag attached to that existing element instead.
     */
    private static void attachMaterializedViews(StructureInfo si, JdbcToCwmConfig config,
            Map<String, Schema> schemasByName, Map<String, NamedColumnSet> tableByFqn) {
        for (MaterializedView mv : si.materializedViews()) {
            String sname = mv.view().schema().map(SchemaReference::name).orElse(null);
            String name = mv.view().name();
            Schema cwmSchema = schemasByName.get(sname);
            if (cwmSchema == null)
                continue;
            if (!config.tableFilter().test(sname, name))
                continue;

            NamedColumnSet ncs = tableByFqn.get(fqn(sname, name));
            if (ncs == null) {
                View v = RF.createView();
                v.setName(name);
                cwmSchema.getOwnedElement().add(v);
                tableByFqn.put(fqn(sname, name), v);
                ncs = v;
            }
            if (ncs instanceof View view) {
                view.setIsReadOnly(true);
                if (view.getQueryExpression() == null) {
                    mv.viewBody().ifPresent(body -> view.setQueryExpression(sqlQuery(body)));
                }
            }
            if (ncs.getTaggedValue().stream().noneMatch(tv -> CwmLoader.TAG_MATERIALIZED.equals(tv.getTag()))) {
                TaggedValue tag = CF.createTaggedValue();
                tag.setTag(CwmLoader.TAG_MATERIALIZED);
                tag.setValue("true");
                ncs.getTaggedValue().add(tag);
            }
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
            col.setType(SQLSimpleTypes.toCwmType(md.typeName(), md.dataType(), md.columnSize(), md.decimalDigits()));
            md.columnSize().ifPresent(s -> col.setLength(s));
            md.decimalDigits().ifPresent(d -> col.setScale(d));
            md.columnDefault().filter(d -> !d.isBlank()).ifPresent(d -> {
                Expression init = CF.createExpression();
                init.setLanguage("SQL");
                init.setBody(d);
                col.setInitialValue(init);
            });
            md.remarks().filter(r -> !r.isBlank()).ifPresent(r -> attachJdbcRemarks(ncs, col, r));
            ncs.getFeature().add(col);
            out.computeIfAbsent(fqn(sname, tname), k -> new LinkedHashMap<>()).put(cr.name(), col);
        }
        return out;
    }

    private static void attachPrimaryKeys(StructureInfo si, Map<String, NamedColumnSet> tableByFqn,
            Map<String, Map<String, Column>> columnsByTable) {
        for (org.eclipse.daanse.sql.model.schema.PrimaryKey pk : si.primaryKeys()) {
            String sname = pk.table().schema().map(SchemaReference::name).orElse(null);
            String tname = pk.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;
            Map<String, Column> cmap = columnsByTable.getOrDefault(fqn(sname, tname), Map.of());

            PrimaryKey cwmPk = RF.createPrimaryKey();
            cwmPk.setName(pk.constraintName().orElse("pk_" + tname));
            for (ColumnReference cr : pk.columns()) {
                Column c = cmap.get(cr.name());
                if (c == null)
                    continue;
                cwmPk.getFeature().add(c);
            }
            cwmTable.getOwnedElement().add(cwmPk);
        }
    }

    private static void attachUniqueConstraints(StructureInfo si, Map<String, NamedColumnSet> tableByFqn,
            Map<String, Map<String, Column>> columnsByTable) {
        for (org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint uc : si.uniqueConstraints()) {
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

            UniqueConstraint cwmUc = RF.createUniqueConstraint();
            cwmUc.setName(uc.name() == null || uc.name().isBlank() ? "uc_" + tname : uc.name());
            for (ColumnReference cr : uc.columns()) {
                Column c = cmap.get(cr.name());
                if (c == null)
                    continue;
                cwmUc.getFeature().add(c);
            }
            if (cwmUc.getFeature().isEmpty())
                continue;
            cwmTable.getOwnedElement().add(cwmUc);
        }
    }

    private static void attachCheckConstraints(StructureInfo si, Map<String, NamedColumnSet> tableByFqn) {
        for (org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint cc : si.checkConstraints()) {
            String sname = cc.table().schema().map(SchemaReference::name).orElse(null);
            String tname = cc.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;

            CheckConstraint cwmCc = RF.createCheckConstraint();
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
     * Package-private for testing.
     */
    static String unwrapCheckBody(String raw) {
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

    /**
     * Paren balance check that ignores parentheses inside SQL string literals,
     * including SQL-escaped quotes ({@code ''}). Package-private for testing.
     */
    static boolean isBalanced(String s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                if (inString && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    i++; // SQL-escaped quote inside a literal — stay in string
                    continue;
                }
                inString = !inString;
                continue;
            }
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
            if (targetUk == null) {
                targetUk = synthesizeTargetUniqueKey(dstTable, group,
                        columnsByTable.getOrDefault(fqn(pkSchemaName, pkTable.name()), Map.of()));
            }
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
            cwmFk.setDeferrability(mapDeferrability(first.deferrability()));

            Map<String, Column> srcCols = columnsByTable.getOrDefault(fqn(fkSchemaName, fkTable.name()), Map.of());
            for (ImportedKey k : group) {
                Column fc = srcCols.get(k.foreignKeyColumn().name());
                if (fc == null)
                    continue;
                cwmFk.getFeature().add(fc);
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

        Optional<PrimaryKey> pk = Tables.findPrimaryKey(dstTable);
        if (pk.isPresent() && featureNames(pk.get()).equals(refCols)) {
            return pk.get();
        }
        for (UniqueConstraint uc : Tables.uniqueConstraints(dstTable)) {
            if (featureNames(uc).equals(refCols))
                return uc;
        }
        return pk.orElse(null);
    }

    /**
     * A FK must reference a {@link UniqueKey}, but some providers report FKs
     * whose target constraint is not in the snapshot. Rather than dropping the
     * FK, materialize the implied unique constraint on the referenced columns.
     */
    private static UniqueKey synthesizeTargetUniqueKey(Table dstTable, List<ImportedKey> group,
            Map<String, Column> dstCols) {
        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.setName(group.get(0).primaryKeyName().filter(n -> !n.isBlank()).orElse("uc_" + dstTable.getName() + "_ref"));
        for (ImportedKey k : group) {
            Column c = dstCols.get(k.primaryKeyColumn().name());
            if (c == null)
                return null;
            uc.getFeature().add(c);
        }
        dstTable.getOwnedElement().add(uc);
        return uc;
    }

    private static List<String> featureNames(UniqueKey uk) {
        List<String> out = new ArrayList<>();
        for (StructuralFeature f : uk.getFeature()) {
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
                List<IndexInfoItem> items = entry.getValue();
                items.sort(Comparator.comparingInt(IndexInfoItem::ordinalPosition));
                boolean isUnique = items.get(0).unique();
                // Skip only the PK's own backing index: unique with the PK's
                // column sequence. A non-unique index deliberately created on
                // the PK columns is a real user index and is kept.
                if (isUnique && matchesPrimaryKey(cwmTable, items))
                    continue;

                SQLIndex idx = RF.createSQLIndex();
                idx.setName(entry.getKey());
                idx.setSpannedClass(cwmTable);
                idx.setIsUnique(isUnique);
                items.stream().map(IndexInfoItem::filterCondition).flatMap(Optional::stream)
                        .filter(f -> !f.isBlank()).findFirst().ifPresent(idx::setFilterCondition);
                for (IndexInfoItem item : items) {
                    if (item.column().isEmpty())
                        continue;
                    Column col = cmap.get(item.column().get().name());
                    if (col == null)
                        continue;
                    SQLIndexColumn ic = SQLIndexes.indexColumn(col);
                    item.ascending().ifPresent(ic::setIsAscending);
                    idx.getIndexedFeature().add(ic);
                }
                if (!idx.getIndexedFeature().isEmpty()) {
                    cwmSchema.getOwnedElement().add(idx);
                }
            }
        }
    }

    private static void attachTriggers(StructureInfo si, Map<String, NamedColumnSet> tableByFqn) {
        for (org.eclipse.daanse.sql.model.schema.Trigger trg : si.triggers()) {
            String sname = trg.table().schema().map(SchemaReference::name).orElse(null);
            String tname = trg.table().name();
            NamedColumnSet ncs = tableByFqn.get(fqn(sname, tname));
            if (!(ncs instanceof Table cwmTable))
                continue;

            // CWM 1.1 allows only one eventManipulation per Trigger — a
            // multi-event trigger (BEFORE INSERT OR UPDATE) becomes one CWM
            // Trigger per event, suffixed to keep names unique.
            List<org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent> events = trg.events();
            for (org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event : events) {
                Trigger cwmTrg = RF.createTrigger();
                cwmTrg.setName(events.size() > 1 ? trg.name() + "_" + event.name() : trg.name());
                cwmTrg.setTable(cwmTable);
                cwmTrg.setConditionTiming(mapTiming(trg.timing()));
                cwmTrg.setEventManipulation(mapEvent(event));
                cwmTrg.setActionOrientation(mapOrientation(trg.orientation().orElse(null)));

                trg.whenCondition().filter(w -> !w.isBlank()).ifPresent(w -> {
                    BooleanExpression cond = CF.createBooleanExpression();
                    cond.setLanguage("SQL");
                    cond.setBody(w);
                    cwmTrg.setActionCondition(cond);
                });

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
    }

    private static void attachProcedures(StructureInfo si, JdbcToCwmConfig config, Map<String, Schema> schemasByName) {
        for (org.eclipse.daanse.sql.jdbc.api.schema.Procedure proc : si.procedures()) {
            Schema cwmSchema = schemasByName.get(proc.reference().schema().map(SchemaReference::name).orElse(null));
            if (cwmSchema == null)
                continue;
            Procedure cwmProc = createProcedure(proc.reference().name(), ProcedureType.PROCEDURE,
                    proc.body().orElse(proc.fullDefinition().orElse(null)));
            for (ProcedureColumn col : proc.columns()) {
                mapProcedureParameter(col).ifPresent(cwmProc.getParameter()::add);
            }
            cwmSchema.getOwnedElement().add(cwmProc);
            proc.remarks().filter(r -> !r.isBlank()).ifPresent(r -> attachJdbcRemarks(cwmSchema, cwmProc, r));
        }
        for (org.eclipse.daanse.sql.jdbc.api.schema.Function fn : si.functions()) {
            Schema cwmSchema = schemasByName.get(fn.reference().schema().map(SchemaReference::name).orElse(null));
            if (cwmSchema == null)
                continue;
            Procedure cwmProc = createProcedure(fn.reference().name(), ProcedureType.FUNCTION,
                    fn.body().orElse(fn.fullDefinition().orElse(null)));
            for (FunctionColumn col : fn.columns()) {
                mapFunctionParameter(col).ifPresent(cwmProc.getParameter()::add);
            }
            cwmSchema.getOwnedElement().add(cwmProc);
            fn.remarks().filter(r -> !r.isBlank()).ifPresent(r -> attachJdbcRemarks(cwmSchema, cwmProc, r));
        }
    }

    private static Procedure createProcedure(String name, ProcedureType type, String body) {
        Procedure p = RF.createProcedure();
        p.setName(name);
        p.setType(type);
        if (body != null && !body.isBlank()) {
            ProcedureExpression pe = CF.createProcedureExpression();
            pe.setLanguage("SQL");
            pe.setBody(body);
            p.setBody(pe);
        }
        return p;
    }

    private static Optional<SQLParameter> mapProcedureParameter(ProcedureColumn col) {
        ParameterDirectionKind kind = switch (col.columnType()) {
        case IN -> ParameterDirectionKind.PDK_IN;
        case OUT -> ParameterDirectionKind.PDK_OUT;
        case INOUT -> ParameterDirectionKind.PDK_INOUT;
        case RETURN -> ParameterDirectionKind.PDK_RETURN;
        // RESULT columns describe the shape of a returned result set, not a
        // callable parameter; UNKNOWN is unusable.
        case RESULT, UNKNOWN -> null;
        };
        if (kind == null)
            return Optional.empty();
        SQLParameter sp = RF.createSQLParameter();
        sp.setName(col.name());
        sp.setKind(kind);
        sp.setType(SQLSimpleTypes.toCwmType(col.typeName(), col.dataType(), col.precision(), col.scale()));
        return Optional.of(sp);
    }

    private static Optional<SQLParameter> mapFunctionParameter(FunctionColumn col) {
        ParameterDirectionKind kind = switch (col.columnType()) {
        case IN -> ParameterDirectionKind.PDK_IN;
        case OUT -> ParameterDirectionKind.PDK_OUT;
        case INOUT -> ParameterDirectionKind.PDK_INOUT;
        case RETURN -> ParameterDirectionKind.PDK_RETURN;
        case RESULT, UNKNOWN -> null;
        };
        if (kind == null)
            return Optional.empty();
        SQLParameter sp = RF.createSQLParameter();
        sp.setName(col.name());
        sp.setKind(kind);
        sp.setType(SQLSimpleTypes.toCwmType(col.typeName(), col.dataType(), col.precision(), col.scale()));
        return Optional.of(sp);
    }

    private static ConditionTimingType mapTiming(org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming t) {
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

    private static EventManipulationType mapEvent(org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent e) {
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

    /** {@code items} must already be sorted by ordinal position. */
    private static boolean matchesPrimaryKey(Table table, List<IndexInfoItem> items) {
        Optional<PrimaryKey> pkOpt = Tables.findPrimaryKey(table);
        if (pkOpt.isEmpty())
            return false;
        List<String> pkCols = featureNames(pkOpt.get());
        if (items.size() != pkCols.size())
            return false;
        for (int i = 0; i < items.size(); i++) {
            String name = items.get(i).column().map(ColumnReference::name).orElse(null);
            if (!pkCols.get(i).equals(name))
                return false;
        }
        return true;
    }

    private static DeferrabilityType mapDeferrability(ImportedKey.Deferrability d) {
        if (d == null)
            return DeferrabilityType.NOT_DEFERRABLE;
        return switch (d) {
        case INITIALLY_DEFERRED -> DeferrabilityType.INITIALLY_DEFERRED;
        case INITIALLY_IMMEDIATE -> DeferrabilityType.INITIALLY_IMMEDIATE;
        case NOT_DEFERRABLE -> DeferrabilityType.NOT_DEFERRABLE;
        };
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
        // NUL marker for "no schema" — cannot collide with a real schema name
        // (unlike "", which an empty-named schema could produce).
        return (schema == null ? "\0" : schema) + "." + table;
    }
}
