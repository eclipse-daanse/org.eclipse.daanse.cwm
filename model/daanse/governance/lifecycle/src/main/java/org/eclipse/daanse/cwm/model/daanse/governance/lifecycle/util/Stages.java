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

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Aggregate;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.AggregateFunction;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Delete;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Notify;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Restrict;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Review;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Tier;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Transfer;

/**
 * Adds stages to a retention schedule, in the order they follow: each named, due after a
 * {@code Period} literal from the start ({@code null} where another rule gives it), the body of the
 * schedule kept in step.
 */
public final class Stages {

    private static final LifecycleFactory F = LifecycleFactory.eINSTANCE;

    private Stages() {
    }

    /** Adds {@code stage}, of any kind — also of a model built on this one — as the last stage. */
    public static <T extends Stage> T add(RetentionSchedule schedule, String name, String after, T stage) {
        Objects.requireNonNull(schedule, "schedule");
        if (after != null) {
            Periods.parse(after);
        }
        stage.setName(Objects.requireNonNull(name, "name"));
        stage.setAfter(after);
        schedule.getStage().add(stage);
        RetentionSchedules.refreshed(schedule);
        return stage;
    }

    /** The data is deleted. */
    public static Delete delete(RetentionSchedule schedule, String name, String after) {
        return add(schedule, name, after, F.createDelete());
    }

    /** The data is kept but restricted. */
    public static Restrict restrict(RetentionSchedule schedule, String name, String after) {
        return add(schedule, name, after, F.createRestrict());
    }

    /** The data moves to {@code tier}, compressed or not. */
    public static Tier tier(RetentionSchedule schedule, String name, String after, String tier, boolean compressed) {
        Tier stage = F.createTier();
        stage.setTier(Objects.requireNonNull(tier, "tier"));
        stage.setCompressed(compressed);
        return add(schedule, name, after, stage);
    }

    /** The values are summarized per {@code interval} with {@code function}, into {@code target} where given. */
    public static Aggregate aggregate(RetentionSchedule schedule, String name, String after, String interval,
            AggregateFunction function, Classifier target) {
        Periods.parse(interval);
        Aggregate stage = F.createAggregate();
        stage.setInterval(interval);
        stage.setFunction(Objects.requireNonNull(function, "function"));
        stage.setTarget(target);
        return add(schedule, name, after, stage);
    }

    /** The data is handed over to {@code target}. */
    public static Transfer transfer(RetentionSchedule schedule, String name, String after, Namespace target) {
        Transfer stage = F.createTransfer();
        stage.setTarget(Objects.requireNonNull(target, "target"));
        return add(schedule, name, after, stage);
    }

    /** {@code reviewer} reviews the data. */
    public static Review review(RetentionSchedule schedule, String name, String after, ResponsibleParty reviewer) {
        Review stage = F.createReview();
        stage.setReviewer(Objects.requireNonNull(reviewer, "reviewer"));
        return add(schedule, name, after, stage);
    }

    /** {@code party} is told. */
    public static Notify notify(RetentionSchedule schedule, String name, String after, ResponsibleParty party) {
        Notify stage = F.createNotify();
        stage.setParty(Objects.requireNonNull(party, "party"));
        return add(schedule, name, after, stage);
    }

    /** Gives {@code stage} a start of its own. */
    public static <T extends Stage> T from(T stage, PeriodStart start) {
        stage.setStart(Objects.requireNonNull(start, "start"));
        if (stage.eContainer() instanceof RetentionSchedule schedule) {
            RetentionSchedules.refreshed(schedule);
        }
        return stage;
    }
}
