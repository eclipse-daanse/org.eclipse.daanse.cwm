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

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * The {@code Duration} literals of the lifecycle model: ISO-8601 durations in the grammar of
 * {@link Duration} — days, hours, minutes, seconds ({@code P90D}, {@code PT10M},
 * {@code PT1H30M}). Months and years have no fixed length and are not durations.
 */
public final class Durations {

    /** The units a duration is expressed in by {@link #largestUnit(Duration)}, largest first. */
    public static final List<ChronoUnit> UNITS = List.of(ChronoUnit.DAYS, ChronoUnit.HOURS,
            ChronoUnit.MINUTES, ChronoUnit.SECONDS, ChronoUnit.MILLIS);

    /** A duration as a whole number of one unit. */
    public record Amount(long amount, ChronoUnit unit) {
    }

    private Durations() {
    }

    /**
     * The duration of a literal.
     *
     * @throws IllegalArgumentException if the literal is no duration or is negative
     */
    public static Duration parse(String literal) {
        Objects.requireNonNull(literal, "literal");
        Duration d;
        try {
            d = Duration.parse(literal.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("not an ISO-8601 duration of days, hours, minutes and "
                    + "seconds: '" + literal + "'", e);
        }
        if (d.isNegative()) {
            throw new IllegalArgumentException("a negative duration: '" + literal + "'");
        }
        return d;
    }

    /** Whether {@code literal} is a duration {@link #parse(String)} accepts. */
    public static boolean isValid(String literal) {
        if (literal == null) {
            return false;
        }
        try {
            parse(literal);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The literal of a duration, days written as days: {@code P90D}, {@code P1DT12H},
     * {@code PT10M}, {@code PT0S}.
     */
    public static String format(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("a negative duration: " + duration);
        }
        long days = duration.toDays();
        Duration rest = duration.minusDays(days);
        StringBuilder sb = new StringBuilder("P");
        if (days > 0) {
            sb.append(days).append('D');
        }
        if (!rest.isZero()) {
            sb.append(rest.toString().substring(1));
        }
        return sb.length() == 1 ? "PT0S" : sb.toString();
    }

    /** {@code amount} of {@code unit} as a duration literal. */
    public static String of(long amount, ChronoUnit unit) {
        return format(Duration.of(amount, unit));
    }

    /**
     * The duration as a whole number of the largest unit of {@link #UNITS} that divides it
     * ({@code PT1H30M} is 90 minutes), or {@code null} when not even milliseconds divide it.
     */
    public static Amount largestUnit(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        for (ChronoUnit unit : UNITS) {
            Duration one = unit.getDuration();
            if (duration.toNanos() % one.toNanos() == 0) {
                return new Amount(duration.toNanos() / one.toNanos(), unit);
            }
        }
        return null;
    }
}
