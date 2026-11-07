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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Aggregate;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.CountLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Delete;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecyclePackage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Limit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Notify;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Restrict;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Review;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.SizeLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Tier;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Transfer;
import org.eclipse.emf.ecore.EReference;

/**
 * Creates retention schedules: in the {@code ownedElement} of {@code owner}, named, constraining
 * the given elements, its {@code body} set to the schedule's text; {@link Stages} and
 * {@link Limits} add to it, the modifiers here keep the body in step. {@link #effective} is the
 * schedule of a scope that applies to an element.
 */
public final class RetentionSchedules {

    /** The namespace URIs of the CWM 1.1 packages: references every model element has. */
    private static final String CWM = "http://www.omg.org/spec/CWM/";

    private static final LifecycleFactory F = LifecycleFactory.eINSTANCE;

    private RetentionSchedules() {
    }

    /** A schedule of values whose periods start at {@code start}; its stages and limits follow. */
    public static RetentionSchedule create(Namespace owner, String name, PeriodStart start, ModelElement... elements) {
        return create(owner, name, DataScope.VALUE, start, elements);
    }

    /** A schedule of {@code scope} whose periods start at {@code start}; its stages and limits follow. */
    public static RetentionSchedule create(Namespace owner, String name, DataScope scope, PeriodStart start,
            ModelElement... elements) {
        RetentionSchedule schedule = F.createRetentionSchedule();
        schedule.setScope(Objects.requireNonNull(scope, "scope"));
        schedule.setStart(Objects.requireNonNull(start, "start"));
        return refreshed(LifecycleRules.attach(owner, name, schedule, elements));
    }

    /** Deletes the values of the governed elements {@code after} their time (a {@code Period} literal). */
    public static RetentionSchedule values(Namespace owner, String name, String after, ModelElement... elements) {
        RetentionSchedule schedule = create(owner, name, PeriodStarts.valueTime(), elements);
        Stages.delete(schedule, "delete", after);
        return schedule;
    }

    /** Deletes the rows of the governed classifiers {@code after} {@code start}. */
    public static RetentionSchedule rows(Namespace owner, String name, String after, PeriodStart start,
            ModelElement... classifiers) {
        RetentionSchedule schedule = create(owner, name, DataScope.ROW, start, classifiers);
        Stages.delete(schedule, "delete", after);
        return schedule;
    }

    /** Keeps the newest {@code keep} values of the governed elements, whatever their age. */
    public static RetentionSchedule keeping(Namespace owner, String name, long keep, ModelElement... elements) {
        RetentionSchedule schedule = refreshed(LifecycleRules.attach(owner, name, F.createRetentionSchedule(), elements));
        Limits.count(schedule, keep);
        return schedule;
    }

    /** Sets where the periods of the stages start. */
    public static RetentionSchedule startAt(RetentionSchedule schedule, PeriodStart start) {
        schedule.setStart(Objects.requireNonNull(start, "start"));
        return refreshed(schedule);
    }

    /** Runs the cleanup every {@code interval}. */
    public static RetentionSchedule cleanupEvery(RetentionSchedule schedule, Duration interval) {
        schedule.setCleanupInterval(Durations.format(interval));
        return refreshed(schedule);
    }

    /** Lets a cleanup run delete at most {@code max}. */
    public static RetentionSchedule maxDelete(RetentionSchedule schedule, long max) {
        schedule.setMaxDelete(max);
        return refreshed(schedule);
    }

    /** The schedule of {@code scope} that applies to {@code element}: the nearest one along its path. */
    public static Optional<RetentionSchedule> effective(ModelElement element, DataScope scope) {
        return LifecycleRules.nearest(element, RetentionSchedule.class, scope);
    }

    /**
     * The text of a schedule in the language {@value LifecycleRules#LANGUAGE}, e.g.
     * {@code from the time of the value: delete after P90D; cleanup every P1D} or
     * {@code from document_date, end of year in Europe/Berlin: restrict after P6Y, delete after P10Y}.
     */
    public static String render(RetentionSchedule schedule) {
        List<String> parts = new ArrayList<>();
        if (!schedule.getStage().isEmpty() || schedule.getStart() != null) {
            List<String> stages = schedule.getStage().stream().map(RetentionSchedules::render).toList();
            String from = schedule.getStart() == null ? "" : "from " + PeriodStarts.render(schedule.getStart());
            parts.add(from + (from.isEmpty() || stages.isEmpty() ? "" : ": ") + String.join(", ", stages));
        }
        for (Limit limit : schedule.getLimit()) {
            parts.add(render(limit));
        }
        if (schedule.getCleanupInterval() != null) {
            parts.add("cleanup every " + schedule.getCleanupInterval());
        }
        if (schedule.isSetMaxDelete()) {
            parts.add("at most " + schedule.getMaxDelete() + " deletions per cleanup");
        }
        return String.join("; ", parts);
    }

    /** The text of a stage, e.g. {@code tier cold compressed after P30D}. */
    public static String render(Stage stage) {
        StringBuilder sb = new StringBuilder(switch (stage) {
            case Delete d -> "delete";
            case Restrict r -> "restrict";
            case Tier t -> "tier " + t.getTier() + (t.isCompressed() ? " compressed" : "");
            case Aggregate a -> "aggregate per " + a.getInterval() + " as " + a.getFunction().getLiteral()
                    + (a.getTarget() == null ? "" : " into " + a.getTarget().getName());
            case Transfer t -> "transfer to " + (t.getTarget() == null ? "?" : t.getTarget().getName());
            case Review r -> "review by " + (r.getReviewer() == null ? "?" : r.getReviewer().getName());
            case Notify n -> "notify " + (n.getParty() == null ? "?" : n.getParty().getName());
            default -> other(stage);
        });
        if (stage.getAfter() != null) {
            sb.append(" after ").append(stage.getAfter());
        }
        if (stage.getStart() != null) {
            sb.append(" from ").append(PeriodStarts.render(stage.getStart()));
        }
        return sb.toString();
    }

    /**
     * A stage of a model built on this one: its kind in words and the names of the elements it
     * refers to, e.g. {@code anonymize patients-anonymous} or {@code destroy linkage trust-centre}.
     */
    private static String other(Stage stage) {
        StringBuilder sb = new StringBuilder(
                stage.eClass().getName().replaceAll("(?<=[a-z])(?=[A-Z])", " ").toLowerCase(Locale.ROOT));
        for (EReference r : stage.eClass().getEAllReferences()) {
            String nsUri = r.getEContainingClass().getEPackage().getNsURI();
            if (!r.isMany() && !r.isContainment() && !r.isContainer() && !nsUri.startsWith(CWM)
                    && r.getEContainingClass().getEPackage() != LifecyclePackage.eINSTANCE
                    && stage.eGet(r) instanceof ModelElement target && target.getName() != null) {
                sb.append(' ').append(target.getName());
            }
        }
        return sb.toString();
    }

    private static String render(Limit limit) {
        if (limit instanceof CountLimit c) {
            List<String> keys = c.getKey().stream().map(StructuralFeature::getName).toList();
            return "keep the newest " + c.getKeep() + (keys.isEmpty() ? "" : " per " + String.join(", ", keys));
        }
        if (limit instanceof SizeLimit s) {
            return "keep at most " + s.getBytes() + " bytes";
        }
        return limit.eClass().getName();
    }

    /** Sets the {@code body} of a schedule to its {@linkplain #render text}. */
    public static RetentionSchedule refreshed(RetentionSchedule schedule) {
        LifecycleRules.setBody(schedule, render(schedule));
        return schedule;
    }
}
