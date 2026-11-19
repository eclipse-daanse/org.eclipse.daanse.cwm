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
import java.time.LocalDate;

import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.Trim;
import org.junit.jupiter.api.Test;

/** What every form shares: trimming, missing words, no form, positions in the text as given. */
class CodecTest {

    private final Stations stations = new Stations();

    @Test
    void missingWordsAreNoValue() {
        Notation r = Notations.missing(Notations.create(stations.notes, "rain", stations.rain), "-",
                "", "n/a");
        Notations.numberText(r, ",");
        Codec codec = Codec.of(r);
        assertThat(codec.read("-")).as("a missing word").isNull();
        assertThat(codec.read("")).as("the empty text, named").isNull();
        assertThat(codec.read(" n/a ")).as("after trimming").isNull();
        assertThat(codec.read(null)).as("no text").isNull();
        assertThat(codec.read("0,4")).as("a value").isEqualTo(new BigDecimal("0.4"));
        assertThat(codec.write(null)).as("the first missing word is written").isEqualTo("-");
        assertThat(codec.notation()).as("the notation").isSameAs(r);
    }

    @Test
    void withoutMissingWordsAnEmptyTextDoesNotFit() {
        Notation r = Notations.create(stations.notes, "day", stations.day);
        Notations.temporalText(r, TemporalForm.DATE);
        Codec codec = Codec.of(r);
        assertThatThrownBy(() -> codec.read("  ")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("after the white space").isEqualTo(2);
            assertThat(e.reason()).as("says how to read it as no value").contains("name \"\" as a missing word");
        });
        assertThat(codec.write(null)).as("no missing word: no text").isNull();
    }

    @Test
    void aValueWhoseTextIsAMissingWordIsRefused() {
        Notation r = Notations.missing(Notations.create(stations.notes, "rain", stations.rain), "0");
        Notations.numberText(r, ".");
        assertThatThrownBy(() -> Codec.of(r).write(BigDecimal.ZERO)).as("0 would read back as no value")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing word");
    }

    @Test
    void trimming() {
        Notation r = Notations.create(stations.notes, "day", stations.day);
        Notations.temporalText(r, TemporalForm.DATE);
        assertThat(Codec.of(r).read(" 2026-03-19\t")).as("both ends by default").isEqualTo(LocalDate.of(2026, 3, 19));
        Notations.trim(r, Trim.START);
        assertThat(Codec.of(r).read(" 2026-03-19")).as("a no-break space at the start")
                .isEqualTo(LocalDate.of(2026, 3, 19));
        assertThatThrownBy(() -> Codec.of(r).read("2026-03-19 ")).as("not the end")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isEqualTo(10));
        Notations.trim(r, Trim.NONE);
        assertThatThrownBy(() -> Codec.of(r).read(" 2026-03-19")).as("none")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isZero());
        Notations.trim(r, Trim.BOTH);
        assertThatThrownBy(() -> Codec.of(r).read("   2026-13-19")).as("positions count in the text as given")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the month").isEqualTo(8);
                    assertThat(e.getMessage()).as("the message").isEqualTo("'   2026-13-19' does not fit at position 8: "
                            + "13 is no month (1 to 12)");
                });
    }

    @Test
    void withoutAFormTheValueIsTheText() {
        Notation r = Notations.missing(Notations.create(stations.notes, "station", stations.station),
                "?");
        Codec codec = Codec.of(r);
        assertThat(codec.read(" Jena-Paradies ")).as("trimmed text").isEqualTo("Jena-Paradies");
        assertThat(codec.read("?")).as("a missing word").isNull();
        assertThat(codec.write("Erfurt")).as("text written").isEqualTo("Erfurt");
        assertThatThrownBy(() -> codec.write(12)).as("a number is no text").isInstanceOf(IllegalArgumentException.class);
    }
}
