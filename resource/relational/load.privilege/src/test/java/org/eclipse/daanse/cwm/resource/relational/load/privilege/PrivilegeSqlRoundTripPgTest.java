/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.privilege;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.PrivilegeSqlGeneratorImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.api.PrivilegeLoadResult;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.postgresql.PostgresDatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Generate → execute → reload: das statische Privilegienmodell wird per
 * {@link PrivilegeSqlGenerator} in GRANTs übersetzt, gegen ein echtes PG
 * ausgeführt, per {@code CwmLoader}/{@code PrivilegeLoader} zurückgeladen und
 * verglichen (der Grantor wird ignoriert — er ergibt sich aus der ausführenden
 * Session). Danach die Gegenprobe: dieselben Privilegien als REVOKEs, erneut
 * geladen — leer; zuletzt DROP ROLE. Der Beweis, dass das Modell für beide
 * Richtungen vollständig ist.
 */
@TestInstance(Lifecycle.PER_CLASS)
class PrivilegeSqlRoundTripPgTest {

    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();
    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;
    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;

    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        ActiveDatabase dbInit = new PostgresDatabaseProvider().activate();
        connection = dbInit.dataSource().getConnection();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed())
            connection.close();
    }

    @Test
    void generatedGrantsRoundTripAndRevokesTakeThemBack() throws Exception {
        String schemaName = "gen_rt";
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE SCHEMA \"" + schemaName + "\"");
            s.execute("CREATE TABLE \"" + schemaName + "\".\"customers\" (\"id\" integer PRIMARY KEY, "
                    + "\"email\" varchar(120))");
            s.execute("CREATE FUNCTION \"" + schemaName + "\".fn_answer() RETURNS integer LANGUAGE sql "
                    + "AS 'SELECT 42'");
        }
        connection.setSchema(schemaName);

        // Katalog laden; die Privilegien-Ziele referenzieren die geladenen CWM-Objekte.
        Catalog catalog = new CwmLoaderImpl().load(
                DB_SERVICE.createMetaInfo(connection, new PostgreSqlMetadataProvider()), JdbcToCwmConfig.all());
        Table customers = (Table) find(catalog, schemaName, "customers");
        Procedure fnAnswer = (Procedure) find(catalog, schemaName, "fn_answer");

        // Statisches Soll-Modell: zwei Rollen, drei Privilegien.
        ResponsibleParty reader = party("gen_reader");
        ResponsibleParty parent = party("gen_parent");
        TablePrivilege select = PF.createTablePrivilege();
        select.setAction(TablePrivilegeAction.SELECT);
        select.setTable(customers);
        reader.getOwnedElement().add(select);
        // Spalteneingeschraenkt: GRANT UPDATE ("email") ...
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column emailColumn =
                customers.getFeature().stream()
                        .filter(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class::isInstance)
                        .map(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class::cast)
                        .filter(c -> "email".equals(c.getName())).findFirst().orElseThrow();
        TablePrivilege updateEmail = PF.createTablePrivilege();
        updateEmail.setAction(TablePrivilegeAction.UPDATE);
        updateEmail.setTable(customers);
        updateEmail.getColumn().add(emailColumn);
        reader.getOwnedElement().add(updateEmail);
        ProcedurePrivilege execute = PF.createProcedurePrivilege();
        execute.setProcedure(fnAnswer);
        reader.getOwnedElement().add(execute);
        RolePrivilege membership = PF.createRolePrivilege();
        membership.setRole(parent);
        membership.setGrantable(Boolean.TRUE); // WITH ADMIN OPTION
        reader.getOwnedElement().add(membership);
        List<Privilege> privileges = List.of(select, updateEmail, execute, membership);

        Dialect dialect = new PostgreSqlDialect();
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(dialect);

        // Provisionieren: CREATE ROLE + GRANTs.
        try (Statement s = connection.createStatement()) {
            for (String sql : generator.createRoleStatements(List.of(reader, parent))) {
                s.execute(sql);
            }
            for (String sql : generator.grantStatements(privileges)) {
                s.execute(sql);
            }
        }

        // Zurueckladen und vergleichen (grantor ignorieren).
        PrivilegeLoadResult loaded = reload();
        assertThat(PrivilegeModelValidator.validateAll(loaded.parties())).isEmpty();
        assertThat(PrivilegeStereotypes.databaseRoles(loaded.stereotypes()).stream()
                .map(ResponsibleParty::getName)).contains("gen_reader", "gen_parent");
        ResponsibleParty loadedReader = partyByName(loaded, "gen_reader");
        assertThat(loadedReader.getOwnedElement()).hasSize(4);
        TablePrivilege loadedSelect = loadedReader.getOwnedElement().stream()
                .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast)
                .filter(prv -> prv.getAction() == TablePrivilegeAction.SELECT).findFirst().orElseThrow();
        assertThat(loadedSelect.getTable().getName()).isEqualTo("customers");
        assertThat(loadedSelect.getColumn()).isEmpty();
        assertThat(loadedSelect.getGrantable()).isFalse();
        TablePrivilege loadedUpdate = loadedReader.getOwnedElement().stream()
                .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast)
                .filter(prv -> prv.getAction() == TablePrivilegeAction.UPDATE).findFirst().orElseThrow();
        assertThat(loadedUpdate.getColumn()).extracting(c -> c.getName()).containsExactly("email");
        ProcedurePrivilege loadedExecute = firstOf(loadedReader, ProcedurePrivilege.class);
        assertThat(loadedExecute.getProcedure().getName()).isEqualTo("fn_answer");
        RolePrivilege loadedMembership = firstOf(loadedReader, RolePrivilege.class);
        assertThat(loadedMembership.getRole().getName()).isEqualTo("gen_parent");
        assertThat(loadedMembership.getGrantable()).isTrue();

        // Gegenprobe: dieselben Privilegien als REVOKEs — alles weg.
        try (Statement s = connection.createStatement()) {
            for (String sql : generator.revokeStatements(privileges)) {
                s.execute(sql);
            }
        }
        PrivilegeLoadResult afterRevoke = reload();
        assertThat(partyByName(afterRevoke, "gen_reader").getOwnedElement()).isEmpty();

        // Aufraeumen ueber den Generator: DROP ROLE.
        try (Statement s = connection.createStatement()) {
            for (String sql : generator.dropRoleStatements(List.of(reader, parent))) {
                s.execute(sql);
            }
        }
        PrivilegeLoadResult afterDrop = reload();
        assertThat(PrivilegeStereotypes.databaseRoles(afterDrop.stereotypes()).stream()
                .map(ResponsibleParty::getName)).doesNotContain("gen_reader", "gen_parent");
    }

    private PrivilegeLoadResult reload() throws Exception {
        MetaInfo info = DB_SERVICE.createMetaInfo(connection, new PostgreSqlMetadataProvider());
        Catalog catalog = new CwmLoaderImpl().load(info, JdbcToCwmConfig.all());
        return new PrivilegeLoaderImpl().load(info, catalog);
    }

    private static ResponsibleParty partyByName(PrivilegeLoadResult result, String name) {
        return result.parties().stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }

    private static <T> T firstOf(ResponsibleParty party, Class<T> type) {
        return party.getOwnedElement().stream().filter(type::isInstance).map(type::cast)
                .findFirst().orElseThrow();
    }

    private static ModelElement find(Catalog catalog, String schemaName, String name) {
        for (ModelElement se : catalog.getOwnedElement()) {
            if (se instanceof Schema schema && schemaName.equals(schema.getName())) {
                for (ModelElement e : schema.getOwnedElement()) {
                    if (name.equals(e.getName())) {
                        return e;
                    }
                }
            }
        }
        throw new IllegalStateException("not found in catalog: " + schemaName + "." + name);
    }

    private static ResponsibleParty party(String name) {
        ResponsibleParty party = BF.createResponsibleParty();
        party.setName(name);
        party.setResponsibility("database role");
        return party;
    }
}
