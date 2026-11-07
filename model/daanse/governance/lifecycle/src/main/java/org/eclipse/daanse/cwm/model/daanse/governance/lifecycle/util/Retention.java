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

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DestructiveStage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.TerminalStage;

/**
 * What retention means for an element: the schedules that apply to it, the order of their stages,
 * when each stage is due, and which one is in force at a given time.
 *
 * <p>An element has at most one schedule of values and one of rows that apply (the nearest of
 * each scope). Both apply together: the stages of both follow one another, and the last stage of
 * the rows ends the values too. A stage is due when its period has run from its start — the start
 * time aligned to the end of its month, quarter or year, then offset, then the stage's
 * {@code after}. A retention hold suspends the destructive stages while it lasts.</p>
 */
public final class Retention {

    /** The schedules that apply to an element: the nearest one of values and the nearest one of rows. */
    public record Effective(RetentionSchedule values, RetentionSchedule rows) {

        /** Whether no schedule applies. */
        public boolean isEmpty() {
            return values == null && rows == null;
        }
    }

    /** A stage of a schedule. */
    public record Step(RetentionSchedule schedule, Stage stage) {

        /** Where the stage's period starts: its own start, else the start of its schedule. */
        public PeriodStart start() {
            return stage.getStart() != null ? stage.getStart() : schedule.getStart();
        }
    }

    /** The state of a stage at a time. */
    public enum State {
        /** Its time has come, and a later stage's too. */
        PAST,
        /** Its time has come and it is the stage in force. */
        DUE,
        /** Its time has not come, or its start is not known. */
        PENDING,
        /** Its time has come, but a hold suspends it. */
        SUSPENDED,
        /** It is due after a terminal stage: the data is gone before. */
        UNREACHABLE
    }

    /** A stage with the time it is due ({@code null} when its start is unknown) and its state. */
    public record Due(Step step, Instant at, State state) {
    }

    /** The stages of an element at a time, in the order they are due. */
    public record Plan(List<Due> stages) {

        /** The stage in force, if any has come. */
        public Optional<Due> current() {
            return stages.stream().filter(d -> d.state() == State.DUE).findFirst();
        }

        /** The next stage whose time is known and has not come. */
        public Optional<Due> next() {
            return stages.stream().filter(d -> d.state() == State.PENDING && d.at() != null).findFirst();
        }
    }

    /** The time a start stands for: the time of the value, of a feature, of an event; empty when unknown. */
    @FunctionalInterface
    public interface Starts {
        Optional<Instant> of(PeriodStart start);

        /** Every start at the same time. */
        static Starts at(Instant time) {
            Objects.requireNonNull(time, "time");
            return start -> Optional.of(time);
        }
    }

    /**
     * The period after which a stage is due: its own {@code after}, or what another rule gives it — a
     * deletion class gives the term of a last stage that names none.
     */
    @FunctionalInterface
    public interface Terms {

        /** Each stage its own {@code after}. */
        Terms OWN = step -> Optional.ofNullable(step.stage().getAfter());

        Optional<String> of(Step step);
    }

    /** Whether destructive stages are suspended at a time: a retention hold. */
    @FunctionalInterface
    public interface Suspension {

        /** No hold. */
        Suspension NONE = now -> false;

        boolean at(Instant now);

        /** A hold from {@code since}, until {@code until} ({@code null}: until it is released). */
        static Suspension between(Instant since, Instant until) {
            Objects.requireNonNull(since, "since");
            return now -> !now.isBefore(since) && (until == null || now.isBefore(until));
        }
    }

    private Retention() {
    }

    /** The schedules that apply to {@code element}. */
    public static Effective effective(ModelElement element) {
        return new Effective(RetentionSchedules.effective(element, DataScope.VALUE).orElse(null),
                RetentionSchedules.effective(element, DataScope.ROW).orElse(null));
    }

    /**
     * The stages of both schedules in the order they follow: two stages of one start by their
     * {@code after}, stages of different starts as the schedules give them (values first). Each
     * schedule's stages keep their own order.
     */
    public static List<Step> timeline(Effective effective) {
        return timeline(effective, Terms.OWN);
    }

    /** The stages of both schedules in order, each with the period {@code terms} gives it. */
    public static List<Step> timeline(Effective effective, Terms terms) {
        List<Step> values = steps(effective.values());
        List<Step> rows = steps(effective.rows());
        List<Step> merged = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < values.size() || j < rows.size()) {
            if (j >= rows.size()) {
                merged.add(values.get(i++));
            } else if (i >= values.size()) {
                merged.add(rows.get(j++));
            } else {
                Step v = values.get(i);
                Step r = rows.get(j);
                merged.add(before(r, v, terms) ? rows.get(j++) : values.get(i++));
            }
        }
        return merged;
    }

    /**
     * The stages of the values that come after the last stage of the rows, where the two can be
     * compared: they are never reached, because the rows are gone before.
     */
    public static List<Step> unreachable(Effective effective) {
        return unreachable(effective, Terms.OWN);
    }

    /** The unreachable stages of the values, each stage with the period {@code terms} gives it. */
    public static List<Step> unreachable(Effective effective, Terms terms) {
        if (effective.values() == null || effective.rows() == null) {
            return List.of();
        }
        Optional<Step> end = steps(effective.rows()).stream().filter(s -> s.stage() instanceof TerminalStage).findFirst();
        if (end.isEmpty()) {
            return List.of();
        }
        return steps(effective.values()).stream().filter(v -> before(end.get(), v, terms)).toList();
    }

    /** Whether {@code a} is due strictly before {@code b}, decided only where both have the same start. */
    static boolean before(Step a, Step b) {
        return before(a, b, Terms.OWN);
    }

    private static boolean before(Step a, Step b, Terms terms) {
        Optional<String> x = terms.of(a);
        Optional<String> y = terms.of(b);
        return x.isPresent() && y.isPresent() && PeriodStarts.same(a.start(), b.start())
                && Periods.isValid(x.get()) && Periods.isValid(y.get())
                && Periods.compare(x.get(), y.get()).sign() < 0;
    }

    /**
     * When {@code stage} is due for data whose period started at {@code time}; empty without
     * {@code after}, and where a period or zone does not parse — a stage that cannot be dated is
     * never due, so that an inapplicable schedule deletes nothing.
     */
    public static Optional<Instant> dueAt(Stage stage, PeriodStart start, Instant time) {
        return dueAt(stage.getAfter(), start, time);
    }

    /** When a period {@code after} from {@code start} ends for data whose start time is {@code time}. */
    public static Optional<Instant> dueAt(String after, PeriodStart start, Instant time) {
        if (after == null || !Periods.isValid(after) || start == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Periods.parse(after).addTo(PeriodStarts.begin(start, time)).toInstant());
        } catch (IllegalArgumentException | DateTimeException e) {
            return Optional.empty();
        }
    }

    /** The plan of {@code element} at {@code now}. */
    public static Plan plan(ModelElement element, Starts starts, Instant now, Suspension suspension) {
        return plan(effective(element), starts, now, suspension);
    }

    /**
     * The stages at {@code now}: each with the time it is due, ordered by it (unknown times last).
     * Of the stages whose time has come the first terminal one is in force, else the last one; a
     * destructive stage is suspended while {@code suspension} holds; a stage due after the first
     * terminal stage is unreachable.
     */
    public static Plan plan(Effective effective, Starts starts, Instant now, Suspension suspension) {
        return plan(effective, starts, now, suspension, Terms.OWN);
    }

    /** The plan at {@code now}, each stage due after the period {@code terms} gives it. */
    public static Plan plan(Effective effective, Starts starts, Instant now, Suspension suspension, Terms terms) {
        Objects.requireNonNull(now, "now");
        Suspension hold = suspension == null ? Suspension.NONE : suspension;
        List<Due> dated = new ArrayList<>();
        for (Step step : timeline(effective, terms)) {
            PeriodStart start = step.start();
            Optional<String> after = terms.of(step);
            Instant at = start == null || after.isEmpty() ? null
                    : starts.of(start).flatMap(t -> dueAt(after.get(), start, t)).orElse(null);
            dated.add(new Due(step, at, State.PENDING));
        }
        dated.sort(Comparator.comparing(Due::at, Comparator.nullsLast(Comparator.naturalOrder())));
        Instant end = dated.stream().filter(d -> d.step().stage() instanceof TerminalStage && d.at() != null)
                .map(Due::at).findFirst().orElse(null);
        List<Integer> come = new ArrayList<>();
        List<Due> out = new ArrayList<>(dated);
        boolean endSeen = false;
        for (int k = 0; k < out.size(); k++) {
            Due d = out.get(k);
            if (endSeen && d.at() != null) {
                out.set(k, new Due(d.step(), d.at(), State.UNREACHABLE));
                continue;
            }
            endSeen = end != null && d.step().stage() instanceof TerminalStage && end.equals(d.at());
            if (d.at() == null || d.at().isAfter(now)) {
                continue;
            }
            if (d.step().stage() instanceof DestructiveStage && hold.at(now)) {
                out.set(k, new Due(d.step(), d.at(), State.SUSPENDED));
            } else {
                come.add(k);
            }
        }
        int inForce = come.stream().filter(k -> out.get(k).step().stage() instanceof TerminalStage).findFirst()
                .orElse(come.isEmpty() ? -1 : come.get(come.size() - 1));
        for (int k : come) {
            Due d = out.get(k);
            out.set(k, new Due(d.step(), d.at(), k == inForce ? State.DUE : State.PAST));
        }
        return new Plan(List.copyOf(out));
    }

    private static List<Step> steps(RetentionSchedule schedule) {
        return schedule == null ? List.of() : schedule.getStage().stream().map(s -> new Step(schedule, s)).toList();
    }
}
