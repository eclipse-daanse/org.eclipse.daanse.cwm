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

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.Obligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.ObligationsFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;

/**
 * Creates obligations: in the {@code ownedElement} of {@code owner}, named, governing the given
 * elements, counted from {@code start}, the body set to the obligation's text.
 */
public final class Obligations {

    private static final ObligationsFactory F = ObligationsFactory.eINSTANCE;

    private Obligations() {
    }

    /** The data must be kept for at least {@code minimum} (a {@code Period} literal) from {@code start}. */
    public static RetentionObligation retain(Namespace owner, String name, String minimum, PeriodStart start,
            ModelElement... elements) {
        Periods.parse(minimum);
        RetentionObligation obligation = F.createRetentionObligation();
        obligation.setMinimum(minimum);
        obligation.setStart(Objects.requireNonNull(start, "start"));
        return refreshed(LifecycleRules.attach(owner, name, obligation, elements));
    }

    /** The data must be gone within {@code maximum} (a {@code Period} literal) from {@code start}. */
    public static DeletionObligation delete(Namespace owner, String name, String maximum, PeriodStart start,
            ModelElement... elements) {
        Periods.parse(maximum);
        DeletionObligation obligation = F.createDeletionObligation();
        obligation.setMaximum(maximum);
        obligation.setStart(Objects.requireNonNull(start, "start"));
        return refreshed(LifecycleRules.attach(owner, name, obligation, elements));
    }

    /** The obligation is about the rows of the governed classifiers, not the values of their features. */
    public static <O extends Obligation> O onRows(O obligation) {
        obligation.setScope(DataScope.ROW);
        return obligation;
    }

    /** The data must also stay unchanged while it is kept. */
    public static RetentionObligation immutable(RetentionObligation obligation) {
        obligation.setImmutable(true);
        return refreshed(obligation);
    }

    /** Making the data anonymous fulfils the duty to delete it. */
    public static DeletionObligation anonymizationSatisfies(DeletionObligation obligation) {
        obligation.setAnonymizationSatisfies(true);
        return refreshed(obligation);
    }

    /**
     * The text of an obligation in the language {@value LifecycleRules#LANGUAGE}, e.g.
     * {@code keep at least P10Y from issuedOn, end of year in Europe/Berlin; immutable}.
     */
    public static String render(Obligation obligation) {
        String from = obligation.getStart() == null ? "" : " from " + PeriodStarts.render(obligation.getStart());
        if (obligation instanceof RetentionObligation r) {
            return "keep at least " + r.getMinimum() + from + (r.isImmutable() ? "; immutable" : "");
        }
        DeletionObligation d = (DeletionObligation) obligation;
        return "delete within " + d.getMaximum() + from + (d.isAnonymizationSatisfies() ? "; anonymization satisfies" : "");
    }

    /** Sets the {@code body} of an obligation to its {@linkplain #render text}. */
    public static <O extends Obligation> O refreshed(O obligation) {
        LifecycleRules.setBody(obligation, render(obligation));
        return obligation;
    }
}
