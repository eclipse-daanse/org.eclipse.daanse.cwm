/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.transparency.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyAssessment;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyClassRating;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyFieldRating;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;

/**
 * Checks the transparency assessments and ratings in a model; the findings are those of the
 * lifecycle models ({@link Finding}, {@link Severity}).
 *
 * <ul>
 * <li>{@code T1} (warning) — a class published without a request is kept for less than ten years:
 * no retention obligation of at least {@value #TEN_YEARS} reaches its data (§ 7 (8) ThuerTG).</li>
 * <li>{@code T2} — the policy of a rating governs the rated data class.</li>
 * <li>{@code T3} (warning) — an element that does not resolve.</li>
 * </ul>
 */
public final class TransparencyModelValidator {

    /** How long information published without a request is kept at least. */
    public static final String TEN_YEARS = "P10Y";

    private TransparencyModelValidator() {
    }

    /** All findings in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        for (EObject o : all) {
            if (o instanceof TransparencyClassRating r) {
                rating(r, findings);
            } else if (o instanceof TransparencyFieldRating f) {
                proxy(f, f.getDataField(), "field", findings);
            } else if (o instanceof TransparencyAssessment a) {
                proxy(a, a.getContactResponsible(), "responsible party", findings);
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void rating(TransparencyClassRating rating, List<Finding> findings) {
        ModelElement dataClass = rating.getDataClass();
        if (proxy(rating, dataClass, "data class", findings) || proxy(rating, rating.getPolicy(), "policy", findings)
                || dataClass == null) {
            return;
        }
        if (TransparencyRatings.proactive(rating.getPublicationMode()) && Compliance.retentions(dataClass).stream()
                .map(RetentionObligation::getMinimum).filter(Periods::isValid)
                .noneMatch(m -> Periods.compare(m, TEN_YEARS).sign() >= 0)) {
            findings.add(new Finding(Severity.WARNING, "T1", rating, "rating '" + rating.getName() + "' publishes "
                    + LifecycleRules.path(dataClass) + " without a request, but no retention obligation keeps it "
                    + TEN_YEARS + "."));
        }
        if (rating.getPolicy() != null && !rating.getPolicy().getConstrainedElement().contains(dataClass)) {
            findings.add(new Finding(Severity.ERROR, "T2", rating, "rating '" + rating.getName()
                    + "' names the policy '" + rating.getPolicy().getName() + "', which does not govern "
                    + LifecycleRules.path(dataClass) + "."));
        }
    }

    private static boolean proxy(EObject owner, EObject target, String what, List<Finding> findings) {
        if (target != null && target.eIsProxy()) {
            findings.add(new Finding(Severity.WARNING, "T3", owner, owner.eClass().getName() + ": the " + what + " "
                    + ((InternalEObject) target).eProxyURI() + " does not resolve."));
            return true;
        }
        return false;
    }
}
