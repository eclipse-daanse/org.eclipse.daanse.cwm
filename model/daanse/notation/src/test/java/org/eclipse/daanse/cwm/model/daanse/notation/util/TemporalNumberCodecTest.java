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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.junit.jupiter.api.Test;

/** Times written as numbers: Unix seconds and milliseconds, spreadsheet serial dates, days since 1970. */
class TemporalNumberCodecTest {

    private final Stations stations = new Stations();

    private Codec codec(String epoch, String unit, String zone) {
        Notation r = Notations.create(stations.notes, "logged", stations.logged);
        TemporalNumber n = Notations.temporalNumber(r, epoch, stations.unit(unit));
        if (zone != null) {
            Notations.zone(n, zone);
        }
        return Codec.of(r);
    }

    @Test
    void unixSecondsAndMillisecondsAreInstants() {
        Codec seconds = codec(Notations.UNIX_EPOCH, SI.SECOND, null);
        assertThat(seconds.read("1773915330")).as("seconds since 1970")
                .isEqualTo(Instant.parse("2026-03-19T10:15:30Z"));
        assertThat(seconds.write(Instant.parse("2026-03-19T10:15:30Z"))).as("written back").isEqualTo("1773915330");
        assertThat(seconds.write(OffsetDateTime.of(2026, 3, 19, 11, 15, 30, 0, ZoneOffset.ofHours(1))))
                .as("any instant").isEqualTo("1773915330");
        assertThat(seconds.read("-86400")).as("before the epoch").isEqualTo(Instant.parse("1969-12-31T00:00:00Z"));
        Codec millis = codec(Notations.UNIX_EPOCH, SI.MILLISECOND, null);
        assertThat(millis.read("1773915330250")).as("milliseconds")
                .isEqualTo(Instant.parse("2026-03-19T10:15:30.250Z"));
        assertThat(millis.write(Instant.parse("2026-03-19T10:15:30.250Z"))).as("written back")
                .isEqualTo("1773915330250");
        assertThat(seconds.read("1773915330.5")).as("a fraction of a second")
                .isEqualTo(Instant.parse("2026-03-19T10:15:30.500Z"));
    }

    @Test
    void spreadsheetSerialDatesAreLocal() {
        Codec days = codec(Notations.SPREADSHEET_EPOCH, SI.DAY, null);
        assertThat(days.read("46100")).as("a day").isEqualTo(LocalDateTime.of(2026, 3, 19, 0, 0));
        assertThat(days.read("46100.25")).as("a quarter of a day").isEqualTo(LocalDateTime.of(2026, 3, 19, 6, 0));
        assertThat(days.write(LocalDate.of(2026, 3, 19))).as("a date").isEqualTo("46100");
        assertThat(days.write(LocalDateTime.of(2026, 3, 19, 6, 0))).as("a date and time").isEqualTo("46100.25");
        assertThatThrownBy(() -> days.write(Instant.parse("2026-03-19T06:00:00Z")))
                .as("an instant needs the zone of the count").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no zone");
    }

    @Test
    void aZoneMakesALocalCountAnInstant() {
        Codec days = codec(Notations.SPREADSHEET_EPOCH, SI.DAY, "Europe/Berlin");
        assertThat(days.read("46100.5")).as("noon in Berlin")
                .isEqualTo(ZonedDateTime.of(2026, 3, 19, 12, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        assertThat(days.write(Instant.parse("2026-03-19T11:00:00Z"))).as("an instant").isEqualTo("46100.5");
        Codec since1970 = codec("1970-01-01", SI.DAY, null);
        assertThat(since1970.read("20531")).as("days since 1970, a local date")
                .isEqualTo(LocalDateTime.of(2026, 3, 19, 0, 0));
    }

    @Test
    void failuresNameThePositionAndTheReason() {
        Codec seconds = codec(Notations.UNIX_EPOCH, SI.SECOND, null);
        assertThatThrownBy(() -> seconds.read("17739x5330")).isInstanceOfSatisfying(UnfitTextException.class, e -> {
            assertThat(e.position()).as("at the letter").isEqualTo(5);
            assertThat(e.reason()).as("the reason").contains("after the count");
        });
        assertThatThrownBy(() -> seconds.read("1.")).isInstanceOfSatisfying(UnfitTextException.class,
                e -> assertThat(e.position()).as("after the point").isEqualTo(2));
        assertThatThrownBy(() -> seconds.read("9E30")).isInstanceOfSatisfying(UnfitTextException.class,
                e -> assertThat(e.reason()).as("out of range").contains("out of the range"));
        assertThatThrownBy(() -> seconds.write(LocalDateTime.of(2026, 3, 19, 10, 0)))
                .as("a local time into a count from an instant").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFormIsCheckedWhenTheCodecIsMade() {
        assertThatThrownBy(() -> codec(Notations.UNIX_EPOCH, SI.METRE, null)).as("a metre is no unit of time")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no unit of time");
        assertThatThrownBy(() -> codec(Notations.UNIX_EPOCH, SI.SECOND, "Europe/Berlin"))
                .as("a count from an instant takes no zone").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec("1970-01-01T00:00", SI.SECOND, "Europe/Berlin"))
                .as("seconds take no zone").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a day or longer");
        assertThatThrownBy(() -> codec("the year the station opened", SI.DAY, null)).as("no epoch")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no ISO 8601");
    }

    @Test
    void valuesTravelThereAndBack() {
        Codec hours = codec("2000-01-01T00:00:00Z", SI.HOUR, null);
        for (String t : new String[] { "0", "230000.75", "-12" }) {
            assertThat(hours.write(hours.read(t))).as(t + " hours").isEqualTo(t);
        }
    }
}
