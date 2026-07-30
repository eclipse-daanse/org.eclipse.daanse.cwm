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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.api.PrivilegeLoadResult;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.postgresql.PostgresDatabaseProvider;
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
 * PG round trip for the privilege loader: create roles with table, column,
 * function, sequence and role-membership grants, snapshot via the PG
 * MetadataProvider, load the structural CWM catalog (CwmLoader) plus the
 * privilege parties (PrivilegeLoader) from the same snapshot and assert that
 * exactly the common core arrives: the table SELECT, the function EXECUTE and
 * the role membership resolve, while the column grant and the sequence grant
 * are skipped.
 */
@TestInstance(Lifecycle.PER_CLASS)
class PrivilegeLoaderRoundTripPgTest {

    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

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
    void privileges_round_trip_into_parties() throws Exception {
        String schemaName = "grant_rt";
        String roleName = "grant_rt_reader";
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE SCHEMA \"" + schemaName + "\"");
            s.execute("CREATE TABLE \"" + schemaName + "\".\"CUSTOMERS\" (\"ID\" integer PRIMARY KEY, "
                    + "\"EMAIL\" varchar(120))");
            s.execute("CREATE ROLE \"" + roleName + "\" NOLOGIN");
            s.execute("CREATE ROLE \"grant_rt_parent\" NOLOGIN");
            s.execute("CREATE ROLE \"grant_rt_unused\" NOLOGIN");
            // Rollenmitgliedschaft: GRANT rolle TO rolle WITH ADMIN OPTION.
            s.execute("GRANT \"grant_rt_parent\" TO \"" + roleName + "\" WITH ADMIN OPTION");
            s.execute("CREATE FUNCTION \"" + schemaName + "\".fn_answer() RETURNS integer LANGUAGE sql "
                    + "AS 'SELECT 42'");
            s.execute("CREATE SEQUENCE \"" + schemaName + "\".\"SEQ_ORDERS\"");
            s.execute("GRANT SELECT ON \"" + schemaName + "\".\"CUSTOMERS\" TO \"" + roleName + "\"");
            // Spaltenprivileg — kommt als spalteneingeschraenktes TablePrivilege an.
            s.execute("GRANT UPDATE (\"EMAIL\") ON \"" + schemaName + "\".\"CUSTOMERS\" TO \"" + roleName + "\"");
            s.execute("GRANT EXECUTE ON FUNCTION \"" + schemaName + "\".fn_answer() TO \"" + roleName + "\"");
            // Sequenzprivileg — kein Routine-EXECUTE, darf nicht ankommen.
            s.execute("GRANT USAGE ON SEQUENCE \"" + schemaName + "\".\"SEQ_ORDERS\" TO \"" + roleName + "\"");
        }

        // Der PG-Provider sammelt Privilegien fuer das aktuelle Schema der Connection.
        connection.setSchema(schemaName);
        MetaInfo info = DB_SERVICE.createMetaInfo(connection, new PostgreSqlMetadataProvider());
        Catalog catalog = new CwmLoaderImpl().load(info, JdbcToCwmConfig.all());
        PrivilegeLoadResult result = new PrivilegeLoaderImpl().load(info, catalog);
        List<ResponsibleParty> parties = result.parties();
        List<Stereotype> stereotypes = result.stereotypes();

        assertThat(PrivilegeModelValidator.validateAll(parties)).isEmpty();

        // Principals: unprivilegierte Rolle erscheint; Marker via Stereotype, nicht Text.
        List<String> roleNames = PrivilegeStereotypes.databaseRoles(stereotypes).stream()
                .map(ResponsibleParty::getName).toList();
        assertThat(roleNames).contains(roleName, "grant_rt_parent", "grant_rt_unused");
        ResponsibleParty unused = parties.stream()
                .filter(p -> "grant_rt_unused".equals(p.getName())).findFirst().orElseThrow();
        assertThat(unused.getOwnedElement()).isEmpty();
        // Der Login-User des Containers ist als databaseUser markiert.
        assertThat(PrivilegeStereotypes.databaseUsers(stereotypes)).isNotEmpty();

        ResponsibleParty reader = parties.stream()
                .filter(p -> roleName.equals(p.getName()))
                .findFirst().orElseThrow();

        List<TablePrivilege> tablePrivileges = reader.getOwnedElement().stream()
                .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast).toList();
        // SELECT auf der ganzen Tabelle plus UPDATE eingeschraenkt auf EMAIL.
        assertThat(tablePrivileges).hasSize(2);
        TablePrivilege select = tablePrivileges.stream()
                .filter(prv -> prv.getAction() == TablePrivilegeAction.SELECT).findFirst().orElseThrow();
        assertThat(select.getTable()).isInstanceOf(Table.class);
        assertThat(select.getTable().getName()).isEqualTo("CUSTOMERS");
        assertThat(select.getColumn()).isEmpty();
        assertThat(select.getGrantor()).isNotNull();
        TablePrivilege updateEmail = tablePrivileges.stream()
                .filter(prv -> prv.getAction() == TablePrivilegeAction.UPDATE).findFirst().orElseThrow();
        assertThat(updateEmail.getColumn()).extracting(c -> c.getName()).containsExactly("EMAIL");

        List<ProcedurePrivilege> procedurePrivileges = reader.getOwnedElement().stream()
                .filter(ProcedurePrivilege.class::isInstance).map(ProcedurePrivilege.class::cast).toList();
        assertThat(procedurePrivileges).hasSize(1);
        assertThat(procedurePrivileges.get(0).getProcedure().getName()).isEqualTo("fn_answer");

        // Mitgliedschaft aus pg_auth_members: MEMBER OF grant_rt_parent mit ADMIN OPTION.
        List<RolePrivilege> memberships = reader.getOwnedElement().stream()
                .filter(RolePrivilege.class::isInstance).map(RolePrivilege.class::cast).toList();
        assertThat(memberships).hasSize(1);
        RolePrivilege memberOf = memberships.get(0);
        assertThat(memberOf.getRole().getName()).isEqualTo("grant_rt_parent");
        assertThat(memberOf.getGrantable()).isTrue();
        assertThat(memberOf.getGrantor()).isNotNull();

        // Sequenz-USAGE wurde uebersprungen: keine weiteren Privilegien.
        assertThat(reader.getOwnedElement()).hasSize(4);
    }
}
