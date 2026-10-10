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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.SynonymFactory;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** Added, dropped and retargeted synonyms: diff, plan order and emitted SQL, offline and on H2. */
class SynonymDiffTest {

    private static final Dialect ORACLE = new OracleDialect();

    private final SchemaDiffer differ = new SchemaDifferImpl();

    private final MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());

    @Test
    void sameTargetIsNoChange() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        synonym(oldF.schema, "CUST", oldF.customers);
        synonym(newF.schema, "CUST", newF.customers);

        assertThat(differ.diff(oldF.schema, newF.schema).isEmpty()).isTrue();
    }

    @Test
    void addedSynonymIsCreatedAfterTableChanges() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        Synonym cust = synonym(newF.schema, "CUST", newF.customers);
        Column note = SqlGenFixture.RF.createColumn();
        note.setName("NOTE");
        newF.customers.getFeature().add(note);

        SchemaDiff diff = differ.diff(oldF.schema, newF.schema);
        assertThat(diff.synonymsAdded()).containsExactly(cust);

        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        assertThat(ops).first().isInstanceOf(ChangeOp.AddColumn.class);
        assertThat(ops).last().isEqualTo(new ChangeOp.CreateSynonym(cust));
        assertThat(emitter.emit(List.of(ops.get(ops.size() - 1)), ORACLE))
                .containsExactly("CREATE SYNONYM \"HR\".\"CUST\" FOR \"HR\".\"CUSTOMERS\"");
    }

    @Test
    void droppedSynonymIsDroppedFirst() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        Synonym cust = synonym(oldF.schema, "CUST", oldF.customers);
        newF.schema.getOwnedElement().remove(newF.view);

        SchemaDiff diff = differ.diff(oldF.schema, newF.schema);
        assertThat(diff.synonymsDropped()).containsExactly(cust);

        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        assertThat(ops).extracting(op -> op.getClass().getSimpleName())
                .containsExactly("DropSynonym", "DropView");
        assertThat(emitter.emit(ops.subList(0, 1), ORACLE)).containsExactly("DROP SYNONYM \"HR\".\"CUST\"");
    }

    @Test
    void changedRawTargetIsDropAndCreate() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        remote(oldF.schema, "EMP", "HQ", "EMPLOYEES", "HQ_LINK");
        Synonym newEmp = remote(newF.schema, "EMP", "HQ", "EMPLOYEES", "HQ2_LINK");

        SchemaDiff diff = differ.diff(oldF.schema, newF.schema);
        assertThat(diff.synonymsRetargeted()).singleElement()
                .satisfies(r -> assertThat(r.newSynonym()).isSameAs(newEmp));

        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        assertThat(emitter.emit(ops, ORACLE)).containsExactly(
                "DROP SYNONYM \"HR\".\"EMP\"",
                "CREATE SYNONYM \"HR\".\"EMP\" FOR \"HQ\".\"EMPLOYEES\"@HQ2_LINK");
    }

    @Test
    void publicFlagCatalogAndTargetSchemaCount() {
        assertRetargeted(s -> s.setIsPublic(true));
        assertRetargeted(s -> s.setTargetCatalogName("OTHERDB"));
        assertRetargeted(s -> s.setTargetSchemaName("HQ2"));
        assertRetargeted(s -> s.setTargetName("STAFF"));
    }

    @Test
    void targetObjectTypeAloneIsNoChange() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        synonym(oldF.schema, "CUST", oldF.customers).setTargetObjectType("TABLE");
        synonym(newF.schema, "CUST", newF.customers).setTargetObjectType("BASE TABLE");

        assertThat(differ.diff(oldF.schema, newF.schema).isEmpty()).isTrue();
    }

    @Test
    void renamedResolvedTargetRetargetsAfterTheRename() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        synonym(oldF.schema, "CUST", oldF.customers);
        Synonym cust = synonym(newF.schema, "CUST", newF.customers);
        // The model renames the table; the raw target still carries the loaded name.
        newF.customers.setName("CLIENTS_TABLE");

        SchemaDiff diff = differ.diff(oldF.schema, newF.schema);
        assertThat(diff.synonymsRetargeted()).hasSize(1);

        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        assertThat(ops.get(0)).isInstanceOf(ChangeOp.DropSynonym.class);
        assertThat(ops.get(ops.size() - 1)).isEqualTo(new ChangeOp.CreateSynonym(cust));
        assertThat(emitter.emit(List.of(ops.get(ops.size() - 1)), ORACLE))
                .containsExactly("CREATE SYNONYM \"HR\".\"CUST\" FOR \"HR\".\"CLIENTS_TABLE\"");
    }

    @Test
    void chainIsCreatedInLinkOrder() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        Synonym cust = synonym(newF.schema, "CUST", newF.customers);
        Synonym clients = synonym(newF.schema, "CLIENTS", cust);
        newF.schema.getOwnedElement().move(0, clients);

        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(oldF.schema, newF.schema));

        assertThat(ops).containsExactly(new ChangeOp.CreateSynonym(cust), new ChangeOp.CreateSynonym(clients));
    }

    @Test
    void partialScopeSuppressesAddedSynonyms() {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        synonym(newF.schema, "CUST", newF.customers);

        SchemaDiff diff = differ.diff(oldF.schema, newF.schema,
                DiffSettings.defaults().withScope(DiffSettings.Scope.PARTIAL));

        assertThat(diff.synonymsAdded()).isEmpty();
    }

    @Test
    void dialectsWithoutSynonymsEmitNothing() {
        SqlGenFixture f = fixture();
        Synonym cust = synonym(f.schema, "CUST", f.customers);
        List<ChangeOp> ops = List.of(new ChangeOp.DropSynonym(cust), new ChangeOp.CreateSynonym(cust));

        assertThat(emitter.emit(ops, new PostgreSqlDialect())).isEmpty();
        assertThat(emitter.emit(ops, new MicrosoftSqlServerDialect())).containsExactly(
                "DROP SYNONYM IF EXISTS \"HR\".\"CUST\"",
                "CREATE SYNONYM \"HR\".\"CUST\" FOR \"HR\".\"CUSTOMERS\"");
    }

    @Test
    void inexpressibleSynonymIsSkipped() {
        SqlGenFixture f = fixture();
        Synonym remote = remote(f.schema, "EMP", "HQ", "EMPLOYEES", "HQ_LINK");

        assertThat(emitter.emit(List.of(new ChangeOp.CreateSynonym(remote)), new H2Dialect())).isEmpty();
    }

    @Test
    void migrationRetargetsOnH2() throws Exception {
        H2Dialect h2 = new H2Dialect();
        SqlGenFixture oldF = SqlGenFixture.build("HR", h2);
        SqlGenFixture newF = SqlGenFixture.build("HR", h2);
        synonym(oldF.schema, "CUST", oldF.customers);
        synonym(oldF.schema, "GONE", oldF.orders);
        synonym(newF.schema, "CUST", newF.orders);
        synonym(newF.schema, "ORD", newF.orders);
        EnumSet<Feature> noTriggers = EnumSet.complementOf(EnumSet.of(Feature.TRIGGER));

        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:cwmSynonymDiff", "sa", "");
                Statement s = c.createStatement()) {
            for (String sql : new DdlGeneratorImpl(h2).createSchema(oldF.schema, noTriggers)) {
                s.execute(sql);
            }
            for (String sql : emitter.emit(ChangePlanner.create().plan(differ.diff(oldF.schema, newF.schema)), h2)) {
                s.execute(sql);
            }
            try (ResultSet rs = s.executeQuery("SELECT SYNONYM_NAME, SYNONYM_FOR FROM INFORMATION_SCHEMA.SYNONYMS"
                    + " ORDER BY SYNONYM_NAME")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("CUST");
                assertThat(rs.getString(2)).isEqualTo("ORDERS");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("ORD");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    private void assertRetargeted(Consumer<Synonym> change) {
        SqlGenFixture oldF = fixture();
        SqlGenFixture newF = fixture();
        remote(oldF.schema, "EMP", "HQ", "EMPLOYEES", null);
        change.accept(remote(newF.schema, "EMP", "HQ", "EMPLOYEES", null));

        assertThat(differ.diff(oldF.schema, newF.schema).synonymsRetargeted()).hasSize(1);
    }

    private static SqlGenFixture fixture() {
        return SqlGenFixture.build("HR", ORACLE);
    }

    private static Synonym remote(Schema schema, String name, String targetSchema, String targetName,
            String dbLink) {
        Synonym s = synonym(schema, name, null);
        s.setTargetSchemaName(targetSchema);
        s.setTargetName(targetName);
        s.setDbLink(dbLink);
        return s;
    }

    private static Synonym synonym(Schema schema, String name, ModelElement target) {
        Synonym s = SynonymFactory.eINSTANCE.createSynonym();
        s.setName(name);
        s.setTarget(target);
        if (target != null) {
            s.setTargetSchemaName(((Schema) target.getNamespace()).getName());
            s.setTargetName(target.getName());
        }
        schema.getOwnedElement().add(s);
        return s;
    }
}
