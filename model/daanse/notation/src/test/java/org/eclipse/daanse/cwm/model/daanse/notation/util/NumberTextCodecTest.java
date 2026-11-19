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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Consumer;

import org.eclipse.daanse.cwm.model.daanse.notation.NumberText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.junit.jupiter.api.Test;

/** Amounts of rain written as text: decimal and group characters, patterns, signs around the number, failures. */
class NumberTextCodecTest {

    private final Stations stations = new Stations();

    private Codec codec(String decimal, String group, Consumer<NumberText> more) {
        Notation r = Notations.create(stations.notes, "rain", stations.rain);
        NumberText n = group == null ? Notations.numberText(r, decimal)
                : Notations.numberText(r, decimal, group);
        more.accept(n);
        return Codec.of(r);
    }

    private static Consumer<NumberText> none() {
        return n -> {
        };
    }

    private static Consumer<NumberText> pattern(String pattern) {
        return n -> Notations.pattern(n, pattern);
    }

    @Test
    void withoutAPatternAsCsvOnTheWeb() {
        Codec codec = codec(".", null, none());
        assertThat(codec.read("1234.5")).as("a decimal").isEqualTo(new BigDecimal("1234.5"));
        assertThat(codec.read("-0.25")).as("negative").isEqualTo(new BigDecimal("-0.25"));
        assertThat(codec.read("+3")).as("with a plus").isEqualTo(new BigDecimal("3"));
        assertThat((BigDecimal) codec.read("1.5E3")).as("an exponent").isEqualByComparingTo("1500");
        assertThat(codec.write(new BigDecimal("1234.50"))).as("written as it is").isEqualTo("1234.50");
        assertThat(codec.write(0.1d)).as("a double by its shortest text").isEqualTo("0.1");
        assertThat(codec.write(42L)).as("a long").isEqualTo("42");
        assertThat(codec.write(new BigInteger("12345678901234567890"))).as("a big integer")
                .isEqualTo("12345678901234567890");
    }

    @Test
    void decimalAndGroupCharacters() {
        Codec codec = codec(",", ".", none());
        assertThat(codec.read("1.234,5")).as("German").isEqualTo(new BigDecimal("1234.5"));
        assertThat(codec.read("1234,5")).as("groups are optional").isEqualTo(new BigDecimal("1234.5"));
        assertThat(codec.write(new BigDecimal("1234567.891"))).as("written in groups of three")
                .isEqualTo("1.234.567,891");
        assertThatThrownBy(() -> codec.read("1..234")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the second group character").isEqualTo(2);
            assertThat(e.reason()).as("the reason").isEqualTo("two group characters in a row");
        });
        assertThatThrownBy(() -> codec.read("12,")).isInstanceOfSatisfying(UnfitTextException.class,
                e -> assertThat(e.position()).as("after the decimal character").isEqualTo(3));
    }

    @Test
    void strictReadingWantsTheUsualGroups() {
        Codec codec = codec(",", ".", none());
        assertThat(codec.read("-1.234.567,5")).as("a first group of one, then groups of three")
                .isEqualTo(new BigDecimal("-1234567.5"));
        assertThat(codec.read("123.456")).as("a first group of three").isEqualTo(new BigDecimal("123456"));
        assertThatThrownBy(() -> codec.read("12.5")).as("12.5 is no 125")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the last group").isEqualTo(3);
                    assertThat(e.reason()).as("the reason")
                            .isEqualTo("the last group has 1 digit, not 3 (the usual grouping)");
                });
        assertThatThrownBy(() -> codec.read("1.23.456")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the group in between").isEqualTo(2);
            assertThat(e.reason()).as("the reason").isEqualTo("a group has 2 digits, not 3 (the usual grouping)");
        });
        assertThatThrownBy(() -> codec.read("1234.567,5")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the first group").isEqualTo(0);
            assertThat(e.reason()).as("the reason")
                    .isEqualTo("the first group has 4 digits, more than 3 (the usual grouping)");
        });
    }

    @Test
    void lenientReadingTakesAGroupCharacterBetweenAnyDigits() {
        Codec codec = codec(",", ".", Notations::lenient);
        assertThat(codec.read("12.5")).as("as CSV on the Web reads it").isEqualTo(new BigDecimal("125"));
        assertThat(codec.read("1.23.4,5")).as("groups of any size").isEqualTo(new BigDecimal("1234.5"));
        assertThatThrownBy(() -> codec.read("1..234")).as("still not two in a row")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isEqualTo(2));
        assertThatThrownBy(() -> codec.read("12.,5")).as("still not at the end of the whole part")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isEqualTo(2));
        assertThat(codec.write(new BigDecimal("1234567.891"))).as("written in the proper groups")
                .isEqualTo("1.234.567,891");
        Codec patterned = codec(".", ",", n -> Notations.lenient(Notations.pattern(n, "#,##0.00")));
        assertThat(patterned.read("1,23.50")).as("whatever groups the pattern states")
                .isEqualTo(new BigDecimal("123.50"));
        assertThat(patterned.write(new BigDecimal("1234.5"))).as("written in the groups of the pattern")
                .isEqualTo("1,234.50");
        Codec ungrouped = codec(".", ",", n -> Notations.lenient(Notations.pattern(n, "0.00")));
        assertThat(ungrouped.read("1,234.00")).as("where the pattern groups none")
                .isEqualTo(new BigDecimal("1234.00"));
        assertThat(ungrouped.write(new BigDecimal("1234"))).as("written as the pattern writes it")
                .isEqualTo("1234.00");
    }

    @Test
    void aPatternWithGroupsAndAFraction() {
        Codec codec = codec(".", ",", pattern("#,##0.00"));
        assertThat(codec.write(new BigDecimal("1234.5"))).as("two fraction digits, groups").isEqualTo("1,234.50");
        assertThat(codec.write(new BigDecimal("0.125"))).as("rounded half to even").isEqualTo("0.12");
        assertThat(codec.write(new BigDecimal("-1234567"))).as("negative").isEqualTo("-1,234,567.00");
        assertThat(codec.read("1,234.50")).as("read back").isEqualTo(new BigDecimal("1234.50"));
        assertThat(codec.read("-0.75")).as("negative").isEqualTo(new BigDecimal("-0.75"));
        assertThat(codec.read("1234.50")).as("a text without groups").isEqualTo(new BigDecimal("1234.50"));
        assertThatThrownBy(() -> codec.read("1,23.50")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("after the group character").isEqualTo(2);
            assertThat(e.reason()).as("the reason")
                    .isEqualTo("the last group has 2 digits, not 3 (the grouping of the pattern)");
        });
        assertThatThrownBy(() -> codec.read("1,2,345.00")).isInstanceOfSatisfying(UnfitTextException.class,
                e -> assertThat(e.position()).as("at the group in between").isEqualTo(2));
        assertThatThrownBy(() -> codec.read("1234,567.00")).isInstanceOfSatisfying(UnfitTextException.class,
                e -> assertThat(e.position()).as("at the first group").isEqualTo(0));
        assertThatThrownBy(() -> codec(".", ",", pattern("0.00")).read("1,234.00"))
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the group character").isEqualTo(1);
                    assertThat(e.reason()).as("the reason")
                            .isEqualTo("a group character, but the pattern groups no digits");
                });
        assertThatThrownBy(() -> codec.read("1,234.5")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the fraction").isEqualTo(6);
            assertThat(e.reason()).as("the reason").contains("the fraction has 1 digits");
        });
        Codec indian = codec(".", ",", pattern("#,##,##0"));
        assertThat(indian.write(12345678L)).as("a secondary group of two").isEqualTo("1,23,45,678");
        assertThat(indian.read("1,23,45,678")).as("read back").isEqualTo(new BigDecimal("12345678"));
        assertThatThrownBy(() -> indian.read("123,45,678")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the first group").isEqualTo(0);
            assertThat(e.reason()).as("the reason")
                    .isEqualTo("the first group has 3 digits, more than 2 (the grouping of the pattern)");
        });
    }

    @Test
    void aNegativeSubpatternAndQuotedText() {
        Codec codec = codec(".", ",", pattern("#,##0.00' mm';(#,##0.00' mm')"));
        assertThat(codec.read("(1,234.50 mm)")).as("in parentheses").isEqualTo(new BigDecimal("-1234.50"));
        assertThat(codec.read("12.00 mm")).as("positive").isEqualTo(new BigDecimal("12.00"));
        assertThat(codec.write(new BigDecimal("-12"))).as("written in parentheses").isEqualTo("(12.00 mm)");
        assertThatThrownBy(() -> codec.read("12.00")).as("the suffix is missing")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.reason()).contains("' mm'"));
        Codec percent = codec(".", null, pattern("#0.0'%'"));
        assertThat(percent.read("12.5%")).as("a quoted percent sign is text").isEqualTo(new BigDecimal("12.5"));
        assertThat(percent.write(new BigDecimal("12.5"))).as("no scale").isEqualTo("12.5%");
    }

    @Test
    void anExponent() {
        Codec codec = codec(".", null, pattern("0.###E0"));
        assertThat(codec.write(new BigDecimal("12345"))).as("one digit before the point").isEqualTo("1.234E4");
        assertThat(codec.write(new BigDecimal("0.00042"))).as("a negative exponent").isEqualTo("4.2E-4");
        assertThat((BigDecimal) codec.read("1.234E4")).as("read").isEqualByComparingTo("12340");
        Codec plus = codec(".", null, pattern("00.0E+00"));
        assertThat(plus.write(new BigDecimal("12345"))).as("two digits before the point, a plus").isEqualTo("12.3E+03");
    }

    @Test
    void aScaleIsAUnitNotAPattern() {
        assertThatThrownBy(() -> codec(".", null, pattern("#0.0%"))).as("an unquoted percent sign scales")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("a scale is a unit");
        assertThatThrownBy(() -> codec(".", null, pattern("@@@"))).as("significant digits are not read")
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> codec(".", ".", none())).as("one character for both")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec("..", null, none())).as("two characters")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signsAroundTheNumberAreStrippedWhereItIsNotBare() {
        Codec bare = codec(",", null, none());
        assertThatThrownBy(() -> bare.read("12,5 %")).as("bare: the sign does not fit")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isEqualTo(4));
        Codec signs = codec(",", null, Notations::withSigns);
        assertThat(signs.read("12,5 %")).as("a percent sign").isEqualTo(new BigDecimal("12.5"));
        assertThat(signs.read("€ 12,50")).as("a currency sign").isEqualTo(new BigDecimal("12.50"));
        assertThat(signs.read("EUR -3,00")).as("a currency code").isEqualTo(new BigDecimal("-3.00"));
        assertThatThrownBy(() -> signs.read("(3,00)")).as("parentheses are no sign")
                .isInstanceOf(UnfitTextException.class);
        Codec suffix = codec(".", null, n -> Notations.withSigns(Notations.pattern(n, "0.0' mm'")));
        assertThat(suffix.read("4.2 mm")).as("the pattern's suffix first").isEqualTo(new BigDecimal("4.2"));
        assertThat(suffix.read("4.2")).as("else the signs around the number are optional")
                .isEqualTo(new BigDecimal("4.2"));
        assertThat(suffix.read("-4.2 Liter")).as("and other signs are stripped").isEqualTo(new BigDecimal("-4.2"));
    }

    @Test
    void specialValues() {
        Codec codec = codec(".", null, none());
        assertThat(codec.read("NaN")).as("not a number").isEqualTo(Double.NaN);
        assertThat(codec.read("-INF")).as("minus infinity").isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(codec.write(Double.POSITIVE_INFINITY)).as("infinity").isEqualTo("INF");
        assertThatThrownBy(() -> codec.write("twelve")).as("text is no number")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valuesTravelThereAndBack() {
        Codec codec = codec(",", ".", pattern("#.##0,###"));
        for (String n : new String[] { "0", "7", "1234.5", "-98765.432", "1000000" }) {
            BigDecimal value = new BigDecimal(n);
            String text = codec.write(value);
            assertThat((BigDecimal) codec.read(text)).as(n + " as " + text).isEqualByComparingTo(value);
            assertThat(codec.write(codec.read(text))).as(text + " again").isEqualTo(text);
        }
    }
}
