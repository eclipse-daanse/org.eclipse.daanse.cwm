/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods.Comparison;
import org.junit.jupiter.api.Test;

/** The grammar of a period, its exact part, the comparison of two periods and the calendar. */
class PeriodsTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void grammar() {
        for (String valid : new String[] { "P10Y", "P6M", "P2W", "P90D", "P1Y6M", "P1DT12H", "PT6H", "PT0.5S", "P0D" }) {
            assertThat(Periods.isValid(valid)).as(valid).isTrue();
        }
        for (String invalid : new String[] { "", "P", "PT", "P1DT", "10Y", "P-1D", "p1d", "P1.5D", "P1H" }) {
            assertThat(Periods.isValid(invalid)).as(invalid).isFalse();
        }
        assertThat(Periods.isValid(null)).isFalse();
        assertThatThrownBy(() -> Periods.parse("P1M1Y")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weeksAreDaysAndTheTimePartIsExact() {
        assertThat(Periods.parse("P2W").days()).isEqualTo(14);
        Periods.Amount a = Periods.parse("P1DT12H30M0.25S");
        assertThat(a.days()).isEqualTo(1);
        assertThat(a.time()).isEqualTo(Duration.ofHours(12).plusMinutes(30).plusMillis(250));
        assertThat(Periods.isExact("P90D")).isTrue();
        assertThat(Periods.isExact("PT6H")).isTrue();
        assertThat(Periods.isExact("P1M")).isFalse();
        assertThat(Periods.isExact("P1Y2D")).isFalse();
        assertThat(Periods.toDuration("P2W")).isEqualTo(Duration.ofDays(14));
        assertThatThrownBy(() -> Periods.toDuration("P1Y")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void literalsOfOneUnit() {
        assertThat(Periods.of(10, ChronoUnit.YEARS)).isEqualTo("P10Y");
        assertThat(Periods.of(6, ChronoUnit.MONTHS)).isEqualTo("P6M");
        assertThat(Periods.of(2, ChronoUnit.WEEKS)).isEqualTo("P2W");
        assertThat(Periods.of(90, ChronoUnit.DAYS)).isEqualTo("P90D");
        assertThat(Periods.of(6, ChronoUnit.HOURS)).isEqualTo("PT6H");
        assertThat(Periods.of(30, ChronoUnit.MINUTES)).isEqualTo("PT30M");
        assertThatThrownBy(() -> Periods.of(-1, ChronoUnit.DAYS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Periods.of(1, ChronoUnit.MILLIS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparisonExactWhereTheCalendarAllows() {
        assertThat(Periods.compare("P1Y", "P12M")).isEqualTo(new Comparison(0, true));
        assertThat(Periods.compare("P1Y", "P13M")).isEqualTo(new Comparison(-1, true));
        assertThat(Periods.compare("P2W", "P14D")).isEqualTo(new Comparison(0, true));
        assertThat(Periods.compare("P1Y1D", "P1Y2D")).isEqualTo(new Comparison(-1, true));
        assertThat(Periods.compare("P90D", "PT2160H")).isEqualTo(new Comparison(0, true));
        assertThat(Periods.compare("P1Y", "P365D")).as("a mean year has 365.2425 days").isEqualTo(new Comparison(1, false));
        assertThat(Periods.compare("P3M", "P90D")).isEqualTo(new Comparison(1, false));
    }

    @Test
    void yearsAndMonthsFollowTheCalendarDaysAreExact() {
        ZonedDateTime leapDay = ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, BERLIN);
        assertThat(Periods.parse("P1Y").addTo(leapDay)).isEqualTo(ZonedDateTime.of(2025, 2, 28, 12, 0, 0, 0, BERLIN));
        assertThat(Periods.parse("P1Y1M").addTo(leapDay)).as("a year and a month as 13 months")
                .isEqualTo(ZonedDateTime.of(2025, 3, 29, 12, 0, 0, 0, BERLIN));
        ZonedDateTime endOfJanuary = ZonedDateTime.of(2024, 1, 31, 12, 0, 0, 0, BERLIN);
        assertThat(Periods.parse("P1M").addTo(endOfJanuary)).isEqualTo(ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, BERLIN));
        ZonedDateTime beforeSummerTime = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, BERLIN);
        assertThat(Periods.parse("P1D").addTo(beforeSummerTime)).as("24 hours")
                .isEqualTo(ZonedDateTime.of(2026, 3, 29, 13, 0, 0, 0, BERLIN));
        assertThat(Periods.parse("P1M").addTo(beforeSummerTime)).as("a calendar month")
                .isEqualTo(ZonedDateTime.of(2026, 4, 28, 12, 0, 0, 0, BERLIN));
    }
}
