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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.BusinessInformationRules;
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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection.Verdict;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CatalogPartLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.ObjectKey;
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
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * Builds a CWM relational {@link Catalog} from a {@link MetaInfo} snapshot
 * produced by {@code DatabaseService.createMetaInfo}. Pass a {@code Dialect} as
 * the {@code MetadataProvider} so the snapshot includes UNIQUE/CHECK
 * constraints, indexes and triggers.
 *
 * <p>
 * The loader builds each schema, table, view and routine first as a shell, in
 * its place, and asks the selection about it, carrying the verdict down from
 * the catalog: what is excluded is taken out again before anything below it is
 * read, and so is what no include rule reaches when no include rule could reach
 * anything below it either. A kind the selection excludes as a whole is not
 * read at all. Then the parts add theirs, and the catalog is cut to the
 * selection as a copy of it would be ({@link Selections#cut}); that settles
 * what the shells could not tell, such as a column or a key left out, and the
 * foreign keys and indexes that lose it.
 * </p>
 *
 * <p>
 * Inverse of {@code org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator}
 * — together they enable a JDBC → CWM → SQL → JDBC round trip.
 * </p>
 */
@Component(service = CwmLoader.class)
public final class CwmLoaderImpl implements CwmLoader {

    private static final Logger LOGGER = System.getLogger(CwmLoaderImpl.class.getName());
    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;
    private static final EClass DESCRIPTION = BusinessinformationPackage.Literals.DESCRIPTION;

    /** What the loader puts below a table or a view. */
    private static final List<EClass> IN_A_COLUMN_SET = List.of(RelationalPackage.Literals.COLUMN,
            RelationalPackage.Literals.PRIMARY_KEY, RelationalPackage.Literals.UNIQUE_CONSTRAINT,
            RelationalPackage.Literals.FOREIGN_KEY, RelationalPackage.Literals.CHECK_CONSTRAINT, DESCRIPTION);

    /** What the loader puts below a routine. */
    private static final List<EClass> IN_A_ROUTINE = List.of(RelationalPackage.Literals.SQL_PARAMETER);

    /** What the loader puts below a schema. */
    private static final List<EClass> IN_A_SCHEMA = concat(List.of(RelationalPackage.Literals.TABLE,
            RelationalPackage.Literals.VIEW, RelationalPackage.Literals.SQL_INDEX,
            RelationalPackage.Literals.SQL_INDEX_COLUMN, RelationalPackage.Literals.TRIGGER,
            RelationalPackage.Literals.PROCEDURE), IN_A_COLUMN_SET, IN_A_ROUTINE);

    private final List<CatalogPartLoader> parts;

    /** A loader of the relational structure alone. */
    public CwmLoaderImpl() {
        this(List.of());
    }

    /** A loader of the relational structure and of {@code parts}, in that order. */
    @Activate
    public CwmLoaderImpl(@Reference(cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY) List<CatalogPartLoader> parts) {
        this.parts = List.copyOf(parts);
    }

    @Override
    public Catalog load(MetaInfo info, JdbcToCwmConfig config) {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }
        JdbcToCwmConfig settings = config == null ? JdbcToCwmConfig.defaults() : config;
        Selection selection = settings.selection();

        Catalog catalog = RF.createCatalog();
        catalog.setName(pickCatalogName(info, settings));
        new Loading(info, settings, catalog).load();

        boolean hasRules = !selection.includes().isEmpty() || !selection.excludes().isEmpty();
        // rules on stereotypes and descriptions look up what refers to an element
        ECrossReferenceAdapter index = hasRules ? InverseReferences.install(catalog) : null;
        try {
            for (CatalogPartLoader part : parts) {
                part.load(info, catalog, selection);
            }
            if (hasRules) {
                Selections.cut(catalog, selection, consequences());
            }
        } finally {
            if (index != null) {
                catalog.eAdapters().remove(index);
            }
        }
        selection.requireIncludesMatch(catalog);
        return catalog;
    }

    /** Those of the parts, then those of the relational and the business information model. */
    private List<ConsequenceRule> consequences() {
        List<ConsequenceRule> ofParts = new ArrayList<>();
        parts.forEach(part -> ofParts.addAll(part.consequences()));
        return ConsequenceRule.combine(ofParts, RelationalRules.CONSEQUENCES, BusinessInformationRules.CONSEQUENCES);
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

    /** The state of one load: the snapshot, the selection and what is built so far. */
    private static final class Loading {

        private final StructureInfo si;
        private final MetaInfo info;
        private final Selection selection;
        private final String commentType;
        private final Catalog catalog;
        private final Verdict catalogVerdict;
        private final CatalogTypes types;
        private final boolean comments;

        /** The schemas kept, by name; {@code null} is the schema of a database without schemas. */
        private final Map<String, Judged<Schema>> schemas = new HashMap<>();
        private final Map<ObjectKey, NamedColumnSet> relations = new LinkedHashMap<>();
        private final Map<ObjectKey, Map<String, Column>> columns = new HashMap<>();
        /** The column lists of each table's primary and unique keys, as the database reports them. */
        private final Map<ObjectKey, List<List<String>>> keyColumns = new HashMap<>();
        /** The bodies of the routines, read when a trigger only calls its own procedure. */
        private Map<ObjectKey, String> routineBodies;

        Loading(MetaInfo info, JdbcToCwmConfig config, Catalog catalog) {
            this.info = info;
            this.si = info.structureInfo();
            this.selection = config.selection();
            this.commentType = config.commentType();
            this.catalog = catalog;
            this.catalogVerdict = selection.verdictOf(catalog);
            this.types = new CatalogTypes(catalog);
            this.comments = !selection.excludesKind(DESCRIPTION);
        }

        void load() {
            schemas();
            relations();
            if (!selection.excludesKind(RelationalPackage.Literals.COLUMN)) {
                columns();
            }
            keys();
            if (!selection.excludesKind(RelationalPackage.Literals.CHECK_CONSTRAINT)) {
                checkConstraints();
            }
            if (!selection.excludesKind(RelationalPackage.Literals.FOREIGN_KEY)) {
                foreignKeys();
            }
            if (!selection.excludesKind(RelationalPackage.Literals.SQL_INDEX)) {
                indexes();
            }
            if (!selection.excludesKind(RelationalPackage.Literals.TRIGGER)) {
                triggers();
            }
            if (!selection.excludesKind(RelationalPackage.Literals.PROCEDURE)) {
                procedures();
            }
            // what the database does not name is named as the DDL generator names it, so a model
            // written back and read again keeps its names
            RelationalNames.assign(catalog);
        }

        // --- schemas, tables, views: shells first, then asked -------------------------------

        /**
         * The schemas the database lists and those its tables, views and routines name; a
         * table without a schema goes into a schema without a name.
         */
        private void schemas() {
            Set<String> names = new LinkedHashSet<>();
            si.schemas().forEach(sr -> names.add(sr.name()));
            si.tables().forEach(td -> names.add(schemaName(td.table())));
            if (!selection.excludesKind(RelationalPackage.Literals.VIEW)) {
                si.materializedViews().forEach(mv -> names.add(schemaName(mv.view())));
            }
            if (!selection.excludesKind(RelationalPackage.Literals.PROCEDURE)) {
                si.procedures().forEach(p -> names.add(p.reference().schema().map(SchemaReference::name).orElse(null)));
                si.functions().forEach(f -> names.add(f.reference().schema().map(SchemaReference::name).orElse(null)));
            }
            for (String name : names) {
                Schema schema = RF.createSchema();
                schema.setName(name);
                catalog.getOwnedElement().add(schema);
                Verdict verdict = selection.below(catalogVerdict, schema);
                if (keeps(verdict, IN_A_SCHEMA)) {
                    schemas.put(name, new Judged<>(schema, verdict));
                } else {
                    EcoreUtil.remove(schema);
                }
            }
        }

        /**
         * Whether an element with {@code verdict} is kept: not excluded, and either selected or
         * the owner of something an include rule may reach, one of {@code below}.
         */
        private boolean keeps(Verdict verdict, List<EClass> below) {
            return !verdict.excluded() && (verdict.included() || below.stream().anyMatch(selection::mayInclude));
        }

        private void relations() {
            boolean tables = !selection.excludesKind(RelationalPackage.Literals.TABLE);
            boolean views = !selection.excludesKind(RelationalPackage.Literals.VIEW);
            Map<ObjectKey, MaterializedView> materialized = new LinkedHashMap<>();
            si.materializedViews().forEach(mv -> materialized.put(ObjectKey.of(mv.view()), mv));
            Set<ObjectKey> seen = new HashSet<>();
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
                if (!isTable && !isView) {
                    continue;
                }
                ObjectKey key = ObjectKey.of(tr);
                seen.add(key);
                // a provider that keeps a materialized view in a table (Oracle) reports it as a table too
                MaterializedView mv = materialized.get(key);
                if (isView || mv != null ? !views : !tables) {
                    continue;
                }
                NamedColumnSet relation;
                if (isView || mv != null) {
                    relation = view(tr.name(), mv);
                } else {
                    Table table = RF.createTable();
                    table.setName(tr.name());
                    table.setIsSystem(isSystem);
                    if (isGlobalTemporary || isLocalTemporary) {
                        table.setIsTemporary(true);
                        table.setTemporaryScope(isGlobalTemporary ? "GLOBAL" : "LOCAL");
                    }
                    relation = table;
                }
                if (place(key, relation) && comments) {
                    // MySQL and MariaDB keep no comment on a view and report the word VIEW for every one
                    td.tableMetaData().remarks().filter(r -> !r.isBlank())
                            .filter(r -> !(relation instanceof View && "VIEW".equals(r)))
                            .ifPresent(r -> describe(relation, relation, r));
                }
            }
            if (views) {
                for (Map.Entry<ObjectKey, MaterializedView> entry : materialized.entrySet()) {
                    if (seen.add(entry.getKey())) {
                        place(entry.getKey(), view(entry.getKey().name(), entry.getValue()));
                    }
                }
                for (ViewDefinition vd : si.viewDefinitions()) {
                    if (relations.get(ObjectKey.of(vd.view())) instanceof View view) {
                        vd.viewBody().ifPresent(body -> view.setQueryExpression(sqlQuery(body)));
                    }
                }
            }
        }

        /**
         * A view; a materialized one has no CWM 1.1 class of its own and is a read-only view
         * with the tagged value {@link RelationalRules#MATERIALIZED_TAG}.
         */
        private static View view(String name, MaterializedView materialized) {
            View view = RF.createView();
            view.setName(name);
            if (materialized != null) {
                view.setIsReadOnly(true);
                TaggedValues.set(view, RelationalRules.MATERIALIZED_TAG, "true");
                materialized.viewBody().ifPresent(body -> view.setQueryExpression(sqlQuery(body)));
            }
            return view;
        }

        /** Puts {@code relation} into its schema and keeps it there if the selection lets it. */
        private boolean place(ObjectKey key, NamedColumnSet relation) {
            Judged<Schema> schema = schemas.get(key.schema());
            if (schema == null) {
                return false;
            }
            schema.element().getOwnedElement().add(relation);
            if (!keeps(selection.below(schema.verdict(), relation), IN_A_COLUMN_SET)) {
                EcoreUtil.remove(relation);
                return false;
            }
            relations.put(key, relation);
            return true;
        }

        // --- what a kept table or view holds ---------------------------------------------------

        private void columns() {
            for (ColumnDefinition cd : si.columns()) {
                ColumnReference cr = cd.column();
                if (cr.table().isEmpty()) {
                    continue;
                }
                ObjectKey key = ObjectKey.of(cr.table().get());
                NamedColumnSet ncs = relations.get(key);
                if (ncs == null) {
                    continue;
                }
                Column col = RF.createColumn();
                col.setName(cr.name());
                ColumnMetaData md = cd.columnMetaData();
                col.setIsNullable(toCwmNullability(md.nullability()));
                col.setType(types.of(md.typeName(), md.dataType()));
                if (isNumeric(md.dataType())) {
                    md.columnSize().ifPresent(s -> col.setPrecision(s));
                } else {
                    md.columnSize().ifPresent(s -> col.setLength(s));
                }
                md.decimalDigits().ifPresent(d -> col.setScale(d));
                // a default of NULL is no default; MariaDB reports it for every nullable column without one
                md.columnDefault().filter(d -> !d.isBlank() && !"NULL".equalsIgnoreCase(d.strip())).ifPresent(d -> {
                    Expression init = CF.createExpression();
                    init.setLanguage("SQL");
                    init.setBody(d);
                    col.setInitialValue(init);
                });
                ncs.getFeature().add(col);
                if (comments) {
                    md.remarks().filter(r -> !r.isBlank()).ifPresent(r -> describe(ncs, col, r));
                }
                columns.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(cr.name(), col);
            }
        }

        /**
         * The primary keys and unique constraints. Their columns are noted whether they are
         * read or not: they tell the index a database keeps for a key, and a unique constraint
         * that is the primary key over again. A primary key is read for a foreign key that
         * references it even where the selection leaves it out; the cut then removes both.
         */
        private void keys() {
            boolean primary = !selection.excludesKind(RelationalPackage.Literals.PRIMARY_KEY)
                    || !selection.excludesKind(RelationalPackage.Literals.FOREIGN_KEY);
            boolean unique = !selection.excludesKind(RelationalPackage.Literals.UNIQUE_CONSTRAINT);
            Map<ObjectKey, List<String>> primaryKeyColumns = new HashMap<>();
            for (org.eclipse.daanse.sql.model.schema.PrimaryKey pk : si.primaryKeys()) {
                ObjectKey key = ObjectKey.of(pk.table());
                List<String> names = pk.columns().stream().map(ColumnReference::name).toList();
                primaryKeyColumns.put(key, names);
                keyColumns.computeIfAbsent(key, k -> new ArrayList<>()).add(names);
                if (primary && relations.get(key) instanceof Table table) {
                    PrimaryKey cwmPk = RF.createPrimaryKey();
                    cwmPk.setName(pk.constraintName().filter(n -> !n.isBlank()).orElse(null));
                    if (addColumns(cwmPk.getFeature(), key, names)) {
                        table.getOwnedElement().add(cwmPk);
                    }
                }
            }
            for (org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint uc : si.uniqueConstraints()) {
                ObjectKey key = ObjectKey.of(uc.table());
                List<String> names = uc.columns().stream().map(ColumnReference::name).toList();
                keyColumns.computeIfAbsent(key, k -> new ArrayList<>()).add(names);
                // one over exactly the columns of the primary key, in their order, is the primary
                // key over again; one over a part of them holds where the primary key does not
                if (unique && relations.get(key) instanceof Table table && !names.isEmpty()
                        && !names.equals(primaryKeyColumns.get(key))) {
                    UniqueConstraint cwmUc = RF.createUniqueConstraint();
                    cwmUc.setName(uc.name() == null || uc.name().isBlank() ? null : uc.name());
                    if (addColumns(cwmUc.getFeature(), key, names)) {
                        table.getOwnedElement().add(cwmUc);
                    }
                }
            }
        }

        private void checkConstraints() {
            for (org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint cc : si.checkConstraints()) {
                if (!(relations.get(ObjectKey.of(cc.table())) instanceof Table table)) {
                    continue;
                }
                CheckConstraint cwmCc = RF.createCheckConstraint();
                cwmCc.setName(cc.name() == null || cc.name().isBlank() ? null : cc.name());
                BooleanExpression be = CF.createBooleanExpression();
                be.setLanguage("SQL");
                be.setBody(unwrapCheckBody(cc.checkClause()));
                cwmCc.setBody(be);
                table.getOwnedElement().add(cwmCc);
            }
        }

        /**
         * The foreign keys, one of the rows of {@code getImportedKeys} per column. The rows of
         * one key are those of one name; a database that reports no name leaves the referenced
         * table and key to tell them apart, and where two keys of a table reference the same
         * key, their rows interleave by sequence number: each sequence number starts the next
         * key. An unnamed key gets its name with the other unnamed elements, when the load is done.
         */
        private void foreignKeys() {
            Map<ForeignKeyRows, List<List<ImportedKey>>> groups = new LinkedHashMap<>();
            for (ImportedKey ik : si.importedKeys()) {
                if (ik.foreignKeyColumn().table().isEmpty() || ik.primaryKeyColumn().table().isEmpty()) {
                    continue;
                }
                ObjectKey source = ObjectKey.of(ik.foreignKeyColumn().table().get());
                ForeignKeyRows rows = ik.name() == null || ik.name().isBlank()
                        ? new ForeignKeyRows(source, null, ObjectKey.of(ik.primaryKeyColumn().table().get()),
                                ik.primaryKeyName().filter(n -> !n.isBlank()).orElse(null))
                        : new ForeignKeyRows(source, ik.name(), null, null);
                List<List<ImportedKey>> keys = groups.computeIfAbsent(rows, k -> new ArrayList<>());
                keys.stream().filter(k -> k.stream().noneMatch(r -> r.keySequence() == ik.keySequence()))
                        .findFirst().orElseGet(() -> {
                            List<ImportedKey> key = new ArrayList<>();
                            keys.add(key);
                            return key;
                        }).add(ik);
            }
            for (Map.Entry<ForeignKeyRows, List<List<ImportedKey>>> group : groups.entrySet()) {
                for (List<ImportedKey> rows : group.getValue()) {
                    rows.sort(Comparator.comparingInt(ImportedKey::keySequence));
                    foreignKey(group.getKey().name(), rows);
                }
            }
        }

        private void foreignKey(String name, List<ImportedKey> rows) {
            ImportedKey first = rows.get(0);
            ObjectKey sourceKey = ObjectKey.of(first.foreignKeyColumn().table().get());
            ObjectKey targetKey = ObjectKey.of(first.primaryKeyColumn().table().get());
            if (!(relations.get(sourceKey) instanceof Table source)
                    || !(relations.get(targetKey) instanceof Table target)) {
                return;
            }
            List<String> referenced = rows.stream().map(r -> r.primaryKeyColumn().name()).toList();
            UniqueKey key = targetKey(target, referenced);
            if (key == null) {
                key = synthesizeTargetKey(target, targetKey, first, referenced);
            }
            if (key == null) {
                return;
            }
            // CWM pairs the columns of a foreign key with those of its key by position
            Map<String, String> byReferenced = new HashMap<>();
            rows.forEach(r -> byReferenced.put(r.primaryKeyColumn().name(), r.foreignKeyColumn().name()));
            List<String> own = key.getFeature().stream().map(f -> byReferenced.get(f.getName())).toList();
            ForeignKey cwmFk = RF.createForeignKey();
            cwmFk.setName(name);
            if (!addColumns(cwmFk.getFeature(), sourceKey, own)) {
                return;
            }
            cwmFk.setUniqueKey(key);
            cwmFk.setDeleteRule(mapReferentialAction(first.deleteRule()));
            cwmFk.setUpdateRule(mapReferentialAction(first.updateRule()));
            cwmFk.setDeferrability(mapDeferrability(first.deferrability()));
            source.getOwnedElement().add(cwmFk);
        }

        /**
         * The primary key or unique constraint of {@code target} over exactly the referenced
         * columns, in whatever order; {@code null} when there is none.
         */
        private static UniqueKey targetKey(Table target, List<String> referenced) {
            Set<String> wanted = Set.copyOf(referenced);
            for (UniqueConstraint key : Tables.uniqueConstraints(target)) {
                if (key.getFeature().size() == referenced.size() && Set.copyOf(featureNames(key)).equals(wanted)) {
                    return key;
                }
            }
            return null;
        }

        /**
         * A foreign key must reference a {@link UniqueKey}, but some databases let it reference
         * columns that only a unique index, or nothing the snapshot reports, makes unique.
         * Rather than dropping the foreign key, the implied unique constraint is built on the
         * referenced columns, under the name the database gives the referenced key.
         */
        private UniqueKey synthesizeTargetKey(Table target, ObjectKey targetKey, ImportedKey first,
                List<String> referenced) {
            UniqueConstraint uc = RF.createUniqueConstraint();
            uc.setName(first.primaryKeyName().filter(n -> !n.isBlank()).orElse(null));
            if (!addColumns(uc.getFeature(), targetKey, referenced)) {
                return null;
            }
            target.getOwnedElement().add(uc);
            keyColumns.computeIfAbsent(targetKey, k -> new ArrayList<>()).add(referenced);
            return uc;
        }

        private void indexes() {
            for (IndexInfo ii : info.indexInfos()) {
                ObjectKey key = ObjectKey.of(ii.tableReference());
                if (!(relations.get(key) instanceof Table table)) {
                    continue;
                }
                Map<String, List<IndexInfoItem>> byIndex = new LinkedHashMap<>();
                for (IndexInfoItem item : ii.indexInfoItems()) {
                    if (item.type() == IndexInfoItem.IndexType.TABLE_INDEX_STATISTIC) {
                        continue;
                    }
                    String iname = item.indexName().orElse("");
                    if (iname.isBlank()) {
                        continue;
                    }
                    byIndex.computeIfAbsent(iname, k -> new ArrayList<>()).add(item);
                }
                Map<String, Column> cmap = columns.getOrDefault(key, Map.of());
                for (Map.Entry<String, List<IndexInfoItem>> entry : byIndex.entrySet()) {
                    List<IndexInfoItem> items = entry.getValue();
                    items.sort(Comparator.comparingInt(IndexInfoItem::ordinalPosition));
                    boolean isUnique = items.get(0).unique();
                    // The index a database made itself for a constraint is no index of the model:
                    // H2, MySQL and MariaDB make one for a foreign key whose columns have none, H2
                    // also those behind a primary key or a unique constraint, and say so.
                    if (items.stream().anyMatch(IndexInfoItem::createdForConstraint)) {
                        continue;
                    }
                    // Where the database does not say so, skip only the backing index of a primary
                    // key or unique constraint: unique with the key's column sequence. A non-unique
                    // index deliberately created on those columns is a real user index and is kept.
                    if (isUnique && isKeyIndex(key, items)) {
                        continue;
                    }
                    SQLIndex idx = RF.createSQLIndex();
                    idx.setName(entry.getKey());
                    idx.setSpannedClass(table);
                    idx.setIsUnique(isUnique);
                    items.stream().map(IndexInfoItem::filterCondition).flatMap(Optional::stream)
                            .filter(f -> !f.isBlank()).findFirst().ifPresent(idx::setFilterCondition);
                    boolean complete = true;
                    for (IndexInfoItem item : items) {
                        if (item.column().isEmpty()) {
                            // an expression, not a column
                            continue;
                        }
                        Column col = cmap.get(item.column().get().name());
                        if (col == null) {
                            complete = false;
                            break;
                        }
                        SQLIndexColumn ic = SQLIndexes.indexColumn(col);
                        // where the database reports no order, the column sorts as SQL sorts by default
                        ic.setIsAscending(item.ascending().orElse(true));
                        idx.getIndexedFeature().add(ic);
                    }
                    if (complete && !idx.getIndexedFeature().isEmpty()) {
                        table.getNamespace().getOwnedElement().add(idx);
                    }
                }
            }
        }

        /**
         * Whether the unique index with {@code items} (sorted by ordinal position) is the index a
         * database keeps for a primary key or a unique constraint of the table: the same columns
         * in the same order. Oracle and PostgreSQL report it as an index of its own, often under
         * the constraint's name; written as DDL next to the constraint it would be created twice.
         * The keys are those the database reports, read or not.
         */
        private boolean isKeyIndex(ObjectKey table, List<IndexInfoItem> items) {
            List<String> indexed = items.stream().map(i -> i.column().map(ColumnReference::name).orElse(null))
                    .toList();
            return keyColumns.getOrDefault(table, List.of()).contains(indexed);
        }

        /**
         * The triggers on tables. CWM 1.1 knows a trigger before or after the change of a table
         * only: an {@code INSTEAD OF} trigger, which replaces the change, is left out with a
         * warning rather than read as one that runs before it.
         */
        private void triggers() {
            for (org.eclipse.daanse.sql.model.schema.Trigger trg : si.triggers()) {
                if (!(relations.get(ObjectKey.of(trg.table())) instanceof Table table)) {
                    continue;
                }
                if (trg.timing() == org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.INSTEAD_OF) {
                    LOGGER.log(Level.WARNING, "trigger {0} on {1} not read: CWM has no INSTEAD OF trigger",
                            trg.name(), QualifiedNames.of(table));
                    continue;
                }
                // CWM 1.1 allows only one eventManipulation per Trigger — a
                // multi-event trigger (BEFORE INSERT OR UPDATE) becomes one CWM
                // Trigger per event, suffixed to keep names unique.
                List<org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent> events = trg.events();
                for (org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event : events) {
                    Trigger cwmTrg = RF.createTrigger();
                    cwmTrg.setName(events.size() > 1 ? trg.name() + "_" + event.name() : trg.name());
                    cwmTrg.setTable(table);
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
                    String reported = trg.body().orElse(trg.fullDefinition().orElse(null));
                    String body = bodyOfOwnProcedure(trg, reported).orElse(reported);
                    if (body != null && !body.isBlank()) {
                        ProcedureExpression action = CF.createProcedureExpression();
                        action.setLanguage("SQL");
                        action.setBody(body);
                        cwmTrg.setActionStatement(action);
                    }
                    // CWM 1.1: the schema owns the trigger, the table only refers to it
                    table.getNamespace().getOwnedElement().add(cwmTrg);
                }
            }
        }

        /**
         * The body of the trigger's own procedure where the trigger's action does nothing but call
         * it: the procedure named for the trigger as {@link RelationalNames#triggerProcedure} names
         * it, in the trigger's schema, as the DDL generator writes a trigger where the dialect keeps
         * the body in a procedure of its own. MySQL and MariaDB report the action of such a trigger
         * as the call, {@code CALL `s`.`t_fn`()}; the procedure is not read as a routine
         * ({@link #procedures()}), so its body is the trigger's, and the trigger written again gets
         * the same procedure. Empty where the action does more than the call, where the procedure is
         * not there, and where its body is reported only as its whole {@code CREATE} statement.
         */
        private Optional<String> bodyOfOwnProcedure(org.eclipse.daanse.sql.model.schema.Trigger trg,
                String action) {
            if (action == null) {
                return Optional.empty();
            }
            String schema = trg.table().schema().map(SchemaReference::name).orElse(null);
            String procedure = RelationalNames.triggerProcedure(trg.name());
            if (!onlyCalls(action, schema, procedure)) {
                return Optional.empty();
            }
            if (routineBodies == null) {
                routineBodies = new HashMap<>();
                for (org.eclipse.daanse.sql.jdbc.api.schema.Procedure proc : si.procedures()) {
                    routineBody(proc.reference().schema(), proc.reference().name(), proc.body(),
                            proc.fullDefinition());
                }
                for (org.eclipse.daanse.sql.jdbc.api.schema.Function fn : si.functions()) {
                    routineBody(fn.reference().schema(), fn.reference().name(), fn.body(), fn.fullDefinition());
                }
            }
            return Optional.ofNullable(routineBodies.get(new ObjectKey(schema, procedure)));
        }

        /** Notes the body of a routine, where it is reported apart from its {@code CREATE} statement. */
        private void routineBody(Optional<SchemaReference> schema, String name, Optional<String> body,
                Optional<String> definition) {
            body.filter(b -> !b.isBlank() && !b.strip().equals(definition.map(String::strip).orElse(null)))
                    .ifPresent(b -> routineBodies.putIfAbsent(
                            new ObjectKey(schema.map(SchemaReference::name).orElse(null), name), b));
        }

        /**
         * Whether {@code action} is nothing but a call of {@code procedure} without arguments,
         * qualified by {@code schema} or not, in any quoting: {@code CALL p()}, {@code EXECUTE
         * FUNCTION s.p()}, {@code EXEC [s].[p]}.
         */
        private static boolean onlyCalls(String action, String schema, String procedure) {
            String name = (schema == null ? "" : "(?:" + Pattern.quote(schema) + "\\s*\\.\\s*)?")
                    + Pattern.quote(procedure);
            return Pattern.compile("(?:CALL|EXEC(?:UTE)?(?:\\s+(?:FUNCTION|PROCEDURE))?)\\s+" + name
                    + "\\s*(?:\\(\\s*\\))?\\s*;?", Pattern.CASE_INSENSITIVE)
                    .matcher(action.replaceAll("[`\"\\[\\]]", "").strip()).matches();
        }

        /**
         * The routines, but not the procedure that holds the body of a trigger: named for the
         * trigger as {@link RelationalNames#triggerProcedure} names it, in the trigger's schema,
         * and called by the trigger or holding its body. The trigger's body is the trigger's
         * {@code actionStatement}; read as a routine as well, it would be in the model twice, and
         * the DDL generator writes it again with the trigger.
         */
        private void procedures() {
            Map<ObjectKey, List<org.eclipse.daanse.sql.model.schema.Trigger>> triggerProcedures = new HashMap<>();
            for (org.eclipse.daanse.sql.model.schema.Trigger trg : si.triggers()) {
                triggerProcedures.computeIfAbsent(new ObjectKey(trg.table().schema().map(SchemaReference::name)
                        .orElse(null), RelationalNames.triggerProcedure(trg.name())), k -> new ArrayList<>()).add(trg);
            }
            for (org.eclipse.daanse.sql.jdbc.api.schema.Procedure proc : si.procedures()) {
                if (holdsTriggerBody(triggerProcedures, new ObjectKey(proc.reference().schema()
                        .map(SchemaReference::name).orElse(null), proc.reference().name()), proc.body())) {
                    continue;
                }
                Procedure cwmProc = createProcedure(proc.reference().name(), ProcedureType.PROCEDURE,
                        proc.body().orElse(proc.fullDefinition().orElse(null)));
                specificName(cwmProc, proc.reference().specificName());
                for (ProcedureColumn col : proc.columns()) {
                    mapProcedureParameter(col, types).ifPresent(cwmProc.getParameter()::add);
                }
                routine(proc.reference().schema().map(SchemaReference::name).orElse(null), cwmProc,
                        proc.remarks());
            }
            for (org.eclipse.daanse.sql.jdbc.api.schema.Function fn : si.functions()) {
                if (holdsTriggerBody(triggerProcedures, new ObjectKey(fn.reference().schema()
                        .map(SchemaReference::name).orElse(null), fn.reference().name()), fn.body())) {
                    continue;
                }
                Procedure cwmProc = createProcedure(fn.reference().name(), ProcedureType.FUNCTION,
                        fn.body().orElse(fn.fullDefinition().orElse(null)));
                specificName(cwmProc, fn.reference().specificName());
                for (FunctionColumn col : fn.columns()) {
                    mapFunctionParameter(col, types).ifPresent(cwmProc.getParameter()::add);
                }
                routine(fn.reference().schema().map(SchemaReference::name).orElse(null), cwmProc, fn.remarks());
            }
        }

        /**
         * Whether the routine {@code key} with {@code body} is the procedure of a trigger: named for
         * one of {@code triggerProcedures}, and called by that trigger or holding its body.
         */
        private static boolean holdsTriggerBody(Map<ObjectKey, List<org.eclipse.daanse.sql.model.schema.Trigger>> triggerProcedures,
                ObjectKey key, Optional<String> body) {
            for (org.eclipse.daanse.sql.model.schema.Trigger trg : triggerProcedures.getOrDefault(key, List.of())) {
                String calls = trg.fullDefinition().orElse("") + " " + trg.body().orElse("");
                boolean sameBody = body.isPresent() && trg.body().isPresent()
                        && body.get().strip().equals(trg.body().get().strip());
                if (sameBody || calls.contains(key.name())) {
                    LOGGER.log(Level.DEBUG, "routine {0} read as the body of trigger {1}", key, trg.name());
                    return true;
                }
            }
            return false;
        }

        /** Puts {@code routine} into its schema, keeps it there if the selection lets it, with its comment. */
        private void routine(String schemaName, Procedure routine, Optional<String> remarks) {
            Judged<Schema> schema = schemas.get(schemaName);
            if (schema == null) {
                return;
            }
            schema.element().getOwnedElement().add(routine);
            if (!keeps(selection.below(schema.verdict(), routine), IN_A_ROUTINE)) {
                EcoreUtil.remove(routine);
                return;
            }
            if (comments) {
                remarks.filter(r -> !r.isBlank()).ifPresent(r -> describe(schema.element(), routine, r));
            }
        }

        /**
         * Adds the columns named {@code names} of the table or view {@code key} to
         * {@code features}, all or none: a key over fewer columns is another key.
         */
        private boolean addColumns(List<? super Column> features, ObjectKey key, List<String> names) {
            Map<String, Column> cmap = columns.getOrDefault(key, Map.of());
            List<Column> found = new ArrayList<>();
            for (String name : names) {
                Column c = name == null ? null : cmap.get(name);
                if (c == null) {
                    return false;
                }
                found.add(c);
            }
            features.addAll(found);
            return true;
        }

        /**
         * A {@link Description} of the configured type for {@code described}, owned by
         * {@code owner}. Containment only matters for serialization; lookup goes through the
         * description's reference to the element.
         */
        private void describe(Namespace owner, ModelElement described, String remarks) {
            Description description = BusinessinformationFactory.eINSTANCE.createDescription();
            description.setType(commentType);
            description.setBody(remarks);
            description.getModelElement().add(described);
            owner.getOwnedElement().add(description);
        }
    }

    /** An element in its place and what the selection says about it. */
    private record Judged<T extends ModelElement>(T element, Verdict verdict) {
    }

    /**
     * The rows of {@code getImportedKeys} that may form one foreign key: of one name, or
     * without a name, of one referenced table and key.
     */
    private record ForeignKeyRows(ObjectKey source, String name, ObjectKey target, String targetKey) {
    }

    /**
     * The catalog's SQL types, found when a column or parameter is typed and put into the catalog the
     * first time: one per name and type number, so the catalog is self-contained and serialises
     * without dangling references. The size belongs to the column (CWM's {@code Column} has length,
     * precision and scale), so all columns of a type share it, and the type's id, built from its
     * name, does not depend on which other sizes the database holds.
     */
    private static final class CatalogTypes {

        private final Catalog catalog;
        private final Map<List<Object>, SQLSimpleType> byNameAndNumber = new HashMap<>();

        CatalogTypes(Catalog catalog) {
            this.catalog = catalog;
        }

        SQLSimpleType of(String typeName, JDBCType dataType) {
            SQLSimpleType type = SQLSimpleTypes.toCwmType(typeName, dataType, OptionalInt.empty(),
                    OptionalInt.empty());
            return byNameAndNumber.computeIfAbsent(List.of(String.valueOf(type.getName()), type.getTypeNumber()),
                    key -> {
                        catalog.getOwnedElement().add(type);
                        return type;
                    });
        }
    }

    /** Whether a column's size is a number of digits (its precision) rather than a length. */
    private static boolean isNumeric(JDBCType type) {
        return type != null && switch (type) {
        case TINYINT, SMALLINT, INTEGER, BIGINT, REAL, FLOAT, DOUBLE, NUMERIC, DECIMAL -> true;
        default -> false;
        };
    }

    private static String schemaName(TableReference table) {
        return table.schema().map(SchemaReference::name).orElse(null);
    }

    private static QueryExpression sqlQuery(String body) {
        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody(body);
        return qe;
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

    private static List<String> featureNames(UniqueKey uk) {
        List<String> out = new ArrayList<>();
        for (StructuralFeature f : uk.getFeature()) {
            out.add(f.getName());
        }
        return out;
    }

    /**
     * Keeps the specific name of {@code routine} as the database gives it, where it differs from the
     * routine's name: it tells overloads apart, and a privilege on a routine names its overload by it
     * ({@link RelationalNames#SPECIFIC_NAME_TAG}).
     */
    private static void specificName(Procedure routine, String specificName) {
        if (specificName != null && !specificName.isBlank() && !specificName.equals(routine.getName())) {
            TaggedValues.set(routine, RelationalNames.SPECIFIC_NAME_TAG, specificName);
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

    /** A parameter has no size of its own in CWM; it is typed with the catalog's type of its name. */
    private static Optional<SQLParameter> mapProcedureParameter(ProcedureColumn col, CatalogTypes types) {
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
        sp.setType(types.of(col.typeName(), col.dataType()));
        return Optional.of(sp);
    }

    private static Optional<SQLParameter> mapFunctionParameter(FunctionColumn col, CatalogTypes types) {
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
        sp.setType(types.of(col.typeName(), col.dataType()));
        return Optional.of(sp);
    }

    /** BEFORE or AFTER; an INSTEAD OF trigger is not read, see {@code triggers()}. */
    private static ConditionTimingType mapTiming(org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming t) {
        return t == org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.AFTER ? ConditionTimingType.AFTER
                : ConditionTimingType.BEFORE;
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
        return switch (s.toUpperCase(Locale.ROOT)) {
        case "ROW" -> ActionOrientationType.ROW;
        default -> ActionOrientationType.STATEMENT;
        };
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

    @SafeVarargs
    private static List<EClass> concat(List<EClass>... lists) {
        List<EClass> all = new ArrayList<>();
        for (List<EClass> list : lists) {
            all.addAll(list);
        }
        return List.copyOf(all);
    }
}
