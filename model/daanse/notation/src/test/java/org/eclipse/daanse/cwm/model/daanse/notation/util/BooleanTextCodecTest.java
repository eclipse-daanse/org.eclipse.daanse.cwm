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

import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.junit.jupiter.api.Test;

/** Whether a day was dry, written as words. */
class BooleanTextCodecTest {

    private final Stations stations = new Stations();

    @Test
    void theWordsBothWays() {
        Notation r = Notations.create(stations.notes, "dry", stations.dry);
        BooleanText b = Notations.also(Notations.booleanText(r, "J", "N"), List.of("ja"),
                List.of("nein"));
        Codec codec = Codec.of(r);
        assertThat(codec.read("J")).as("the first true word").isEqualTo(true);
        assertThat(codec.read("nein")).as("another false word").isEqualTo(false);
        assertThat(codec.write(true)).as("the first true word is written").isEqualTo("J");
        assertThat(codec.write(false)).as("the first false word is written").isEqualTo("N");
        assertThatThrownBy(() -> codec.read("Ja")).as("the case counts")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the word").isZero();
                    assertThat(e.reason()).as("names the words").contains("J, ja").contains("N, nein");
                });
        Notations.ignoreCase(b);
        assertThat(Codec.of(r).read("JA")).as("in any case").isEqualTo(true);
        assertThatThrownBy(() -> codec.write("J")).as("a word is no truth value")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWordIsNotTrueAndFalse() {
        Notation r = Notations.create(stations.notes, "dry", stations.dry);
        Notations.also(Notations.booleanText(r, "1", "0"), List.of(), List.of("1"));
        assertThatThrownBy(() -> Codec.of(r)).as("1 is true and false").isInstanceOf(IllegalArgumentException.class);
    }
}
