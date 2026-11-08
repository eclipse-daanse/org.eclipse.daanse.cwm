/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DifferentialPrivacy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DiversityVariant;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.KAnonymity;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.LDiversity;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PrivacyModel;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.TCloseness;

/** Adds the privacy models a policy's result meets; the body of the policy is kept in step. */
public final class PrivacyModels {

    private static final DeidentificationFactory F = DeidentificationFactory.eINSTANCE;

    private PrivacyModels() {
    }

    /** Every combination of quasi-identifying values is shared by at least {@code k} rows. */
    public static KAnonymity kAnonymity(DeidentificationPolicy policy, long k) {
        KAnonymity m = F.createKAnonymity();
        m.setK(k);
        return add(policy, m);
    }

    /** Every group of rows alike holds at least {@code l} well-represented values of {@code sensitive}. */
    public static LDiversity lDiversity(DeidentificationPolicy policy, long l, DiversityVariant variant,
            StructuralFeature sensitive) {
        LDiversity m = F.createLDiversity();
        m.setL(l);
        m.setVariant(Objects.requireNonNull(variant, "variant"));
        m.setSensitive(Objects.requireNonNull(sensitive, "sensitive"));
        return add(policy, m);
    }

    /** In every group of rows alike, the distribution of {@code sensitive} is within {@code t} of the whole. */
    public static TCloseness tCloseness(DeidentificationPolicy policy, double t, StructuralFeature sensitive) {
        TCloseness m = F.createTCloseness();
        m.setT(Policies.decimal(t));
        m.setSensitive(Objects.requireNonNull(sensitive, "sensitive"));
        return add(policy, m);
    }

    /** The result is differentially private with budget {@code epsilon} and {@code delta} ({@code null}: pure). */
    public static DifferentialPrivacy differentialPrivacy(DeidentificationPolicy policy, double epsilon,
            Double delta) {
        DifferentialPrivacy m = F.createDifferentialPrivacy();
        m.setEpsilon(Policies.decimal(epsilon));
        m.setDelta(delta == null ? null : Policies.decimal(delta));
        return add(policy, m);
    }

    private static <M extends PrivacyModel> M add(DeidentificationPolicy policy, M model) {
        policy.getModel().add(model);
        Policies.refreshed(policy);
        return model;
    }

    /** The text of a privacy model, e.g. {@code distinct 3-diversity of diagnosis}. */
    public static String render(PrivacyModel model) {
        return switch (model) {
            case KAnonymity k -> k.getK() + "-anonymity";
            case LDiversity l -> (l.getVariant() == null ? "" : l.getVariant().getLiteral() + " ") + l.getL()
                    + "-diversity of " + name(l.getSensitive());
            case TCloseness t -> t.getT() + "-closeness of " + name(t.getSensitive());
            case DifferentialPrivacy d -> "differential privacy with epsilon " + d.getEpsilon()
                    + (d.getDelta() == null ? "" : " and delta " + d.getDelta());
            default -> model.eClass().getName();
        };
    }

    private static String name(StructuralFeature feature) {
        return feature == null ? "?" : feature.getName();
    }
}
