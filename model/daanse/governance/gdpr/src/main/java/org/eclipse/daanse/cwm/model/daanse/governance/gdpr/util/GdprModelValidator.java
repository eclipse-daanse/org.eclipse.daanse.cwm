/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPackage;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Treatment;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PrivacyFieldRating;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataErasure;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessing;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataTransmission;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.emf.ecore.EObject;

/**
 * Checks the processings, erasures, transmissions and privacy ratings in a model; the findings are
 * those of the lifecycle models ({@link Finding}, {@link Severity}). An erasure is a deletion
 * obligation as well: {@code ObligationsModelValidator} checks it against the schedules of its data.
 *
 * <ul>
 * <li>{@code G1} — an erasure governs the data its processing processes: its data classes, or
 * features of them.</li>
 * <li>{@code G2} (warning) — a field rated with a special category of personal data (Art. 9 and 10
 * GDPR) is treated as insensitive by a de-identification policy.</li>
 * <li>{@code G3} — the policy of a transmission is for its recipient: the audience of the policy,
 * where it names one, is the recipient.</li>
 * <li>{@code G4} (warning) — a processing that no legal source names.</li>
 * </ul>
 */
public final class GdprModelValidator {

    private GdprModelValidator() {
    }

    /** All findings in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        for (EObject o : all) {
            if (o instanceof DataErasure e) {
                erasure(e, findings);
            } else if (o instanceof PrivacyFieldRating r) {
                special(r, findings);
            } else if (o instanceof DataTransmission t) {
                transmission(t, findings);
            } else if (o instanceof DataProcessing p && LegalSources.of(p).isEmpty()) {
                findings.add(new Finding(Severity.WARNING, "G4", p, "processing '" + p.getName()
                        + "' names no legal source: no LegalSource names it."));
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void erasure(DataErasure erasure, List<Finding> findings) {
        DataProcessing processing = DataErasures.processing(erasure);
        if (processing == null) {
            return;
        }
        for (ModelElement e : erasure.getConstrainedElement()) {
            if (e.eIsProxy()) {
                continue;
            }
            ModelElement dataClass = e instanceof Feature f && f.getOwner() != null ? f.getOwner() : e;
            if (!processing.getDataClasses().contains(dataClass)) {
                findings.add(new Finding(Severity.ERROR, "G1", erasure, "erasure '" + erasure.getName() + "' governs "
                        + LifecycleRules.path(e) + ", which its processing '" + processing.getName()
                        + "' does not process."));
            }
        }
    }

    private static void special(PrivacyFieldRating rating, List<Finding> findings) {
        StructuralFeature field = rating.getDataField();
        if (field == null || field.eIsProxy() || !PrivacyRatings.special(rating.getPersonalDataCategory())) {
            return;
        }
        for (Treatment t : InverseReferences.referencingList(field, DeidentificationPackage.Literals.TREATMENT__FEATURE,
                Treatment.class)) {
            if (t.getRole() == AttributeRole.INSENSITIVE && t.eContainer() instanceof DeidentificationPolicy policy) {
                findings.add(new Finding(Severity.WARNING, "G2", policy, "policy '" + policy.getName() + "' treats "
                        + LifecycleRules.path(field) + " as insensitive, but it holds "
                        + rating.getPersonalDataCategory().getLiteral() + ", a special category of personal data."));
            }
        }
    }

    private static void transmission(DataTransmission transmission, List<Finding> findings) {
        DeidentificationPolicy policy = transmission.getPolicy();
        if (policy == null || policy.eIsProxy() || policy.getAudience() == null
                || transmission.getContactRecipient() == null
                || policy.getAudience() == transmission.getContactRecipient()) {
            return;
        }
        findings.add(new Finding(Severity.ERROR, "G3", transmission, "transmission '" + transmission.getName()
                + "' goes to " + transmission.getContactRecipient().getName() + ", but its policy '" + policy.getName()
                + "' is for " + policy.getAudience().getName() + "."));
    }
}
