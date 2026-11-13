/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.junit.jupiter.api.Test;

/** Roles and grants in the scripts: written as the settings say, and undone in reverse. */
class PrivilegeScriptTest {

    /** Catalog {@code shop}, schema {@code sales} with {@code orders}; role {@code reader} may read it. */
    private static Catalog shop() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Stereotype databaseRole = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
        shop.getOwnedElement().add(databaseRole);
        ResponsibleParty reader = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        reader.setName("reader");
        shop.getOwnedElement().add(reader);
        databaseRole.getExtendedElement().add(reader);
        ResponsibleParty clerk = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        clerk.setName("clerk");
        shop.getOwnedElement().add(clerk);
        databaseRole.getExtendedElement().add(clerk);
        TablePrivilege select = PrivilegeFactory.eINSTANCE.createTablePrivilege();
        select.setAction(TablePrivilegeAction.SELECT);
        select.setTable(orders);
        reader.getOwnedElement().add(select);
        RolePrivilege membership = PrivilegeFactory.eINSTANCE.createRolePrivilege();
        membership.setRole(reader);
        clerk.getOwnedElement().add(membership);
        return shop;
    }

    @Test
    void grantsNameTheTableAsTheSettingsSay() {
        Catalog shop = shop();

        List<String> create = create(shop, DdlSettings.defaults().withIncludeSchema(false));

        assertThat(create).contains("GRANT SELECT ON \"orders\" TO \"reader\"");
    }

    @Test
    void theDropScriptRevokesWhatTheCreateScriptGrantedBeforeItDropsTheRoles() {
        Catalog shop = shop();

        List<String> drop = drop(shop, DdlSettings.defaults());

        assertThat(drop).contains("REVOKE SELECT ON \"sales\".\"orders\" FROM \"reader\"",
                "REVOKE \"reader\" FROM \"clerk\"");
        assertThat(drop.indexOf("REVOKE \"reader\" FROM \"clerk\"")).isLessThan(drop.indexOf("DROP ROLE \"reader\""));
        assertThat(drop.indexOf("REVOKE SELECT ON \"sales\".\"orders\" FROM \"reader\""))
                .isLessThan(drop.indexOf("DROP ROLE \"reader\""));
    }

    /**
     * A party marked {@code databaseUser} is created as a user, without credentials, after the roles
     * and before the grants, and dropped after the revokes; a dialect without users, DuckDB's,
     * writes none, and the grant to the user stays.
     */
    @Test
    void usersAreCreatedBeforeTheGrantsWhereTheDialectHasUsers() {
        Catalog shop = shop();
        ResponsibleParty auditor = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        auditor.setName("auditor");
        shop.getOwnedElement().add(auditor);
        Stereotype databaseUser = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_USER);
        shop.getOwnedElement().add(databaseUser);
        databaseUser.getExtendedElement().add(auditor);
        RolePrivilege membership = PrivilegeFactory.eINSTANCE.createRolePrivilege();
        membership.setRole(Namespaces.findOwnedByName(shop, ResponsibleParty.class, "reader").orElseThrow());
        auditor.getOwnedElement().add(membership);
        DdlSettings settings = DdlSettings.defaults().withIfNotExists(false);

        List<String> create = create(shop, settings);
        List<String> drop = drop(shop, settings);

        assertThat(create).contains("CREATE USER \"auditor\"", "GRANT \"reader\" TO \"auditor\"");
        assertThat(create.indexOf("CREATE USER \"auditor\"")).isGreaterThan(create.indexOf("CREATE ROLE \"reader\""))
                .isLessThan(create.indexOf("GRANT \"reader\" TO \"auditor\""));
        assertThat(drop.indexOf("DROP USER \"auditor\""))
                .isGreaterThan(drop.indexOf("REVOKE \"reader\" FROM \"auditor\""))
                .isLessThan(drop.indexOf("DROP ROLE \"reader\""));
        assertThat(new CwmDdlRenderer(List.of(Dialects.require("duckdb")), settings).scripts(shop).get(0).createSql())
                .noneMatch(s -> s.contains("USER"));
    }

    @Test
    void aSelectionWithoutPrivilegesWritesNeitherRolesNorGrants() {
        Catalog shop = shop();
        Catalog structure = CwmDdlRenderer.select(shop, CwmDdlScripts.selection(List.of("!is:privilege", "!is:role")))
                .model();

        DdlScript script = new CwmDdlRenderer(List.of(Dialects.require("postgresql"))).scripts(structure).get(0);

        assertThat(script.createSql()).isNotEmpty().noneMatch(s -> s.contains("ROLE") || s.startsWith("GRANT"));
        assertThat(script.dropSql()).isNotEmpty().noneMatch(s -> s.contains("ROLE") || s.startsWith("REVOKE"));
    }

    @Test
    void eachRoleAndGrantNamesItsPartyOrPrivilege() {
        Catalog shop = shop();

        List<DdlStatement> create = new CwmDdlRenderer(List.of(Dialects.require("postgresql"))).scripts(shop).get(0)
                .create();

        assertThat(create).filteredOn(s -> s.sql().startsWith("CREATE ROLE"))
                .allSatisfy(s -> assertThat(s.element()).isInstanceOf(ResponsibleParty.class));
        assertThat(create).filteredOn(s -> s.sql().startsWith("GRANT"))
                .allSatisfy(s -> assertThat(s.element()).isInstanceOf(Privilege.class));
    }

    private static List<String> create(Catalog shop, DdlSettings settings) {
        return new CwmDdlRenderer(List.of(Dialects.require("postgresql")), settings).scripts(shop).get(0).createSql();
    }

    private static List<String> drop(Catalog shop, DdlSettings settings) {
        return new CwmDdlRenderer(List.of(Dialects.require("postgresql")), settings).scripts(shop).get(0).dropSql();
    }
}
