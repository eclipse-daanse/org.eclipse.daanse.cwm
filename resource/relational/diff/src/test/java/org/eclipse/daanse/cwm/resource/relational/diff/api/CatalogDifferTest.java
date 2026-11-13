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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * The comparison of two catalogs finds what differs in both directions — schemas, tables, columns,
 * roles, privileges, memberships — and says it in readable lines naming each element.
 */
class CatalogDifferTest {

    private final CatalogDiffer differ = CatalogDiffer.create();

    @Test
    void aCatalogEqualsItsCopy() {
        Catalog shop = shop();

        CatalogDiff diff = differ.diff(shop, EcoreUtil.copy(shop));

        assertThat(diff.isEmpty()).isTrue();
        assertThat(diff.lines()).isEmpty();
    }

    @Test
    void aMissingColumnAChangedLengthAndAMissingPrivilegeAreFound() {
        Catalog written = shop();
        Catalog readBack = EcoreUtil.copy(written);
        Table customer = customer(readBack);
        EcoreUtil.delete(ColumnSets.findColumn(customer, "referred_by").orElseThrow(), true);
        ColumnSets.findColumn(customer, "name").orElseThrow().setLength(50);
        ResponsibleParty reader = party(readBack, "shop_reader");
        EcoreUtil.delete(reader.getOwnedElement().stream().filter(TablePrivilege.class::isInstance).findFirst()
                .orElseThrow(), true);

        List<String> lines = differ.diff(written, readBack).lines();

        assertThat(lines).containsExactly("column shop.sales.customer.name: size (100) left, (50) right",
                "column shop.sales.customer.referred_by: only left",
                "privilege of shop_reader: SELECT on shop.sales.customer only left");
    }

    @Test
    void whatOnlyTheSecondHoldsIsFoundToo() {
        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        Schemas.create(right, "archive");
        Table audit = TestModels.table(Catalogs.findSchema(right, "sales").orElseThrow(), "audit", "id");
        Columns.create(customer(right), "nickname", type(right, "VARCHAR", Types.VARCHAR),
                NullableType.COLUMN_NULLABLE, 20, 0, 0);
        role(right, "shop_auditor");
        grant(party(right, "shop_reader"), TablePrivilegeAction.SELECT, audit, false);

        CatalogDiff diff = differ.diff(left, right);

        assertThat(diff.schemasOnlyRight()).extracting(Schema::getName).containsExactly("archive");
        assertThat(diff.partiesOnlyRight()).extracting(ResponsibleParty::getName).containsExactly("shop_auditor");
        assertThat(diff.lines()).containsExactly("column shop.sales.customer.nickname: only right",
                "privilege of shop_reader: SELECT on shop.sales.audit only right", "role shop_auditor: only right",
                "schema shop.archive: only right", "table shop.sales.audit: only right");
        assertThat(differ.diff(right, left).lines()).as("and the other way round")
                .containsExactly("column shop.sales.customer.nickname: only left",
                        "privilege of shop_reader: SELECT on shop.sales.audit only left", "role shop_auditor: only left",
                        "schema shop.archive: only left", "table shop.sales.audit: only left");
    }

    @Test
    void howAPrivilegeOrAMembershipIsGrantedAndHowAPartyIsMarkedCount() {
        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        ResponsibleParty writer = party(right, "shop_writer");
        writer.getOwnedElement().stream().filter(RolePrivilege.class::isInstance).map(RolePrivilege.class::cast)
                .forEach(m -> m.setGrantable(false));
        Stereotype user = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_USER);
        right.getOwnedElement().add(user);
        user.getExtendedElement().add(writer);

        CatalogDiff diff = differ.diff(left, right);

        assertThat(diff.parties()).singleElement().satisfies(p -> {
            assertThat(p.stereotypesOnlyRight()).containsExactly(PrivilegeStereotypes.DATABASE_USER);
            assertThat(p.privilegesChanged()).singleElement()
                    .satisfies(c -> assertThat(c.aspects()).containsExactly(PrivilegeChange.Aspect.GRANTABLE));
        });
        assertThat(diff.lines()).containsExactly(
                "membership of shop_writer: member of shop_reader grantable yes left, no right",
                "role shop_writer: stereotype databaseUser only right");
    }

    @Test
    void privilegesOfPartiesTheFirstDoesNotHoldPassWithTheirTolerance() {
        Catalog withoutParties = shop();
        Namespaces.ownedElements(withoutParties, ResponsibleParty.class).forEach(p -> EcoreUtil.delete(p, true));
        Catalog readBack = shop();

        assertThat(differ.diff(withoutParties, readBack).partiesOnlyRight()).hasSize(2);
        assertThat(differ.diff(withoutParties, readBack,
                DiffSettings.defaults().withTolerances(Tolerance.IGNORE_EXTRA_PARTIES)).isEmpty()).isTrue();
        assertThat(differ.diff(readBack, withoutParties,
                DiffSettings.defaults().withTolerances(Tolerance.IGNORE_EXTRA_PARTIES)).partiesOnlyLeft())
                .as("only the second model's parties pass").hasSize(2);
    }

    @Test
    void theSchemaLinesAreThoseOfTheSchemaDiff() {
        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        ColumnSets.findColumn(customer(right), "name").orElseThrow().setLength(80);

        CatalogDiff diff = differ.diff(left, right);

        assertThat(diff.schemas()).singleElement()
                .satisfies(s -> assertThat(diff.lines()).containsExactlyElementsOf(s.lines()));
    }

    // fixture

    /**
     * {@code shop.sales.customer(id PK, name VARCHAR(100), referred_by)}; the roles
     * {@code shop_reader} (SELECT on customer) and {@code shop_writer} (member of shop_reader with
     * admin option).
     */
    static Catalog shop() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table customer = Tables.create(sales, "customer");
        Column id = Columns.create(customer, "id", type(shop, "INTEGER", Types.INTEGER), NullableType.COLUMN_NO_NULLS);
        Columns.create(customer, "name", type(shop, "VARCHAR", Types.VARCHAR), NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        Columns.create(customer, "referred_by", type(shop, "INTEGER", Types.INTEGER), NullableType.COLUMN_NULLABLE);
        PrimaryKeys.create(customer, "pk_customer", id);
        ResponsibleParty reader = role(shop, "shop_reader");
        ResponsibleParty writer = role(shop, "shop_writer");
        grant(reader, TablePrivilegeAction.SELECT, customer, false);
        RolePrivilege membership = PrivilegeFactory.eINSTANCE.createRolePrivilege();
        membership.setRole(reader);
        membership.setGrantable(Boolean.TRUE);
        writer.getOwnedElement().add(membership);
        return shop;
    }

    private static SQLSimpleType type(Catalog catalog, String name, int code) {
        return Namespaces.findOwnedByName(catalog, SQLSimpleType.class, name)
                .orElseGet(() -> SQLSimpleTypes.create(catalog, name, code));
    }

    private static ResponsibleParty role(Catalog catalog, String name) {
        Stereotype databaseRole = Namespaces.findOwnedByName(catalog, Stereotype.class, PrivilegeStereotypes.DATABASE_ROLE)
                .orElseGet(() -> {
                    Stereotype s = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
                    catalog.getOwnedElement().add(s);
                    return s;
                });
        ResponsibleParty role = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        role.setName(name);
        catalog.getOwnedElement().add(role);
        databaseRole.getExtendedElement().add(role);
        return role;
    }

    static TablePrivilege grant(ResponsibleParty grantee, TablePrivilegeAction action, Table table,
            boolean grantable) {
        TablePrivilege privilege = PrivilegeFactory.eINSTANCE.createTablePrivilege();
        privilege.setAction(action);
        privilege.setTable(table);
        privilege.setGrantable(grantable);
        grantee.getOwnedElement().add(privilege);
        return privilege;
    }

    static ResponsibleParty party(Catalog catalog, String name) {
        return Namespaces.findOwnedByName(catalog, ResponsibleParty.class, name).orElseThrow();
    }

    static Table customer(Catalog catalog) {
        return Schemas.findTable(Catalogs.findSchema(catalog, "sales").orElseThrow(), "customer").orElseThrow();
    }
}
