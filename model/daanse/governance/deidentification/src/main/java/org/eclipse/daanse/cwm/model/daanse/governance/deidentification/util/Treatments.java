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
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Consistency;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DateShift;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Generalization;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Masking;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.NoiseAddition;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Pseudonymization;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Removal;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Rounding;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.TopBottomCoding;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Treatment;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Truncation;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Unchanged;

/**
 * Adds treatments to a policy, one per feature: the feature, the role it plays, the technique and
 * its parameters. The body of the policy is kept in step.
 */
public final class Treatments {

    private static final DeidentificationFactory F = DeidentificationFactory.eINSTANCE;

    private Treatments() {
    }

    /** Adds {@code treatment} of any kind for {@code feature} in {@code role}. */
    public static <T extends Treatment> T add(DeidentificationPolicy policy, StructuralFeature feature,
            AttributeRole role, T treatment) {
        treatment.setFeature(Objects.requireNonNull(feature, "feature"));
        treatment.setRole(Objects.requireNonNull(role, "role"));
        policy.getTreatment().add(treatment);
        Policies.refreshed(policy);
        return treatment;
    }

    /** The feature is kept as it is. */
    public static Unchanged unchanged(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role) {
        return add(policy, feature, role, F.createUnchanged());
    }

    /** The feature is removed from every row. */
    public static Removal remove(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role) {
        return add(policy, feature, role, F.createRemoval());
    }

    /** Characters are masked: {@code X} masks one, {@code #} keeps one. */
    public static Masking mask(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role,
            String pattern) {
        Masking t = F.createMasking();
        t.setPattern(Objects.requireNonNull(pattern, "pattern"));
        return add(policy, feature, role, t);
    }

    /** Only the first {@code keep} characters remain. */
    public static Truncation truncate(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role,
            long keep) {
        Truncation t = F.createTruncation();
        t.setKeep(keep);
        return add(policy, feature, role, t);
    }

    /** The value is rounded to a multiple of {@code to}. */
    public static Rounding round(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role,
            double to) {
        Rounding t = F.createRounding();
        t.setTo(Policies.decimal(to));
        return add(policy, feature, role, t);
    }

    /** Values below {@code low} become {@code low}, above {@code high} become {@code high}; one may be null. */
    public static TopBottomCoding code(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role,
            Double low, Double high) {
        TopBottomCoding t = F.createTopBottomCoding();
        t.setLow(low == null ? null : Policies.decimal(low));
        t.setHigh(high == null ? null : Policies.decimal(high));
        return add(policy, feature, role, t);
    }

    /** Noise of {@code distribution} (laplace, gaussian, uniform) with {@code scale} is added. */
    public static NoiseAddition noise(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role,
            String distribution, double scale) {
        NoiseAddition t = F.createNoiseAddition();
        t.setDistribution(Objects.requireNonNull(distribution, "distribution"));
        t.setScale(Policies.decimal(scale));
        return add(policy, feature, role, t);
    }

    /** A date is shifted by at most {@code maximum} (a {@code Period} literal), alike within {@code consistency}. */
    public static DateShift shiftDates(DeidentificationPolicy policy, StructuralFeature feature, AttributeRole role,
            String maximum, Consistency consistency) {
        Periods.parse(maximum);
        DateShift t = F.createDateShift();
        t.setMaximum(maximum);
        t.setConsistency(Objects.requireNonNull(consistency, "consistency"));
        return add(policy, feature, role, t);
    }

    /** A number becomes the interval of {@code width} it falls into. */
    public static Generalization generalizeBy(DeidentificationPolicy policy, StructuralFeature feature,
            AttributeRole role, double width) {
        Generalization t = F.createGeneralization();
        t.setWidth(Policies.decimal(width));
        return add(policy, feature, role, t);
    }

    /** A date or a code becomes its {@code granularity}: year, month, the first three digits. */
    public static Generalization generalizeTo(DeidentificationPolicy policy, StructuralFeature feature,
            AttributeRole role, String granularity) {
        Generalization t = F.createGeneralization();
        t.setGranularity(Objects.requireNonNull(granularity, "granularity"));
        return add(policy, feature, role, t);
    }

    /**
     * The value is replaced by a pseudonym made by {@code method}, alike within {@code consistency},
     * in {@code domain} ({@code null}: none).
     */
    public static Pseudonymization pseudonymize(DeidentificationPolicy policy, StructuralFeature feature,
            AttributeRole role, PseudonymMethod method, Consistency consistency, PseudonymizationDomain domain) {
        Pseudonymization t = F.createPseudonymization();
        t.setMethod(Objects.requireNonNull(method, "method"));
        t.setConsistency(Objects.requireNonNull(consistency, "consistency"));
        t.setDomain(domain);
        return add(policy, feature, role, t);
    }

    /** The text of a treatment, e.g. {@code zip truncated to 3}. */
    public static String render(Treatment treatment) {
        String f = treatment.getFeature() == null ? "?" : treatment.getFeature().getName();
        return f + switch (treatment) {
            case Unchanged u -> " unchanged";
            case Removal r -> " removed";
            case Masking m -> " masked as " + m.getPattern();
            case Truncation t -> " truncated to " + t.getKeep();
            case Rounding r -> " rounded to " + r.getTo();
            case TopBottomCoding c -> " coded" + (c.getLow() == null ? "" : " from " + c.getLow())
                    + (c.getHigh() == null ? "" : " to " + c.getHigh());
            case NoiseAddition n -> " with " + n.getDistribution() + " noise of scale " + n.getScale();
            case DateShift d -> " shifted by up to " + d.getMaximum() + " " + literal(d.getConsistency());
            case Generalization g -> " generalized to " + (g.getWidth() != null ? "intervals of " + g.getWidth()
                    : g.getGranularity());
            case Pseudonymization p -> " pseudonymized by " + literal(p.getMethod()) + " " + literal(p.getConsistency())
                    + (p.getDomain() == null ? "" : " in " + p.getDomain().getName());
            default -> " " + treatment.eClass().getName();
        };
    }

    private static String literal(org.eclipse.emf.common.util.Enumerator e) {
        return e == null ? "?" : e.getLiteral();
    }
}
