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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;
import org.eclipse.daanse.cwm.model.daanse.notation.NumberText;
import org.eclipse.daanse.cwm.model.daanse.notation.PatternText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.eclipse.daanse.cwm.model.daanse.notation.Trim;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.junit.jupiter.api.Test;

/** The helpers: one element per create, hung on its owner; the notation of an element, own or of its type. */
class NotationsTest {

    private final Stations s = new Stations();

    @Test
    void createHangsTheNotationOnItsOwner() {
        Notation r = Notations.create(s.notes, "observed", s.observed, s.day);
        assertThat(r.getNamespace()).as("owned by the package").isSameAs(s.notes);
        assertThat(r.getName()).as("named").isEqualTo("observed");
        assertThat(r.getModelElement()).as("the elements").containsExactlyInAnyOrder(s.observed, s.day);
        assertThat(r.getForm()).as("no form yet").isNull();
        assertThat(r.getTrim()).as("trims both ends by default").isEqualTo(Trim.BOTH);
        assertThatThrownBy(() -> Notations.create(s.notes, "none")).as("at least one element")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notations.create(s.notes, null, s.day)).as("a name")
                .isInstanceOf(NullPointerException.class).hasMessage("name");
    }

    @Test
    void eachFormByOneHelper() {
        Notation r = Notations.create(s.notes, "observed", s.observed);
        TemporalText named = Notations.lenient(Notations.language(Notations.zone(
                Notations.temporalText(r, TemporalForm.DATE_TIME), "Europe/Berlin"), "de"));
        assertThat(r.getForm()).as("the form of the notation").isSameAs(named);
        assertThat(named.isSetNamed() && named.isLenient() && "de".equals(named.getLanguage())
                && "Europe/Berlin".equals(named.getZone())).as("named, lenient, a language and a zone").isTrue();
        TemporalText pattern = Notations.temporalText(r, "dd.MM.yyyy");
        assertThat(r.getForm()).as("a new form replaces the old").isSameAs(pattern);
        assertThat(pattern.isSetNamed()).as("no named form").isFalse();
        assertThat(pattern.isLenient()).as("strict by default").isFalse();
        TemporalNumber count = Notations.zone(Notations.temporalNumber(r,
                Notations.SPREADSHEET_EPOCH, s.unit(SI.DAY)), "Europe/Berlin");
        assertThat(count.getUnit()).as("the unit").isSameAs(s.unit(SI.DAY));
        NumberText number = Notations.withSigns(Notations.pattern(Notations.numberText(r, ",", "."),
                "#.##0,00"));
        assertThat(number.isBare()).as("not bare").isFalse();
        assertThat(Notations.numberText(r, ".").getGroupChar()).as("no group character").isNull();
        assertThat(Notations.numberText(r, ".").isBare()).as("bare by default").isTrue();
        assertThat(number.isLenient()).as("groups read strictly by default").isFalse();
        assertThat(Notations.lenient(number).isLenient()).as("lenient").isTrue();
        BooleanText words = Notations.ignoreCase(Notations.also(Notations.booleanText(r, "J", "N"),
                List.of("ja"), List.of("nein")));
        assertThat(words.getTrueWords()).as("the first is written").containsExactly("J", "ja");
        assertThat(words.isIgnoreCase()).as("any case").isTrue();
        PatternText postcode = Notations.patternText(r, "[0-9]{5}");
        assertThat(postcode.getPattern()).as("the expression").isEqualTo("[0-9]{5}");
        assertThat(Notations.missing(Notations.trim(r, Trim.NONE), "-", "").getMissingWords())
                .as("the missing words in order").containsExactly("-", "");
    }

    @Test
    void theNotationOfAnElementIsItsOwnElseItsTypes() {
        Attribute inlet = Stations.attribute(s.reading, "inlet", s.decimal);
        Notation ofType = Notations.create(s.notes, "decimal", s.decimal);
        Notations.numberText(ofType, ",");
        Notation own = Notations.create(s.notes, "amount", s.amount);
        Notations.numberText(own, ".");
        assertThat(Notations.of(inlet)).as("the type's").contains(ofType);
        assertThat(Notations.of(s.amount)).as("its own first").contains(own);
        assertThat(Notations.own(inlet)).as("none of its own").isEmpty();
        assertThat(Notations.of(s.station)).as("none").isEmpty();
        assertThat(Notations.typeOf(inlet)).as("the data type").contains(s.decimal);
        assertThat(Notations.typeOf(s.reading)).as("a class has no type").isEmpty();
    }
}
