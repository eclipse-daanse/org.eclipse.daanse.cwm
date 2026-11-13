/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegePackage;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.junit.jupiter.api.Test;

class PrivilegeRulesTest {

    @Test
    void privilegesRolesAndUsers() {
        ShopWithGrants m = new ShopWithGrants();

        assertThat(PrivilegeRules.PRIVILEGES.test(m.updateQty)).isTrue();
        assertThat(PrivilegeRules.PRIVILEGES.test(m.reader)).isFalse();
        assertThat(PrivilegeRules.ROLES.test(m.reader)).isTrue();
        assertThat(PrivilegeRules.ROLES.test(m.alice)).isFalse();
        assertThat(PrivilegeRules.ROLES.test(m.databaseRole)).isFalse();
        assertThat(PrivilegeRules.USERS.test(m.alice)).isTrue();
        assertThat(PrivilegeRules.USERS.describe()).isEqualTo("is:user");
        Selection noPrivileges = Selection.parse(PrivilegeRules.VOCABULARY, "!is:privilege", "!is:role");
        assertThat(noPrivileges.excludesKind(PrivilegePackage.Literals.TABLE_PRIVILEGE)).isTrue();
        assertThat(noPrivileges.excludesKind(PrivilegePackage.Literals.ROLE_PRIVILEGE)).isTrue();
        assertThat(noPrivileges.excludesKind(BusinessinformationPackage.Literals.RESPONSIBLE_PARTY))
                .as("a role is told by its stereotype, not by its kind").isFalse();
    }

    @Test
    void vocabulary_holdsTheWordsOfTheModelsBelow() {
        assertThat(PrivilegeRules.VOCABULARY.selectors()).contains("schema", "table", "name", "path", "kind",
                "tag", "stereotype", "is");
        assertThat(PrivilegeRules.VOCABULARY.rules()).contains("privilege", "role", "user", "comment",
                "constraint", "documentary", "table", "index");
    }

    @Test
    void consequences_ownFirst_thenTheModelsBelow_coreLast() {
        assertThat(PrivilegeRules.CONSEQUENCES).map(ConsequenceRule::describe).containsExactly(
                "table privilege without its table or a column", "procedure privilege without its procedure",
                "membership without its role", "SQL data type", "foreign key without its key or a column",
                "key without a column", "index without its table or a column", "trigger without its table",
                "description of nothing", "dependency on a removed supplier", "required reference");
    }

    @Test
    void membershipWithoutItsRole_goes() {
        ShopWithGrants m = new ShopWithGrants();

        Selected<Catalog> cut = copy(m, "!name:shop_reader");

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.shop_reader", "ResponsibleParty", "name:shop_reader"),
                new LeftOut("shop.shop_writer", "RolePrivilege",
                        "membership without its role (role: shop.shop_reader)"));
    }

    @Test
    void procedurePrivilegeWithoutItsProcedure_goes() {
        ShopWithGrants m = new ShopWithGrants();

        Selected<Catalog> cut = copy(m, "!is:procedure");

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.p_archive", "Procedure", "is:procedure"),
                new LeftOut("shop.shop_writer", "ProcedurePrivilege",
                        "procedure privilege without its procedure (procedure: shop.sales.p_archive)"));
    }

    @Test
    void columnGrantThatLosesAColumn_goes_ratherThanGrantTheWholeTable() {
        ShopWithGrants m = new ShopWithGrants();

        Selected<Catalog> cut = copy(m, "!path:shop.sales.orders.qty");

        assertThat(cut.leftOut()).contains(new LeftOut("shop.shop_writer", "TablePrivilege",
                "table privilege without its table or a column (column: shop.sales.orders.qty)"));
        assertThat(Selections.list(cut.model(), Selection.all(), TablePrivilege.class))
                .allSatisfy(p -> assertThat(p.getAction().getLiteral()).isNotEqualTo("UPDATE"));
    }

    @Test
    void privilegeThatLosesItsGrantor_stays() {
        ShopWithGrants m = new ShopWithGrants();
        m.updateQty.setGrantor(m.alice);

        Selected<Catalog> cut = copy(m, "!is:user");

        assertThat(cut.leftOut()).extracting(LeftOut::path).containsExactly("shop.alice");
        TablePrivilege update = Selections.list(cut.model(), Selection.all(), TablePrivilege.class).stream()
                .filter(p -> !p.getColumn().isEmpty()).findFirst().orElseThrow();
        assertThat(update.getGrantor()).isNull();
        assertThat(QualifiedNames.of(update.getColumn().get(0))).isEqualTo("shop.sales.orders.qty");
        assertThat(m.updateQty.getGrantor()).isSameAs(m.alice);
    }

    private static Selected<Catalog> copy(ShopWithGrants m, String... rules) {
        return Selections.copy(m.shop, Selection.parse(PrivilegeRules.VOCABULARY, rules), PrivilegeRules.CONSEQUENCES);
    }
}
