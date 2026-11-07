/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Durations;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ChangedValueRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SamplingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SwingingDoorRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;

/**
 * Evaluates a recording rule on a stream of values of one element. For the rules Eclipse
 * Fennec's change-rule filter knows, it decides the way that filter does for a resource of a
 * digital twin.
 *
 * <p>A value is judged in this order:</p>
 * <ol>
 * <li>The first value is always recorded.</li>
 * <li>A <em>late</em> value — older than the last recorded one, for a swinging door not newer
 * than the held one — is dropped ({@code drop}), recorded without becoming the last value
 * ({@code insert}), or judged like any other value ({@code compare}, the default; a swinging
 * door cannot judge it and drops it).</li>
 * <li>A value sooner than {@code minimumInterval} after the last recorded one is dropped.</li>
 * <li>A value is recorded regardless once the last recorded one is older than
 * {@code maximumInterval} (heartbeat), or when its status differs from the status of the last
 * value (the status feature of the rule); a swinging door records its held value first.</li>
 * <li>Otherwise the rule decides: a change of at least a delta, a percent of the last value or a
 * percent of the range; one value out of every n; every value (a throttle, whose minimum
 * interval has already decided); a changed value; the doors of a swinging door.</li>
 * </ol>
 * <p>Every comparison is against the last <em>recorded</em> value, never a dropped one, so a slow
 * drift is not filtered away. A sampling rule counts every value that reaches it. A rule that
 * compares numbers records a value that is not a number, and a rule whose parameter does not
 * parse records every value: an inapplicable rule never drops data.</p>
 */
public final class Recording {

    /** A value with its status and time. */
    public record Sample(Object value, Object status, Instant timestamp) {
    }

    /**
     * What a value leads to. Every rule but a swinging door decides {@link #RECORD} or
     * {@link #DROP}; a swinging door holds a value until it knows whether the value is needed.
     */
    public enum Decision {
        /** Nothing is recorded. */
        DROP,
        /** The value is recorded. */
        RECORD,
        /** The value is held, nothing is recorded; a value held before is not needed. */
        HOLD,
        /** The held value is recorded ({@link State#released()}); the value is held in its place. */
        RECORD_HELD_THEN_HOLD,
        /** The held value is recorded ({@link State#released()}), then the value. */
        RECORD_HELD_AND_VALUE;

        /** Whether the value itself is recorded now. */
        public boolean recordsValue() {
            return this == RECORD || this == RECORD_HELD_AND_VALUE;
        }

        /** Whether the value held before is recorded now. */
        public boolean recordsHeld() {
            return this == RECORD_HELD_THEN_HOLD || this == RECORD_HELD_AND_VALUE;
        }
    }

    /** What a rule is evaluated against; one state per recorded element. */
    public static final class State {
        private boolean seen;
        private Object lastValue;
        private Object lastStatus;
        private Instant lastTimestamp;
        private long count;
        private Sample held;
        private Sample released;
        private double upper = Double.POSITIVE_INFINITY;
        private double lower = Double.NEGATIVE_INFINITY;

        /** Whether a value has been recorded. */
        public boolean seen() {
            return seen;
        }

        /** The last recorded value. */
        public Object lastValue() {
            return lastValue;
        }

        /** The status of the last recorded value. */
        public Object lastStatus() {
            return lastStatus;
        }

        /** The time of the last recorded value. */
        public Instant lastTimestamp() {
            return lastTimestamp;
        }

        /** The value a swinging door holds, if any. */
        public Optional<Sample> held() {
            return Optional.ofNullable(held);
        }

        /** The held value the last decision recorded, if it recorded one. */
        public Optional<Sample> released() {
            return Optional.ofNullable(released);
        }

        /** Forgets everything: the next value counts as the first. */
        public void reset() {
            seen = false;
            lastValue = null;
            lastStatus = null;
            lastTimestamp = null;
            count = 0;
            held = null;
            released = null;
            closeDoors();
        }

        private void closeDoors() {
            upper = Double.POSITIVE_INFINITY;
            lower = Double.NEGATIVE_INFINITY;
        }
    }

    private Recording() {
    }

    /**
     * Whether {@code value} at {@code timestamp} is recorded under {@code rule}; updates
     * {@code state}. No rule records every value; no timestamp means now.
     */
    public static boolean accepts(RecordingRule rule, State state, Object value, Instant timestamp) {
        return accepts(rule, state, value, null, timestamp);
    }

    /** Whether {@code value} with {@code status} at {@code timestamp} is recorded now under {@code rule}. */
    public static boolean accepts(RecordingRule rule, State state, Object value, Object status, Instant timestamp) {
        return decide(rule, state, value, status, timestamp).recordsValue();
    }

    /**
     * What {@code value} with {@code status} at {@code timestamp} leads to under {@code rule};
     * updates {@code state}. Where the decision records a held value, {@link State#released()}
     * is that value.
     */
    public static Decision decide(RecordingRule rule, State state, Object value, Object status, Instant timestamp) {
        Objects.requireNonNull(state, "state");
        Instant ts = timestamp == null ? Instant.now() : timestamp;
        state.released = null;
        if (rule == null || !state.seen) {
            state.count++;
            return record(state, value, status, ts);
        }
        boolean door = rule instanceof SwingingDoorRule;
        Instant newest = state.held != null ? state.held.timestamp() : state.lastTimestamp;
        if (ts.isBefore(newest) || door && !ts.isAfter(newest)) {
            LateValueHandling late = rule.getLateValues();
            if (late == LateValueHandling.INSERT) {
                return Decision.RECORD;
            }
            if (late == LateValueHandling.DROP || door) {
                return Decision.DROP;
            }
        }
        Duration between = Duration.between(state.lastTimestamp, ts);
        Duration minimum = duration(rule.getMinimumInterval());
        if (minimum != null && between.compareTo(minimum) < 0) {
            return Decision.DROP;
        }
        state.count++;
        Duration maximum = duration(rule.getMaximumInterval());
        Object newestStatus = state.held != null ? state.held.status() : state.lastStatus;
        boolean forced = maximum != null && between.compareTo(maximum) >= 0
                || rule.getStatusFeature() != null && !Objects.equals(status, newestStatus);
        if (forced) {
            return state.held != null ? recordHeldAndValue(state, value, status, ts) : record(state, value, status, ts);
        }
        if (rule instanceof SwingingDoorRule swingingDoor) {
            return swingingDoor(swingingDoor, state, value, status, ts);
        }
        return criterion(rule, state, value) ? record(state, value, status, ts) : Decision.DROP;
    }

    /**
     * Records the value a swinging door holds, at the end of a stream; empty when it holds none.
     */
    public static Optional<Sample> flush(State state) {
        Objects.requireNonNull(state, "state");
        Sample held = state.held;
        if (held == null) {
            return Optional.empty();
        }
        state.lastValue = held.value();
        state.lastStatus = held.status();
        state.lastTimestamp = held.timestamp();
        state.held = null;
        state.closeDoors();
        return Optional.of(held);
    }

    private static boolean criterion(RecordingRule rule, State state, Object value) {
        if (rule instanceof SamplingRule sampling) {
            long n = sampling.getEvery();
            return n <= 1 || (state.count - 1) % n == 0;
        }
        if (rule instanceof ThrottleRule) {
            return true;
        }
        Double last = numeric(state.lastValue);
        Double current = numeric(value);
        if (rule instanceof ChangedValueRule) {
            return last != null && current != null ? !last.equals(current) : !Objects.equals(value, state.lastValue);
        }
        if (last == null || current == null) {
            return true;
        }
        if (rule instanceof AbsoluteChangeRule absolute) {
            Double delta = numeric(absolute.getDelta());
            return delta == null || Math.abs(current - last) >= delta;
        }
        if (rule instanceof RelativeChangeRule relative) {
            Double percent = numeric(relative.getPercent());
            if (percent == null) {
                return true;
            }
            if (last == 0.0d) {
                return current != 0.0d;
            }
            return Math.abs(current - last) / Math.abs(last) * 100.0d >= percent;
        }
        if (rule instanceof RangeChangeRule range) {
            Double percent = numeric(range.getPercent());
            Double low = numeric(range.getLow());
            Double high = numeric(range.getHigh());
            if (percent == null || low == null || high == null || high <= low) {
                return true;
            }
            return Math.abs(current - last) >= percent / 100.0d * (high - low);
        }
        return true;
    }

    /**
     * The doors swing from the last recorded value: of the lines from it, those that pass within
     * the deviation of every value since lie between the lower and the upper slope. While one
     * line still fits, the value is held; once none fits, the held value is recorded and the
     * doors swing anew from it.
     */
    private static Decision swingingDoor(SwingingDoorRule rule, State state, Object value, Object status, Instant ts) {
        Double deviation = numeric(rule.getDeviation());
        Double current = numeric(value);
        Double last = numeric(state.lastValue);
        if (deviation == null || deviation < 0 || current == null || last == null) {
            return state.held != null ? recordHeldAndValue(state, value, status, ts) : record(state, value, status, ts);
        }
        double upper = Math.min(state.upper, slope(last, current + deviation, state.lastTimestamp, ts));
        double lower = Math.max(state.lower, slope(last, current - deviation, state.lastTimestamp, ts));
        if (lower <= upper) {
            state.upper = upper;
            state.lower = lower;
            state.held = new Sample(value, status, ts);
            return Decision.HOLD;
        }
        Sample held = state.held;
        state.released = held;
        state.lastValue = held.value();
        state.lastStatus = held.status();
        state.lastTimestamp = held.timestamp();
        double base = numeric(held.value());
        state.upper = slope(base, current + deviation, held.timestamp(), ts);
        state.lower = slope(base, current - deviation, held.timestamp(), ts);
        state.held = new Sample(value, status, ts);
        return Decision.RECORD_HELD_THEN_HOLD;
    }

    private static double slope(double from, double to, Instant start, Instant end) {
        Duration d = Duration.between(start, end);
        return (to - from) / (d.getSeconds() + d.getNano() / 1.0e9d);
    }

    private static Decision record(State state, Object value, Object status, Instant ts) {
        state.lastValue = value;
        state.lastStatus = status;
        state.lastTimestamp = ts;
        state.seen = true;
        state.held = null;
        state.closeDoors();
        return Decision.RECORD;
    }

    private static Decision recordHeldAndValue(State state, Object value, Object status, Instant ts) {
        state.released = state.held;
        record(state, value, status, ts);
        return Decision.RECORD_HELD_AND_VALUE;
    }

    private static Duration duration(String literal) {
        return Durations.isValid(literal) ? Durations.parse(literal) : null;
    }

    /** A value as a number, or {@code null} when it is none. */
    static Double numeric(Object value) {
        double d;
        if (value instanceof Number n) {
            d = n.doubleValue();
        } else if (value instanceof CharSequence s) {
            try {
                d = Double.parseDouble(s.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        return Double.isNaN(d) ? null : d;
    }
}
