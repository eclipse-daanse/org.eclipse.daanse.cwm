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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** Each rule of {@link NotationModelValidator}: a model that keeps it, and one that breaks it. */
class NotationModelValidatorTest {

    private final Stations s = new Stations();

    private Notation on(String name, org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement... elements) {
        return Notations.create(s.notes, name, elements);
    }

    @Test
    void aWellWrittenNetworkHasNoFinding() {
        Notations.zone(Notations.temporalText(on("observed", s.observed), "dd.MM.yyyy HH:mm"),
                "Europe/Berlin");
        Notations.temporalText(on("day", s.day), TemporalForm.DATE);
        Notations.temporalText(on("dayKey", s.dayKey), TemporalForm.BASIC_DATE);
        Notations.numberText(Notations.missing(on("rain", s.rain), "-"), ",", ".");
        Notations.booleanText(on("dry", s.dry), "J", "N");
        Notations.temporalNumber(on("logged", s.logged), Notations.UNIX_EPOCH, s.unit(SI.SECOND));
        Notations.patternText(on("postcode", s.postcode), "[0-9]{5}");
        Notations.temporalText(on("takenAt", s.takenAt), TemporalForm.DATE_TIME);
        Notations.temporalText(on("receivedAt", s.receivedAt), TemporalForm.DATE_TIME_WITH_OFFSET);
        Notations.numberText(on("amount", s.amount), ".");
        assertThat(NotationModelValidator.check(s.model)).as("no finding").isEmpty();
        assertThat(NotationModelValidator.validate(s.model)).as("no error").isEmpty();
    }

    @Test
    void nt1_aNotationNamesAttributesParametersAndDataTypes() {
        Notations.temporalText(on("observed", s.observed, s.takenAt), TemporalForm.DATE_TIME_WITH_OFFSET);
        Notations.numberText(on("decimal", s.decimal), ".");
        Notations.numberText(on("limit", Stations.parameter(s.design, "limit", s.decimal)), ".");
        assertThat(rules()).as("columns, attributes, data types, parameters").doesNotContain("NT1");
        Notations.numberText(on("reading", s.reading), ".");
        assertThat(errors("NT1")).as("a class").singleElement().asString().contains("names Class weather.design.Reading");
    }

    @Test
    void nt1_noDataTypeOwnsANotation() {
        Notations.numberText(Notations.create(s.decimal, "decimal", s.decimal), ".");
        assertThat(errors("NT1")).as("owned by its data type").singleElement().asString()
                .contains("owned by a data type");
    }

    @Test
    void nt2_oneNotationPerElement() {
        Notations.patternText(on("station", s.station), "[A-Za-z-]+");
        assertThat(rules()).as("one").doesNotContain("NT2");
        Notations.patternText(on("stationAgain", s.station), "[A-Z]+");
        assertThat(errors("NT2")).as("two").singleElement().asString().contains("has 2 notations");
    }

    @Test
    void nt3_aNamedFormOrAPattern() {
        TemporalText both = Notations.temporalText(on("day", s.day), TemporalForm.DATE);
        assertThat(rules()).as("a named form").doesNotContain("NT3");
        both.setPattern("dd.MM.yyyy");
        TemporalText neither = Notations.temporalText(on("observed", s.observed), "yyyy");
        neither.setPattern(null);
        assertThat(errors("NT3")).as("both and neither").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("names the form date and the pattern"))
                .anyMatch(m -> m.contains("names neither"));
    }

    @Test
    void nt4_aDatePatternIsWellFormed() {
        Notations.temporalText(on("day", s.day), "dd.MM.yyyy");
        assertThat(rules()).as("well-formed").doesNotContain("NT4");
        Notations.temporalText(on("observed", s.observed), "dd.MM.yyyy at HH");
        assertThat(errors("NT4")).as("t is no field").singleElement().asString().contains("at position 12")
                .contains("t is no field of LDML");
    }

    @Test
    void nt5_patternsBeyondCsvOnTheWebAreAWarning() {
        Notations.temporalText(on("day", s.day), "d.M.yyyy");
        Notations.pattern(Notations.numberText(on("rain", s.rain), ".", ","), "#,##0.0");
        assertThat(rules()).as("in the set").doesNotContain("NT5");
        Notations.temporalText(on("dayShort", s.dayKey), "dd.MM.yy");
        Notations.pattern(Notations.numberText(on("amount", s.amount), ".", ","), "#,##0.0;(#,##0.0)");
        assertThat(warnings("NT5")).as("two patterns").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("'dd.MM.yy'")).anyMatch(m -> m.contains("'#,##0.0;(#,##0.0)'"));
        assertThat(errors("NT5")).as("the code takes no error").isEmpty();
    }

    @Test
    void nt6_wordsNeedALanguage() {
        TemporalText german = Notations.temporalText(on("day", s.day), "d. MMMM yyyy");
        Notations.language(german, "de");
        assertThat(rules()).as("with a language").doesNotContain("NT6");
        german.setLanguage(null);
        Notations.language(Notations.temporalText(on("observed", s.observed), "yyyy-MM-dd'T'HH:mmXXX"),
                "de_DE!");
        assertThat(errors("NT6")).as("no language, a tag that is no BCP 47").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("MMMM (month) in words but names no language"))
                .anyMatch(m -> m.contains("no BCP 47 language tag"));
    }

    @Test
    void nt7_anInstantNeedsAZone() {
        Notations.temporalText(on("receivedAt", s.receivedAt), TemporalForm.DATE_TIME_WITH_OFFSET);
        Notations.zone(Notations.temporalText(on("sealedText", Stations.attribute(s.reading, "sealedAt",
                s.instant)), "dd.MM.yyyy HH:mm"), "Europe/Berlin");
        assertThat(rules()).as("an offset in the text, a zone named").doesNotContain("NT7");
        Notations.temporalText(on("arrivedAt", Stations.attribute(s.reading, "arrivedAt", s.instant)),
                TemporalForm.DATE_TIME);
        Notations.temporalNumber(on("countedAt", Stations.attribute(s.reading, "countedAt", s.instant)),
                Notations.SPREADSHEET_EPOCH, s.unit(SI.DAY));
        assertThat(errors("NT7")).as("a local text and a local count").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("weather.design.Reading.arrivedAt of type Instant holds an instant"))
                .anyMatch(m -> m.contains("from the local epoch 1899-12-30"));
    }

    @Test
    void nt8_aLocalTimeOfDayIsAWarning() {
        Notations.temporalText(on("takenAt", s.takenAt), TemporalForm.DATE_TIME);
        Notations.temporalText(on("day", s.day), TemporalForm.DATE);
        assertThat(rules()).as("a type that says local, a date").doesNotContain("NT8");
        Notations.temporalText(on("observed", s.observed), "dd.MM.yyyy HH:mm");
        assertThat(warnings("NT8")).as("a text column says nothing").singleElement().asString()
                .contains("read as a local time");
        assertThat(errors("NT8")).as("the code takes no error").isEmpty();
    }

    @Test
    void nt9_theUnitIsAUnitOfTime() {
        Notations.temporalNumber(on("logged", s.logged), Notations.UNIX_EPOCH, s.unit(SI.MILLISECOND));
        assertThat(rules()).as("milliseconds").doesNotContain("NT9");
        Notations.temporalNumber(on("loggedTooFar", s.dayKey), Notations.UNIX_EPOCH, s.unit(SI.METRE));
        assertThat(errors("NT9")).as("metres").singleElement().asString().contains("no unit of time");
    }

    @Test
    void nt10_decimalAndGroupCharacters() {
        Notations.numberText(on("rain", s.rain), ",", ".");
        assertThat(rules()).as("comma and point").doesNotContain("NT10");
        Notations.numberText(on("amount", s.amount), ".", ".");
        Notations.numberText(on("dry", s.dry), "..");
        Notations.numberText(on("label", s.label), "5");
        assertThat(errors("NT10")).as("the same, two characters, a digit").extracting(Finding::message).hasSize(3)
                .anyMatch(m -> m.contains("are both '.'")).anyMatch(m -> m.contains("is not one character"))
                .anyMatch(m -> m.contains("is a digit, a sign or E"));
    }

    @Test
    void nt11_wordsAreTrueOrFalseOrMissing() {
        BooleanText dry = Notations.booleanText(Notations.missing(on("dry", s.dry), "-"), "ja", "nein");
        assertThat(rules()).as("distinct words").doesNotContain("NT11");
        Notations.also(dry, List.of("-"), List.of("JA"));
        Notations.ignoreCase(dry);
        assertThat(errors("NT11")).as("JA in any case, - missing and true").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("'ja' is a true and a false word"))
                .anyMatch(m -> m.contains("the missing word '-' is a true word"));
    }

    @Test
    void nt12_theFormFitsTheType() {
        Notations.temporalText(on("dayKey", s.dayKey), TemporalForm.BASIC_DATE);
        Notations.booleanText(on("flag", Stations.column(s.readings, "flag", s.integer)), "1", "0");
        Notations.temporalText(on("takenAt", s.takenAt), TemporalForm.DATE_TIME);
        Notations.numberText(on("amount", s.amount), ".");
        assertThat(rules()).as("digits of a day in a number, 1 and 0, values").doesNotContain("NT12");
        Notations.temporalNumber(on("observed", s.observed), Notations.UNIX_EPOCH, s.unit(SI.SECOND));
        Notations.temporalText(on("measuredOn", s.measuredOn), TemporalForm.DATE);
        Notations.numberText(on("logged", s.logged), ".");
        Notations.temporalText(on("dayKeyDots", Stations.column(s.readings, "day_key_2", s.integer)),
                "dd.MM.yyyy");
        Notations.temporalText(on("label", s.label), TemporalForm.DATE);
        Notations.temporalText(on("volume", Stations.attribute(s.reading, "volume", s.decimal)),
                TemporalForm.DATE);
        assertThat(errors("NT12")).as("five misfits; the text of a date in a logical text fits")
                .extracting(Finding::message).hasSize(5)
                .anyMatch(m -> m.contains("observed is of type VARCHAR (text); a temporal number is stored as a number"))
                .anyMatch(m -> m.contains("measured_on is of type DATE (temporal)"))
                .anyMatch(m -> m.contains("logged is of type BIGINT (number); a number text is stored as text"))
                .anyMatch(m -> m.contains("day_key_2 is of type INTEGER"))
                .anyMatch(m -> m.contains("volume is of type Decimal (number); a temporal text writes a time as text"));
    }

    @Test
    void nt13_zonesAndEpochs() {
        Notations.zone(Notations.temporalText(on("observed", s.observed), "HH:mm"), "+01:00");
        Notations.temporalNumber(on("logged", s.logged), "1970-01-01T00:00:00+01:00", s.unit(SI.SECOND));
        assertThat(rules()).as("a fixed offset, an epoch with an offset").doesNotContain("NT13");
        Notations.zone(Notations.temporalText(on("day", s.day), "HH:mm"), "Europe/Berlin");
        Notations.zone(Notations.temporalText(on("label", s.label), TemporalForm.DATE_TIME),
                "Thuringia/Jena");
        Notations.temporalNumber(on("dayKey", s.dayKey), "since the station opened", s.unit(SI.DAY));
        assertThat(errors("NT13")).as("a changing zone without a date, an unknown zone, no epoch")
                .extracting(Finding::message).hasSize(3)
                .anyMatch(m -> m.contains("cannot take the zone Europe/Berlin"))
                .anyMatch(m -> m.contains("'Thuringia/Jena' is no zone"))
                .anyMatch(m -> m.contains("is no ISO 8601 date"));
    }

    @Test
    void nt14_aZoneForADayCountFromALocalEpoch() {
        Notations.zone(Notations.temporalNumber(on("dayKey", s.dayKey), Notations.SPREADSHEET_EPOCH,
                s.unit(SI.DAY)), "Europe/Berlin");
        assertThat(rules()).as("days from a local epoch").doesNotContain("NT14");
        Notations.zone(Notations.temporalNumber(on("logged", s.logged), Notations.UNIX_EPOCH,
                s.unit(SI.SECOND)), "Europe/Berlin");
        Notations.zone(Notations.temporalNumber(on("amount", s.amount), "1970-01-01", s.unit(SI.SECOND)),
                "Europe/Berlin");
        assertThat(errors("NT14")).as("an instant epoch, seconds").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("a count from an instant takes no zone"))
                .anyMatch(m -> m.contains("a zone stands with a unit of a day or longer"));
    }

    @Test
    void nt15_aNumberPatternIsWellFormedAndHoldsNoScale() {
        Notations.pattern(Notations.numberText(on("rain", s.rain), "."), "#0.0' %'");
        assertThat(rules()).as("a quoted percent sign").doesNotContain("NT15");
        Notations.pattern(Notations.numberText(on("amount", s.amount), "."), "#0.0%");
        Notations.pattern(Notations.numberText(on("label", s.label), "."), "#0#");
        assertThat(errors("NT15")).as("a scale, not well-formed").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("a scale is a unit")).anyMatch(m -> m.contains("# after 0"));
    }

    @Test
    void nt16_aRegularExpressionCompiles() {
        Notations.patternText(on("postcode", s.postcode), "[0-9]{5}");
        assertThat(rules()).as("compiles").doesNotContain("NT16");
        Notations.patternText(on("station", s.station), "[A-Z");
        assertThat(errors("NT16")).as("an open class").singleElement().asString().contains("does not compile");
    }

    @Test
    void nt17_aReferenceThatDoesNotResolveIsAWarning() {
        Notations.temporalNumber(on("logged", s.logged), Notations.UNIX_EPOCH, s.unit(SI.SECOND));
        assertThat(rules()).as("resolves").doesNotContain("NT17");
        Unit missing = UnitsFactory.eINSTANCE.createBaseUnit();
        ((InternalEObject) missing).eSetProxyURI(URI.createURI("units.xmi#_nowhere"));
        Attribute ghost = org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory.eINSTANCE.createAttribute();
        ((InternalEObject) ghost).eSetProxyURI(URI.createURI("design.xmi#_nowhere"));
        Notations.temporalNumber(on("ghost", ghost), Notations.UNIX_EPOCH, missing);
        assertThat(warnings("NT17")).as("the element and the unit").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("the unit does not resolve"))
                .anyMatch(m -> m.contains("an element does not resolve"));
    }

    @Test
    void nt18_javaOnlyConstructsAreAWarning() {
        Notations.patternText(on("postcode", s.postcode), "[0-9]{5}");
        assertThat(rules()).as("plain").doesNotContain("NT18");
        Notations.patternText(on("station", s.station), "(?i)[a-z-]+");
        Notations.patternText(on("label", s.label), "\\A[A-Z]++");
        assertThat(warnings("NT18")).as("inline flags, \\A").extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("'(?i)'")).anyMatch(m -> m.contains("'\\A'"));
    }

    @Test
    void validateReturnsOnlyErrors() {
        Notations.temporalText(on("observed", s.observed), "dd.MM.yyyy HH:mm");
        Notations.numberText(on("reading", s.reading), ".");
        assertThat(NotationModelValidator.validate(s.model)).as("NT1, not the warning NT8").singleElement()
                .asString().startsWith("NT1 ");
    }

    /** Each rule of the validator is stated once, of its module, an error or a warning: never both under one code. */
    @Test
    void everyRuleIsStatedOnceWithOneSeverity() {
        assertThat(NotationModelValidator.RULES).as("the rules").extracting(Rule::code).doesNotHaveDuplicates()
                .containsExactly("NT1", "NT2", "NT3", "NT4", "NT5", "NT6", "NT7", "NT8", "NT9", "NT10", "NT11", "NT12",
                        "NT13", "NT14", "NT15", "NT16", "NT17", "NT18");
        assertThat(NotationModelValidator.RULES).as("the module").allSatisfy(rule -> assertThat(rule.module())
                .isEqualTo(NotationModelValidator.MODULE));
        assertThat(NotationModelValidator.RULES.stream().filter(rule -> rule.severity() == Severity.WARNING)
                .map(Rule::code)).as("the warnings").containsExactly("NT5", "NT8", "NT17", "NT18");
    }

    private List<String> rules() {
        return NotationModelValidator.check(s.model).stream().map(Finding::rule).toList();
    }

    private List<Finding> errors(String rule) {
        return NotationModelValidator.check(s.model).stream()
                .filter(f -> f.severity() == Severity.ERROR && f.rule().equals(rule)).toList();
    }

    private List<Finding> warnings(String rule) {
        return NotationModelValidator.check(s.model).stream()
                .filter(f -> f.severity() == Severity.WARNING && f.rule().equals(rule)).toList();
    }
}
