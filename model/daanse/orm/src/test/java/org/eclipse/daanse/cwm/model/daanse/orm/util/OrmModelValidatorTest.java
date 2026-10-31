/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.Converter;
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.GeneratedValue;
import org.eclipse.daanse.cwm.model.daanse.orm.GenerationType;
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.InheritanceType;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedNativeQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.SqlResultSetMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator.Severity;
import org.junit.jupiter.api.Test;

/**
 * The explicit profile, one test per rule: the {@link Shop} fixture is free of findings
 * (positive case), each test breaks it in one place (negative case).
 */
class OrmModelValidatorTest {

    private final Shop shop = new Shop();

    @Test
    void theFixtureIsExplicitAndComplete() {
        assertThat(OrmModelValidator.check(shop.em)).isEmpty();
        assertThat(OrmModelValidator.validate(shop.em)).isEmpty();
    }

    @Test
    void validateReturnsOnlyErrors() {
        shop.extraColumn("CUSTOMER", "NOTE");
        assertThat(OrmModelValidator.check(shop.em)).extracting(Finding::severity).containsOnly(Severity.WARNING);
        assertThat(OrmModelValidator.validate(shop.em)).isEmpty();
    }

    @Test
    void multiplicity() {
        assertThat(rules()).doesNotContain("MULTIPLICITY");
        multiplicity(shop.toCustomer, 0, -1);
        assertThat(errors("MULTIPLICITY")).anySatisfy(m -> assertThat(m).contains("to-one"));
    }

    @Test
    void toManyAgainstOneEndIsReported() {
        multiplicity(shop.toOrders, 0, 1);
        assertThat(errors("MULTIPLICITY")).anySatisfy(m -> assertThat(m).contains("to-many"));
    }

    @Test
    void owningSide() {
        assertThat(rules()).doesNotContain("OWNING_SIDE");
        shop.orders.unsetOwningSide();
        assertThat(errors("OWNING_SIDE")).anySatisfy(m -> assertThat(m).contains("owning side"));
    }

    @Test
    void e1_everyAttributeAndNavigableEndHasExactlyOneMapping() {
        assertThat(errors("E1")).as("positive").isEmpty();
        shop.customer.getBasic().clear();
        assertThat(errors("E1")).anySatisfy(m -> assertThat(m).contains("'name' is not mapped"));
    }

    @Test
    void e1_transientMarksANonPersistentFeature() {
        assertThat(errors("E1")).as("positive").isEmpty();
        shop.customer.getBasic().clear();
        Transients.create(shop.customer, shop.customerName);
        shop.extraColumnUse();
        assertThat(errors("E1")).isEmpty();
    }

    @Test
    void e1_anUnmappedNavigableEndIsReported() {
        assertThat(errors("E1")).as("positive").isEmpty();
        shop.customer.getOneToMany().clear();
        assertThat(errors("E1")).anySatisfy(m -> assertThat(m).contains("'orders' is not mapped"));
        shop.toOrders.setIsNavigable(false);
        assertThat(errors("E1")).isEmpty();
    }

    @Test
    void e1_aForeignOrDuplicateFeatureIsReported() {
        assertThat(errors("E1")).as("positive").isEmpty();
        Transients.create(shop.customer, shop.orderTotal);
        assertThat(errors("E1")).anySatisfy(m -> assertThat(m).contains("not an own feature"));
        Transients.create(shop.order, shop.orderTotal);
        assertThat(errors("E1")).anySatisfy(m -> assertThat(m).contains("mapped 2 times"));
    }

    @Test
    void e2_aBasicInAnEntityNamesItsColumn() {
        assertThat(errors("E2")).as("positive").isEmpty();
        shop.customer.getBasic().get(0).setColumn(null);
        shop.extraColumnUse();
        assertThat(errors("E2")).anySatisfy(m -> assertThat(m).contains("names its column"));
    }

    @Test
    void e2_everyUserOverridesWhatTheEmbeddableLeavesOpen() {
        assertThat(errors("E2")).as("positive").isEmpty();
        Embedded address = shop.customer.getEmbedded().get(0);
        address.getAttributeOverride().remove(1);
        assertThat(errors("E2")).anySatisfy(m -> assertThat(m).contains("'city' has no column"));
    }

    @Test
    void e2_tablePerClassOverridesWhatItInheritsFromAnEntity() {
        assertThat(errors("E2")).as("positive").isEmpty();
        Entity vip = shop.vipCustomer();
        shop.customer.setInheritanceStrategy(InheritanceType.TABLEPERCLASS);
        Table vipTable = shop.newTable("VIP", "ID", "NAME", "STREET", "CITY");
        vip.setCwmTable(vipTable);
        assertThat(errors("E2"))
                .anySatisfy(m -> assertThat(m).contains("'id' from").contains("AttributeOverride missing"))
                .anySatisfy(m -> assertThat(m).contains("'name' from"))
                .anySatisfy(m -> assertThat(m).contains("'address.street'"));
        Attribute id = shop.customer.getId().get(0).getCwmAttribute();
        Embedded address = shop.customer.getEmbedded().get(0);
        Overrides.attribute(vip, shop.col("VIP", "ID"), id);
        Overrides.attribute(vip, shop.col("VIP", "NAME"), shop.customerName);
        Overrides.attribute(vip, shop.col("VIP", "STREET"), address.getCwmAttribute(),
                address.getAttributeOverride().get(0).getCwmPath().get(0));
        Overrides.attribute(vip, shop.col("VIP", "CITY"), address.getCwmAttribute(),
                address.getAttributeOverride().get(1).getCwmPath().get(0));
        assertThat(errors("E2")).isEmpty();
    }

    @Test
    void e3_columnsLiveInTheEntityTable() {
        assertThat(errors("E3")).as("positive").isEmpty();
        shop.customer.getBasic().get(0).setColumn(Columns.of(shop.col("ORDERS", "TOTAL")));
        assertThat(errors("E3")).anySatisfy(m -> assertThat(m).contains("ORDERS.TOTAL"));
    }

    @Test
    void e3_nullabilityAgainstTheLowerBoundIsAWarning() {
        assertThat(errors("E3")).as("positive").isEmpty();
        multiplicity(shop.customerName, 1, 1);
        shop.col("CUSTOMER", "NAME").setIsNullable(NullableType.COLUMN_NULLABLE);
        assertThat(OrmModelValidator.check(shop.em)).filteredOn(f -> f.rule().equals("E3"))
                .extracting(Finding::severity).containsExactly(Severity.WARNING);
        assertThat(errors("E3")).isEmpty();
    }

    @Test
    void e4_theOwningSideHasExactlyOneConnection() {
        assertThat(errors("E4")).as("positive").isEmpty();
        shop.order.getManyToOne().get(0).setJoin(null);
        assertThat(errors("E4")).anySatisfy(m -> assertThat(m).contains("exactly one connection"));
    }

    @Test
    void e4_theInverseSideCarriesNoConnectionAndNeedsItsOwner() {
        assertThat(errors("E4")).as("positive").isEmpty();
        shop.orders.setJoin(Joins.of(shop.fkOrdersCustomer));
        assertThat(errors("E4")).anySatisfy(m -> assertThat(m).contains("inverse side carries no"));
        shop.orders.setJoin(null);
        shop.order.getManyToOne().clear();
        shop.toCustomer.setIsNavigable(false);
        assertThat(errors("E4")).anySatisfy(m -> assertThat(m).contains("not mapped as the owning side"));
    }

    @Test
    void e5_aJoinIsAForeignKeyOrColumnPairsNotBoth() {
        assertThat(errors("E5")).as("positive").isEmpty();
        Join join = shop.order.getManyToOne().get(0).getJoin();
        join.getJoinColumn().addAll(Joins.column(shop.col("ORDERS", "CUSTOMER_ID"),
                shop.col("CUSTOMER", "ID")).getJoinColumn());
        assertThat(errors("E5")).anySatisfy(m -> assertThat(m).contains("not both"));
    }

    @Test
    void e5_theReferencedColumnsLiveInTheTargetTable() {
        assertThat(errors("E5")).as("positive").isEmpty();
        shop.order.getManyToOne().get(0)
                .setJoin(Joins.column(shop.col("ORDERS", "CUSTOMER_ID"), shop.col("ORDERS", "ID")));
        assertThat(errors("E5")).anySatisfy(m -> assertThat(m).contains("expected [CUSTOMER]"));
    }

    @Test
    void e6_theRootOfAHierarchyStatesItsStrategy() {
        assertThat(errors("E6")).as("positive").isEmpty();
        Entity vip = shop.vipCustomer();
        assertThat(errors("E6")).anySatisfy(m -> assertThat(m).contains("without inheritanceStrategy"));
        shop.customer.setInheritanceStrategy(InheritanceType.SINGLETABLE);
        assertThat(errors("E6")).anySatisfy(m -> assertThat(m).contains("discriminator column"));
        Column kind = shop.extraColumn("CUSTOMER", "KIND");
        shop.customer.setCwmDiscriminatorColumn(kind);
        shop.customer.setDiscriminatorValue("C");
        vip.setDiscriminatorValue("V");
        assertThat(errors("E6")).isEmpty();
        vip.setCwmTable(shop.table("ORDERS"));
        assertThat(errors("E6")).anySatisfy(m -> assertThat(m).contains("table of the root"));
    }

    @Test
    void e6_anAbstractTablePerClassRootHasNoTable() {
        assertThat(errors("E6")).as("positive").isEmpty();
        Entity vip = shop.vipCustomer();
        vip.setCwmTable(shop.table("ORDERS"));
        shop.customer.setCwmTable(null);
        shop.customer.setInheritanceStrategy(InheritanceType.TABLEPERCLASS);
        assertThat(errors("E6")).anySatisfy(m -> assertThat(m).contains("needs its table"));
        shop.customer.getCwmClass().setIsAbstract(true);
        assertThat(errors("E6")).isEmpty();
    }

    @Test
    void e7_aColumnUsedTwiceIsWrittenOnlyOnce() {
        assertThat(errors("E7")).as("positive").isEmpty();
        Attribute customerId = attr(shop.order.getCwmClass(), "customerId", null);
        Basics.create(shop.order, customerId, shop.col("ORDERS", "CUSTOMER_ID"));
        assertThat(errors("E7")).anySatisfy(m -> assertThat(m).contains("ORDERS.CUSTOMER_ID"));
        Joins.readOnly(shop.order.getManyToOne().get(0).getJoin());
        assertThat(errors("E7")).isEmpty();
    }

    @Test
    void e8_everyEntityHasAnId() {
        assertThat(errors("E8")).as("positive").isEmpty();
        Id id = shop.order.getId().remove(0);
        Basics.create(shop.order, id.getCwmAttribute(), shop.col("ORDERS", "ID"));
        assertThat(errors("E8")).anySatisfy(m -> assertThat(m).contains("at least one Id"));
    }

    @Test
    void e8_idColumnsBelongToThePrimaryKey() {
        assertThat(errors("E8")).as("positive").isEmpty();
        shop.order.getId().get(0).setColumn(Columns.of(shop.col("ORDERS", "TOTAL")));
        shop.order.getBasic().get(0).setColumn(Columns.of(shop.col("ORDERS", "ID")));
        assertThat(errors("E8")).anySatisfy(m -> assertThat(m).contains("not belong to the primary key"));
    }

    @Test
    void e8_mapsIdNamesAnAttributeOfTheEmbeddedIdClass() {
        assertThat(errors("E8")).as("positive").isEmpty();
        ManyToOne customer = shop.order.getManyToOne().get(0);
        customer.setCwmMapsIdAttribute(shop.orderTotal);
        assertThat(errors("E8")).anySatisfy(m -> assertThat(m).contains("without mapsId"));
        customer.setMapsId(true);
        assertThat(errors("E8")).anySatisfy(m -> assertThat(m).contains("needs an EmbeddedId"));
    }

    @Test
    void e9_theStrategyMatchesTheGenerator() {
        assertThat(errors("E9")).as("positive").isEmpty();
        Id id = shop.order.getId().get(0);
        GeneratedValue gv = Generators.generated(id, GenerationType.IDENTITY, null);
        assertThat(errors("E9")).isEmpty();
        gv.setStrategy(GenerationType.SEQUENCE);
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("does not match the generator"));
        gv.setGenerator(Generators.sequence(shop.em, "orderSeq", shop.schema, "ORDER_SEQ"));
        assertThat(errors("E9")).isEmpty();
        gv.unsetStrategy();
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("no silent AUTO"));
    }

    @Test
    void e9_aConvertAppliesOrDisablesAConverter() {
        assertThat(errors("E9")).as("positive").isEmpty();
        var converterClass = cls(shop.pkg, "MoneyConverter");
        Converter converter = Converters.create(shop.em, converterClass, false);
        Basic total = shop.order.getBasic().get(0);
        Converts.of(total, converter);
        assertThat(errors("E9")).isEmpty();
        total.getConvert().setDisableConversion(true);
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("either a converter"));
    }

    @Test
    void e9_aPathFollowsTheTypes() {
        assertThat(errors("E9")).as("positive").isEmpty();
        Embedded address = shop.customer.getEmbedded().get(0);
        address.getAttributeOverride().get(0).getCwmPath().set(0, shop.orderTotal);
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("not a feature of Address"));
    }

    @Test
    void e9_resultColumnsComeFromTheStoredQuery() {
        assertThat(errors("E9")).as("positive").isEmpty();
        QueryColumnSet qcs = RelationalFactory.eINSTANCE.createQueryColumnSet();
        qcs.setName("ORDER_TOTALS");
        Column total = RelationalFactory.eINSTANCE.createColumn();
        total.setName("TOTAL");
        qcs.getFeature().add(total);
        shop.schema.getOwnedElement().add(qcs);
        NamedNativeQuery q = Queries.nativeOf(shop.em, qcs);
        SqlResultSetMapping rsm = ResultSets.create(shop.em, "totals");
        q.setResultSetMapping(rsm);
        ResultSets.column(rsm, total);
        assertThat(errors("E9")).isEmpty();
        ResultSets.column(rsm, shop.col("ORDERS", "TOTAL"));
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("ORDERS.TOTAL")
                .contains("does not belong to the stored query ORDER_TOTALS"));
    }

    @Test
    void e9_anOrderingPathLeadsIntoTheTargetType() {
        assertThat(errors("E9")).as("positive").isEmpty();
        Orderings.descending(shop.orders, shop.orderTotal);
        assertThat(errors("E9")).isEmpty();
        Orderings.by(shop.orders, shop.customerName);
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("'name'").contains("not a feature of Order"));
        shop.orders.setOrderColumn(Columns.of(shop.col("ORDERS", "TOTAL")));
        assertThat(errors("E9")).anySatisfy(m -> assertThat(m).contains("not both"));
    }

    @Test
    void e10_theNameOfAnAnchoredElementIsTheAnchorName() {
        assertThat(errors("E10")).as("positive").isEmpty();
        shop.customer.getBasic().get(0).setName("somethingElse");
        assertThat(errors("E10")).anySatisfy(m -> assertThat(m).contains("differs from the CWM anchor"));
    }

    @Test
    void e10_unitWideNamesAreUnique() {
        assertThat(errors("E10")).as("positive").isEmpty();
        Generators.sequence(shop.em, "seq", shop.schema, "A_SEQ");
        Generators.sequence(shop.em, "seq", shop.schema, "B_SEQ");
        assertThat(errors("E10")).anySatisfy(m -> assertThat(m).contains("is not unique"));
    }

    @Test
    void e11_anUnusedColumnIsAWarning() {
        assertThat(rules()).as("positive").doesNotContain("E11");
        shop.extraColumn("ORDERS", "REMARK");
        assertThat(OrmModelValidator.check(shop.em)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("E11");
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.message()).contains("ORDERS.REMARK");
        });
    }

    private List<String> rules() {
        return OrmModelValidator.check(shop.em).stream().map(Finding::rule).toList();
    }

    private List<String> errors(String rule) {
        return OrmModelValidator.check(shop.em).stream()
                .filter(f -> f.severity() == Severity.ERROR && f.rule().equals(rule)).map(Finding::message)
                .toList();
    }

    private static void multiplicity(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature f,
            long lower, long upper) {
        Multiplicity m = CoreFactory.eINSTANCE.createMultiplicity();
        MultiplicityRange r = CoreFactory.eINSTANCE.createMultiplicityRange();
        r.setLower(lower);
        r.setUpper(upper);
        m.getRange().add(r);
        f.setMultiplicity(m);
    }

    private static org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class cls(
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg, String name) {
        var c = CoreFactory.eINSTANCE.createClass();
        c.setName(name);
        pkg.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attr(Classifier owner, String name, Classifier type) {
        Attribute a = CoreFactory.eINSTANCE.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }

    /**
     * Customer (with embedded Address and the inverse list of its orders) and Order (owning
     * many-to-one over the catalog foreign key) on CUSTOMER and ORDERS — explicit in every place.
     */
    private static final class Shop {
        final EntityMappings em = CWMORMFactory.eINSTANCE.createEntityMappings();
        final org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg = CoreFactory.eINSTANCE.createPackage();
        final Schema schema = RelationalFactory.eINSTANCE.createSchema();
        final Attribute customerName;
        final Attribute orderTotal;
        final AssociationEnd toCustomer;
        final AssociationEnd toOrders;
        final ForeignKey fkOrdersCustomer;
        final Entity customer;
        final Entity order;
        final OneToMany orders;

        Shop() {
            em.setName("shop");
            pkg.setName("shop");
            schema.setName("SHOP");
            var cCustomer = cls(pkg, "Customer");
            var cOrder = cls(pkg, "Order");
            var cAddress = cls(pkg, "Address");
            Attribute customerId = attr(cCustomer, "id", null);
            customerName = attr(cCustomer, "name", null);
            Attribute address = attr(cCustomer, "address", cAddress);
            Attribute street = attr(cAddress, "street", null);
            Attribute city = attr(cAddress, "city", null);
            Attribute orderId = attr(cOrder, "id", null);
            orderTotal = attr(cOrder, "total", null);
            Association placed = RelationshipsFactory.eINSTANCE.createAssociation();
            placed.setName("Customer_Order");
            toCustomer = end(placed, "customer", cCustomer);
            toOrders = end(placed, "orders", cOrder);
            pkg.getOwnedElement().add(placed);
            multiplicity(toCustomer, 1, 1);
            multiplicity(toOrders, 0, -1);

            Table tCustomer = table("CUSTOMER", "ID", "NAME", "STREET", "CITY");
            Table tOrders = table("ORDERS", "ID", "TOTAL", "CUSTOMER_ID");
            PrimaryKey pkCustomer = pk(tCustomer, "PK_CUSTOMER");
            pk(tOrders, "PK_ORDERS");
            fkOrdersCustomer = RelationalFactory.eINSTANCE.createForeignKey();
            fkOrdersCustomer.setName("FK_ORDERS_CUSTOMER");
            fkOrdersCustomer.getFeature().add(col("ORDERS", "CUSTOMER_ID"));
            fkOrdersCustomer.setUniqueKey(pkCustomer);
            tOrders.getOwnedElement().add(fkOrdersCustomer);

            var addressType = Embeddables.create(em, cAddress);
            Basics.create(addressType, street);
            Basics.create(addressType, city);

            customer = Entities.create(em, cCustomer, tCustomer);
            Ids.create(customer, customerId, col("CUSTOMER", "ID"));
            Basics.create(customer, customerName, col("CUSTOMER", "NAME"));
            Embedded emb = Embeddeds.create(customer, address);
            Overrides.attribute(emb, col("CUSTOMER", "STREET"), street);
            Overrides.attribute(emb, col("CUSTOMER", "CITY"), city);
            orders = OneToManys.inverse(customer, toOrders);

            order = Entities.create(em, cOrder, tOrders);
            Ids.create(order, orderId, col("ORDERS", "ID"));
            Basics.create(order, orderTotal, col("ORDERS", "TOTAL"));
            ManyToOnes.create(order, toCustomer, Joins.of(fkOrdersCustomer));
        }

        /** VipCustomer extends Customer, without table of its own. */
        Entity vipCustomer() {
            var vip = cls(pkg, "VipCustomer");
            var g = RelationshipsFactory.eINSTANCE.createGeneralization();
            g.setName("VipCustomer_extends_Customer");
            g.setChild(vip);
            g.setParent(customer.getCwmClass());
            pkg.getOwnedElement().add(g);
            return Entities.create(em, vip, null);
        }

        /** A further table with the given columns, in the schema SHOP. */
        Table newTable(String name, String... columns) {
            return table(name, columns);
        }

        /** A further column; unused it is an E11 warning. */
        Column extraColumn(String table, String name) {
            Column c = RelationalFactory.eINSTANCE.createColumn();
            c.setName(name);
            table(table).getFeature().add(c);
            return c;
        }

        /** Keeps CUSTOMER.NAME in use when a test takes its basic mapping away. */
        void extraColumnUse() {
            Attribute nick = attr(customer.getCwmClass(), "nick", null);
            Basics.create(customer, nick, col("CUSTOMER", "NAME"));
        }

        Table table(String name) {
            return schema.getOwnedElement().stream().filter(Table.class::isInstance).map(Table.class::cast)
                    .filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
        }

        Column col(String table, String name) {
            return table(table).getFeature().stream().filter(f -> name.equals(f.getName()))
                    .map(Column.class::cast).findFirst().orElseThrow();
        }

        private Table table(String name, String... columns) {
            Table t = RelationalFactory.eINSTANCE.createTable();
            t.setName(name);
            for (String c : columns) {
                Column col = RelationalFactory.eINSTANCE.createColumn();
                col.setName(c);
                t.getFeature().add(col);
            }
            schema.getOwnedElement().add(t);
            return t;
        }

        private PrimaryKey pk(Table t, String name) {
            PrimaryKey pk = RelationalFactory.eINSTANCE.createPrimaryKey();
            pk.setName(name);
            pk.getFeature().add(col(t.getName(), "ID"));
            t.getOwnedElement().add(pk);
            return pk;
        }

        private static AssociationEnd end(Association a, String name, Classifier type) {
            AssociationEnd e = RelationshipsFactory.eINSTANCE.createAssociationEnd();
            e.setName(name);
            e.setType(type);
            e.setIsNavigable(true);
            a.getFeature().add(e);
            return e;
        }
    }
}
