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
import static org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDifferTest.customer;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDifferTest.party;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDifferTest.shop;

import java.sql.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * Each tolerance lets exactly its kind of difference pass and nothing else; without it, the
 * difference counts.
 */
class ToleranceTest {

    private final CatalogDiffer differ = CatalogDiffer.create();

    @Test
    void typeByJdbcCodeComparesTheCodeNotTheName() {
        assertTolerated(Tolerance.TYPE_BY_JDBC_CODE, right -> ColumnSets.findColumn(customer(right), "name")
                .orElseThrow().setType(type(right, "CHARACTER VARYING", Types.VARCHAR)),
                "column shop.sales.customer.name: type VARCHAR left, CHARACTER VARYING (VARCHAR) right");
        assertStillCounts(Tolerance.TYPE_BY_JDBC_CODE, right -> retype(right, "BIGINT", Types.BIGINT));

        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        Columns.create(customer(left), "price", type(left, "DECIMAL", Types.DECIMAL)).setPrecision(10);
        Columns.create(customer(right), "price", type(right, "numeric", Types.NUMERIC)).setPrecision(10);
        assertThat(differ.diff(left, right).lines()).as("DECIMAL and NUMERIC")
                .containsExactly("column shop.sales.customer.price: type DECIMAL left, numeric right");
        assertThat(differ.diff(left, right, DiffSettings.defaults().withTolerances(Tolerance.TYPE_BY_JDBC_CODE))
                .isEmpty()).as("DECIMAL and NUMERIC are one").isTrue();
    }

    @Test
    void integerAsNumericTakesAnIntegerForANumericWithoutPrecision() {
        assertTolerated(Tolerance.INTEGER_AS_NUMERIC, right -> retype(right, "NUMBER", Types.NUMERIC),
                "column shop.sales.customer.id: size none left, (0, 0) right",
                "column shop.sales.customer.id: type INTEGER left, NUMBER (NUMERIC) right");
        assertStillCounts(Tolerance.INTEGER_AS_NUMERIC, right -> {
            retype(right, "NUMBER", Types.NUMERIC);
            ColumnSets.findColumn(customer(right), "id").orElseThrow().setScale(2);
        });
    }

    @Test
    void ignoreConstraintNamesMatchesConstraintsByWhatTheyConstrain() {
        assertTolerated(Tolerance.IGNORE_CONSTRAINT_NAMES,
                right -> UniqueConstraints.create(customer(right), "customer_name_key", name(right)),
                left -> UniqueConstraints.create(customer(left), "uq_customer_name", name(left)),
                "unique constraint shop.sales.customer.uq_customer_name: name uq_customer_name left,"
                        + " customer_name_key right");
        assertStillCounts(Tolerance.IGNORE_CONSTRAINT_NAMES,
                right -> UniqueConstraints.create(customer(right), "uq_customer_name", name(right)));
    }

    @Test
    void ignoreExpressionTextLetsRewrittenConditionsQueriesAndDefaultsPass() {
        assertTolerated(Tolerance.IGNORE_EXPRESSION_TEXT, right -> check(customer(right), "(id > 0)"),
                left -> check(customer(left), "id > 0"), "check constraint shop.sales.customer.ck_customer: only left",
                "check constraint shop.sales.customer.ck_customer: only right");
        assertTolerated(Tolerance.IGNORE_EXPRESSION_TEXT,
                right -> Views.create(sales(right), "v", "SELECT c.id FROM sales.customer c"),
                left -> Views.create(sales(left), "v", "SELECT id FROM sales.customer"),
                "view shop.sales.v: query differs");
        assertTolerated(Tolerance.IGNORE_EXPRESSION_TEXT, right -> defaultOf(customer(right), "'x'::character varying"),
                left -> defaultOf(customer(left), "'x'"),
                "column shop.sales.customer.name: default 'x' left, 'x'::character varying right");
        assertStillCounts(Tolerance.IGNORE_EXPRESSION_TEXT, right -> check(customer(right), "id > 0"));
    }

    @Test
    void ignoreCharacterLengthLetsAMissingLengthPass() {
        assertTolerated(Tolerance.IGNORE_CHARACTER_LENGTH,
                right -> ColumnSets.findColumn(customer(right), "name").orElseThrow().setLength(0),
                "column shop.sales.customer.name: size (100) left, none right");
        assertStillCounts(Tolerance.IGNORE_CHARACTER_LENGTH, right -> {
            Column amount = Columns.create(customer(right), "amount", type(right, "DECIMAL", Types.DECIMAL));
            amount.setPrecision(12);
        });
    }

    @Test
    void ignorePrimaryKeyNamesComparesTheColumnsOfAKeyOnly() {
        assertTolerated(Tolerance.IGNORE_PRIMARY_KEY_NAMES, right -> primaryKey(right).setName("customer_id_pkey"),
                "primary key shop.sales.customer.pk_customer: name pk_customer left, customer_id_pkey right");
        assertStillCounts(Tolerance.IGNORE_PRIMARY_KEY_NAMES,
                right -> primaryKey(right).getFeature().add(ColumnSets.findColumn(customer(right), "name").orElseThrow()));
    }

    @Test
    void ignoreForeignKeyRulesLetsAnotherRulePass() {
        assertTolerated(Tolerance.IGNORE_FK_RULES,
                right -> foreignKey(right).setDeleteRule(ReferentialRuleType.IMPORTED_KEY_RESTRICT),
                left -> foreignKey(left), "foreign key shop.sales.customer.fk_customer_referred_by: only left",
                "foreign key shop.sales.customer.fk_customer_referred_by: only right");
        assertStillCounts(Tolerance.IGNORE_FK_RULES, right -> {
            ForeignKey fk = foreignKey(right);
            fk.getFeature().clear();
            fk.getFeature().add(ColumnSets.findColumn(customer(right), "id").orElseThrow());
        });
    }

    @Test
    void ignoreGrantorLetsTheGrantingUserPass() {
        assertTolerated(Tolerance.IGNORE_GRANTOR, right -> {
            ResponsibleParty login = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
            login.setName("shop");
            right.getOwnedElement().add(login);
            party(right, "shop_reader").getOwnedElement().stream().filter(Privilege.class::isInstance)
                    .map(Privilege.class::cast).forEach(p -> p.setGrantor(login));
        }, "privilege of shop_reader: SELECT on shop.sales.customer grantor none left, shop right");
        assertStillCounts(Tolerance.IGNORE_GRANTOR, right -> party(right, "shop_reader").getOwnedElement().stream()
                .filter(Privilege.class::isInstance).map(Privilege.class::cast).forEach(p -> p.setGrantable(true)));
    }

    @Test
    void ignoreExtraPartiesLetsPartiesOnlyTheSecondHoldsPass() {
        assertTolerated(Tolerance.IGNORE_EXTRA_PARTIES, right -> {
            ResponsibleParty publicParty = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
            publicParty.setName("PUBLIC");
            right.getOwnedElement().add(publicParty);
            CatalogDifferTest.grant(publicParty, TablePrivilegeAction.SELECT, customer(right), false);
        }, "party PUBLIC: only right");
        assertStillCounts(Tolerance.IGNORE_EXTRA_PARTIES,
                right -> CatalogDifferTest.grant(party(right, "shop_reader"), TablePrivilegeAction.UPDATE,
                        customer(right), false));
    }

    @Test
    void caseInsensitiveNamesMatchesNamesInAnotherCase() {
        assertTolerated(Tolerance.CASE_INSENSITIVE_NAMES, right -> customer(right).setName("CUSTOMER"),
                "privilege of shop_reader: SELECT on shop.sales.CUSTOMER only right",
                "privilege of shop_reader: SELECT on shop.sales.customer only left",
                "table shop.sales.customer: name customer left, CUSTOMER right");
        assertStillCounts(Tolerance.CASE_INSENSITIVE_NAMES, right -> customer(right).setName("client"));
    }

    @Test
    void byDefaultNamesCompareExactly() {
        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        customer(right).setName("Customer");
        TestModels.table(sales(right), "Orders", "id");
        TestModels.table(sales(left), "orders", "id");

        SchemaDiff diff = differ.diff(left, right).schemas().get(0);

        assertThat(diff.tablesDropped()).extracting(Table::getName).containsExactlyInAnyOrder("customer", "orders");
        assertThat(diff.tablesAdded()).extracting(Table::getName).containsExactlyInAnyOrder("Customer", "Orders");
    }

    @Test
    void aRoundTripTakesWhatEveryDatabaseNeedsAndWhatItsOwnNeeds() {
        Set<Tolerance> base = EnumSet.of(Tolerance.TYPE_BY_JDBC_CODE, Tolerance.IGNORE_EXPRESSION_TEXT,
                Tolerance.IGNORE_GRANTOR, Tolerance.IGNORE_EXTRA_PARTIES);

        assertThat(Tolerance.roundTrip("unknown")).isEqualTo(base);
        assertThat(Tolerance.roundTrip(null)).isEqualTo(base);
        assertThat(Tolerance.roundTrip("postgresql")).isEqualTo(base);
        assertThat(Tolerance.roundTrip("PG")).isEqualTo(Tolerance.roundTrip("postgres"))
                .isEqualTo(Tolerance.roundTrip("postgresql"));
        assertThat(Tolerance.roundTrip("DuckDB")).containsAll(base).contains(Tolerance.IGNORE_CHARACTER_LENGTH,
                Tolerance.IGNORE_PRIMARY_KEY_NAMES, Tolerance.IGNORE_CONSTRAINT_NAMES, Tolerance.IGNORE_FK_RULES);
        assertThat(Tolerance.roundTrip("mariadb")).containsAll(base).contains(Tolerance.IGNORE_PRIMARY_KEY_NAMES)
                .doesNotContain(Tolerance.IGNORE_CONSTRAINT_NAMES);
        assertThat(Tolerance.roundTrip("MySQL")).as("MySQL names every primary key PRIMARY, as MariaDB does")
                .isEqualTo(Tolerance.roundTrip("mariadb"));
        assertThat(Tolerance.roundTrip("oracle")).containsAll(base).contains(Tolerance.INTEGER_AS_NUMERIC);
        assertThat(Tolerance.roundTrip("mssql")).isEqualTo(Tolerance.roundTrip("sqlserver"));
        for (String id : List.of("postgresql", "duckdb", "h2", "mariadb", "mysql", "oracle", "sqlserver")) {
            assertThat(Tolerance.roundTrip(id)).as(id).containsAll(base)
                    .doesNotContain(Tolerance.CASE_INSENSITIVE_NAMES);
        }
        Set<Tolerance> mine = Tolerance.roundTrip("h2");
        mine.add(Tolerance.CASE_INSENSITIVE_NAMES);
        assertThat(Tolerance.roundTrip("h2")).as("a new set each time").doesNotContain(Tolerance.CASE_INSENSITIVE_NAMES);
    }

    // helpers

    private void assertTolerated(Tolerance tolerance, Consumer<Catalog> changeRight, String... linesWithout) {
        assertTolerated(tolerance, changeRight, left -> {
        }, linesWithout);
    }

    /**
     * The catalogs, changed, differ in exactly {@code linesWithout} without {@code tolerance}, not at
     * all with it, and still with every other tolerance alone.
     */
    private void assertTolerated(Tolerance tolerance, Consumer<Catalog> changeRight, Consumer<Catalog> changeLeft,
            String... linesWithout) {
        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        changeLeft.accept(left);
        changeRight.accept(right);

        assertThat(differ.diff(left, right).lines()).as("without %s", tolerance).containsExactly(linesWithout);
        assertThat(differ.diff(left, right, DiffSettings.defaults().withTolerances(tolerance)).lines())
                .as("with %s", tolerance).isEmpty();
        for (Tolerance other : Tolerance.values()) {
            if (other != tolerance) {
                assertThat(differ.diff(left, right, DiffSettings.defaults().withTolerances(other)).isEmpty())
                        .as("%s does not let pass what %s does", other, tolerance).isFalse();
            }
        }
    }

    /** A difference of another kind still counts with {@code tolerance}. */
    private void assertStillCounts(Tolerance tolerance, Consumer<Catalog> changeRight) {
        Catalog left = shop();
        Catalog right = EcoreUtil.copy(left);
        changeRight.accept(right);

        assertThat(differ.diff(left, right, DiffSettings.defaults().withTolerances(tolerance)).isEmpty())
                .as("still counts with %s", tolerance).isFalse();
    }

    private static org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema sales(Catalog catalog) {
        return org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs.findSchema(catalog, "sales")
                .orElseThrow();
    }

    private static SQLSimpleType type(Catalog catalog, String name, int code) {
        return SQLSimpleTypes.create(catalog, name, code);
    }

    private static void retype(Catalog catalog, String name, int code) {
        ColumnSets.findColumn(customer(catalog), "id").orElseThrow().setType(type(catalog, name, code));
    }

    private static CheckConstraint check(Table table, String condition) {
        CheckConstraint check = RelationalFactory.eINSTANCE.createCheckConstraint();
        BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
        body.setLanguage("SQL");
        body.setBody(condition);
        check.setBody(body);
        table.getOwnedElement().add(check);
        return check;
    }

    private static void defaultOf(Table table, String body) {
        Expression expression = CoreFactory.eINSTANCE.createExpression();
        expression.setLanguage("SQL");
        expression.setBody(body);
        ColumnSets.findColumn(table, "name").orElseThrow().setInitialValue(expression);
    }

    private static Column name(Catalog catalog) {
        return ColumnSets.findColumn(customer(catalog), "name").orElseThrow();
    }

    private static PrimaryKey primaryKey(Catalog catalog) {
        return Tables.findPrimaryKey(customer(catalog)).orElseThrow();
    }

    /** The foreign key {@code customer.referred_by → customer}, created where it is not yet. */
    private static ForeignKey foreignKey(Catalog catalog) {
        Table customer = customer(catalog);
        return Tables.foreignKeys(customer).stream().findFirst().orElseGet(() -> ForeignKeys.create(customer,
                "fk_customer_referred_by", primaryKey(catalog),
                ColumnSets.findColumn(customer, "referred_by").orElseThrow()));
    }
}
