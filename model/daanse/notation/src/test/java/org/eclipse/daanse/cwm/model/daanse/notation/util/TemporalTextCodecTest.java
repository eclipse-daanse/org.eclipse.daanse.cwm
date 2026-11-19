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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.junit.jupiter.api.Test;

/** Dates and times written as text by the stations' loggers: each form both ways, round trips, and the failures. */
class TemporalTextCodecTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final Stations stations = new Stations();

    private Codec named(TemporalForm form, Consumer<TemporalText> more) {
        Notation r = Notations.create(stations.notes, "observed", stations.observed);
        more.accept(Notations.temporalText(r, form));
        return Codec.of(r);
    }

    private Codec pattern(String pattern, Consumer<TemporalText> more) {
        Notation r = Notations.create(stations.notes, "observed", stations.observed);
        more.accept(Notations.temporalText(r, pattern));
        return Codec.of(r);
    }

    private static Consumer<TemporalText> none() {
        return t -> {
        };
    }

    @Test
    void namedDatesBothWays() {
        Codec extended = named(TemporalForm.DATE, none());
        assertThat(extended.read("2026-03-19")).as("an ISO date").isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(extended.write(LocalDate.of(2026, 3, 19))).as("written back").isEqualTo("2026-03-19");
        Codec basic = named(TemporalForm.BASIC_DATE, none());
        assertThat(basic.read("20260319")).as("a basic ISO date").isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(basic.write(LocalDate.of(2026, 3, 19))).as("written back").isEqualTo("20260319");
    }

    @Test
    void namedDateTimesWithAnOffsetAreInstants() {
        Codec codec = named(TemporalForm.DATE_TIME_WITH_OFFSET, none());
        OffsetDateTime jena = OffsetDateTime.of(2026, 3, 19, 10, 15, 30, 250_000_000, ZoneOffset.ofHours(1));
        assertThat(codec.read("2026-03-19T10:15:30.25+01:00")).as("with a fraction and an offset").isEqualTo(jena);
        assertThat(codec.read("2026-03-19T09:15:30Z")).as("Z is UTC")
                .isEqualTo(OffsetDateTime.of(2026, 3, 19, 9, 15, 30, 0, ZoneOffset.UTC));
        assertThat(codec.write(jena)).as("the fraction written with its digits only")
                .isEqualTo("2026-03-19T10:15:30.25+01:00");
        assertThat(codec.write(Instant.parse("2026-03-19T09:15:30Z"))).as("an instant is written in UTC")
                .isEqualTo("2026-03-19T09:15:30Z");
        Codec basic = named(TemporalForm.BASIC_DATE_TIME_WITH_OFFSET, none());
        assertThat(basic.read("20260319T101530+0100")).as("the basic form")
                .isEqualTo(OffsetDateTime.of(2026, 3, 19, 10, 15, 30, 0, ZoneOffset.ofHours(1)));
        assertThat(basic.write(OffsetDateTime.of(2026, 3, 19, 10, 15, 30, 0, ZoneOffset.ofHours(1))))
                .as("written back").isEqualTo("20260319T101530+0100");
    }

    @Test
    void namedTimes() {
        assertThat(named(TemporalForm.TIME, none()).read("06:45:00")).as("a local time").isEqualTo(LocalTime.of(6, 45));
        assertThat(named(TemporalForm.TIME_WITH_OFFSET, none()).read("06:45:00+01:00")).as("a time with an offset")
                .isEqualTo(OffsetTime.of(6, 45, 0, 0, ZoneOffset.ofHours(1)));
        assertThat(named(TemporalForm.BASIC_TIME, none()).write(LocalTime.of(6, 45, 7))).as("the basic form")
                .isEqualTo("064507");
    }

    @Test
    void aZoneGivesALocalTextItsOffset() {
        Codec codec = named(TemporalForm.DATE_TIME, t -> Notations.zone(t, "Europe/Berlin"));
        assertThat(codec.read("2026-03-19T10:15:00")).as("read in Berlin")
                .isEqualTo(ZonedDateTime.of(2026, 3, 19, 10, 15, 0, 0, BERLIN));
        assertThat(codec.write(Instant.parse("2026-07-01T08:00:00Z"))).as("an instant is written in Berlin, summer")
                .isEqualTo("2026-07-01T10:00:00");
        Codec fixed = named(TemporalForm.DATE_TIME, t -> Notations.zone(t, "+01:00"));
        assertThat(fixed.read("2026-07-01T10:00:00")).as("a fixed offset gives an offset date and time")
                .isEqualTo(OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.ofHours(1)));
        Codec withOffset = named(TemporalForm.DATE_TIME_WITH_OFFSET, t -> Notations.zone(t, "Europe/Berlin"));
        assertThat(withOffset.read("2026-03-19T10:15:00Z")).as("the text's offset wins over the zone")
                .isEqualTo(OffsetDateTime.of(2026, 3, 19, 10, 15, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void aLocalTimeInTheGapIsAnErrorUnlessLenient() {
        Codec strict = pattern("dd.MM.yyyy HH:mm", t -> Notations.zone(t, "Europe/Berlin"));
        assertThatThrownBy(() -> strict.read("29.03.2026 02:30")).as("the clocks moved forward at 02:00")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the hour").isEqualTo(11);
                    assertThat(e.reason()).as("the reason").contains("does not exist in Europe/Berlin");
                });
        Codec lenient = pattern("dd.MM.yyyy HH:mm", t -> Notations.lenient(Notations.zone(t,
                "Europe/Berlin")));
        assertThat(lenient.read("29.03.2026 02:30")).as("moved forward")
                .isEqualTo(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, BERLIN));
    }

    @Test
    void namesOfMonthsAreReadInTheirLanguage() {
        Codec german = pattern("d. MMMM yyyy", t -> Notations.language(t, "de"));
        assertThat(german.read("19. März 2026")).as("German").isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(german.write(LocalDate.of(2026, 3, 19))).as("written back").isEqualTo("19. März 2026");
        Codec english = pattern("MMMM d, yyyy", t -> Notations.language(t, "en"));
        assertThat(english.read("March 19, 2026")).as("English").isEqualTo(LocalDate.of(2026, 3, 19));
        assertThatThrownBy(() -> german.read("19. March 2026")).as("an English name in a German text")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the name").isEqualTo(4);
                    assertThat(e.reason()).as("names the field").contains("name of the month");
                });
    }

    @Test
    void lenientReadsNamesByTheirFirstLetters() {
        Codec lenient = pattern("MMM yyyy", t -> Notations.lenient(Notations.language(t, "de")));
        assertThat(lenient.read("Mär 2026")).as("the one month that begins with Mär").isEqualTo(YearMonth.of(2026, 3));
        assertThat(lenient.read("jun 2026")).as("in any case").isEqualTo(YearMonth.of(2026, 6));
        assertThat(lenient.read("SEPT. 2026")).as("with the dot").isEqualTo(YearMonth.of(2026, 9));
        assertThat(lenient.read("Oktober 2026")).as("the full name").isEqualTo(YearMonth.of(2026, 10));
        assertThatThrownBy(() -> lenient.read("Ju 2026")).as("two letters are no name")
                .isInstanceOf(UnfitTextException.class);
        Codec strict = pattern("MMMM yyyy", t -> Notations.language(t, "de"));
        assertThatThrownBy(() -> strict.read("Mär 2026")).as("strict wants the name as written")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isZero());
    }

    @Test
    void theNameOfTheDayIsChecked() {
        Codec codec = pattern("EEE, dd.MM.yyyy", t -> Notations.language(t, "en"));
        assertThat(codec.read("Thu, 19.03.2026")).as("a Thursday").isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(codec.write(LocalDate.of(2026, 3, 19))).as("written back").isEqualTo("Thu, 19.03.2026");
        assertThatThrownBy(() -> codec.read("Mon, 19.03.2026")).as("the wrong day")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the name of the day").isZero();
                    assertThat(e.reason()).as("the reason").contains("is a Thursday, not a Monday");
                });
    }

    @Test
    void hoursOfTwelveWithAmAndPm() {
        Codec codec = pattern("hh:mm a", t -> Notations.language(t, "en"));
        assertThat(codec.read("10:15 PM")).as("evening").isEqualTo(LocalTime.of(22, 15));
        assertThat(codec.read("12:05 AM")).as("after midnight").isEqualTo(LocalTime.of(0, 5));
        assertThat(codec.write(LocalTime.of(0, 5))).as("written back").isEqualTo("12:05 AM");
    }

    @Test
    void theFormatsOfCsvOnTheWeb() {
        assertThat(pattern("M/d/yyyy", none()).read("3/19/2026")).as("one or two digits")
                .isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(pattern("yyyy-MM-ddTHH:mm:ss.SSS", none()).read("2026-03-19T10:15:30.5")).as("T as CSVW writes it")
                .isEqualTo(LocalDateTime.of(2026, 3, 19, 10, 15, 30, 500_000_000));
        assertThat(pattern("yyyy-MM-ddTHH:mm:ss.SSS", none()).write(LocalDateTime.of(2026, 3, 19, 10, 15, 30,
                500_000_000))).as("S written as many times as the pattern says").isEqualTo("2026-03-19T10:15:30.500");
        assertThat(pattern("HHmm", none()).read("0645")).as("digits only").isEqualTo(LocalTime.of(6, 45));
        assertThat(pattern("dd.MM.yyyy HH:mm X", none()).read("19.03.2026 10:15 +01")).as("an offset of hours")
                .isEqualTo(OffsetDateTime.of(2026, 3, 19, 10, 15, 0, 0, ZoneOffset.ofHours(1)));
        assertThat(pattern("yyyy-MM-dd HH:mmxxx", none()).write(OffsetDateTime.of(2026, 3, 19, 10, 15, 0, 0,
                ZoneOffset.UTC))).as("x writes zero as digits").isEqualTo("2026-03-19 10:15+00:00");
        assertThat(pattern("yyyy-MM-dd HH:mmXXX", none()).write(OffsetDateTime.of(2026, 3, 19, 10, 15, 0, 0,
                ZoneOffset.UTC))).as("X writes zero as Z").isEqualTo("2026-03-19 10:15Z");
    }

    @Test
    void strictAndLenientDigits() {
        assertThatThrownBy(() -> pattern("MM/dd/yyyy", none()).read("3/19/2026")).as("MM wants two digits")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> {
                    assertThat(e.position()).as("at the month").isZero();
                    assertThat(e.reason()).as("the reason").contains("has 1 digit");
                });
        assertThat(pattern("MM/dd/yyyy", Notations::lenient).read("3/19/2026")).as("lenient")
                .isEqualTo(LocalDate.of(2026, 3, 19));
    }

    @Test
    void failuresNameThePositionAndTheReason() {
        Codec codec = pattern("dd.MM.yyyy", none());
        assertThatThrownBy(() -> codec.read("19.13.2026")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the month").isEqualTo(3);
            assertThat(e.reason()).as("the reason").isEqualTo("13 is no month (1 to 12)");
            assertThat(e.text()).as("the text").isEqualTo("19.13.2026");
        });
        assertThatThrownBy(() -> codec.read("30.02.2026")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the day").isZero();
            assertThat(e.reason()).as("the reason").contains("February 2026 has 28 days, not 30");
        });
        assertThatThrownBy(() -> codec.read("19.03.2026 Jena")).isInstanceOfSatisfying(UnfitTextException.class,
                e -> assertThat(e.position()).as("after the year").isEqualTo(10));
        assertThatThrownBy(() -> codec.read("19-03-2026")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the dash").isEqualTo(2);
            assertThat(e.reason()).as("the reason").contains("expected '.'");
        });
        assertThatThrownBy(() -> codec.read("19.03.")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the end").isEqualTo(6);
            assertThat(e.reason()).as("the reason").contains("the end of the text");
        });
    }

    @Test
    void zonesInTheText() {
        assertThat(pattern("yyyy-MM-dd HH:mm VV", none()).read("2026-03-19 10:15 Europe/Berlin")).as("a zone id")
                .isEqualTo(ZonedDateTime.of(2026, 3, 19, 10, 15, 0, 0, BERLIN));
        assertThat(pattern("yyyy-MM-dd HH:mm VV", none()).write(ZonedDateTime.of(2026, 3, 19, 10, 15, 0, 0, BERLIN)))
                .as("written back").isEqualTo("2026-03-19 10:15 Europe/Berlin");
        assertThat(pattern("HH:mmXXX", none()).read("06:45-05:00")).as("a time with an offset")
                .isEqualTo(OffsetTime.of(6, 45, 0, 0, ZoneOffset.ofHours(-5)));
        assertThatThrownBy(() -> pattern("yyyy-MM-dd HH:mm VV", none()).read("2026-03-19 10:15 Thuringia/Jena"))
                .as("an unknown zone").isInstanceOfSatisfying(UnfitTextException.class,
                        e -> assertThat(e.position()).isEqualTo(17));
        assertThatThrownBy(() -> pattern("HH:mmXXX", none()).read("06:45+0100")).as("XXX wants the colon")
                .isInstanceOfSatisfying(UnfitTextException.class, e -> assertThat(e.position()).isEqualTo(8));
    }

    @Test
    void partsOfADate() {
        assertThat(pattern("yyyy-MM", none()).read("2026-03")).as("a month").isEqualTo(YearMonth.of(2026, 3));
        assertThat(pattern("yyyy", none()).read("2026")).as("a year").isEqualTo(Year.of(2026));
        assertThat(pattern("dd.MM.", none()).read("19.03.")).as("a day of a year").isEqualTo(MonthDay.of(3, 19));
        assertThat(pattern("yyyy-DDD", none()).read("2026-078")).as("a day of the year")
                .isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(pattern("dd.MM.yy", none()).read("19.03.26")).as("two digits are 2000 to 2099")
                .isEqualTo(LocalDate.of(2026, 3, 19));
        assertThatThrownBy(() -> pattern("dd.MM.yy", none()).write(LocalDate.of(1999, 3, 19)))
                .as("1999 does not fit two digits").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writingNeedsWhatTheFormWrites() {
        assertThatThrownBy(() -> named(TemporalForm.DATE_TIME, none()).write(Instant.parse("2026-03-19T09:15:30Z")))
                .as("an instant without a zone into a local form").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names none");
        assertThatThrownBy(() -> named(TemporalForm.DATE_TIME_WITH_OFFSET, none())
                .write(LocalDateTime.of(2026, 3, 19, 10, 15))).as("a local time into a form with an offset")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("names no zone");
        assertThatThrownBy(() -> named(TemporalForm.DATE_TIME, none()).write(LocalDate.of(2026, 3, 19)))
                .as("a date has no time of day").isInstanceOf(IllegalArgumentException.class);
        assertThat(named(TemporalForm.DATE, none()).write(OffsetDateTime.of(2026, 3, 19, 23, 30, 0, 0,
                ZoneOffset.ofHours(1)))).as("the date of an instant in its own offset").isEqualTo("2026-03-19");
        assertThat(named(TemporalForm.DATE, t -> Notations.zone(t, "UTC")).write(OffsetDateTime.of(2026, 3, 19,
                0, 30, 0, 0, ZoneOffset.ofHours(1)))).as("or in the zone of the form").isEqualTo("2026-03-18");
    }

    @Test
    void formsTheReaderDoesNotTake() {
        assertThatThrownBy(() -> pattern("QQQ yyyy", t -> Notations.language(t, "en")))
                .as("a quarter is LDML, not read here").isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("quarter");
        assertThatThrownBy(() -> pattern("HH:mm", t -> Notations.zone(t, "Europe/Berlin")))
                .as("a time without a date takes no zone whose offset changes")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fixed offset");
        assertThatThrownBy(() -> pattern("dd.MM.yyyy XXX", none())).as("a date with an offset")
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern("MMM yyyy", none())).as("names need a language")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language");
        assertThatThrownBy(() -> pattern("dd.mm.yyyy", none())).as("minutes without an hour")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void valuesTravelThereAndBack() {
        List<Object[]> cases = List.of(
                new Object[] { "dd.MM.yyyy HH:mm:ss", LocalDateTime.of(2026, 3, 19, 6, 5, 9) },
                new Object[] { "yyyyMMddHHmmss", LocalDateTime.of(2026, 12, 31, 23, 59, 59) },
                new Object[] { "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", OffsetDateTime.of(2026, 3, 19, 10, 15, 30, 125_000_000,
                        ZoneOffset.ofHoursMinutes(5, 30)) },
                new Object[] { "d/M/yyyy", LocalDate.of(2026, 1, 2) },
                new Object[] { "HH:mm:ss", LocalTime.of(23, 0, 1) });
        for (Object[] c : cases) {
            Codec codec = pattern((String) c[0], none());
            String text = codec.write(c[1]);
            assertThat(codec.read(text)).as(c[0] + ": " + text).isEqualTo(c[1]);
            assertThat(codec.write(codec.read(text))).as(c[0] + " text again").isEqualTo(text);
        }
    }
}
