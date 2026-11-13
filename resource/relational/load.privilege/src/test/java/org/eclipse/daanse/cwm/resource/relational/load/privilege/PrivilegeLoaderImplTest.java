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

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.sql.jdbc.api.meta.DatabaseInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IdentifierInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.api.schema.ObjectPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.RoleMembership;
import org.eclipse.daanse.sql.jdbc.record.meta.StructureInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnPrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.DatabasePrincipalRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ObjectPrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.RoleMembershipRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TablePrivilegeRecord;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

class PrivilegeLoaderImplTest {

    private static final TableReference CUSTOMERS = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "public")), "customers");
    private static final TableReference GHOST = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "public")), "ghost");

    @Test
    void commonCoreLoadsPartiesAndPrivileges() {
        Catalog catalog = cwmCatalog();

        List<org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege> tablePrivileges = List.of(
                new TablePrivilegeRecord(CUSTOMERS, Optional.of("alice"), "app_reader", "SELECT", Optional.of("NO")),
                new TablePrivilegeRecord(CUSTOMERS, Optional.of("alice"), "app_reader", "INSERT", Optional.of("YES")),
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), "PUBLIC", "SELECT", Optional.empty()),
                // Nicht Teil des gemeinsamen Kerns: wird uebersprungen.
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), "intern", "DENY DELETE", Optional.empty()),
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), "app_reader", "TRUNCATE", Optional.empty()),
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), "app_reader", "QUERY REWRITE",
                        Optional.empty()),
                // Ziel nicht im CWM-Katalog: wird uebersprungen.
                new TablePrivilegeRecord(GHOST, Optional.empty(), "app_reader", "SELECT", Optional.empty()));
        // Spaltenprivilegien verdichten sich zu einem spalteneingeschraenkten
        // TablePrivilege je (Grantee, Aktion, Tabelle); unbekannte Spalten werden
        // uebersprungen.
        List<ColumnPrivilege> columnPrivileges = List.of(
                new ColumnPrivilegeRecord(new ColumnReference(Optional.of(CUSTOMERS), "email"), Optional.of("alice"),
                        "app_reader", "UPDATE", Optional.of("NO")),
                new ColumnPrivilegeRecord(new ColumnReference(Optional.of(CUSTOMERS), "name"), Optional.of("alice"),
                        "app_reader", "UPDATE", Optional.of("NO")),
                new ColumnPrivilegeRecord(new ColumnReference(Optional.of(CUSTOMERS), "ghost_col"),
                        Optional.empty(), "app_reader", "UPDATE", Optional.empty()));
        List<ObjectPrivilege> objectPrivileges = List.of(
                new ObjectPrivilegeRecord("FUNCTION", Optional.empty(), Optional.of("public"), "fn_answer",
                        Optional.of("alice"), "app_reader", "EXECUTE", Optional.empty()),
                // Nicht-Routine-Objektprivilegien werden uebersprungen.
                new ObjectPrivilegeRecord("SEQUENCE", Optional.empty(), Optional.of("public"), "seq_orders",
                        Optional.empty(), "app_reader", "USAGE", Optional.empty()));
        List<RoleMembership> memberships = List.of(
                new RoleMembershipRecord("app_reader", "readonly", Optional.of("alice"), Optional.of("YES")),
                // Selbstmitgliedschaft und leere Namen werden uebersprungen.
                new RoleMembershipRecord("loop", "loop", Optional.empty(), Optional.empty()),
                new RoleMembershipRecord("app_reader", " ", Optional.empty(), Optional.empty()));
        List<DatabasePrincipal> principals = List.of(
                new DatabasePrincipalRecord("app_reader", DatabasePrincipal.KIND_ROLE),
                new DatabasePrincipalRecord("alice", DatabasePrincipal.KIND_USER),
                // Unprivilegierte Rolle: erscheint nur ueber die Principal-Liste.
                new DatabasePrincipalRecord("unused_role", DatabasePrincipal.KIND_ROLE),
                // MySQL-Fall: strukturell nicht unterscheidbar -> kein Stereotype.
                new DatabasePrincipalRecord("mystery", DatabasePrincipal.KIND_UNKNOWN));

        List<ResponsibleParty> parties = load(
                metaInfo(tablePrivileges, columnPrivileges, objectPrivileges, memberships, principals), catalog);

        // Parties: die Principals (inkl. unprivilegierter Rolle), PUBLIC und die nur aus
        // Grant-Zeilen bekannte Rolle readonly; "intern" hatte nur die uebersprungene
        // DENY-Zeile, "loop" nur die Selbstmitgliedschaft — beide existieren nicht.
        assertThat(parties.stream().map(ResponsibleParty::getName))
                .containsExactlyInAnyOrder("app_reader", "PUBLIC", "alice", "readonly", "unused_role",
                        "mystery");
        ResponsibleParty appReader = parties.stream()
                .filter(p -> "app_reader".equals(p.getName())).findFirst().orElseThrow();
        assertThat(appReader.getResponsibility()).isEqualTo(PrivilegeLoaderImpl.RESPONSIBILITY_DATABASE_ROLE);

        // Stereotype-Marker: Entscheidungen laufen ueber sie, nicht ueber responsibility. Der Katalog
        // haelt Parties und Marker, die Marker vor den Parties.
        Catalog loaded = catalog;
        assertThat(catalog.getOwnedElement()).extracting(e -> e.eClass().getName()).containsSubsequence(
                "Schema", "Stereotype", "Stereotype", "ResponsibleParty");
        assertThat(PrivilegeStereotypes.databaseRoles(loaded).stream().map(ResponsibleParty::getName))
                .containsExactlyInAnyOrder("app_reader", "unused_role");
        assertThat(PrivilegeStereotypes.databaseUsers(loaded).stream().map(ResponsibleParty::getName))
                .containsExactlyInAnyOrder("alice");
        assertThat(PrivilegeStereotypes.isDatabaseRole(appReader)).isTrue();
        ResponsibleParty aliceParty = parties.stream()
                .filter(p -> "alice".equals(p.getName())).findFirst().orElseThrow();
        assertThat(aliceParty.getResponsibility()).isEqualTo(PrivilegeLoaderImpl.RESPONSIBILITY_DATABASE_USER);
        // PUBLIC und die nur aus Grants bekannte readonly-Rolle tragen keinen Stereotype.
        ResponsibleParty publicPseudo = parties.stream()
                .filter(p -> "PUBLIC".equals(p.getName())).findFirst().orElseThrow();
        assertThat(PrivilegeStereotypes.isDatabaseRole(publicPseudo)).isFalse();
        assertThat(publicPseudo.getResponsibility()).isEqualTo(PrivilegeLoaderImpl.RESPONSIBILITY_PUBLIC);
        ResponsibleParty unusedRole = parties.stream()
                .filter(p -> "unused_role".equals(p.getName())).findFirst().orElseThrow();
        assertThat(unusedRole.getOwnedElement()).isEmpty();
        // KIND_UNKNOWN: Party erscheint, traegt aber keinen Marker.
        ResponsibleParty mystery = parties.stream()
                .filter(p -> "mystery".equals(p.getName())).findFirst().orElseThrow();
        assertThat(PrivilegeStereotypes.isDatabaseRole(mystery)).isFalse();
        assertThat(PrivilegeStereotypes.databaseUsers(loaded)).doesNotContain(mystery);
        assertThat(mystery.getResponsibility()).isEqualTo(PrivilegeLoaderImpl.RESPONSIBILITY_DATABASE_ROLE);

        // app_reader: SELECT + INSERT auf der Tabelle, UPDATE (email, name) als
        // Spalteneinschraenkung, EXECUTE auf der Funktion, Mitgliedschaft in readonly.
        assertThat(appReader.getOwnedElement()).hasSize(5);
        TablePrivilege updateColumns = appReader.getOwnedElement().stream()
                .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast)
                .filter(prv -> prv.getAction() == TablePrivilegeAction.UPDATE).findFirst().orElseThrow();
        assertThat(updateColumns.getColumn()).extracting(c -> c.getName())
                .containsExactly("email", "name");
        assertThat(updateColumns.getTable().getName()).isEqualTo("customers");
        assertThat(updateColumns.getGrantable()).isFalse();
        TablePrivilege select = appReader.getOwnedElement().stream()
                .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast)
                .filter(p -> p.getAction() == TablePrivilegeAction.SELECT).findFirst().orElseThrow();
        assertThat(select.getTable().getName()).isEqualTo("customers");
        assertThat(select.getGrantable()).isFalse();
        assertThat(select.getGrantor().getName()).isEqualTo("alice");
        assertThat(select.getNamespace()).isSameAs(appReader);

        TablePrivilege insert = appReader.getOwnedElement().stream()
                .filter(TablePrivilege.class::isInstance).map(TablePrivilege.class::cast)
                .filter(p -> p.getAction() == TablePrivilegeAction.INSERT).findFirst().orElseThrow();
        assertThat(insert.getGrantable()).isTrue();

        // Aktion implizit EXECUTE — die Klasse ist die Aktion.
        ProcedurePrivilege execute = appReader.getOwnedElement().stream()
                .filter(ProcedurePrivilege.class::isInstance).map(ProcedurePrivilege.class::cast)
                .findFirst().orElseThrow();
        assertThat(execute.getProcedure().getName()).isEqualTo("fn_answer");

        // Mitgliedschaft: GRANT readonly TO app_reader WITH ADMIN OPTION.
        RolePrivilege memberOf = appReader.getOwnedElement().stream()
                .filter(RolePrivilege.class::isInstance).map(RolePrivilege.class::cast)
                .findFirst().orElseThrow();
        assertThat(memberOf.getRole().getName()).isEqualTo("readonly");
        assertThat(memberOf.getGrantable()).isTrue();
        assertThat(memberOf.getGrantor().getName()).isEqualTo("alice");

        // PUBLIC ist eine gewoehnliche Party mit dem SELECT.
        ResponsibleParty publicParty = parties.stream()
                .filter(p -> "PUBLIC".equals(p.getName())).findFirst().orElseThrow();
        assertThat(publicParty.getOwnedElement()).hasSize(1);
        // Grantor alice traegt selbst keine Privilegien.
        assertThat(aliceParty.getOwnedElement()).isEmpty();

        assertThat(PrivilegeModelValidator.validateAll(parties)).isEmpty();
    }

    @Test
    void rowsWithoutGranteeOrPrivilegeAreSkipped() {
        Catalog catalog = cwmCatalog();
        ColumnReference email = new ColumnReference(Optional.of(CUSTOMERS), "email");
        List<org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege> tablePrivileges = List.of(
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), null, "SELECT", Optional.empty()),
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), "app_reader", null, Optional.empty()),
                new TablePrivilegeRecord(CUSTOMERS, Optional.empty(), "app_reader", "SELECT", Optional.empty()));
        List<ColumnPrivilege> columnPrivileges = List.of(
                new ColumnPrivilegeRecord(email, Optional.empty(), null, "UPDATE", Optional.empty()),
                new ColumnPrivilegeRecord(email, Optional.empty(), "app_reader", null, Optional.empty()));
        List<ObjectPrivilege> objectPrivileges = List.of(
                new ObjectPrivilegeRecord("FUNCTION", Optional.empty(), Optional.of("public"), "fn_answer",
                        Optional.empty(), null, "EXECUTE", Optional.empty()),
                new ObjectPrivilegeRecord("FUNCTION", Optional.empty(), Optional.of("public"), "fn_answer",
                        Optional.empty(), "app_reader", null, Optional.empty()),
                new ObjectPrivilegeRecord("FUNCTION", Optional.empty(), Optional.of("public"), null,
                        Optional.empty(), "app_reader", "EXECUTE", Optional.empty()));
        List<RoleMembership> memberships = List.of(
                new RoleMembershipRecord(null, "readonly", Optional.empty(), Optional.empty()),
                new RoleMembershipRecord("app_reader", null, Optional.empty(), Optional.empty()));
        List<DatabasePrincipal> principals = List.of(new DatabasePrincipalRecord(null, DatabasePrincipal.KIND_ROLE),
                new DatabasePrincipalRecord("app_reader", DatabasePrincipal.KIND_ROLE));

        List<ResponsibleParty> parties = load(
                metaInfo(tablePrivileges, columnPrivileges, objectPrivileges, memberships, principals), catalog);

        assertThat(parties).extracting(ResponsibleParty::getName).containsExactly("app_reader");
        assertThat(parties.get(0).getOwnedElement()).singleElement()
                .isInstanceOfSatisfying(TablePrivilege.class,
                        p -> assertThat(p.getAction()).isEqualTo(TablePrivilegeAction.SELECT));
    }

    /**
     * PostgreSQL names the overload a routine privilege is on by its specific name, the name and the
     * oid; the loader keeps it on the routine. Each row reaches its overload, whatever their order,
     * with its own grant option; a row on one of two overloads reaches that one only.
     */
    @Test
    void eachRowReachesTheOverloadItsSpecificNameNames() {
        Catalog catalog = cwmCatalog();
        Procedure first = fnAnswer(catalog);
        TaggedValues.set(first, RelationalNames.SPECIFIC_NAME_TAG, "fn_answer_101");
        Procedure second = overload(first);
        TaggedValues.set(second, RelationalNames.SPECIFIC_NAME_TAG, "fn_answer_102");

        List<ResponsibleParty> parties = load(metaInfo(List.of(), List.of(),
                List.of(execute("app_writer", "fn_answer_102", "YES"), execute("app_reader", "fn_answer_102", "NO"),
                        execute("app_writer", "fn_answer_101", "NO")),
                List.of(), List.of()), catalog);

        assertThat(privileges(parties, "app_reader")).extracting(ProcedurePrivilege::getProcedure)
                .containsExactly(second);
        assertThat(privileges(parties, "app_writer"))
                .extracting(p -> p.getProcedure() == first ? "first " + p.getGrantable() : "second " + p.getGrantable())
                .containsExactlyInAnyOrder("first false", "second true");
    }

    /**
     * A row that names no overload by its specific name reaches the routine of its name where the
     * schema holds one, and is left out where it holds several, rather than given to an arbitrary one.
     */
    @Test
    void aRowWithoutTheSpecificNameOfAnOverloadIsNotGivenToAnArbitraryOne() {
        Catalog catalog = cwmCatalog();
        Procedure first = fnAnswer(catalog);
        ObjectPrivilege byName = new ObjectPrivilegeRecord("FUNCTION", Optional.empty(), Optional.of("public"),
                "fn_answer", Optional.empty(), "app_reader", "EXECUTE", Optional.empty());

        assertThat(privileges(load(metaInfo(List.of(), List.of(), List.of(byName), List.of(), List.of()), catalog),
                "app_reader")).extracting(ProcedurePrivilege::getProcedure).containsExactly(first);

        Catalog withOverloads = cwmCatalog();
        overload(fnAnswer(withOverloads));
        assertThat(load(metaInfo(List.of(), List.of(), List.of(byName, execute("app_reader", "fn_answer_9", "NO")),
                List.of(), List.of()), withOverloads)).allSatisfy(p -> assertThat(p.getOwnedElement()).isEmpty());
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static Catalog cwmCatalog() {
        RelationalFactory rf = RelationalFactory.eINSTANCE;
        Catalog catalog = rf.createCatalog();
        catalog.setName("test");
        Schema schema = rf.createSchema();
        schema.setName("public");
        catalog.getOwnedElement().add(schema);
        Table customers = rf.createTable();
        customers.setName("customers");
        schema.getOwnedElement().add(customers);
        for (String col : List.of("email", "name")) {
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column = rf.createColumn();
            column.setName(col);
            customers.getFeature().add(column);
        }
        Procedure fnAnswer = rf.createProcedure();
        fnAnswer.setName("fn_answer");
        schema.getOwnedElement().add(fnAnswer);
        return catalog;
    }

    /** Loads the privileges of {@code info} into {@code catalog}, all of them, and gives its parties. */
    private static List<ResponsibleParty> load(MetaInfo info, Catalog catalog) {
        new PrivilegeLoaderImpl().load(info, catalog, Selection.all());
        return CatalogParties.of(catalog);
    }

    private static Procedure fnAnswer(Catalog catalog) {
        return (Procedure) catalog.getOwnedElement().stream().filter(Schema.class::isInstance)
                .map(Schema.class::cast).findFirst().orElseThrow().getOwnedElement().stream()
                .filter(Procedure.class::isInstance).findFirst().orElseThrow();
    }

    /** {@code EXECUTE} on the overload of {@code public.fn_answer} with the specific name {@code specificName}. */
    private static ObjectPrivilege execute(String grantee, String specificName, String grantable) {
        return new ObjectPrivilegeRecord("FUNCTION", Optional.empty(), Optional.of("public"), "fn_answer",
                Optional.empty(), grantee, "EXECUTE", Optional.of(grantable), specificName);
    }

    /** The routine privileges of the party {@code name}. */
    private static List<ProcedurePrivilege> privileges(List<ResponsibleParty> parties, String name) {
        return parties.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow().getOwnedElement()
                .stream().map(ProcedurePrivilege.class::cast).toList();
    }

    /** A second routine of the same name next to {@code procedure}, as an overload with other parameters. */
    private static Procedure overload(Procedure procedure) {
        Procedure overload = RelationalFactory.eINSTANCE.createProcedure();
        overload.setName(procedure.getName());
        ((Schema) procedure.getNamespace()).getOwnedElement().add(overload);
        return overload;
    }

    private static MetaInfo metaInfo(List<org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege> tablePrivileges,
            List<ColumnPrivilege> columnPrivileges, List<ObjectPrivilege> objectPrivileges,
            List<RoleMembership> roleMemberships, List<DatabasePrincipal> principals) {
        StructureInfo structureInfo = new StructureInfoRecord(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), tablePrivileges, columnPrivileges, objectPrivileges,
                roleMemberships, principals);
        return new MetaInfo() {

            @Override
            public DatabaseInfo databaseInfo() {
                return null;
            }

            @Override
            public IdentifierInfo identifierInfo() {
                return null;
            }

            @Override
            public List<TypeInfo> typeInfos() {
                return List.of();
            }

            @Override
            public StructureInfo structureInfo() {
                return structureInfo;
            }

            @Override
            public List<IndexInfo> indexInfos() {
                return List.of();
            }
        };
    }
}
