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
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void literalsOfDaysHoursMinutesAndSeconds() {
        assertThat(Durations.parse("P90D")).isEqualTo(Duration.ofDays(90));
        assertThat(Durations.parse("PT10M")).isEqualTo(Duration.ofMinutes(10));
        assertThat(Durations.parse("PT1H30M")).isEqualTo(Duration.ofMinutes(90));
        assertThat(Durations.parse("P1DT12H")).isEqualTo(Duration.ofHours(36));
    }

    @Test
    void monthsYearsAndNegativeDurationsAreRefused() {
        assertThatThrownBy(() -> Durations.parse("P1M")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P1M");
        assertThatThrownBy(() -> Durations.parse("P1Y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("-PT1H")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
        assertThat(Durations.isValid("ten minutes")).isFalse();
        assertThat(Durations.isValid(null)).isFalse();
    }

    @Test
    void daysAreWrittenAsDays() {
        assertThat(Durations.format(Duration.ofDays(90))).isEqualTo("P90D");
        assertThat(Durations.format(Duration.ofHours(36))).isEqualTo("P1DT12H");
        assertThat(Durations.format(Duration.ofMinutes(10))).isEqualTo("PT10M");
        assertThat(Durations.format(Duration.ZERO)).isEqualTo("PT0S");
        assertThat(Durations.of(7, ChronoUnit.DAYS)).isEqualTo("P7D");
    }

    @Test
    void theLargestUnitThatDividesTheDuration() {
        assertThat(Durations.largestUnit(Duration.ofDays(90))).isEqualTo(new Durations.Amount(90, ChronoUnit.DAYS));
        assertThat(Durations.largestUnit(Duration.ofMinutes(90))).isEqualTo(new Durations.Amount(90, ChronoUnit.MINUTES));
        assertThat(Durations.largestUnit(Duration.ofHours(2))).isEqualTo(new Durations.Amount(2, ChronoUnit.HOURS));
        assertThat(Durations.largestUnit(Duration.ofMillis(1500))).isEqualTo(new Durations.Amount(1500, ChronoUnit.MILLIS));
        assertThat(Durations.largestUnit(Duration.ofNanos(10))).isNull();
    }
}
