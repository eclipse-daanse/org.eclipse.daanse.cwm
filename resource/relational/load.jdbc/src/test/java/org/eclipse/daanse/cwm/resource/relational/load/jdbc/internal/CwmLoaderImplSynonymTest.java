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
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.util.Synonyms;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionReference;
import org.eclipse.daanse.sql.jdbc.api.schema.SynonymReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.record.meta.MetaInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.StructureInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SynonymRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/**
 * {@code StructureInfo.synonyms()} → {@link Synonym} elements: target resolution,
 * PUBLIC handling and the config switches — on a hand-built snapshot, no database.
 */
class CwmLoaderImplSynonymTest {

    private static final String HR = "HR";
    private static final String SALES = "SALES";

    private final List<TableDefinition> tables = new ArrayList<>();
    private final List<Function> functions = new ArrayList<>();
    private final List<org.eclipse.daanse.sql.jdbc.api.schema.Synonym> synonyms = new ArrayList<>();

    @Test
    void synonymToTableResolvesAndKeepsRawTarget() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        synonym(HR, "EMP", HR, "EMPLOYEES", "TABLE");

        Schema hr = schema(load(JdbcToCwmConfig.all()), HR);
        Synonym emp = Synonyms.find(hr, "EMP").orElseThrow();

        assertThat(emp.getTarget()).isSameAs(Schemas.findTable(hr, "EMPLOYEES").orElseThrow());
        assertThat(emp.getTargetSchemaName()).isEqualTo(HR);
        assertThat(emp.getTargetName()).isEqualTo("EMPLOYEES");
        assertThat(emp.getTargetObjectType()).isEqualTo("TABLE");
        assertThat(emp.getTargetCatalogName()).isNull();
        assertThat(emp.getDbLink()).isNull();
        assertThat(emp.isIsPublic()).isFalse();
        // Not a column set: existing consumers keep seeing only the table.
        assertThat(Schemas.columnSets(hr)).extracting(t -> t.getName()).containsExactly("EMPLOYEES");
    }

    @Test
    void synonymToViewInOtherSchema() {
        table(SALES, "V_ORDERS", TableReference.TYPE_VIEW);
        synonym(HR, "ORDERS", SALES, "V_ORDERS", "VIEW");

        Catalog catalog = load(JdbcToCwmConfig.all());
        Synonym orders = Synonyms.find(schema(catalog, HR), "ORDERS").orElseThrow();

        assertThat(orders.getTarget()).isInstanceOf(View.class);
        assertThat(orders.getTarget().getNamespace()).isSameAs(schema(catalog, SALES));
    }

    @Test
    void chainResolvesRegardlessOfOrder() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        // The outer link comes first in the snapshot.
        synonym(HR, "EMP2", HR, "EMP", "SYNONYM");
        synonym(HR, "EMP", HR, "EMPLOYEES", "TABLE");

        Schema hr = schema(load(JdbcToCwmConfig.all()), HR);
        Synonym emp2 = Synonyms.find(hr, "EMP2").orElseThrow();

        assertThat(emp2.getTarget()).isSameAs(Synonyms.find(hr, "EMP").orElseThrow());
        assertThat(Synonyms.resolveFinal(emp2)).contains(Schemas.findTable(hr, "EMPLOYEES").orElseThrow());
    }

    @Test
    void synonymToFunctionResolvesToProcedure() {
        function(HR, "FN_BONUS");
        synonym(HR, "BONUS", HR, "FN_BONUS", "FUNCTION");

        Schema hr = schema(load(JdbcToCwmConfig.all()), HR);

        assertThat(Synonyms.find(hr, "BONUS").orElseThrow().getTarget()).isInstanceOf(Procedure.class)
                .extracting(t -> t.getName()).isEqualTo("FN_BONUS");
    }

    @Test
    void synonymToFunctionStaysUnresolvedWithoutProcedures() {
        function(HR, "FN_BONUS");
        synonym(HR, "BONUS", HR, "FN_BONUS", "FUNCTION");

        Schema hr = schema(load(JdbcToCwmConfig.builder().includeProcedures(false).build()), HR);

        assertThat(Synonyms.find(hr, "BONUS").orElseThrow().getTarget()).isNull();
    }

    @Test
    void remoteTargetsStayUnresolvedEvenWhenALocalNameMatches() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        synonyms.add(new SynonymRecord(ref(HR, "EMP_LINK"), schemaRef(HR), "EMPLOYEES", Optional.of("HQ.EXAMPLE"),
                false, Optional.empty()));
        synonyms.add(new SynonymRecord(ref(HR, "EMP_OTHER_DB"),
                Optional.of(new SchemaReference(Optional.of(new CatalogReference("OTHERDB")), HR)), "EMPLOYEES",
                Optional.empty(), false, Optional.empty()));

        Schema hr = schema(load(JdbcToCwmConfig.all()), HR);

        Synonym link = Synonyms.find(hr, "EMP_LINK").orElseThrow();
        assertThat(link.getTarget()).isNull();
        assertThat(link.getDbLink()).isEqualTo("HQ.EXAMPLE");
        Synonym otherDb = Synonyms.find(hr, "EMP_OTHER_DB").orElseThrow();
        assertThat(otherDb.getTarget()).isNull();
        assertThat(otherDb.getTargetCatalogName()).isEqualTo("OTHERDB");
    }

    @Test
    void targetOutsideSchemaFilterStaysUnresolved() {
        table(SALES, "ORDERS", TableReference.TYPE_TABLE);
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        synonym(HR, "ORDERS", SALES, "ORDERS", "TABLE");
        synonym(SALES, "EMP", HR, "EMPLOYEES", "TABLE");

        Catalog catalog = load(JdbcToCwmConfig.builder().schemas(HR).build());

        assertThat(Catalogs.schemas(catalog)).extracting(Schema::getName).containsExactly(HR);
        Synonym orders = Synonyms.find(schema(catalog, HR), "ORDERS").orElseThrow();
        assertThat(orders.getTarget()).isNull();
        assertThat(orders.getTargetSchemaName()).isEqualTo(SALES);
    }

    @Test
    void publicSynonymsGoToPublicSchemaWhenTargetSchemaIsAccepted() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        table(SALES, "ORDERS", TableReference.TYPE_TABLE);
        publicSynonym("EMPLOYEES", HR, "EMPLOYEES");
        publicSynonym("ORDERS", SALES, "ORDERS");

        Catalog catalog = load(JdbcToCwmConfig.builder().schemas(HR).build());

        assertThat(Catalogs.schemas(catalog)).extracting(Schema::getName).containsExactly(HR, "PUBLIC");
        Schema pub = schema(catalog, "PUBLIC");
        assertThat(Synonyms.synonyms(pub)).extracting(Synonym::getName).containsExactly("EMPLOYEES");
        Synonym employees = Synonyms.find(pub, "EMPLOYEES").orElseThrow();
        assertThat(employees.isIsPublic()).isTrue();
        assertThat(employees.getTarget()).isSameAs(Schemas.findTable(schema(catalog, HR), "EMPLOYEES").orElseThrow());
    }

    @Test
    void noPublicSchemaWithoutPublicSynonyms() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        table(SALES, "ORDERS", TableReference.TYPE_TABLE);
        publicSynonym("ORDERS", SALES, "ORDERS");

        Catalog catalog = load(JdbcToCwmConfig.builder().schemas(HR).build());

        assertThat(Catalogs.schemas(catalog)).extracting(Schema::getName).containsExactly(HR);
    }

    @Test
    void tableFilterAppliesToSynonymName() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        synonym(HR, "EMP", HR, "EMPLOYEES", "TABLE");
        synonym(HR, "TMP_EMP", HR, "EMPLOYEES", "TABLE");

        Schema hr = schema(load(JdbcToCwmConfig.builder().tableFilter((s, t) -> !t.startsWith("TMP_")).build()), HR);

        assertThat(Synonyms.synonyms(hr)).extracting(Synonym::getName).containsExactly("EMP");
    }

    @Test
    void includeSynonymsFalseSkipsThem() {
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        synonym(HR, "EMP", HR, "EMPLOYEES", "TABLE");

        Schema hr = schema(load(JdbcToCwmConfig.builder().includeSynonyms(false).build()), HR);

        assertThat(Synonyms.synonyms(hr)).isEmpty();
    }

    @Test
    void driverReportedSynonymRowIsNotATable() {
        // H2 lists a synonym in getTables() as TABLE_TYPE = SYNONYM, next to the
        // INFORMATION_SCHEMA.SYNONYMS entry the provider reports.
        table(HR, "EMPLOYEES", TableReference.TYPE_TABLE);
        table(HR, "EMP", TableReference.TYPE_SYNONYM);
        synonym(HR, "EMP", HR, "EMPLOYEES", "BASE TABLE");

        Schema hr = schema(load(JdbcToCwmConfig.all()), HR);

        assertThat(Schemas.tables(hr)).extracting(Table::getName).containsExactly("EMPLOYEES");
        assertThat(Synonyms.synonyms(hr)).extracting(Synonym::getName).containsExactly("EMP");
    }

    private Catalog load(JdbcToCwmConfig config) {
        StructureInfoRecord structure = new StructureInfoRecord(List.of(),
                List.of(new SchemaReference(HR), new SchemaReference(SALES)), List.copyOf(tables),
                List.<ColumnDefinition>of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.copyOf(functions), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.copyOf(synonyms));
        MetaInfo info = new MetaInfoRecord(null, structure, null, List.of(), List.of());
        return new CwmLoaderImpl().load(info, config);
    }

    private static Schema schema(Catalog catalog, String name) {
        return Catalogs.schemas(catalog).stream().filter(s -> name.equals(s.getName())).findFirst().orElseThrow();
    }

    private void table(String schema, String name, String type) {
        tables.add(new TableDefinitionRecord(new TableReference(schemaRef(schema), name, type)));
    }

    private void function(String schema, String name) {
        functions.add(new FunctionRecord(new FunctionReference(schemaRef(schema), name), Function.FunctionType.NO_TABLE,
                Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private void synonym(String schema, String name, String targetSchema, String targetName, String targetType) {
        synonyms.add(new SynonymRecord(ref(schema, name), schemaRef(targetSchema), targetName, Optional.empty(), false,
                Optional.of(targetType)));
    }

    private void publicSynonym(String name, String targetSchema, String targetName) {
        synonyms.add(new SynonymRecord(ref("PUBLIC", name), schemaRef(targetSchema), targetName, Optional.empty(),
                true, Optional.of("TABLE")));
    }

    private static SynonymReference ref(String schema, String name) {
        return new SynonymReference(schemaRef(schema), name);
    }

    private static Optional<SchemaReference> schemaRef(String name) {
        return Optional.of(new SchemaReference(Optional.empty(), name));
    }
}
