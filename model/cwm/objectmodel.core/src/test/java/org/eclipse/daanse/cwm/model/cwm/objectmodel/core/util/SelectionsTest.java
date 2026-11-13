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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule.Consequence;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.Retained;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

class SelectionsTest {

    @Test
    void list_returnsTheSelectedElementsOfAType() {
        Shop model = new Shop();

        assertThat(Selections.list(model.shop, Selection.parse("name:sales"), Class.class))
                .containsExactly(model.customer, model.orders, model.orderLog);
        assertThat(Selections.list(model.shop, Selection.all(), Attribute.class)).containsExactly(model.id);
        assertThat(Selections.list(model.shop, Selection.parse("!name:sales"), Class.class))
                .containsExactly(model.product);
    }

    @Test
    void list_leavesOutContainersThatAreNotSelectedThemselves() {
        Shop model = new Shop();

        assertThat(Selections.list(model.shop, Selection.parse("name:orders"), ModelElement.class))
                .containsExactly(model.orders, model.id);
    }

    @Test
    void list_withImports_entersWhatAPackageImports_once() {
        Shop model = new Shop();
        model.sales.getImportedElement().add(model.product);
        model.sales.getImportedElement().add(model.customer);

        assertThat(Selections.list(model.sales, Selection.all(), Class.class))
                .containsExactly(model.customer, model.orders, model.orderLog);
        assertThat(Selections.list(model.sales, Selection.all(), Class.class, true))
                .containsExactly(model.customer, model.orders, model.orderLog, model.product);
        // judged where it is owned: product is not in sales
        assertThat(Selections.list(model.sales, Selection.parse("path:shop.sales.*"), Class.class, true))
                .containsExactly(model.customer, model.orders, model.orderLog);
    }

    @Test
    void stream_isEmptyForNull() {
        Shop model = new Shop();

        assertThat(Selections.stream(null, Selection.all(), Class.class)).isEmpty();
        assertThat(Selections.stream(model.shop, null, Class.class)).isEmpty();
        assertThat(Selections.stream(model.shop, Selection.all(), null)).isEmpty();
    }

    @Test
    void copy_leavesOutWhatIsNotSelected_andSaysWhy() {
        Shop model = new Shop();

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("path:shop.sales.**", "!name:order_log"));

        assertThat(cut.model()).isNotSameAs(model.shop);
        assertThat(paths(cut.model())).containsExactly("shop", "shop.sales", "shop.sales.customer",
                "shop.sales.orders", "shop.sales.orders.id");
        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.order_log", "Class", "name:order_log"),
                new LeftOut("shop.stock", "Package", Selections.NOT_INCLUDED));
        assertThat(cut.leftOut().get(0)).hasToString("Class shop.sales.order_log (name:order_log)");
    }

    @Test
    void copy_neverRemovesTheRoot() {
        Shop model = new Shop();

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:shop"));

        assertThat(cut.model().getName()).isEqualTo("shop");
        assertThat(cut.model().getOwnedElement()).isEmpty();
        assertThat(cut.leftOut()).extracting(LeftOut::path, LeftOut::reason).containsExactly(
                tuple("shop.sales", "name:shop"),
                tuple("shop.stock", "name:shop"));
    }

    @Test
    void copy_dependencyWhoseSupplierIsRemoved_goes_itsClientStays() {
        Shop model = new Shop();
        Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:product"));

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.stock.product", "Class", "name:product"),
                new LeftOut("shop.sales.orders_use_product", "Dependency",
                        "dependency on a removed supplier (supplier: shop.stock.product)"));
        Class orders = QualifiedNames.find(cut.model(), "shop.sales.orders", Class.class).orElseThrow();
        assertThat(orders.getClientDependency()).isEmpty();
    }

    @Test
    void copy_dependentClientsOfAKind_goWithTheirSupplier() {
        Shop model = new Shop();
        Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);
        Dependencies.create(model.sales, "customer_traces_product", "trace", model.customer, model.product);
        List<ConsequenceRule> consequences = new ArrayList<>();
        consequences.add(ConsequenceRule.dependentClients("usage"));
        consequences.addAll(ConsequenceRule.CORE);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:product"), consequences);

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.stock.product", "Class", "name:product"),
                new LeftOut("shop.sales.orders_use_product", "Dependency",
                        "dependency of kind usage on a removed supplier (supplier: shop.stock.product)"),
                new LeftOut("shop.sales.customer_traces_product", "Dependency",
                        "dependency on a removed supplier (supplier: shop.stock.product)"),
                new LeftOut("shop.sales.orders", "Class", "dependency of kind usage on a removed supplier"
                        + " (clientDependency: shop.sales.orders_use_product)"));
        assertThat(paths(cut.model())).containsExactly("shop", "shop.sales", "shop.sales.customer",
                "shop.sales.order_log", "shop.stock");
        assertThatThrownBy(() -> ConsequenceRule.dependentClients(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void copy_dependencyLeftOutItself_keepsItsClient() {
        Shop model = new Shop();
        Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!kind:Dependency"));

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.orders_use_product", "Dependency", "kind:Dependency"));
        Class orders = QualifiedNames.find(cut.model(), "shop.sales.orders", Class.class).orElseThrow();
        assertThat(orders.getClientDependency()).isEmpty();
        assertThat(QualifiedNames.find(cut.model(), "shop.stock.product")).isPresent();
    }

    @Test
    void copy_requiredReference_removesTheElementThatLosesIt() {
        Shop model = new Shop();
        DataType text = typeIn(Packages.create(model.shop, "types"), "text");
        model.id.setType(text);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:types"));

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.types", "Package", "name:types"),
                new LeftOut("shop.sales.orders.id", "Attribute", "required reference (type: shop.types.text)"));
        Class orders = QualifiedNames.find(cut.model(), "shop.sales.orders", Class.class).orElseThrow();
        assertThat(orders.getFeature()).isEmpty();
    }

    @Test
    void copy_requiredReference_staysWhileAValueRemains() {
        Shop model = new Shop();
        Dependency uses = Dependencies.create(model.sales, "orders_use", "usage", model.orders, model.product);
        uses.getSupplier().add(model.customer);
        List<ConsequenceRule> requiredOnly = List.of(ConsequenceRule.requiredReferences());

        Selected<Model> one = Selections.copy(model.shop, Selection.parse("!name:product"), requiredOnly);
        Selected<Model> both = Selections.copy(model.shop, Selection.parse("!name:product", "!name:customer"),
                requiredOnly);

        Dependency kept = QualifiedNames.find(one.model(), "shop.sales.orders_use", Dependency.class).orElseThrow();
        assertThat(kept.getSupplier()).extracting(ModelElement::getName).containsExactly("customer");
        assertThat(both.leftOut()).extracting(LeftOut::path).containsExactly("shop.sales.customer",
                "shop.stock.product", "shop.sales.orders_use");
    }

    @Test
    void copy_unsetsOptionalReferences() {
        Shop model = new Shop();
        model.sales.getImportedElement().add(model.product);
        Constraint rule = Shop.CF.createConstraint();
        rule.setName("rule");
        rule.setBody(Expressions.bool("OCL", "true"));
        rule.getConstrainedElement().add(model.orders);
        rule.getConstrainedElement().add(model.product);
        model.sales.getOwnedElement().add(rule);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:product"));

        assertThat(cut.leftOut()).extracting(LeftOut::path).containsExactly("shop.stock.product");
        Package sales = QualifiedNames.find(cut.model(), "shop.sales", Package.class).orElseThrow();
        assertThat(sales.getImportedElement()).isEmpty();
        Constraint copied = QualifiedNames.find(cut.model(), "shop.sales.rule", Constraint.class).orElseThrow();
        assertThat(copied.getConstrainedElement()).extracting(ModelElement::getName).containsExactly("orders");
    }

    @Test
    void copy_retain_keepsTheNeededElementWithItsOwners_andReportsIt() {
        Shop model = new Shop();
        Package types = Packages.create(model.shop, "types");
        DataType text = typeIn(types, "text");
        attribute(text, "length");
        attribute(text, "tmp_cache");
        typeIn(types, "number");
        model.id.setType(text);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("name:orders", "!name:tmp_*"),
                retainingTypes());

        assertThat(paths(cut.model())).containsExactly("shop", "shop.sales", "shop.sales.orders",
                "shop.sales.orders.id", "shop.types", "shop.types.text", "shop.types.text.length");
        assertThat(cut.retained()).containsExactly(
                new Retained("shop.types.text", "DataType", "needed by shop.sales.orders.id.type"));
        assertThat(cut.retained().get(0))
                .hasToString("DataType shop.types.text (needed by shop.sales.orders.id.type)");
        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.customer", "Class", Selections.NOT_INCLUDED),
                new LeftOut("shop.sales.order_log", "Class", Selections.NOT_INCLUDED),
                new LeftOut("shop.stock", "Package", Selections.NOT_INCLUDED),
                new LeftOut("shop.types.text.tmp_cache", "Attribute", "name:tmp_*"),
                new LeftOut("shop.types.number", "DataType", Selections.NOT_INCLUDED));
        Attribute id = QualifiedNames.find(cut.model(), "shop.sales.orders.id", Attribute.class).orElseThrow();
        assertThat(id.getType()).isNotSameAs(text);
        assertThat(QualifiedNames.of(id.getType())).isEqualTo("shop.types.text");
    }

    @Test
    void copy_retain_neverBringsBackWhatAnExcludeRuleReaches() {
        Shop model = new Shop();
        DataType text = typeIn(Packages.create(model.shop, "types"), "text");
        model.id.setType(text);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("name:orders", "!name:types"),
                retainingTypes());

        assertThat(cut.retained()).isEmpty();
        // RETAIN counts as no answer, so the next rule decides: the attribute needs its type
        assertThat(cut.leftOut()).contains(
                new LeftOut("shop.types", "Package", "name:types"),
                new LeftOut("shop.sales.orders.id", "Attribute", "required reference (type: shop.types.text)"));
    }

    @Test
    void copy_retain_followsWhatTheRetainedNeed() {
        Shop model = new Shop();
        DataType text = typeIn(Packages.create(model.shop, "types"), "text");
        attribute(model.customer, "name").setType(text);
        model.id.setType(model.customer);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("name:orders"), retainingTypes());

        assertThat(cut.retained()).containsExactly(
                new Retained("shop.sales.customer", "Class", "needed by shop.sales.orders.id.type"),
                new Retained("shop.types.text", "DataType", "needed by shop.sales.customer.name.type"));
        assertThat(paths(cut.model())).containsExactly("shop", "shop.sales", "shop.sales.customer",
                "shop.sales.customer.name", "shop.sales.orders", "shop.sales.orders.id", "shop.types",
                "shop.types.text");
        assertThat(Selections.list(model.shop, Selection.parse("name:orders"), ModelElement.class))
                .as("what is retained is not selected").containsExactly(model.orders, model.id);
    }

    @Test
    void copy_withoutRetain_theNeedingElementGoes() {
        Shop model = new Shop();
        model.id.setType(model.customer);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("name:orders"));

        assertThat(cut.retained()).isEmpty();
        assertThat(cut.leftOut()).contains(new LeftOut("shop.sales.orders.id", "Attribute",
                "required reference (type: shop.sales.customer)"));
    }

    @Test
    void copy_stereotypeOfAnElementThatStays_comesAlong() {
        Shop model = new Shop();
        Stereotype entity = Shop.CF.createStereotype();
        entity.setName("entity");
        entity.setBaseClass("Class");
        entity.getExtendedElement().add(model.customer);
        entity.getExtendedElement().add(model.orders);
        model.shop.getOwnedElement().add(entity);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("name:orders"));
        Selected<Model> without = Selections.copy(model.shop, Selection.parse("name:orders", "!kind:Stereotype"));

        assertThat(cut.retained())
                .containsExactly(new Retained("shop.entity", "Stereotype", "brands shop.sales.orders"));
        Class orders = QualifiedNames.find(cut.model(), "shop.sales.orders", Class.class).orElseThrow();
        Stereotype copied = QualifiedNames.find(cut.model(), "shop.entity", Stereotype.class).orElseThrow();
        assertThat(copied.getExtendedElement()).containsExactly(orders);
        assertThat(Stereotypes.has(orders, "entity")).isTrue();
        assertThat(without.retained()).isEmpty();
        assertThat(QualifiedNames.find(without.model(), "shop.entity")).isEmpty();
    }

    @Test
    void copy_asksTheContainerOfWhatIsLeftOut() {
        Shop model = new Shop();
        List<ConsequenceRule> wholeOrNothing = ConsequenceRule.combine(
                List.of(ConsequenceRule.on(CorePackage.Literals.CLASSIFIER__FEATURE, Consequence.REMOVE)),
                ConsequenceRule.CORE);

        Selected<Model> plain = Selections.copy(model.shop, Selection.parse("!name:id"));
        Selected<Model> whole = Selections.copy(model.shop, Selection.parse("!name:id"), wholeOrNothing);

        assertThat(plain.leftOut()).extracting(LeftOut::path).containsExactly("shop.sales.orders.id");
        assertThat(whole.leftOut()).containsExactly(
                new LeftOut("shop.sales.orders.id", "Attribute", "name:id"),
                new LeftOut("shop.sales.orders", "Class", "Classifier.feature remove (feature: shop.sales.orders.id)"));
    }

    @Test
    void combine_putsEachRuleOnce_andTheCoreRulesLast() {
        ConsequenceRule own = ConsequenceRule.of("own", loss -> Optional.empty());
        ConsequenceRule other = ConsequenceRule.of("other", loss -> Optional.empty());
        List<ConsequenceRule> base = ConsequenceRule.combine(List.of(other));

        List<ConsequenceRule> combined = ConsequenceRule.combine(List.of(own), base, List.of(own, other));

        assertThat(combined).map(ConsequenceRule::describe).containsExactly("own", "other",
                "dependency on a removed supplier", "required reference");
    }

    @Test
    void copy_endsOnDependencyCycles() {
        Shop model = new Shop();
        Dependencies.create(model.sales, "orders_use_customer", "usage", model.orders, model.customer);
        Dependencies.create(model.sales, "customer_uses_orders", "usage", model.customer, model.orders);
        List<ConsequenceRule> consequences = List.of(ConsequenceRule.dependentClients("usage"),
                ConsequenceRule.requiredReferences());

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:orders"), consequences);

        assertThat(cut.leftOut()).extracting(LeftOut::path).containsExactlyInAnyOrder("shop.sales.orders",
                "shop.sales.orders_use_customer", "shop.sales.customer_uses_orders", "shop.sales.customer");
        assertThat(paths(cut.model())).containsExactly("shop", "shop.sales", "shop.sales.order_log", "shop.stock",
                "shop.stock.product");
    }

    @Test
    void copy_leavesTheOriginalUntouched() {
        Shop model = new Shop();
        DataType text = typeIn(Packages.create(model.shop, "types"), "text");
        model.id.setType(text);
        Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);
        model.sales.getImportedElement().add(model.product);
        Stereotype entity = Shop.CF.createStereotype();
        entity.setName("entity");
        entity.setBaseClass("Class");
        entity.getExtendedElement().add(model.orderLog);
        model.shop.getOwnedElement().add(entity);
        TaggedValues.set(model.customer, "documentary", "true");
        Model before = EcoreUtil.copy(model.shop);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("path:shop.sales.**", "!name:product",
                "!tag:documentary=true", "!stereotype:entity"), retainingTypes());

        assertThat(cut.leftOut()).isNotEmpty();
        assertThat(cut.retained()).isNotEmpty();
        assertThat(EcoreUtil.equals(before, model.shop)).isTrue();
        assertThat(model.shop.eAdapters()).isEmpty();
        for (TreeIterator<EObject> it = model.shop.eAllContents(); it.hasNext();) {
            assertThat(it.next().eAdapters()).isEmpty();
        }
    }

    @Test
    void copy_leavesNoIndexOnTheCopy() {
        Shop model = new Shop();
        Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);

        Selected<Model> cut = Selections.copy(model.shop, Selection.parse("!name:customer"));

        assertThat(ECrossReferenceAdapter.getCrossReferenceAdapter(cut.model())).isNull();
        for (TreeIterator<EObject> it = cut.model().eAllContents(); it.hasNext();) {
            assertThat(ECrossReferenceAdapter.getCrossReferenceAdapter(it.next())).isNull();
        }
        assertThat(cut.model().eResource()).isNull();
    }

    @Test
    void cut_leavesWhatACopyLeaves_inTheModelItself() {
        Shop original = new Shop();
        Shop inPlace = new Shop();
        for (Shop model : List.of(original, inPlace)) {
            model.id.setType(typeIn(Packages.create(model.shop, "types"), "text"));
            Dependencies.create(model.sales, "orders_use_product", "usage", model.orders, model.product);
        }
        Selection selection = Selection.parse("path:shop.sales.**", "!name:customer");

        Selected<Model> copy = Selections.copy(original.shop, selection, retainingTypes());
        Selected<Model> cut = Selections.cut(inPlace.shop, selection, retainingTypes());

        assertThat(cut.model()).isSameAs(inPlace.shop);
        assertThat(cut.leftOut()).isEqualTo(copy.leftOut()).isNotEmpty();
        assertThat(cut.retained()).isEqualTo(copy.retained()).isNotEmpty();
        assertThat(paths(cut.model())).isEqualTo(paths(copy.model()));
        assertThat(inPlace.customer.eContainer()).as("detached").isNull();
        assertThat(inPlace.orders.getClientDependency()).as("references to what is left out are unset").isEmpty();
        assertThat(ECrossReferenceAdapter.getCrossReferenceAdapter(inPlace.shop)).isNull();
    }

    /** The core consequences behind a rule that keeps the type of a feature. */
    private static List<ConsequenceRule> retainingTypes() {
        List<ConsequenceRule> consequences = new ArrayList<>();
        consequences.add(ConsequenceRule.on(CorePackage.Literals.STRUCTURAL_FEATURE__TYPE, Consequence.RETAIN));
        consequences.addAll(ConsequenceRule.CORE);
        return consequences;
    }

    private static Attribute attribute(Classifier owner, String name) {
        Attribute attribute = Shop.CF.createAttribute();
        attribute.setName(name);
        owner.getFeature().add(attribute);
        return attribute;
    }

    private static DataType typeIn(Package owner, String name) {
        DataType type = Shop.CF.createDataType();
        type.setName(name);
        owner.getOwnedElement().add(type);
        return type;
    }

    private static List<String> paths(ModelElement root) {
        return Selections.stream(root, Selection.all(), ModelElement.class)
                .filter(e -> !(e instanceof Dependency) && !(e instanceof Constraint))
                .map(QualifiedNames::of)
                .toList();
    }
}
