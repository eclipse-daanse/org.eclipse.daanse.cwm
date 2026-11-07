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
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Aggregate;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.CountLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.EventTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Limit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.SizeLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.TerminalStage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Transfer;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Step;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;

/**
 * Checks the retention schedules in a model; the rules of the other lifecycle models are checked
 * by their own validators, with the same {@link Finding}s.
 *
 * <ul>
 * <li>{@code L1} — a schedule governs data, not a part of a rule (a stage); a schedule of rows
 * governs classifiers or namespaces. A schedule of values may govern any element that holds values:
 * a feature, the features of a classifier or namespace, the observations of a measurement, the runs
 * of an activity.</li>
 * <li>{@code L2} — at most one schedule of a scope names an element.</li>
 * <li>{@code L3} — a schedule has a stage or a limit, its stages a start and a period (the last
 * stage may take its term from a deletion class: a warning); periods, durations and zones parse, an
 * alignment has a zone; a cleanup interval is positive, counts are at least 1, sizes above 0.</li>
 * <li>{@code L4} — the feature of a start and the keys of a count limit belong to the governed
 * classifier (or an ancestor), or to the owner of the governed feature.</li>
 * <li>{@code L5} — at most one terminal stage, as the last; stages of one start in the order of
 * their periods.</li>
 * <li>{@code L6} (warning) — a stage of values after the last stage of the rows of the same
 * element: it is never reached.</li>
 * <li>{@code L7} — a transfer goes to another namespace than the governed element and the
 * namespaces around it.</li>
 * <li>{@code L8} (warning) — the target of an aggregation has no features.</li>
 * <li>{@code L9} (warning) — an element or a reference that does not resolve; the body is not the
 * text of the schedule.</li>
 * <li>{@code L10} — a schedule governs data: no rule, and not the namespace that owns it.</li>
 * </ul>
 */
public final class LifecycleModelValidator {

    /** Severity of a finding; only {@link #ERROR} makes a model invalid. */
    public enum Severity {
        ERROR, WARNING
    }

    /** One finding: severity, rule code, the element it is about, message. */
    public record Finding(Severity severity, String rule, EObject element, String message) {
        @Override
        public String toString() {
            return severity + " " + rule + ": " + message;
        }
    }

    private LifecycleModelValidator() {
    }

    /** All findings for the retention schedules in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        Map<ModelElement, List<RetentionSchedule>> byElement = new LinkedHashMap<>();
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            visit(it.next(), findings, byElement);
        }
        visit(root, findings, byElement);
        byElement.forEach((element, schedules) -> conflicts(element, schedules, findings));
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void visit(EObject o, List<Finding> findings, Map<ModelElement, List<RetentionSchedule>> byElement) {
        if (!(o instanceof RetentionSchedule schedule)) {
            return;
        }
        for (ModelElement e : schedule.getConstrainedElement()) {
            byElement.computeIfAbsent(e, k -> new ArrayList<>()).add(schedule);
            if (e.eIsProxy()) {
                findings.add(warning("L9", schedule, ": element " + ((InternalEObject) e).eProxyURI()
                        + " does not resolve."));
            } else if (e instanceof Constraint) {
                findings.add(error("L10", schedule, ": a schedule governs data, not the rule " + path(e) + "."));
            } else if (partOfARule(e)) {
                findings.add(error("L1", schedule, ": a schedule governs data, not the " + e.eClass().getName() + " "
                        + path(e) + " of a rule."));
            } else if (schedule.getScope() == DataScope.ROW && !(e instanceof Namespace)) {
                findings.add(error("L1", schedule, ": a schedule of rows governs classifiers or namespaces, not the "
                        + e.eClass().getName() + " " + path(e) + "."));
            } else if (e == schedule.getNamespace()) {
                findings.add(error("L10", schedule, ": it governs " + path(e) + ", the namespace that owns it; a "
                        + "schedule governs data and lives beside it."));
            }
        }
        parameters(schedule, findings);
        anchors(schedule, findings);
        order(schedule, findings);
        unreachable(schedule, findings);
        for (Stage stage : schedule.getStage()) {
            if (stage instanceof Transfer t && t.getTarget() != null) {
                transfer(schedule, t, findings);
            }
            if (stage instanceof Aggregate a && a.getTarget() != null && !a.getTarget().eIsProxy()
                    && a.getTarget().getFeature().isEmpty()) {
                findings.add(warning("L8", schedule, ": the target " + path(a.getTarget()) + " of stage "
                        + a.getName() + " has no features to hold the summaries."));
            }
        }
        proxies(schedule, findings);
        String expected = RetentionSchedules.render(schedule);
        if (!LifecycleRules.hasBody(schedule, expected)) {
            findings.add(warning("L9", schedule, ": the body is not the text of the schedule '" + expected + "' in "
                    + LifecycleRules.LANGUAGE + "."));
        }
    }

    /** The references of a schedule and of its start, stages and limits that do not resolve. */
    private static void proxies(RetentionSchedule schedule, List<Finding> findings) {
        List<EObject> owners = new ArrayList<>(List.of(schedule));
        schedule.eAllContents().forEachRemaining(owners::add);
        for (EObject owner : owners) {
            for (EObject target : owner.eCrossReferences()) {
                if (target.eIsProxy() && !schedule.getConstrainedElement().contains(target)) {
                    findings.add(warning("L9", schedule, ": a reference of " + owner.eClass().getName() + " to "
                            + ((InternalEObject) target).eProxyURI() + " does not resolve."));
                }
            }
        }
    }

    private static boolean partOfARule(EObject element) {
        for (EObject o = element.eContainer(); o != null; o = o.eContainer()) {
            if (o instanceof Constraint) {
                return true;
            }
        }
        return false;
    }

    private static void conflicts(ModelElement element, List<RetentionSchedule> schedules, List<Finding> findings) {
        for (DataScope scope : DataScope.values()) {
            List<RetentionSchedule> same = schedules.stream().filter(s -> s.getScope() == scope).toList();
            if (same.size() > 1) {
                findings.add(new Finding(Severity.ERROR, "L2", element, path(element) + " is named by " + same.size()
                        + " schedules of " + scope.getLiteral() + "s (" + names(same) + "); one applies to an element."));
            }
        }
    }

    private static void parameters(RetentionSchedule schedule, List<Finding> findings) {
        if (schedule.getStage().isEmpty() && schedule.getLimit().isEmpty()) {
            findings.add(error("L3", schedule, " has neither a stage nor a limit: it would keep everything."));
        }
        if (!schedule.getStage().isEmpty() && schedule.getStart() == null
                && schedule.getStage().stream().anyMatch(s -> s.getStart() == null)) {
            findings.add(error("L3", schedule, " has stages without a start: the schedule or each stage says where "
                    + "the periods start."));
        }
        start(schedule, schedule.getStart(), "the schedule", findings);
        for (Stage stage : schedule.getStage()) {
            if (stage.getAfter() == null && !(stage instanceof TerminalStage)) {
                findings.add(error("L3", schedule, ": stage " + stage.getName() + " has no period; only the last "
                        + "stage may take its term from a deletion class."));
            } else if (stage.getAfter() == null) {
                findings.add(warning("L3", schedule, ": the last stage " + stage.getName() + " has no period; it is "
                        + "never due unless a deletion class gives its term."));
            }
            period(schedule, "after of stage " + stage.getName(), stage.getAfter(), findings);
            start(schedule, stage.getStart(), "stage " + stage.getName(), findings);
            if (stage instanceof Aggregate a) {
                period(schedule, "interval of stage " + a.getName(), a.getInterval(), findings);
            }
        }
        if (schedule.getCleanupInterval() != null) {
            try {
                Duration d = Durations.parse(schedule.getCleanupInterval());
                if (d.isZero()) {
                    findings.add(error("L3", schedule, ": cleanupInterval is zero, it must be positive."));
                }
            } catch (IllegalArgumentException e) {
                findings.add(error("L3", schedule, ": cleanupInterval '" + schedule.getCleanupInterval()
                        + "' is no ISO-8601 duration of days, hours, minutes and seconds (P1D, PT6H)."));
            }
        }
        if (schedule.isSetMaxDelete() && schedule.getMaxDelete() < 1) {
            findings.add(error("L3", schedule, ": maxDelete is " + schedule.getMaxDelete() + ", it must be at least 1."));
        }
        for (Limit limit : schedule.getLimit()) {
            if (limit instanceof CountLimit c && c.getKeep() < 1) {
                findings.add(error("L3", schedule, ": a count limit keeps " + c.getKeep() + ", it must keep at least 1."));
            } else if (limit instanceof SizeLimit s && s.getBytes() < 1) {
                findings.add(error("L3", schedule, ": a size limit of " + s.getBytes() + " bytes, it must be above 0."));
            }
        }
    }

    private static void start(RetentionSchedule schedule, PeriodStart start, String of, List<Finding> findings) {
        if (start == null) {
            return;
        }
        period(schedule, "offset of " + of, start.getOffset(), findings);
        if (start.getZone() != null) {
            try {
                ZoneId.of(start.getZone());
            } catch (DateTimeException e) {
                findings.add(error("L3", schedule, ": the zone '" + start.getZone() + "' of " + of
                        + " is no time zone (Europe/Berlin, UTC)."));
            }
        } else if (start.getAlignment() != Alignment.NONE) {
            findings.add(error("L3", schedule, ": the start of " + of + " is aligned to the end of a "
                    + "month, quarter or year without a zone in which that end lies."));
        }
    }

    private static void period(RetentionSchedule schedule, String what, String literal, List<Finding> findings) {
        if (literal != null && !Periods.isValid(literal)) {
            findings.add(error("L3", schedule, ": the " + what + " '" + literal
                    + "' is no ISO-8601 period (P10Y, P6M, P2W, P90D, P1DT12H)."));
        }
    }

    /** The features a schedule names besides its elements belong to the governed classifier. */
    private static void anchors(RetentionSchedule schedule, List<Finding> findings) {
        List<StructuralFeature> features = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        List<PeriodStart> starts = new ArrayList<>();
        if (schedule.getStart() != null) {
            starts.add(schedule.getStart());
        }
        schedule.getStage().stream().map(Stage::getStart).filter(s -> s != null).forEach(starts::add);
        for (PeriodStart start : starts) {
            StructuralFeature f = start instanceof FeatureTime t ? t.getFeature()
                    : start instanceof EventTime e ? e.getFeature() : null;
            if (f != null) {
                features.add(f);
                roles.add("time feature");
            }
        }
        for (Limit limit : schedule.getLimit()) {
            if (limit instanceof CountLimit c) {
                for (StructuralFeature k : c.getKey()) {
                    features.add(k);
                    roles.add("key");
                }
            }
        }
        for (int i = 0; i < features.size(); i++) {
            StructuralFeature f = features.get(i);
            for (ModelElement e : schedule.getConstrainedElement()) {
                if (e.eIsProxy() || f.eIsProxy()) {
                    continue;
                }
                Classifier holder = e instanceof Classifier c ? c : e instanceof StructuralFeature s ? s.getOwner() : null;
                if (holder == null) {
                    findings.add(error("L4", schedule, ": a " + roles.get(i) + " needs a classifier or a feature as "
                            + "governed element, " + path(e) + " is a " + e.eClass().getName() + "."));
                } else if (!LifecycleRules.lineage(holder).contains(f.getOwner())) {
                    findings.add(error("L4", schedule, ": the " + roles.get(i) + " " + path(f) + " is no feature of "
                            + path(holder) + "."));
                }
            }
        }
    }

    private static void order(RetentionSchedule schedule, List<Finding> findings) {
        List<Stage> stages = schedule.getStage();
        long terminals = stages.stream().filter(TerminalStage.class::isInstance).count();
        if (terminals > 1) {
            findings.add(error("L5", schedule, " has " + terminals + " terminal stages; after the first the data "
                    + "is gone."));
        } else if (terminals == 1 && !(stages.get(stages.size() - 1) instanceof TerminalStage)) {
            Stage end = stages.stream().filter(TerminalStage.class::isInstance).findFirst().orElseThrow();
            findings.add(error("L5", schedule, ": the terminal stage " + end.getName() + " is not the last; the "
                    + "stages after it are never reached."));
        }
        for (int i = 1; i < stages.size(); i++) {
            Step before = new Step(schedule, stages.get(i - 1));
            Step after = new Step(schedule, stages.get(i));
            if (Retention.before(after, before)) {
                findings.add(error("L5", schedule, ": stage " + after.stage().getName() + " (" + after.stage().getAfter()
                        + ") comes after stage " + before.stage().getName() + " (" + before.stage().getAfter()
                        + ") but is due before it."));
            }
        }
    }

    private static void unreachable(RetentionSchedule schedule, List<Finding> findings) {
        if (schedule.getScope() != DataScope.VALUE) {
            return;
        }
        for (ModelElement e : schedule.getConstrainedElement()) {
            if (e.eIsProxy()) {
                continue;
            }
            Retention.Effective effective = Retention.effective(e);
            if (effective.values() != schedule) {
                continue;
            }
            for (Step step : Retention.unreachable(effective)) {
                findings.add(warning("L6", schedule, ": stage " + step.stage().getName() + " is due after the last "
                        + "stage of " + effective.rows().getName() + ", which ends the rows of " + path(e)
                        + " before."));
            }
        }
    }

    private static void transfer(RetentionSchedule schedule, Transfer transfer, List<Finding> findings) {
        Namespace target = transfer.getTarget();
        for (ModelElement e : schedule.getConstrainedElement()) {
            for (EObject o = e; o != null; o = o instanceof Feature f && f.getOwner() != null ? f.getOwner()
                    : o instanceof ModelElement m && m.getNamespace() != null ? m.getNamespace() : o.eContainer()) {
                if (o == target) {
                    findings.add(error("L7", schedule, ": stage " + transfer.getName() + " transfers " + path(e)
                            + " to " + path(target) + ", where it already is."));
                    break;
                }
            }
        }
    }

    private static Finding error(String rule, RetentionSchedule schedule, String text) {
        return new Finding(Severity.ERROR, rule, schedule, name(schedule) + text);
    }

    private static Finding warning(String rule, RetentionSchedule schedule, String text) {
        return new Finding(Severity.WARNING, rule, schedule, name(schedule) + text);
    }

    private static String name(RetentionSchedule schedule) {
        return "RetentionSchedule '" + schedule.getName() + "'";
    }

    private static String names(List<RetentionSchedule> schedules) {
        return String.join(", ", schedules.stream().map(RetentionSchedule::getName).toList());
    }

    private static String path(EObject element) {
        return LifecycleRules.path(element);
    }
}
