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

import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.junit.jupiter.api.Test;

/** Postcodes of the stations: five digits, the whole text. */
class PatternTextCodecTest {

    private final Stations stations = new Stations();

    private Codec codec(String regex) {
        Notation r = Notations.create(stations.notes, "postcode", stations.postcode);
        Notations.patternText(r, regex);
        return Codec.of(r);
    }

    @Test
    void theWholeTextMatches() {
        Codec codec = codec("[0-9]{5}");
        assertThat(codec.read("07743")).as("Jena").isEqualTo("07743");
        assertThat(codec.write("99423")).as("Weimar").isEqualTo("99423");
        assertThatThrownBy(() -> codec.read("07a43")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the letter").isEqualTo(2);
            assertThat(e.reason()).as("the reason").contains("does not match");
        });
        assertThatThrownBy(() -> codec.read("0774")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the end").isEqualTo(4);
            assertThat(e.reason()).as("the reason").contains("ends before it matches");
        });
        assertThatThrownBy(() -> codec.read("077431")).as("more than the whole")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isEqualTo(5));
        assertThatThrownBy(() -> codec.write("D-07743")).as("the writer checks too")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.write(7743)).as("a number is no text")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExpressionThatDoesNotCompile() {
        assertThatThrownBy(() -> codec("[0-9{5}")).as("an open class").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not compile");
    }
}
