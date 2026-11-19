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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.util.Dimensions;
import org.eclipse.daanse.cwm.model.daanse.units.util.Units;

/**
 * Reads and writes a {@link TemporalNumber}: a count of a unit of time since an epoch.
 *
 * <p>From an instant epoch the value is an {@link Instant}; from a local epoch a {@link LocalDateTime}, or a
 * {@link ZonedDateTime} where the form names a zone (a local time in the gap of a change of offset moves forward, in
 * an overlap it takes the earlier offset). The count is a decimal number: an optional sign, digits, an optional
 * fraction after a point, an optional exponent. It converts to seconds by the unit's factor and is rounded half to
 * even to nanoseconds; a writer writes the shortest decimal of the count.</p>
 */
final class TemporalNumberCodec implements FormCodec {

    /** Seconds in a day: a unit of at least this many seconds may carry a zone. */
    static final BigDecimal DAY = BigDecimal.valueOf(86_400);

    private final Instant instantEpoch;
    private final LocalDateTime localEpoch;
    private final BigDecimal secondsPerUnit;
    private final ZoneId zone;
    private final String unitSymbol;

    /**
     * A reader and writer of {@code form}.
     *
     * @throws IllegalArgumentException where the epoch is no ISO 8601 date or date and time, the unit is no linear unit
     *                                  of time, or a zone stands where it says nothing
     */
    TemporalNumberCodec(TemporalNumber form) {
        Epoch epoch = epoch(form.getEpoch());
        this.instantEpoch = epoch.instant();
        this.localEpoch = epoch.local();
        Unit unit = form.getUnit();
        if (unit == null || unit.eIsProxy()) {
            throw new IllegalArgumentException("the temporal number names no unit");
        }
        this.unitSymbol = unit.getSymbol();
        this.secondsPerUnit = secondsPerUnit(unit);
        if (form.getZone() == null) {
            this.zone = null;
        } else {
            if (instantEpoch != null) {
                throw new IllegalArgumentException("the count from the instant " + form.getEpoch() + " takes no zone");
            }
            if (secondsPerUnit.compareTo(DAY) < 0) {
                throw new IllegalArgumentException("a count of " + unitSymbol + " takes no zone; a zone stands with "
                        + "a unit of a day or longer");
            }
            this.zone = TemporalTextCodec.zone(form.getZone());
        }
    }

    /** An epoch: an instant where the text has an offset, else a local date and time. */
    record Epoch(Instant instant, LocalDateTime local) {
    }

    /**
     * Reads an epoch in ISO 8601.
     *
     * @throws IllegalArgumentException where it is no date or date and time
     */
    static Epoch epoch(String text) {
        if (text == null) {
            throw new IllegalArgumentException("the temporal number names no epoch");
        }
        String t = text.trim();
        try {
            return new Epoch(OffsetDateTime.parse(t).toInstant(), null);
        } catch (DateTimeParseException e) {
            // not an instant
        }
        try {
            return new Epoch(null, LocalDateTime.parse(t));
        } catch (DateTimeParseException e) {
            // not a local date and time
        }
        try {
            return new Epoch(null, LocalDate.parse(t).atStartOfDay());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("the epoch '" + text + "' is no ISO 8601 date or date and time", e);
        }
    }

    /**
     * The seconds in one {@code unit}.
     *
     * @throws IllegalArgumentException where the unit is no linear unit of time
     */
    static BigDecimal secondsPerUnit(Unit unit) {
        if (unit.getDimension() == null || unit.getDimension().eIsProxy()
                || !Arrays.equals(Dimensions.exponents(unit.getDimension()), Dimensions.base(Dimensions.TIME, 1))) {
            throw new IllegalArgumentException(unit.getSymbol() + " is no unit of time");
        }
        Units.Linear linear = Units.toBase(unit);
        if (linear.term().signum() != 0 || linear.factor().signum() <= 0) {
            throw new IllegalArgumentException(unit.getSymbol() + " does not count time from zero");
        }
        return linear.factor();
    }

    @Override
    public Object read(String text, int start, int end) {
        BigDecimal count = count(text, start, end);
        BigDecimal seconds = count.multiply(secondsPerUnit).setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal whole = seconds.setScale(0, RoundingMode.FLOOR);
        long nanos = seconds.subtract(whole).movePointRight(9).longValueExact();
        try {
            long s = whole.longValueExact();
            if (instantEpoch != null) {
                return instantEpoch.plusSeconds(s).plusNanos(nanos);
            }
            LocalDateTime local = localEpoch.plusSeconds(s).plusNanos(nanos);
            return zone == null ? local : ZonedDateTime.ofLocal(local, zone, null);
        } catch (ArithmeticException | DateTimeException e) {
            throw new UnfitTextException(text, start, count.toPlainString() + " " + unitSymbol + " is out of the "
                    + "range of times");
        }
    }

    /** The count: an optional sign, digits, an optional fraction, an optional exponent. */
    private static BigDecimal count(String text, int start, int end) {
        int i = start;
        if (i < end && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            i++;
        }
        int digits = i;
        while (i < end && digit(text.charAt(i))) {
            i++;
        }
        if (i == digits) {
            throw new UnfitTextException(text, i, "expected the count as digits, found " + FormCodec.found(text, i,
                    end));
        }
        if (i < end && text.charAt(i) == '.') {
            int f = i + 1;
            while (f < end && digit(text.charAt(f))) {
                f++;
            }
            if (f == i + 1) {
                throw new UnfitTextException(text, f, "expected digits after the point, found " + FormCodec.found(text,
                        f, end));
            }
            i = f;
        }
        if (i < end && (text.charAt(i) == 'E' || text.charAt(i) == 'e')) {
            int e = i + 1;
            if (e < end && (text.charAt(e) == '+' || text.charAt(e) == '-')) {
                e++;
            }
            int expDigits = e;
            while (e < end && digit(text.charAt(e))) {
                e++;
            }
            if (e == expDigits) {
                throw new UnfitTextException(text, e, "expected digits of the exponent, found " + FormCodec.found(text,
                        e, end));
            }
            i = e;
        }
        if (i < end) {
            throw new UnfitTextException(text, i, "after the count the text goes on with " + FormCodec.found(text, i,
                    end));
        }
        return new BigDecimal(text.substring(start, end));
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    @Override
    public String write(Object value) {
        Duration since;
        if (instantEpoch != null) {
            Instant instant = switch (value) {
            case Instant i -> i;
            case OffsetDateTime o -> o.toInstant();
            case ZonedDateTime z -> z.toInstant();
            case null -> throw new IllegalArgumentException("no time to write");
            default -> throw new IllegalArgumentException(value + " (" + value.getClass().getSimpleName()
                    + ") is no instant; the count starts at the instant " + instantEpoch);
            };
            since = Duration.between(instantEpoch, instant);
        } else {
            LocalDateTime local = switch (value) {
            case LocalDateTime l -> l;
            case LocalDate d -> d.atStartOfDay();
            case Instant i when zone != null -> LocalDateTime.ofInstant(i, zone);
            case OffsetDateTime o when zone != null -> o.atZoneSameInstant(zone).toLocalDateTime();
            case ZonedDateTime z when zone != null -> z.withZoneSameInstant(zone).toLocalDateTime();
            case null -> throw new IllegalArgumentException("no time to write");
            default -> throw new IllegalArgumentException(value + " (" + value.getClass().getSimpleName()
                    + ") is no local date and time" + (zone == null ? ", and the form names no zone" : ""));
            };
            since = Duration.between(localEpoch, local);
        }
        BigDecimal seconds = BigDecimal.valueOf(since.getSeconds()).add(BigDecimal.valueOf(since.getNano(), 9));
        BigDecimal count;
        try {
            count = seconds.divide(secondsPerUnit);
        } catch (ArithmeticException e) {
            count = seconds.divide(secondsPerUnit, MathContext.DECIMAL128);
        }
        count = count.stripTrailingZeros();
        return (count.scale() < 0 ? count.setScale(0) : count).toPlainString();
    }
}
