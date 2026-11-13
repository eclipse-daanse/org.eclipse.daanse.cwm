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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.junit.jupiter.api.Test;

class ElementRulesTest {

    @Test
    void named_matchesTheNameByGlob() {
        Shop model = new Shop();
        ElementRule rule = ElementRules.named("order*");

        assertThat(rule.describe()).isEqualTo("name:order*");
        assertThat(rule.test(model.orders)).isTrue();
        assertThat(rule.test(model.orderLog)).isTrue();
        assertThat(rule.test(model.customer)).isFalse();
        assertThat(rule.test(null)).isFalse();
        assertThat(ElementRules.named("*").test(Shop.CF.createClass())).as("unnamed").isFalse();
    }

    @Test
    void path_matchesTheQualifiedNameByGlob() {
        Shop model = new Shop();

        assertThat(ElementRules.path("shop.sales.*").test(model.orders)).isTrue();
        assertThat(ElementRules.path("shop.sales.*").test(model.id)).isFalse();
        assertThat(ElementRules.path("shop.sales.**").test(model.id)).isTrue();
        assertThat(ElementRules.path("shop.sales.**").test(model.sales)).isTrue();
        assertThat(ElementRules.path("**.id").test(model.id)).isTrue();
        assertThat(ElementRules.path("shop.sales.*").describe()).isEqualTo("path:shop.sales.*");
    }

    @Test
    void kind_matchesClassAndSubclasses_byClassEClassOrName() {
        Shop model = new Shop();

        for (ElementRule rule : new ElementRule[] { ElementRules.kind(Classifier.class),
                ElementRules.kind(CorePackage.Literals.CLASSIFIER), ElementRules.kind("Classifier") }) {
            assertThat(rule.describe()).isEqualTo("kind:Classifier");
            assertThat(rule.test(model.orders)).as(rule.describe()).isTrue();
            assertThat(rule.test(model.id)).as(rule.describe()).isFalse();
            assertThat(rule.test(model.sales)).as(rule.describe()).isFalse();
        }
        assertThat(ElementRules.kind("Nothing").test(model.orders)).isFalse();
    }

    @Test
    void kindExactly_matchesTheClassAlone() {
        Shop model = new Shop();
        Model nested = Models.create(model.shop, "nested");

        assertThat(ElementRules.kindExactly(CorePackage.Literals.PACKAGE).test(model.sales)).isTrue();
        assertThat(ElementRules.kindExactly(CorePackage.Literals.PACKAGE).test(nested)).isFalse();
        assertThat(ElementRules.kindExactly(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package.class)
                .test(nested)).isFalse();
        assertThat(ElementRules.kindExactly("Model").test(nested)).isTrue();
        assertThat(ElementRules.kind("Package").test(nested)).isTrue();
        assertThat(ElementRules.kindExactly("Model").describe()).isEqualTo("kindExactly(Model)");
    }

    @Test
    void kindRules_answerForAWholeClass() {
        ElementRule attributes = ElementRules.kind(Attribute.class);

        assertThat(attributes.matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isTrue();
        assertThat(attributes.matchesNoneOf(CorePackage.Literals.CLASS)).isTrue();
        assertThat(attributes.negate().matchesAllOf(CorePackage.Literals.CLASS)).isTrue();
        assertThat(attributes.negate().matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isFalse();
        assertThat(ElementRules.kind("StructuralFeature").matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isTrue();
        // rules on the element itself cannot answer for a class
        assertThat(ElementRules.named("*").matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isFalse();
        assertThat(ElementRules.named("*").matchesNoneOf(CorePackage.Literals.ATTRIBUTE)).isFalse();
        assertThat(ElementRule.of("x", e -> true).matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isFalse();
        // combinations carry the answer through
        assertThat(attributes.and(ElementRules.named("a*")).matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isFalse();
        assertThat(attributes.and(ElementRules.named("a*")).matchesNoneOf(CorePackage.Literals.CLASS)).isTrue();
        assertThat(attributes.or(ElementRules.named("a*")).matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isTrue();
        assertThat(ElementRules.none(attributes).matchesAllOf(CorePackage.Literals.CLASS)).isTrue();
        assertThat(attributes.describedAs("is:attribute").matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isTrue();
    }

    @Test
    void tagged_matchesTagAndValue() {
        Shop model = new Shop();
        TaggedValues.set(model.orders, "documentary", "true");
        TaggedValues.set(model.customer, "documentary", "false");

        assertThat(ElementRules.tagged("documentary").test(model.customer)).isTrue();
        assertThat(ElementRules.tagged("documentary").test(model.product)).isFalse();
        assertThat(ElementRules.tagged("documentary", "true").test(model.orders)).isTrue();
        assertThat(ElementRules.tagged("documentary", "true").test(model.customer)).isFalse();
        assertThat(ElementRules.tagged("documentary", "true").describe()).isEqualTo("tag:documentary=true");
    }

    @Test
    void stereotyped_findsTheStereotypeThatExtendsTheElement() {
        Shop model = new Shop();
        Stereotype entity = Shop.CF.createStereotype();
        entity.setName("entity");
        entity.setBaseClass("Class");
        entity.getExtendedElement().add(model.orders);
        model.shop.getOwnedElement().add(entity);

        assertThat(ElementRules.stereotyped("entity").test(model.orders)).isTrue();
        assertThat(ElementRules.stereotyped("entity").test(model.customer)).isFalse();
        assertThat(ElementRules.stereotyped("entity").describe()).isEqualTo("stereotype:entity");
    }

    @Test
    void within_asksTheOwnersNotTheElement() {
        Shop model = new Shop();
        ElementRule inSales = ElementRules.within(ElementRules.named("sales"));

        assertThat(inSales.test(model.orders)).isTrue();
        assertThat(inSales.test(model.id)).isTrue();
        assertThat(inSales.test(model.sales)).isFalse();
        assertThat(inSales.test(model.product)).isFalse();
        assertThat(inSales.describe()).isEqualTo("within(name:sales)");
    }

    @Test
    void dependsOn_followsTheDependenciesOfAClient() {
        Shop model = new Shop();
        Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);

        assertThat(ElementRules.dependsOn(ElementRules.named("product"), null).test(model.orders)).isTrue();
        assertThat(ElementRules.dependsOn(ElementRules.named("product"), "usage").test(model.orders)).isTrue();
        assertThat(ElementRules.dependsOn(ElementRules.named("product"), "trace").test(model.orders)).isFalse();
        assertThat(ElementRules.dependsOn(ElementRules.named("product"), null).test(model.product)).isFalse();
        assertThat(ElementRules.dependsOn(ElementRules.named("product"), "usage").describe())
                .isEqualTo("dependsOn(name:product, usage)");
    }

    @Test
    void anyAllNone_combine_andReadAsFunctions() {
        Shop model = new Shop();
        ElementRule orders = ElementRules.named("orders");
        ElementRule types = ElementRules.kind("Class");

        assertThat(ElementRules.any(orders, ElementRules.named("product")).test(model.product)).isTrue();
        assertThat(ElementRules.all(orders, types).test(model.orders)).isTrue();
        assertThat(ElementRules.all(orders, types).test(model.customer)).isFalse();
        assertThat(ElementRules.none(orders, types).test(model.sales)).isTrue();
        assertThat(ElementRules.none(orders, types).test(model.customer)).isFalse();
        assertThat(ElementRules.any().test(model.orders)).isFalse();
        assertThat(ElementRules.all().test(model.orders)).isTrue();
        assertThat(orders.and(types).describe()).isEqualTo("all(name:orders, kind:Class)");
        assertThat(orders.or(types).describe()).isEqualTo("any(name:orders, kind:Class)");
        assertThat(orders.negate().describe()).isEqualTo("not(name:orders)");
        assertThat(orders.negate().test(model.customer)).isTrue();
        assertThat(orders.negate().test(null)).isFalse();
    }

    @Test
    void lambdas_areRules_andOfNamesThem() {
        Shop model = new Shop();
        ElementRule withoutFeatures = e -> e instanceof Classifier c && c.getFeature().isEmpty();

        assertThat(withoutFeatures.test(model.customer)).isTrue();
        assertThat(withoutFeatures.test(model.orders)).isFalse();
        assertThat(withoutFeatures.describe()).isEqualTo("custom rule");
        assertThat(ElementRule.of("without features", withoutFeatures::test).describe()).isEqualTo("without features");
        assertThat(withoutFeatures.describedAs("empty").describe()).isEqualTo("empty");
    }
}
