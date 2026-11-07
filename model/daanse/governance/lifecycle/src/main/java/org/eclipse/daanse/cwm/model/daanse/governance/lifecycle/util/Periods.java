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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data type {@code Period}: an ISO-8601 period literal of years, months, weeks and days with an
 * optional time part — {@code P10Y}, {@code P6M}, {@code P2W}, {@code P90D}, {@code P1Y6M},
 * {@code P1DT12H}. Years and months are calendar units, added in the time zone of a start; weeks,
 * days (of 24 hours) and the time part are exact. A period without years and months is
 * {@linkplain #isExact exact} and has a {@linkplain #toDuration duration}.
 */
public final class Periods {

    private static final Pattern GRAMMAR = Pattern.compile(
            "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d{1,9})?)S)?)?");
    /** The mean length of a Gregorian year, in days. */
    private static final double YEAR_DAYS = 365.2425d;

    /**
     * A parsed period: the calendar part in years and months, the exact part in days (weeks
     * counted as 7) and the time part.
     */
    public record Amount(long years, long months, long days, Duration time) {

        /** Whether the period has no years and months, so that it is of fixed length. */
        public boolean isExact() {
            return years == 0 && months == 0;
        }

        /** The calendar part in months. */
        public long totalMonths() {
            return years * 12 + months;
        }

        /** The exact part: the days of 24 hours and the time part. */
        public Duration exactPart() {
            return Duration.ofDays(days).plus(time);
        }

        /** The period as a duration; only for an {@linkplain #isExact exact} period. */
        public Duration toDuration() {
            if (!isExact()) {
                throw new IllegalArgumentException("a period of years or months has no fixed length");
            }
            return exactPart();
        }

        /** The length in days, a month counted as the mean of the Gregorian calendar. */
        public double approximateDays() {
            return totalMonths() * YEAR_DAYS / 12 + exactPart().toNanos() / 86_400e9d;
        }

        /**
         * {@code start} plus this period: years and months together as months in the calendar of its
         * zone (the 29th of February plus a year is the 28th, plus a year and a month the 29th of
         * March), then the exact part — a day is 24 hours, also across a change of daylight saving
         * time.
         */
        public ZonedDateTime addTo(ZonedDateTime start) {
            return start.plusMonths(totalMonths()).plus(exactPart());
        }
    }

    /**
     * The order of two periods, and whether it is exact: two periods of the same months compare by
     * their exact part, two periods of months only by their months, two exact periods by their
     * length; any other pair only approximately (a month as the mean of the Gregorian calendar).
     */
    public record Comparison(int sign, boolean exact) {
    }

    private Periods() {
    }

    /** Parses a period literal; {@link IllegalArgumentException} when it does not follow the grammar. */
    public static Amount parse(String literal) {
        Matcher m = literal == null ? null : GRAMMAR.matcher(literal.trim());
        if (m == null || !m.matches() || literal.trim().equals("P") || literal.trim().endsWith("T")) {
            throw new IllegalArgumentException("'" + literal + "' is no ISO-8601 period (P10Y, P6M, P2W, P90D, P1DT12H)");
        }
        long years = number(m.group(1));
        long months = number(m.group(2));
        long days = number(m.group(3)) * 7 + number(m.group(4));
        Duration time = Duration.ofHours(number(m.group(5))).plusMinutes(number(m.group(6)));
        if (m.group(7) != null) {
            BigDecimal seconds = new BigDecimal(m.group(7));
            time = time.plusSeconds(seconds.longValue())
                    .plusNanos(seconds.remainder(BigDecimal.ONE).movePointRight(9).longValue());
        }
        return new Amount(years, months, days, time);
    }

    /** Whether a literal follows the grammar. */
    public static boolean isValid(String literal) {
        try {
            parse(literal);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Whether a literal is a valid period without years and months. */
    public static boolean isExact(String literal) {
        return isValid(literal) && parse(literal).isExact();
    }

    /** The duration of an exact period literal. */
    public static Duration toDuration(String literal) {
        return parse(literal).toDuration();
    }

    /** {@code amount} of {@code unit} as a literal: years, months, weeks, days, hours, minutes or seconds. */
    public static String of(long amount, ChronoUnit unit) {
        if (amount < 0) {
            throw new IllegalArgumentException("a period is not negative: " + amount);
        }
        return switch (Objects.requireNonNull(unit, "unit")) {
            case YEARS -> "P" + amount + "Y";
            case MONTHS -> "P" + amount + "M";
            case WEEKS -> "P" + amount + "W";
            case DAYS -> "P" + amount + "D";
            case HOURS -> "PT" + amount + "H";
            case MINUTES -> "PT" + amount + "M";
            case SECONDS -> "PT" + amount + "S";
            default -> throw new IllegalArgumentException("no unit of a period: " + unit);
        };
    }

    /** {@code a} compared with {@code b}, and whether the comparison is exact. */
    public static Comparison compare(String a, String b) {
        Amount x = parse(a);
        Amount y = parse(b);
        if (x.totalMonths() == y.totalMonths()) {
            return new Comparison(Integer.signum(x.exactPart().compareTo(y.exactPart())), true);
        }
        if (x.exactPart().isZero() && y.exactPart().isZero()) {
            return new Comparison(Long.signum(x.totalMonths() - y.totalMonths()), true);
        }
        return new Comparison((int) Math.signum(x.approximateDays() - y.approximateDays()), false);
    }

    private static long number(String digits) {
        return digits == null ? 0 : Long.parseLong(digits);
    }
}
