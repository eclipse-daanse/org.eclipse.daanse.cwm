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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SelectionTest {

    /**
     * The table in the Javadoc of {@link Selection}, row by row: the rules
     * (comma separated, none when empty), the element, whether
     * {@link Selection#selects} it and whether {@link Selections#copy} keeps it.
     */
    @ParameterizedTest(name = "[{0}] {1}: selects={2}, in copy={3}")
    @CsvSource(delimiter = '|', nullValues = "none", textBlock = """
            none                          | shop.sales.orders     | true  | true
            name:orders                   | shop.sales.orders     | true  | true
            name:orders                   | shop.sales.orders.id  | true  | true
            name:orders                   | shop.sales.customer   | false | false
            name:orders                   | shop.sales            | false | true
            name:orders                   | shop.stock            | false | false
            name:sales, !name:order*      | shop.sales.customer   | true  | true
            name:sales, !name:order*      | shop.sales.order_log  | false | false
            name:sales, !name:order*      | shop.sales.orders.id  | false | false
            name:orders, !name:sales      | shop.sales.orders     | false | false
            name:orders, !kind:Attribute  | shop.sales.orders     | true  | true
            name:orders, !kind:Attribute  | shop.sales.orders.id  | false | false
            !name:stock                   | shop.sales.customer   | true  | true
            !name:stock                   | shop.stock.product    | false | false
            """)
    void semantics(String rules, String path, boolean selects, boolean inCopy) {
        Shop model = new Shop();
        Selection selection = rules == null ? Selection.all() : Selection.parse(rules.split(","));
        ModelElement element = QualifiedNames.find(model.shop, path).orElseThrow();

        assertThat(selection.selects(element)).as("selects").isEqualTo(selects);
        Model copy = Selections.copy(model.shop, selection).model();
        assertThat(QualifiedNames.find(copy, path).isPresent()).as("in the copy").isEqualTo(inCopy);
    }

    @Test
    void all_selectsEverything_andHasNoRules() {
        Shop model = new Shop();

        assertThat(Selection.all().selects(model.id)).isTrue();
        assertThat(Selection.all().includes()).isEmpty();
        assertThat(Selection.all().excludes()).isEmpty();
        assertThat(Selection.builder().build()).isSameAs(Selection.all());
        assertThat(Selection.all().selects(null)).isFalse();
    }

    @Test
    void excludedBy_namesTheRuleOfTheOutermostExcludedOwner() {
        Shop model = new Shop();
        Selection selection = Selection.parse("!kind:Attribute", "!name:sales", "!name:product");

        assertThat(selection.excludedBy(model.id)).map(ElementRule::describe).contains("name:sales");
        assertThat(selection.excludedBy(model.orders)).map(ElementRule::describe).contains("name:sales");
        assertThat(selection.excludedBy(model.product)).map(ElementRule::describe).contains("name:product");
        assertThat(selection.excludedBy(model.stock)).isEmpty();
        assertThat(selection.excludedBy(null)).isEmpty();
    }

    /** A walk carries the verdict down; it must answer as the question about one element does. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "none", textBlock = """
            none
            name:orders
            name:sales, !name:order*
            name:orders, !name:sales
            name:orders, !kind:Attribute
            !name:stock
            path:shop.*.order*, name:product, !name:id
            """)
    void walkAndSingleElementAgree(String rules) {
        Shop model = new Shop();
        Selection selection = rules == null ? Selection.all() : Selection.parse(rules.split(","));
        List<ModelElement> all = Selections.list(model.shop, Selection.all(), ModelElement.class);

        assertThat(Selections.list(model.shop, selection, ModelElement.class))
                .containsExactlyElementsOf(all.stream().filter(selection::selects).toList());
        Selected<Model> cut = Selections.copy(model.shop, selection);
        for (LeftOut left : cut.leftOut()) {
            ModelElement element = QualifiedNames.find(model.shop, left.path()).orElseThrow();
            assertThat(left.reason()).as(left.path()).isEqualTo(
                    selection.excludedBy(element).map(ElementRule::describe).orElse(Selections.NOT_INCLUDED));
        }
    }

    @Test
    void parse_readsLinesCommentsAndExclusions() {
        Selection selection = Selection.parse("""
                # the whole package sales
                path:shop.sales.**

                !name:tmp_*      # but nothing temporary
                ! kind:Attribute
                """, "  tag:x=1  ");

        assertThat(selection.includes()).map(ElementRule::describe).containsExactly("path:shop.sales.**", "tag:x=1");
        assertThat(selection.excludes()).map(ElementRule::describe).containsExactly("name:tmp_*", "kind:Attribute");
        assertThat(selection).hasToString(
                "Selection[path:shop.sales.**, tag:x=1, !name:tmp_*, !kind:Attribute]");
    }

    @Test
    void parse_aHashInsideAPatternIsNoComment() {
        Selection selection = Selection.parse("name:a#b");

        assertThat(selection.includes()).map(ElementRule::describe).containsExactly("name:a#b");
    }

    @Test
    void parse_rejectsAnExclamationMarkAlone() {
        assertThatThrownBy(() -> Selection.parse("!")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'!'");
    }

    @Test
    void parse_rejectsUnknownSelector_namingTheTextAndTheKnownSelectors() {
        assertThatThrownBy(() -> Selection.parse("name:a", "!table:*_log"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'table'")
                .hasMessageContaining("table:*_log")
                .hasMessageContaining("is, kind, name, path, stereotype, tag");
    }

    @Test
    void parse_textAndCodeSelectTheSame() {
        Shop model = new Shop();
        Selection text = Selection.parse("name:sales", "!name:order*");
        Selection code = Selection.builder()
                .include(ElementRules.named("sales"))
                .exclude(ElementRules.named("order*"))
                .build();

        assertThat(text).hasToString(code.toString());
        for (ModelElement element : Selections.list(model.shop, Selection.all(), ModelElement.class)) {
            assertThat(text.selects(element)).as(QualifiedNames.of(element)).isEqualTo(code.selects(element));
        }
    }

    @Test
    void excludesKind_onlyWhenARuleCoversTheWholeClass() {
        Selection selection = Selection.builder()
                .exclude(ElementRules.kind("Attribute"))
                .exclude(ElementRules.kind(CorePackage.Literals.PACKAGE)
                        .and(ElementRules.kindExactly(CorePackage.Literals.MODEL).negate()))
                .exclude(ElementRules.named("tmp_*"))
                .build();

        assertThat(selection.excludesKind(CorePackage.Literals.ATTRIBUTE)).isTrue();
        assertThat(selection.excludesKind(CorePackage.Literals.PACKAGE)).isTrue();
        assertThat(selection.excludesKind(CorePackage.Literals.SUBSYSTEM)).isTrue();
        assertThat(selection.excludesKind(CorePackage.Literals.MODEL)).isFalse();
        assertThat(selection.excludesKind(CorePackage.Literals.CLASS)).isFalse();
        assertThat(selection.excludesKind(null)).isFalse();
        // include rules never exclude a kind
        assertThat(Selection.parse("kind:Class").excludesKind(CorePackage.Literals.ATTRIBUTE)).isFalse();
    }

    @Test
    void unmatched_namesTheRulesThatMatchNothing() {
        Shop model = new Shop();
        Selection selection = Selection.parse("name:sales", "name:sale", "path:shop", "!kind:Attribute",
                "!kind:Atribute");

        assertThat(selection.unmatched(model.shop)).map(ElementRule::describe)
                .containsExactly("name:sale", "kind:Atribute");
        assertThat(selection.unmatched(null)).isEmpty();
        assertThat(Selection.all().unmatched(model.shop)).isEmpty();
    }

    @Test
    void requireIncludesMatch_namesTheIncludeRulesThatMatchNothing_notTheExcludeRules() {
        Shop model = new Shop();

        Selection.parse("name:sales", "!name:nowhere").requireIncludesMatch(model.shop);
        Selection.all().requireIncludesMatch(model.shop);
        assertThatThrownBy(() -> Selection.parse("name:sales", "name:sale", "path:shop.stok.*", "!name:nowhere")
                .requireIncludesMatch(model.shop))
                .isInstanceOfSatisfying(UnmatchedRulesException.class, e -> assertThat(e.rules())
                        .map(ElementRule::describe).containsExactly("name:sale", "path:shop.stok.*"))
                .hasMessage("These rules match nothing in shop: name:sale, path:shop.stok.*");
    }

    @Test
    void mayInclude_isFalseOnlyWhenNoIncludeRuleCanMatchTheClass() {
        assertThat(Selection.all().mayInclude(CorePackage.Literals.ATTRIBUTE)).isTrue();
        assertThat(Selection.parse("!kind:Attribute").mayInclude(CorePackage.Literals.ATTRIBUTE))
                .as("no include rule: everything is included").isTrue();
        Selection classes = Selection.parse("kind:Class");
        assertThat(classes.mayInclude(CorePackage.Literals.CLASS)).isTrue();
        assertThat(classes.mayInclude(CorePackage.Literals.ATTRIBUTE)).isFalse();
        assertThat(Selection.parse("kind:Class", "name:id").mayInclude(CorePackage.Literals.ATTRIBUTE))
                .as("a name may be anything's").isTrue();
        assertThat(classes.mayInclude(null)).isFalse();
    }

    @Test
    void below_carriesTheVerdictDown_asVerdictOfFindsItFromTheTop() {
        Shop model = new Shop();
        Selection selection = Selection.parse("name:sales", "!name:order*");

        Selection.Verdict shop = selection.verdictOf(model.shop);
        Selection.Verdict sales = selection.below(shop, model.sales);
        Selection.Verdict orders = selection.below(sales, model.orders);
        Selection.Verdict id = selection.below(orders, model.id);

        assertThat(shop.selected()).isFalse();
        assertThat(sales.selected()).isTrue();
        assertThat(orders.excluded()).isTrue();
        assertThat(id).isEqualTo(orders).isEqualTo(selection.verdictOf(model.id));
        assertThat(id.excludedBy().describe()).isEqualTo("name:order*");
    }

    @Test
    void builder_rejectsNull() {
        assertThatThrownBy(() -> Selection.builder().include((ElementRule) null))
                .isInstanceOf(NullPointerException.class);
    }
}
