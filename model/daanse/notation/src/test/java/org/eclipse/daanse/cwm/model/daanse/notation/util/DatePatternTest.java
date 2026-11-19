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

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.notation.util.DatePattern.Field;
import org.eclipse.daanse.cwm.model.daanse.notation.util.DatePattern.Literal;
import org.junit.jupiter.api.Test;

/** LDML date patterns: fields, literals, quotes, the set of CSV on the Web, what a pattern holds. */
class DatePatternTest {

    @Test
    void fieldsAndLiterals() {
        DatePattern p = DatePattern.parse("dd.MM.yyyy 'um' HH:mm");
        assertThat(p.tokens).as("the pieces").containsExactly(new Field('d', 2, 0), new Literal(".", 2),
                new Field('M', 2, 3), new Literal(".", 5), new Field('y', 4, 6), new Literal(" um ", 10),
                new Field('H', 2, 16), new Literal(":", 18), new Field('m', 2, 19));
        assertThat(DatePattern.parse("HH 'o''clock'").tokens).as("two quotes are one")
                .contains(new Literal(" o'clock", 2));
        assertThat(DatePattern.parse("yyyy-MM-ddTHH").tokens).as("a lone T is literal")
                .contains(new Literal("T", 10));
    }

    @Test
    void notWellFormed() {
        assertThatThrownBy(() -> DatePattern.parse("dd.MM.yyyy 'um HH")).as("an open quote")
                .isInstanceOfSatisfying(DatePattern.PatternException.class, e -> {
                    assertThat(e.position).as("at the quote").isEqualTo(11);
                    assertThat(e.reason).as("the reason").isEqualTo("the quote is not closed");
                });
        assertThatThrownBy(() -> DatePattern.parse("yyyy-MM-dd at HH")).as("letters that are no field")
                .isInstanceOfSatisfying(DatePattern.PatternException.class, e -> {
                    assertThat(e.position).as("at the t").isEqualTo(12);
                    assertThat(e.reason).as("the reason").contains("t is no field of LDML");
                });
        assertThatThrownBy(() -> DatePattern.parse("ddd.MM")).as("too many d")
                .isInstanceOfSatisfying(DatePattern.PatternException.class,
                        e -> assertThat(e.reason).contains("is not written 3 times"));
        assertThatThrownBy(() -> DatePattern.parse("jj:mm")).as("a symbol of skeletons")
                .isInstanceOfSatisfying(DatePattern.PatternException.class,
                        e -> assertThat(e.reason).contains("skeleton"));
        assertThatThrownBy(() -> DatePattern.parse("")).as("empty").isInstanceOf(DatePattern.PatternException.class);
    }

    @Test
    void theSetOfCsvOnTheWeb() {
        for (String p : List.of("yyyy-MM-dd", "yyyyMMdd", "d.M.yyyy", "M/d/yyyy", "HH:mm:ss.SSS", "HHmm",
                "yyyy-MM-ddTHH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssXXX", "dd/MM/yyyy HH:mm x", "HH:mmX")) {
            assertThat(DatePattern.parse(p).isCsvw()).as(p).isTrue();
        }
        for (String p : List.of("dd.MM.yy", "d. MMMM yyyy", "yyyy-MM", "HH:mm:ss VV", "yyyy/MM/dd", "dd.MM.yyyy XXXX")) {
            assertThat(DatePattern.parse(p).isCsvw()).as(p).isFalse();
        }
    }

    @Test
    void whatAPatternHolds() {
        assertThat(DatePattern.parse("EEE, d. MMM yyyy").textField()).as("the first field in words")
                .isEqualTo(new Field('E', 3, 0));
        assertThat(DatePattern.parse("dd.MM.yyyy HH:mm").textField()).as("no words").isNull();
        assertThat(DatePattern.parse("HH:mm").hasTimeOfDay()).as("a time of day").isTrue();
        assertThat(DatePattern.parse("HH:mm").hasDate()).as("no date").isFalse();
        assertThat(DatePattern.parse("HH:mmXXX").hasZoneField()).as("an offset").isTrue();
        assertThat(DatePattern.parse("yyyyMMdd").digitsOnlyYearFirst()).as("a key of a day").isTrue();
        assertThat(DatePattern.parse("ddMMyyyy").digitsOnlyYearFirst()).as("a leading zero would be lost").isFalse();
        assertThat(DatePattern.parse("yyyy-MM").digitsOnlyYearFirst()).as("a dash").isFalse();
        assertThat(DatePattern.name('Q')).as("the name of a field").isEqualTo("quarter");
    }
}
