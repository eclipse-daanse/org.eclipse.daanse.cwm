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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.PrivilegeSqlGeneratorImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.MariaDbMetadataProvider;
import org.eclipse.daanse.sql.jdbc.metadata.MicrosoftSqlServerMetadataProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cross-Dialekt-Härtung des {@code PrivilegeSqlGenerator}: dieselben
 * Modell-Privilegien werden je Dialekt gerendert und gegen eine echte Datenbank
 * ausgeführt (H2 in-process, MariaDB und SQL Server via Testcontainers —
 * activateOrSkip, wenn kein Docker verfügbar ist). Zurückgelesen wird, was der
 * jeweilige Metadata-Provider hergibt: SQL Server Tabellen-Privilegien +
 * Mitgliedschaften + Principals, MariaDB Mitgliedschaften + Principals, H2
 * nichts (reiner Ausführungs-Beweis). Abschließend REVOKE + DROP ROLE über den
 * Generator. PostgreSQL deckt {@code PrivilegeSqlRoundTripPgTest} vollständig ab.
 */
class PrivilegeSqlCrossDialectTest {

    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();
    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;
    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;

    enum Db {
        H2("h2", H2Dialect::new, () -> null,
                // H2: keine Spaltenprivilegien, nichts zurücklesbar — Ausführungs-Beweis.
                false, false, false, "xd",
                List.of("CREATE SCHEMA \"xd\"",
                        "CREATE TABLE \"xd\".\"customers\" (\"id\" integer PRIMARY KEY, \"email\" varchar(120))")),
        MARIADB("mariadb", MariaDBDialect::new, MariaDbMetadataProvider::new,
                true, false, true, "xd",
                List.of("CREATE SCHEMA `xd`",
                        "CREATE TABLE `xd`.`customers` (`id` integer PRIMARY KEY, `email` varchar(120))")),
        // MSSQL liest Privilegien fuers aktuelle Schema (dbo) — Tabelle liegt dort.
        MSSQL("mssql", MicrosoftSqlServerDialect::new, MicrosoftSqlServerMetadataProvider::new,
                true, true, true, "dbo",
                List.of("CREATE TABLE dbo.customers (id integer PRIMARY KEY, email varchar(120))"));

        final String providerId;
        final Supplier<Dialect> dialect;
        final Supplier<MetadataProvider> metadataProvider;
        final boolean columnGrants;
        final boolean reloadTablePrivileges;
        final boolean reloadMembershipsAndPrincipals;
        final String schemaName;
        final List<String> structureSql;

        Db(String providerId, Supplier<Dialect> dialect, Supplier<MetadataProvider> metadataProvider,
                boolean columnGrants, boolean reloadTablePrivileges, boolean reloadMembershipsAndPrincipals,
                String schemaName, List<String> structureSql) {
            this.providerId = providerId;
            this.dialect = dialect;
            this.metadataProvider = metadataProvider;
            this.columnGrants = columnGrants;
            this.reloadTablePrivileges = reloadTablePrivileges;
            this.reloadMembershipsAndPrincipals = reloadMembershipsAndPrincipals;
            this.schemaName = schemaName;
            this.structureSql = structureSql;
        }
    }

    static Stream<Db> dialects() {
        return Stream.of(Db.values());
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void generatedSqlExecutesAndReloadsWhereReadable(Db db) throws Exception {
        ActiveDatabase active = activateOrSkip(db);
        try (Connection connection = active.dataSource().getConnection()) {
            try (Statement s = connection.createStatement()) {
                for (String sql : db.structureSql) {
                    s.execute(sql);
                }
            }
            if (db == Db.MARIADB) {
                // MariaDB fuehrt die Datenbank im Catalog-Slot.
                connection.setCatalog("xd");
            }

            // Katalog laden; Ziele referenzieren die geladenen CWM-Objekte.
            Catalog catalog = new CwmLoaderImpl().load(DB_SERVICE.createMetaInfo(connection),
                    JdbcToCwmConfig.all());
            Table customers = (Table) find(catalog, db.schemaName, "customers");

            ResponsibleParty reader = party("xd_reader");
            ResponsibleParty parent = party("xd_parent");
            TablePrivilege select = PF.createTablePrivilege();
            select.setAction(TablePrivilegeAction.SELECT);
            select.setTable(customers);
            reader.getOwnedElement().add(select);
            RolePrivilege membership = PF.createRolePrivilege();
            membership.setRole(parent);
            membership.setGrantable(Boolean.TRUE); // MSSQL/H2 ignorieren das dialektbedingt
            reader.getOwnedElement().add(membership);
            java.util.List<Privilege> privileges = new java.util.ArrayList<>(List.of(select, membership));
            if (db.columnGrants) {
                Column email = customers.getFeature().stream()
                        .filter(Column.class::isInstance).map(Column.class::cast)
                        .filter(c -> "email".equals(c.getName())).findFirst().orElseThrow();
                TablePrivilege updateEmail = PF.createTablePrivilege();
                updateEmail.setAction(TablePrivilegeAction.UPDATE);
                updateEmail.setTable(customers);
                updateEmail.getColumn().add(email);
                reader.getOwnedElement().add(updateEmail);
                privileges.add(updateEmail);
            }

            PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(db.dialect.get());

            try (Statement s = connection.createStatement()) {
                for (String sql : generator.createRoleStatements(List.of(reader, parent))) {
                    s.execute(sql);
                }
                for (String sql : generator.grantStatements(privileges)) {
                    s.execute(sql);
                }
            }

            if (db.metadataProvider.get() != null) {
                MetaInfo info = DB_SERVICE.createMetaInfo(connection, db.metadataProvider.get());
                var loaded = new PrivilegeLoaderImpl().load(info, catalog);
                if (db.reloadMembershipsAndPrincipals) {
                    ResponsibleParty loadedReader = loaded.parties().stream()
                            .filter(p -> "xd_reader".equals(p.getName())).findFirst().orElseThrow();
                    assertThat(loadedReader.getOwnedElement().stream()
                            .filter(RolePrivilege.class::isInstance).map(RolePrivilege.class::cast)
                            .map(rp -> rp.getRole().getName()))
                            .contains("xd_parent");
                    assertThat(PrivilegeStereotypes.databaseRoles(loaded.stereotypes()).stream()
                            .map(ResponsibleParty::getName))
                            .contains("xd_reader", "xd_parent");
                }
                if (db.reloadTablePrivileges) {
                    ResponsibleParty loadedReader = loaded.parties().stream()
                            .filter(p -> "xd_reader".equals(p.getName())).findFirst().orElseThrow();
                    assertThat(loadedReader.getOwnedElement().stream()
                            .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast)
                            .filter(tp -> tp.getAction() == TablePrivilegeAction.SELECT)
                            .map(tp -> tp.getTable().getName()))
                            .contains("customers");
                }
            }

            // Gegenprobe und Aufraeumen ueber den Generator.
            try (Statement s = connection.createStatement()) {
                for (String sql : generator.revokeStatements(privileges)) {
                    s.execute(sql);
                }
                for (String sql : generator.dropRoleStatements(List.of(reader, parent))) {
                    s.execute(sql);
                }
            }
        }
    }

    private static ActiveDatabase activateOrSkip(Db db) {
        try {
            return DatabaseProvider.byId(db.providerId).activate();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "database '" + db.providerId + "' unavailable: " + t);
            throw new IllegalStateException("unreachable");
        }
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
