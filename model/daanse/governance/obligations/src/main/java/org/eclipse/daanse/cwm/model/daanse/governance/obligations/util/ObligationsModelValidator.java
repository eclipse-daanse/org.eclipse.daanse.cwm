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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Delete;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DestructiveStage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.EventTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleRule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Restrict;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.TerminalStage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.ValueTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Step;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionClass;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.Obligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;

/**
 * Checks the obligations, holds and deletion classes in a model, and the retention schedules
 * against them; the findings are those of the lifecycle models ({@link Finding}, {@link Severity}).
 * A duty is judged where its data is: a duty on a package at the classes in it, a duty of values on
 * a class at its features; a finding names every element it concerns. Periods compare where their
 * starts are the same; anything else is a warning, and {@link Compliance#check} decides it with an
 * instant for each start.
 *
 * <ul>
 * <li>{@code O1} — a duty, hold or class governs data: no rule, no part of a rule; of rows, a
 * classifier or namespace.</li>
 * <li>{@code O2} — periods, zones and instants parse, an alignment has a zone, a hold ends after it
 * begins, the start of a deletion class is abstract.</li>
 * <li>{@code O3} — no destructive stage before a retention obligation ends.</li>
 * <li>{@code O4} — a terminal stage by the end of a deletion obligation: a delete, or a stage that
 * makes the data anonymous where that satisfies the obligation.</li>
 * <li>{@code O5} — where a retention obligation ends after a deletion obligation: a restriction by
 * the end of the deletion obligation, the terminal stage when the retention obligation ends.</li>
 * <li>{@code O6} (warning) — a duty and a stage of different starts, or periods compared only
 * approximately: decided with instants.</li>
 * <li>{@code O7} — the last stage of a schedule takes its period from itself or from the deletion
 * class of its data, never both and never neither; taken from the class, the stage starts from the
 * class's start — the same event, aligned, offset and zoned alike, bound to the feature that holds
 * the time of the event.</li>
 * <li>{@code O8} (warning) — an obligation without legal source, or with a source not in force at
 * the time of the check.</li>
 * <li>{@code O9} (warning) — an element that does not resolve; the body is not the text of the
 * rule.</li>
 * </ul>
 */
public final class ObligationsModelValidator {

    private ObligationsModelValidator() {
    }

    /** All findings in {@code root} and below, the legal sources taken as of now. */
    public static List<Finding> check(EObject root) {
        return check(root, Instant.now());
    }

    /** All findings in {@code root} and below, the legal sources taken as of {@code at}. */
    public static List<Finding> check(EObject root, Instant at) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        for (EObject o : all) {
            if (o instanceof Obligation || o instanceof RetentionHold || o instanceof DeletionClass) {
                rule((LifecycleRule) o, at, findings);
            }
        }
        Gathered gathered = new Gathered();
        for (EObject o : all) {
            if (o instanceof RetentionObligation r) {
                data(r).forEach(element -> retention(r, element, gathered));
            } else if (o instanceof DeletionObligation d) {
                data(d).forEach(element -> deletion(d, element, gathered));
            } else if (o instanceof RetentionSchedule s) {
                deletionClass(s, gathered);
            }
        }
        gathered.into(findings);
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void rule(LifecycleRule rule, Instant at, List<Finding> findings) {
        for (ModelElement e : rule.getConstrainedElement()) {
            if (e.eIsProxy()) {
                findings.add(finding(Severity.WARNING, "O9", rule, ": element " + ((InternalEObject) e).eProxyURI()
                        + " does not resolve."));
            } else if (e instanceof Constraint || partOfARule(e)) {
                findings.add(finding(Severity.ERROR, "O1", rule, ": it governs data, not the " + e.eClass().getName()
                        + " " + path(e) + "."));
            } else if (rule.getScope() == DataScope.ROW && !(e instanceof Namespace)) {
                findings.add(finding(Severity.ERROR, "O1", rule, ": a rule of rows governs classifiers or namespaces, "
                        + "not the " + e.eClass().getName() + " " + path(e) + "."));
            }
        }
        String expected;
        if (rule instanceof Obligation o) {
            String what = o instanceof RetentionObligation ? "minimum" : "maximum";
            String literal = o instanceof RetentionObligation r ? r.getMinimum()
                    : ((DeletionObligation) o).getMaximum();
            period(rule, what, literal, findings);
            start(rule, o.getStart(), findings);
            if (LegalSources.of(o).isEmpty()) {
                findings.add(finding(Severity.WARNING, "O8", rule, " names no legal source."));
            }
            for (LegalSource source : LegalSources.of(o)) {
                if (!LegalSources.inForce(source, at)) {
                    findings.add(finding(Severity.WARNING, "O8", rule, ": its source " + source.getName()
                            + " is not in force (" + validity(source) + ")."));
                }
            }
            expected = Obligations.render(o);
        } else if (rule instanceof RetentionHold h) {
            Instant since = LegalSources.instant(h.getSince());
            Instant until = LegalSources.instant(h.getUntil());
            if (since == null) {
                findings.add(finding(Severity.ERROR, "O2", rule, ": since '" + h.getSince() + "' is no instant."));
            }
            if (h.getUntil() != null && until == null) {
                findings.add(finding(Severity.ERROR, "O2", rule, ": until '" + h.getUntil() + "' is no instant."));
            } else if (since != null && until != null && !until.isAfter(since)) {
                findings.add(finding(Severity.ERROR, "O2", rule, ": it ends at " + h.getUntil() + ", not after it "
                        + "begins at " + h.getSince() + "."));
            }
            expected = RetentionHolds.render(h);
        } else {
            DeletionClass c = (DeletionClass) rule;
            period(rule, "term", c.getTerm(), findings);
            start(rule, c.getStart(), findings);
            if (c.getStart() != null && !(c.getStart() instanceof ValueTime)
                    && !(c.getStart() instanceof EventTime e && e.getFeature() == null)) {
                findings.add(finding(Severity.ERROR, "O2", rule, ": the start of a deletion class is abstract — the "
                        + "time of a value, or an event without the feature that holds its time."));
            }
            expected = DeletionClasses.render(c);
        }
        if (!LifecycleRules.hasBody(rule, expected)) {
            findings.add(finding(Severity.WARNING, "O9", rule, ": the body is not the text of the rule '" + expected
                    + "' in " + LifecycleRules.LANGUAGE + "."));
        }
    }

    private static String validity(LegalSource source) {
        String from = source.getValidFrom() == null ? "" : "from " + source.getValidFrom();
        String to = source.getValidTo() == null ? "" : "until " + source.getValidTo();
        return from.isEmpty() || to.isEmpty() ? "valid " + from + to : "valid " + from + " " + to;
    }

    private static void period(LifecycleRule rule, String what, String literal, List<Finding> findings) {
        if (literal == null || !Periods.isValid(literal)) {
            findings.add(finding(Severity.ERROR, "O2", rule, ": the " + what + " '" + literal
                    + "' is no ISO-8601 period (P10Y, P6M, P90D)."));
        }
    }

    private static void start(LifecycleRule rule, PeriodStart start, List<Finding> findings) {
        if (start == null) {
            findings.add(finding(Severity.ERROR, "O2", rule, " has no start."));
            return;
        }
        if (start.getOffset() != null && !Periods.isValid(start.getOffset())) {
            findings.add(finding(Severity.ERROR, "O2", rule, ": the offset '" + start.getOffset()
                    + "' is no ISO-8601 period."));
        }
        if (start.getZone() != null) {
            try {
                ZoneId.of(start.getZone());
            } catch (DateTimeException e) {
                findings.add(finding(Severity.ERROR, "O2", rule, ": the zone '" + start.getZone()
                        + "' is no time zone."));
            }
        } else if (start.getAlignment() != Alignment.NONE) {
            findings.add(finding(Severity.ERROR, "O2", rule, ": the start is aligned without a zone."));
        }
    }

    /**
     * The data a rule reaches through the elements it names: a class stands for its rows, or for the
     * values of its features, and for the classes it holds; a package for the classes in it, at any
     * depth; anything else for itself. A retention obligation of rows that keeps the data unchanged
     * reaches the values of its features as well.
     */
    static Set<ModelElement> data(LifecycleRule rule) {
        Set<ModelElement> out = new LinkedHashSet<>();
        for (ModelElement e : rule.getConstrainedElement()) {
            if (!e.eIsProxy() && !(e instanceof Constraint)) {
                out.addAll(data(e, rule.getScope()));
                if (rule instanceof RetentionObligation r && r.isImmutable() && r.getScope() == DataScope.ROW) {
                    out.addAll(data(e, DataScope.VALUE));
                }
            }
        }
        return out;
    }

    private static Set<ModelElement> data(ModelElement element, DataScope scope) {
        Set<ModelElement> out = new LinkedHashSet<>();
        if (element instanceof Classifier c) {
            if (scope == DataScope.ROW) {
                out.add(c);
            } else {
                c.getFeature().stream().filter(StructuralFeature.class::isInstance).map(ModelElement.class::cast)
                        .forEach(out::add);
            }
            for (EObject child : c.eContents()) {
                if (CorePackage.Literals.CLASS.isInstance(child)) {
                    out.addAll(data((ModelElement) child, scope));
                }
            }
        } else if (CorePackage.Literals.PACKAGE.isInstance(element)) {
            for (EObject child : element.eContents()) {
                if (CorePackage.Literals.CLASS.isInstance(child) || CorePackage.Literals.PACKAGE.isInstance(child)) {
                    out.addAll(data((ModelElement) child, scope));
                }
            }
        }
        if (out.isEmpty()) {
            out.add(element);
        }
        return out;
    }

    /** O3, O6: a retention obligation at one element of its data. */
    private static void retention(RetentionObligation r, ModelElement data, Gathered gathered) {
        if (!Periods.isValid(r.getMinimum())) {
            return;
        }
        Retention.Terms terms = Compliance.terms(data);
        for (Step step : relevant(Retention.timeline(Retention.effective(data), terms), r)) {
            Optional<String> after = terms.of(step);
            if (!(step.stage() instanceof DestructiveStage) || after.isEmpty() || !Periods.isValid(after.get())) {
                continue;
            }
            if (!PeriodStarts.same(r.getStart(), step.start())) {
                gathered.add(Severity.WARNING, "O6", r, step.stage(), ": " + stage(step) + " starts elsewhere; compare "
                        + "them with instants", data);
                continue;
            }
            Periods.Comparison c = Periods.compare(after.get(), r.getMinimum());
            if (c.sign() < 0) {
                gathered.add(Severity.ERROR, "O3", r, step.stage(), ": " + stage(step) + " is due after " + after.get()
                        + ", before the data is kept for " + r.getMinimum(), data);
            } else if (!c.exact()) {
                gathered.add(Severity.WARNING, "O6", r, step.stage(), ": " + after.get() + " and " + r.getMinimum()
                        + " compare only approximately", data);
            }
        }
    }

    /** O4 to O6: a deletion obligation at one element of its data, beside the retention obligations there. */
    private static void deletion(DeletionObligation d, ModelElement data, Gathered gathered) {
        if (!Periods.isValid(d.getMaximum())) {
            return;
        }
        Retention.Terms terms = Compliance.terms(data);
        List<Step> mine = relevant(Retention.timeline(Retention.effective(data), terms), d);
        Optional<Step> terminal = mine.stream()
                .filter(s -> s.stage() instanceof TerminalStage && terms.of(s).filter(Periods::isValid).isPresent())
                .findFirst();
        RetentionObligation longest = null;
        RetentionObligation elsewhere = null;
        for (RetentionObligation r : Compliance.retentions(data)) {
            if (!Periods.isValid(r.getMinimum()) || Periods.compare(r.getMinimum(), d.getMaximum()).sign() <= 0) {
                continue;
            }
            if (!PeriodStarts.same(r.getStart(), d.getStart())) {
                elsewhere = elsewhere == null ? r : elsewhere;
            } else if (longest == null || Periods.compare(r.getMinimum(), longest.getMinimum()).sign() > 0) {
                longest = r;
            }
        }
        if (terminal.isEmpty()) {
            gathered.add(Severity.ERROR, longest == null ? "O4" : "O5", d, null, longest == null
                    ? ": no stage deletes the data within " + d.getMaximum()
                    : ": no stage deletes the data when " + longest.getName() + " ends", data);
        }
        if (longest != null) {
            restricted(d, longest, mine, terms, data, gathered);
            if (terminal.isPresent()) {
                due(d, terminal.get(), terms.of(terminal.get()).get(), longest, data, gathered);
            }
        } else if (elsewhere != null) {
            gathered.add(Severity.WARNING, "O6", d, null, ": " + elsewhere.getName() + " starts elsewhere and may keep "
                    + "the data longer than " + d.getMaximum() + "; compare them with instants", data);
        } else {
            terminal.ifPresent(t -> due(d, t, terms.of(t).get(), null, data, gathered));
        }
        if (terminal.isPresent() && !(terminal.get().stage() instanceof Delete) && !d.isAnonymizationSatisfies()) {
            gathered.add(Severity.ERROR, "O4", d, terminal.get().stage(), ": " + stage(terminal.get()) + " does not "
                    + "delete the data, and making it anonymous does not satisfy the obligation", data);
        }
    }

    /** O5, O6: a restriction by the end of a deletion obligation that a longer retention obligation outlasts. */
    private static void restricted(DeletionObligation d, RetentionObligation longest, List<Step> steps,
            Retention.Terms terms, ModelElement data, Gathered gathered) {
        List<Step> restrictions = steps.stream().filter(s -> s.stage() instanceof Restrict
                && terms.of(s).filter(Periods::isValid).isPresent()).toList();
        boolean inTime = restrictions.stream().anyMatch(s -> PeriodStarts.same(s.start(), d.getStart())
                && Periods.compare(terms.of(s).get(), d.getMaximum()).sign() <= 0);
        if (inTime) {
            return;
        }
        Optional<Step> other = restrictions.stream().filter(s -> !PeriodStarts.same(s.start(), d.getStart()))
                .findFirst();
        if (other.isPresent()) {
            gathered.add(Severity.WARNING, "O6", d, other.get().stage(), ": " + stage(other.get()) + " starts "
                    + "elsewhere; compare them with instants", data);
        } else {
            gathered.add(Severity.ERROR, "O5", d, null, ": " + longest.getName() + " keeps the data "
                    + longest.getMinimum() + ", longer than " + d.getMaximum() + "; a stage restricts it by "
                    + d.getMaximum(), data);
        }
    }

    /**
     * O4 to O6: the terminal stage of the data is due by the end of the deletion obligation, or —
     * where {@code longest} keeps the data longer — when that ends.
     */
    private static void due(DeletionObligation d, Step terminal, String after, RetentionObligation longest,
            ModelElement data, Gathered gathered) {
        PeriodStart start = longest == null ? d.getStart() : longest.getStart();
        String until = longest == null ? d.getMaximum() : longest.getMinimum();
        if (!PeriodStarts.same(terminal.start(), start)) {
            gathered.add(Severity.WARNING, "O6", d, terminal.stage(), ": " + stage(terminal) + " starts elsewhere; "
                    + "compare them with instants", data);
            return;
        }
        Periods.Comparison c = Periods.compare(after, until);
        if (c.sign() > 0) {
            gathered.add(Severity.ERROR, longest == null ? "O4" : "O5", d, terminal.stage(), ": " + stage(terminal)
                    + " deletes after " + after + (longest == null ? ", not within " + until
                            : ", not when " + longest.getName() + " ends after " + until), data);
        } else if (!c.exact()) {
            gathered.add(Severity.WARNING, "O6", d, terminal.stage(), ": " + after + " and " + until
                    + " compare only approximately", data);
        }
    }

    /** O7: the last stage of a schedule and the deletion class of each element of its data. */
    private static void deletionClass(RetentionSchedule schedule, Gathered gathered) {
        Optional<Stage> last = schedule.getStage().stream().filter(TerminalStage.class::isInstance).findFirst();
        if (last.isEmpty()) {
            return;
        }
        Stage stage = last.get();
        PeriodStart start = stage.getStart() != null ? stage.getStart() : schedule.getStart();
        for (ModelElement data : data(schedule)) {
            Optional<DeletionClass> c = DeletionClasses.effective(data, schedule.getScope());
            if (stage.getAfter() != null && c.isPresent()) {
                gathered.add(Severity.ERROR, "O7", schedule, stage, ": stage " + stage.getName() + " gives "
                        + stage.getAfter() + " and the deletion class " + c.get().getName() + " gives "
                        + c.get().getTerm() + "; one of them gives the term", data);
            } else if (stage.getAfter() == null && c.isEmpty()) {
                gathered.add(Severity.ERROR, "O7", schedule, stage, ": stage " + stage.getName() + " has no period, "
                        + "and no deletion class gives one", data);
            } else if (stage.getAfter() == null) {
                String problem = binding(start, c.get().getStart());
                if (problem != null) {
                    gathered.add(Severity.ERROR, "O7", schedule, stage, ": stage " + stage.getName()
                            + " takes its term from " + c.get().getName() + ", but " + problem, data);
                }
            }
        }
    }

    /**
     * Why a concrete start does not bind the abstract start of a deletion class — the same event (or
     * both the time of the value), aligned, offset and zoned alike, an event bound to the feature
     * that holds its time — or {@code null} when it does.
     */
    private static String binding(PeriodStart concrete, PeriodStart abstractStart) {
        if (concrete == null || abstractStart == null) {
            return "the starts are not both given";
        }
        boolean kind = concrete instanceof EventTime x && abstractStart instanceof EventTime y
                ? x.getEvent() == y.getEvent()
                : concrete instanceof ValueTime && abstractStart instanceof ValueTime;
        if (!kind || concrete.getAlignment() != abstractStart.getAlignment()
                || !Objects.equals(concrete.getOffset(), abstractStart.getOffset())
                || !Objects.equals(concrete.getZone(), abstractStart.getZone())) {
            return "starts from " + PeriodStarts.render(concrete) + ", not from " + PeriodStarts.render(abstractStart);
        }
        if (concrete instanceof EventTime x && x.getFeature() == null) {
            return "names no feature that holds the time of " + PeriodStarts.render(x);
        }
        return null;
    }

    /**
     * The steps that touch the data of a duty: of rows for rows, of values and of rows for values;
     * every step for data that must stay unchanged.
     */
    private static List<Step> relevant(List<Step> steps, Obligation duty) {
        boolean every = duty.getScope() == DataScope.VALUE || duty instanceof RetentionObligation r && r.isImmutable();
        return steps.stream().filter(s -> every || s.schedule().getScope() == duty.getScope()).toList();
    }

    private static String stage(Step step) {
        return "stage " + step.stage().getName() + " of " + step.schedule().getName();
    }

    /** Findings of O3 to O7 gathered by what they say, with every element of the data they concern. */
    private static final class Gathered {

        private record Key(String code, LifecycleRule rule, Stage stage, String text) {
        }

        private record Entry(Severity severity, Set<String> paths) {
        }

        private final Map<Key, Entry> entries = new LinkedHashMap<>();

        void add(Severity severity, String code, LifecycleRule rule, Stage stage, String text, ModelElement data) {
            entries.computeIfAbsent(new Key(code, rule, stage, text), k -> new Entry(severity, new LinkedHashSet<>()))
                    .paths().add(path(data));
        }

        void into(List<Finding> findings) {
            entries.forEach((k, e) -> findings.add(finding(e.severity(), k.code(), k.rule(), k.text() + " ("
                    + String.join(", ", e.paths()) + ").")));
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

    private static Finding finding(Severity severity, String code, LifecycleRule rule, String text) {
        return new Finding(severity, code, rule, rule.eClass().getName() + " '" + rule.getName() + "'" + text);
    }

    private static String path(EObject element) {
        return LifecycleRules.path(element);
    }
}
