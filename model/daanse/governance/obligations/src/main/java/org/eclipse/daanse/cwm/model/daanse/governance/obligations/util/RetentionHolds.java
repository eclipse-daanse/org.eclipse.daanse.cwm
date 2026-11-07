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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.ObligationsFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;

/**
 * Creates retention holds and says which apply: every hold along the path of an element applies,
 * and while one lasts the destructive stages of the element's schedules are suspended.
 */
public final class RetentionHolds {

    private RetentionHolds() {
    }

    /**
     * A hold from {@code since} on, for {@code reason}, on the given elements. Its times are written
     * to the whole second, in one form, so that they also order as text.
     */
    public static RetentionHold create(Namespace owner, String name, String reason, Instant since,
            ModelElement... elements) {
        RetentionHold hold = ObligationsFactory.eINSTANCE.createRetentionHold();
        hold.setReason(Objects.requireNonNull(reason, "reason"));
        hold.setSince(Objects.requireNonNull(since, "since").truncatedTo(ChronoUnit.SECONDS).toString());
        return refreshed(LifecycleRules.attach(owner, name, hold, elements));
    }

    /** Releases the hold at {@code until}. */
    public static RetentionHold release(RetentionHold hold, Instant until) {
        hold.setUntil(Objects.requireNonNull(until, "until").truncatedTo(ChronoUnit.SECONDS).toString());
        return refreshed(hold);
    }

    /** Names who ordered the hold. */
    public static RetentionHold holder(RetentionHold hold, ResponsibleParty holder) {
        hold.setHolder(Objects.requireNonNull(holder, "holder"));
        return hold;
    }

    /** Whether the hold applies at {@code at}: from {@code since} (inclusive) until {@code until} (exclusive). */
    public static boolean active(RetentionHold hold, Instant at) {
        Instant since = LegalSources.instant(hold.getSince());
        Instant until = LegalSources.instant(hold.getUntil());
        return since != null && !at.isBefore(since) && (until == null || at.isBefore(until));
    }

    /** Every hold along the path of {@code element}. */
    public static List<RetentionHold> of(ModelElement element) {
        return LifecycleRules.all(element, RetentionHold.class);
    }

    /** The holds along the path of {@code element} that apply at {@code at}. */
    public static List<RetentionHold> active(ModelElement element, Instant at) {
        return of(element).stream().filter(h -> active(h, at)).toList();
    }

    /** The suspension the holds of {@code element} make: destructive stages wait while one applies. */
    public static Retention.Suspension suspension(ModelElement element) {
        List<RetentionHold> holds = of(element);
        return now -> holds.stream().anyMatch(h -> active(h, now));
    }

    /** The text of a hold, e.g. {@code hold since 2026-11-01T00:00:00Z: lawsuit 4711}. */
    public static String render(RetentionHold hold) {
        return "hold since " + hold.getSince() + (hold.getUntil() == null ? "" : " until " + hold.getUntil()) + ": "
                + hold.getReason();
    }

    /** Sets the {@code body} of a hold to its {@linkplain #render text}. */
    public static RetentionHold refreshed(RetentionHold hold) {
        LifecycleRules.setBody(hold, render(hold));
        return hold;
    }
}
