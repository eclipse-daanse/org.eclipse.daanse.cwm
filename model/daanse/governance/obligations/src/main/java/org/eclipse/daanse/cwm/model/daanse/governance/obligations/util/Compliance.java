/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Delete;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DestructiveStage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Restrict;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.TerminalStage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Due;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Starts;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionClass;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.Obligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;

/**
 * Whether the retention schedules of an element keep its legal duties.
 *
 * <p>All obligations along the path of an element apply, each must be kept: no destructive stage
 * before the end of a retention obligation, a terminal stage by the end of a deletion obligation —
 * a delete, or a stage that makes the data anonymous where the obligation lets that satisfy it.
 * Where a retention obligation ends after a deletion obligation, the data is restricted at the
 * latest when the deletion obligation ends and deleted when the retention obligation ends. A
 * schedule of rows ends the values too; an obligation of rows is kept by the schedule of rows, and
 * data that must stay unchanged by no stage that changes its values either. The
 * terminal stage of a schedule without a period of its own takes the term of the deletion class of
 * the element in the scope of the schedule. The holds of the element suspend the destructive stages
 * while they last.</p>
 */
public final class Compliance {

    /** A duty not kept: the rule, the obligation, the stage, when the stage is due and when it must be. */
    public record Violation(String rule, Obligation obligation, Stage stage, Instant due, Instant required,
            String message) {
    }

    /** The plan of an element at a time and the duties it does not keep. */
    public record Report(Retention.Plan plan, List<Violation> violations) {

        /** Whether every duty is kept. */
        public boolean compliant() {
            return violations.isEmpty();
        }
    }

    private Compliance() {
    }

    /** The retention obligations along the path of {@code element}, nearest first. */
    public static List<RetentionObligation> retentions(ModelElement element) {
        return LifecycleRules.all(element, RetentionObligation.class);
    }

    /** The deletion obligations along the path of {@code element}, nearest first. */
    public static List<DeletionObligation> deletions(ModelElement element) {
        return LifecycleRules.all(element, DeletionObligation.class);
    }

    /**
     * The period of a stage of the schedules of {@code element}: its own {@code after}, else for a
     * terminal stage the term of the deletion class of the element in the scope of the schedule.
     */
    public static Retention.Terms terms(ModelElement element) {
        Objects.requireNonNull(element, "element");
        return step -> step.stage().getAfter() != null ? Optional.of(step.stage().getAfter())
                : step.stage() instanceof TerminalStage
                        ? DeletionClasses.effective(element, step.schedule().getScope()).map(DeletionClass::getTerm)
                        : Optional.empty();
    }

    /**
     * The duties of {@code element} checked against its schedules, with the start times
     * {@code starts} gives, and its plan at {@code now} under its holds.
     */
    public static Report check(ModelElement element, Starts starts, Instant now) {
        Objects.requireNonNull(element, "element");
        Retention.Effective schedules = Retention.effective(element);
        Retention.Plan plan = Retention.plan(schedules, starts, now, RetentionHolds.suspension(element),
                terms(element));
        List<Violation> violations = new ArrayList<>();
        for (RetentionObligation r : retentions(element)) {
            Instant keepUntil = end(r, r.getMinimum(), starts);
            if (keepUntil == null) {
                continue;
            }
            for (Due d : relevant(plan, r)) {
                if (d.step().stage() instanceof DestructiveStage && d.at() != null && d.at().isBefore(keepUntil)) {
                    violations.add(new Violation("O3", r, d.step().stage(), d.at(), keepUntil, "stage "
                            + d.step().stage().getName() + " is due at " + d.at() + ", before " + r.getName()
                            + " keeps the data until " + keepUntil));
                }
            }
        }
        for (DeletionObligation del : deletions(element)) {
            Instant deleteBy = end(del, del.getMaximum(), starts);
            if (deleteBy == null) {
                continue;
            }
            List<Due> stages = relevant(plan, del);
            Optional<Due> terminal = stages.stream().filter(d -> d.step().stage() instanceof TerminalStage && d.at() != null)
                    .findFirst();
            Instant keepUntil = null;
            RetentionObligation longest = null;
            for (RetentionObligation r : retentions(element)) {
                Instant end = end(r, r.getMinimum(), starts);
                if (end != null && end.isAfter(deleteBy) && (keepUntil == null || end.isAfter(keepUntil))) {
                    keepUntil = end;
                    longest = r;
                }
            }
            if (keepUntil != null) {
                Instant restrictBy = deleteBy;
                if (stages.stream().noneMatch(d -> d.step().stage() instanceof Restrict && d.at() != null
                        && !d.at().isAfter(restrictBy))) {
                    violations.add(new Violation("O5", del, null, null, deleteBy, del.getName() + " ends at " + deleteBy
                            + " but " + longest.getName() + " keeps the data until " + keepUntil
                            + ": the data is restricted by " + deleteBy));
                }
                if (terminal.isEmpty() || terminal.get().at().isAfter(keepUntil)) {
                    violations.add(new Violation("O5", del, terminal.map(d -> d.step().stage()).orElse(null),
                            terminal.map(Due::at).orElse(null), keepUntil, "the data is deleted when " + longest.getName()
                                    + " ends at " + keepUntil + (terminal.isEmpty() ? ", no stage deletes it"
                                            : ", not at " + terminal.get().at())));
                }
            } else if (terminal.isEmpty()) {
                violations.add(new Violation("O4", del, null, null, deleteBy, "no stage deletes the data, which "
                        + del.getName() + " demands by " + deleteBy));
            } else if (terminal.get().at().isAfter(deleteBy)) {
                violations.add(new Violation("O4", del, terminal.get().step().stage(), terminal.get().at(), deleteBy,
                        "stage " + terminal.get().step().stage().getName() + " is due at " + terminal.get().at()
                                + ", after " + del.getName() + " demands the data gone by " + deleteBy));
            }
            if (terminal.isPresent() && !(terminal.get().step().stage() instanceof Delete)
                    && !del.isAnonymizationSatisfies()) {
                violations.add(new Violation("O4", del, terminal.get().step().stage(), terminal.get().at(), deleteBy,
                        "stage " + terminal.get().step().stage().getName() + " does not delete the data, and "
                                + del.getName() + " is not satisfied by making it anonymous"));
            }
        }
        return new Report(plan, List.copyOf(violations));
    }

    /** When the period of an obligation ends for the data at hand; {@code null} when its start is unknown. */
    private static Instant end(Obligation obligation, String period, Starts starts) {
        if (obligation.getStart() == null) {
            return null;
        }
        return starts.of(obligation.getStart())
                .flatMap(t -> Retention.dueAt(period, obligation.getStart(), t)).orElse(null);
    }

    /**
     * The stages that touch the data of a duty: of rows for rows, of values and of rows for values;
     * every stage for data that must stay unchanged.
     */
    private static List<Due> relevant(Retention.Plan plan, Obligation duty) {
        boolean every = duty.getScope() == DataScope.VALUE || duty instanceof RetentionObligation r && r.isImmutable();
        return plan.stages().stream().filter(d -> every || d.step().schedule().getScope() == duty.getScope()).toList();
    }
}
