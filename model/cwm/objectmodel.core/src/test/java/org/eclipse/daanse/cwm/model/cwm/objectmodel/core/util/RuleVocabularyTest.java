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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleVocabularyTest {

    private static final RuleVocabulary CORE = RuleVocabulary.core();

    /** Each text rule reads as written and matches what its rule in code matches. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            name:order*             | name
            path:shop.sales.**      | path
            kind:Attribute          | kind
            tag:documentary         | tag
            tag:documentary=true    | tagValue
            stereotype:entity       | stereotype
            """)
    void textRule_equalsCodeRule(String text, String which) {
        Shop model = new Shop();
        TaggedValues.set(model.orders, "documentary", "true");
        TaggedValues.set(model.customer, "documentary", "false");
        Stereotype entity = Shop.CF.createStereotype();
        entity.setName("entity");
        entity.getExtendedElement().add(model.product);
        model.shop.getOwnedElement().add(entity);
        ElementRule code = switch (which) {
        case "name" -> ElementRules.named("order*");
        case "path" -> ElementRules.path("shop.sales.**");
        case "kind" -> ElementRules.kind(CorePackage.Literals.ATTRIBUTE);
        case "tag" -> ElementRules.tagged("documentary");
        case "tagValue" -> ElementRules.tagged("documentary", "true");
        case "stereotype" -> ElementRules.stereotyped("entity");
        default -> throw new IllegalArgumentException(which);
        };

        ElementRule parsed = CORE.parse(text);

        assertThat(parsed.describe()).isEqualTo(text).isEqualTo(code.describe());
        for (ModelElement element : Selections.list(model.shop, Selection.all(), ModelElement.class)) {
            assertThat(parsed.test(element)).as(QualifiedNames.of(element)).isEqualTo(code.test(element));
        }
    }

    @Test
    void parse_stripsBlanks_andReadsAsWritten() {
        assertThat(CORE.parse("  name :  order*  ").describe()).isEqualTo("name:order*");
        assertThat(CORE.parse("tag: x = 1").describe()).isEqualTo("tag:x = 1");
        assertThat(CORE.parse("tag: x = 1").test(withTag("x", "1"))).isTrue();
        assertThat(CORE.parse("name:a:b").describe()).isEqualTo("name:a:b");
    }

    @Test
    void parse_rejectsUnknownSelector() {
        assertThatThrownBy(() -> CORE.parse("table:*_log"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown selector 'table' in rule 'table:*_log'; known: is, kind, name, path, stereotype, tag");
    }

    @Test
    void parse_rejectsTextWithoutSelector() {
        assertThatThrownBy(() -> CORE.parse("orders"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'orders'")
                .hasMessageContaining("is, kind, name, path, stereotype, tag");
    }

    @Test
    void parse_rejectsEmptyPatternAndEmptyTag() {
        assertThatThrownBy(() -> CORE.parse("name:  ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty pattern");
        assertThatThrownBy(() -> CORE.parse("tag:=1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tag:=1");
    }

    @Test
    void is_looksUpNamedRules_andRejectsUnknownNames() {
        ElementRule attributes = ElementRules.kind("Attribute");
        RuleVocabulary vocabulary = CORE.withRule("attribute", attributes).withRule("class", ElementRules.kind("Class"));

        ElementRule parsed = vocabulary.parse("is:attribute");
        assertThat(parsed.describe()).isEqualTo("is:attribute");
        assertThat(parsed.test(new Shop().id)).isTrue();
        assertThat(parsed.matchesAllOf(CorePackage.Literals.ATTRIBUTE)).isTrue();
        assertThat(vocabulary.rules()).containsExactly("attribute", "class");
        assertThat(vocabulary.rule("class")).isPresent();
        assertThat(vocabulary.rule("nothing")).isEmpty();

        assertThatThrownBy(() -> vocabulary.parse("is:atribute"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown rule 'is:atribute'; known after 'is:': attribute, class");
        assertThatThrownBy(() -> CORE.parse("is:attribute"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known after 'is:': none");
    }

    @Test
    void with_addsTheWordsOfAnotherModule() {
        RuleVocabulary module = RuleVocabulary.empty()
                .withSelector("type", pattern -> ElementRules.kind("Class").and(ElementRules.named(pattern)))
                .withRule("attribute", ElementRules.kind("Attribute"));
        RuleVocabulary vocabulary = CORE.with(module);
        Shop model = new Shop();

        assertThat(vocabulary.selectors()).containsExactly("is", "kind", "name", "path", "stereotype", "tag", "type");
        ElementRule types = vocabulary.parse("type:order*");
        assertThat(types.describe()).isEqualTo("type:order*");
        assertThat(types.test(model.orders)).isTrue();
        assertThat(types.test(model.id)).isFalse();
        assertThat(vocabulary.parse("is:attribute").test(model.id)).isTrue();
        // the same word reached twice is no conflict
        assertThat(vocabulary.with(CORE).with(module).selectors()).isEqualTo(vocabulary.selectors());
        assertThat(CORE.selectors()).doesNotContain("type");
    }

    @Test
    void with_rejectsTwoMeaningsForOneWord() {
        assertThatThrownBy(() -> CORE.withSelector("name", ElementRules::path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'name'");
        RuleVocabulary one = RuleVocabulary.empty().withRule("x", ElementRules.named("a"));
        RuleVocabulary other = RuleVocabulary.empty().withRule("x", ElementRules.named("b"));
        assertThatThrownBy(() -> one.with(other))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'x'");
    }

    @Test
    void withSelector_rejectsIsAndMalformedWords() {
        assertThatThrownBy(() -> CORE.withSelector("is", ElementRules::named))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CORE.withSelector("a:b", ElementRules::named))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CORE.withRule("a b", ElementRules.named("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ModelElement withTag(String tag, String value) {
        ModelElement element = Shop.CF.createClass();
        TaggedValues.set(element, tag, value);
        return element;
    }
}
