/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** LDML number patterns written with the notation's decimal and group character. */
class NumberPatternTest {

    @Test
    void theParts() {
        NumberPattern p = NumberPattern.parse("#,##0.00", '.', ',');
        assertThat(p.minInteger).as("one digit always").isEqualTo(1);
        assertThat(p.primaryGroup).as("groups of three").isEqualTo(3);
        assertThat(p.secondaryGroup).as("all of three").isEqualTo(3);
        assertThat(p.minFraction).as("two fraction digits always").isEqualTo(2);
        assertThat(p.maxFraction).as("at most two").isEqualTo(2);
        assertThat(p.csvw).as("in the set of CSV on the Web").isTrue();
        NumberPattern german = NumberPattern.parse("#.##0,0##", ',', '.');
        assertThat(german.minFraction).as("the German decimal comma").isEqualTo(1);
        assertThat(german.maxFraction).as("three at most").isEqualTo(3);
        NumberPattern indian = NumberPattern.parse("#,##,##0", '.', ',');
        assertThat(indian.secondaryGroup).as("a secondary group of two").isEqualTo(2);
        NumberPattern exponent = NumberPattern.parse("0.###E+00", '.', null);
        assertThat(exponent.exponent && exponent.exponentPlus).as("an exponent with a plus").isTrue();
        assertThat(exponent.minExponent).as("two exponent digits").isEqualTo(2);
    }

    @Test
    void affixesAndTheNegativeSubpattern() {
        NumberPattern p = NumberPattern.parse("'€ '#,##0.00;'€ '(#,##0.00)", '.', ',');
        assertThat(p.prefix).as("a quoted prefix").isEqualTo("€ ");
        assertThat(p.negativePrefix).as("the negative prefix").isEqualTo("€ (");
        assertThat(p.negativeSuffix).as("the negative suffix").isEqualTo(")");
        assertThat(p.csvw).as("a negative subpattern is beyond CSV on the Web").isFalse();
        assertThat(NumberPattern.parse("#0.0 'it''s'", '.', null).suffix).as("a quote in quotes")
                .isEqualTo(" it's");
        assertThat(NumberPattern.parse("#0.0%", '.', null).scalePosition).as("a percent sign scales").isEqualTo(4);
        assertThat(NumberPattern.parse("#0.0'%'", '.', null).scalePosition).as("quoted, it does not").isEqualTo(-1);
        assertThat(NumberPattern.parse("¤#0.00", '.', null).unsupported).as("a currency sign of a locale")
                .singleElement().asString().contains("¤");
    }

    @Test
    void notWellFormed() {
        assertThatThrownBy(() -> NumberPattern.parse("#0#", '.', null)).as("# after 0")
                .isInstanceOfSatisfying(NumberPattern.PatternException.class, e -> {
                    assertThat(e.position).as("at the #").isEqualTo(2);
                    assertThat(e.reason).as("the reason").isEqualTo("# after 0 in the whole part");
                });
        assertThatThrownBy(() -> NumberPattern.parse("0.#0", '.', null)).as("0 after #")
                .isInstanceOfSatisfying(NumberPattern.PatternException.class, e -> assertThat(e.position).isEqualTo(3));
        assertThatThrownBy(() -> NumberPattern.parse("#,##0.00", ',', null)).as("the comma is the decimal character")
                .isInstanceOf(NumberPattern.PatternException.class);
        assertThatThrownBy(() -> NumberPattern.parse("#,##0", '.', null)).as("no group character named")
                .isInstanceOfSatisfying(NumberPattern.PatternException.class,
                        e -> assertThat(e.reason).contains("neither the decimal nor the group character"));
        assertThatThrownBy(() -> NumberPattern.parse("0;0;0", '.', null)).as("two negative subpatterns")
                .isInstanceOf(NumberPattern.PatternException.class);
        assertThatThrownBy(() -> NumberPattern.parse("'mm", '.', null)).as("an open quote")
                .isInstanceOf(NumberPattern.PatternException.class);
        assertThatThrownBy(() -> NumberPattern.parse("mm", '.', null)).as("no digit")
                .isInstanceOfSatisfying(NumberPattern.PatternException.class,
                        e -> assertThat(e.reason).contains("no digit"));
        assertThatThrownBy(() -> NumberPattern.parse("0.0E", '.', null)).as("an exponent without digits")
                .isInstanceOf(NumberPattern.PatternException.class);
    }
}
