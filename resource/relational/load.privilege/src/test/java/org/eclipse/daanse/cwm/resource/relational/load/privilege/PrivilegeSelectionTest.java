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

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.record.meta.DatabaseInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.MetaInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.StructureInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.DatabasePrincipalRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.RoleMembershipRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TablePrivilegeRecord;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/**
 * The parties and privileges as a part of the catalog, chosen by the same selection: alone, as the
 * part reads them, and with the {@code CwmLoader}, which cuts the whole catalog.
 */
class PrivilegeSelectionTest {

    private static final TableReference CUSTOMERS = table("shop", "customers");
    private static final TableReference SECRETS = table("vault", "secrets");

    @Test
    void anExcludedPartyIsNotRead_norItsMemberships_andAsGrantorItIsLeftOut() {
        Catalog catalog = load("!is:user", "!name:readonly");

        assertThat(CatalogParties.of(catalog)).extracting(ResponsibleParty::getName).containsExactly("app_reader");
        ResponsibleParty reader = CatalogParties.named(catalog, "app_reader");
        assertThat(reader.getOwnedElement()).noneMatch(RolePrivilege.class::isInstance)
                .allSatisfy(p -> assertThat(((TablePrivilege) p).getGrantor()).isNull());
        assertThat(stereotypes(catalog)).containsExactly(PrivilegeStereotypes.DATABASE_ROLE);
        assertThat(PrivilegeStereotypes.isDatabaseRole(reader)).isTrue();
    }

    @Test
    void withoutPrivilegesThePartiesStay() {
        Catalog catalog = load("!is:privilege");

        assertThat(CatalogParties.of(catalog)).extracting(ResponsibleParty::getName)
                .containsExactly("alice", "app_reader", "readonly");
        assertThat(CatalogParties.of(catalog)).allSatisfy(p -> assertThat(p.getOwnedElement()).isEmpty());
    }

    @Test
    void withoutPartiesNothingOfThePrivilegesIsRead() {
        Catalog catalog = load("!kind:ResponsibleParty");

        assertThat(catalog.getOwnedElement()).extracting(e -> e.eClass().getName()).doesNotContain(
                "ResponsibleParty", "Stereotype");
    }

    @Test
    void aSchemaRuleDoesNotReachTheParties_aRoleRuleBringsThemWithTheirPrivilegesOnWhatIsRead() {
        Catalog schemaOnly = new CwmLoaderImpl(List.of(new PrivilegeLoaderImpl())).load(snapshot(), config("schema:shop"));
        Catalog withRoles = new CwmLoaderImpl(List.of(new PrivilegeLoaderImpl())).load(snapshot(),
                config("schema:shop", "is:role"));

        assertThat(schemaOnly.getOwnedElement()).extracting(e -> e.eClass().getName())
                .containsExactly("Schema", "SQLSimpleType");
        assertThat(withRoles.getOwnedElement()).filteredOn(e -> !(e instanceof Schema)).extracting(ModelElement::getName)
                .containsExactly("INTEGER", "databaseRole", "app_reader", "readonly");
        ResponsibleParty reader = CatalogParties.named(withRoles, "app_reader");
        assertThat(reader.getOwnedElement()).extracting(ModelElement::getName)
                .as("the privilege on vault.secrets goes with the schema; alice, a user, is no grantor here")
                .containsExactly("SELECT ON shop.customers", "MEMBER OF readonly");
        assertThat(((TablePrivilege) reader.getOwnedElement().get(0)).getGrantor()).isNull();
        assertThat(PrivilegeStereotypes.isDatabaseRole(reader)).isTrue();
    }

    // --- helpers -----------------------------------------------------------------------------------

    /** The privilege part alone, on the catalog of the snapshot's structure. */
    private static Catalog load(String... rules) {
        MetaInfo info = snapshot();
        Catalog catalog = new CwmLoaderImpl().load(info, JdbcToCwmConfig.defaults());
        new PrivilegeLoaderImpl().load(info, catalog, Selection.parse(PrivilegeRules.VOCABULARY, rules));
        return catalog;
    }

    private static JdbcToCwmConfig config(String... rules) {
        return JdbcToCwmConfig.defaults().withSelection(Selection.parse(PrivilegeRules.VOCABULARY, rules));
    }

    private static List<String> stereotypes(Catalog catalog) {
        return catalog.getOwnedElement().stream().filter(Stereotype.class::isInstance).map(ModelElement::getName)
                .toList();
    }

    /**
     * Two tables in two schemas; the role {@code app_reader} may read both and is a member of
     * {@code readonly}, both granted by the user {@code alice}.
     */
    private static MetaInfo snapshot() {
        StructureInfoRecord structure = new StructureInfoRecord(List.of(),
                List.of(new SchemaReference("shop"), new SchemaReference("vault")),
                List.of(new TableDefinitionRecord(CUSTOMERS), new TableDefinitionRecord(SECRETS)),
                List.of(column(CUSTOMERS, "id"), column(SECRETS, "id")), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new TablePrivilegeRecord(CUSTOMERS, Optional.of("alice"), "app_reader", "SELECT",
                        Optional.of("NO")),
                        new TablePrivilegeRecord(SECRETS, Optional.of("alice"), "app_reader", "SELECT",
                                Optional.of("NO"))),
                List.of(), List.of(),
                List.of(new RoleMembershipRecord("app_reader", "readonly", Optional.of("alice"), Optional.of("NO"))),
                List.of(new DatabasePrincipalRecord("app_reader", DatabasePrincipal.KIND_ROLE),
                        new DatabasePrincipalRecord("readonly", DatabasePrincipal.KIND_ROLE),
                        new DatabasePrincipalRecord("alice", DatabasePrincipal.KIND_USER)));
        return new MetaInfoRecord(new DatabaseInfoRecord("shop", "1.0", 1, 0), structure, null, List.of(),
                List.of());
    }

    private static TableReference table(String schema, String name) {
        return new TableReference(Optional.of(new SchemaReference(schema)), name, TableReference.TYPE_TABLE);
    }

    private static ColumnDefinitionRecord column(TableReference table, String name) {
        return new ColumnDefinitionRecord(new ColumnReference(Optional.of(table), name),
                new ColumnMetaDataRecord(JDBCType.INTEGER, "INTEGER", OptionalInt.of(10), OptionalInt.empty(),
                        OptionalInt.of(10), ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(),
                        Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.NO,
                        ColumnMetaData.GeneratedColumn.NO));
    }
}
