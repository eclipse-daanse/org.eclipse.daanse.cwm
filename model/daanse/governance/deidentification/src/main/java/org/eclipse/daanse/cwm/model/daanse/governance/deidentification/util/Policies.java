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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PrivacyModel;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Treatment;

/**
 * Creates de-identification policies and finds them: a policy is a constraint on the classifiers
 * whose rows it de-identifies, so the policies of a classifier are the constraints that name it. The
 * body of a policy is its text in the language {@value #LANGUAGE}, kept in step by the helpers.
 */
public final class Policies {

    /** The language of the body of a policy or a disclosure control. */
    public static final String LANGUAGE = "daanse.deidentification";

    private Policies() {
    }

    /** A policy in the {@code ownedElement} of {@code owner}, claiming {@code outcome}, on the classifiers. */
    public static DeidentificationPolicy create(Namespace owner, String name, Outcome outcome,
            Classifier... classifiers) {
        if (classifiers.length == 0) {
            throw new IllegalArgumentException("a policy governs at least one classifier");
        }
        DeidentificationPolicy policy = DeidentificationFactory.eINSTANCE.createDeidentificationPolicy();
        policy.setName(Objects.requireNonNull(name, "name"));
        policy.setOutcome(Objects.requireNonNull(outcome, "outcome"));
        for (Classifier c : classifiers) {
            policy.getConstrainedElement().add(Objects.requireNonNull(c, "classifier"));
        }
        owner.getOwnedElement().add(policy);
        return refreshed(policy);
    }

    /** Names for whom the outcome holds. */
    public static DeidentificationPolicy audience(DeidentificationPolicy policy, ResponsibleParty audience) {
        policy.setAudience(Objects.requireNonNull(audience, "audience"));
        return refreshed(policy);
    }

    /** Lets at most {@code share} of the rows (between 0 and 1) be suppressed to meet the models. */
    public static DeidentificationPolicy suppressionLimit(DeidentificationPolicy policy, double share) {
        if (share < 0 || share > 1) {
            throw new IllegalArgumentException("a share is between 0 and 1: " + share);
        }
        policy.setSuppressionLimit(decimal(share));
        return refreshed(policy);
    }

    /** The policies that govern {@code classifier}. */
    public static List<DeidentificationPolicy> of(ModelElement classifier) {
        List<DeidentificationPolicy> out = new ArrayList<>();
        for (Constraint c : classifier.getConstraint()) {
            if (c instanceof DeidentificationPolicy p) {
                out.add(p);
            }
        }
        return out;
    }

    /** The treatment of {@code feature} in the policy. */
    public static Optional<Treatment> treatmentOf(DeidentificationPolicy policy, StructuralFeature feature) {
        return policy.getTreatment().stream().filter(t -> t.getFeature() == feature).findFirst();
    }

    /**
     * The text of a policy, e.g. {@code anonymous for statistics; k-anonymity 5; suppression up to
     * 0.05; name removed, birth_date generalized to year}.
     */
    public static String render(DeidentificationPolicy policy) {
        StringBuilder sb = new StringBuilder(policy.getOutcome() == null ? "?" : policy.getOutcome().getLiteral());
        if (policy.getAudience() != null) {
            sb.append(" for ").append(policy.getAudience().getName());
        }
        if (!policy.getModel().isEmpty()) {
            sb.append("; ").append(String.join(", ", policy.getModel().stream().map(PrivacyModels::render).toList()));
        }
        if (policy.getSuppressionLimit() != null) {
            sb.append("; suppression up to ").append(policy.getSuppressionLimit());
        }
        if (!policy.getTreatment().isEmpty()) {
            sb.append("; ").append(String.join(", ", policy.getTreatment().stream().map(Treatments::render).toList()));
        }
        return sb.toString();
    }

    /** Sets the {@code body} of a policy to its {@linkplain #render text}. */
    public static DeidentificationPolicy refreshed(DeidentificationPolicy policy) {
        policy.setBody(Expressions.bool(LANGUAGE, render(policy)));
        return policy;
    }

    /** Whether the {@code body} of a constraint is {@code text} in the language {@value #LANGUAGE}. */
    public static boolean hasBody(Constraint constraint, String text) {
        return constraint.getBody() != null && Objects.equals(text, constraint.getBody().getBody())
                && LANGUAGE.equals(constraint.getBody().getLanguage());
    }

    /** A decimal number as the literal of a CWM {@code Float}, without trailing zeros. */
    static String decimal(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("no number: " + value);
        }
        BigDecimal d = BigDecimal.valueOf(value).stripTrailingZeros();
        return d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
    }

    /** The decimal number of a CWM {@code Float} literal; {@code null} when it is none. */
    static BigDecimal number(String literal) {
        if (literal == null) {
            return null;
        }
        try {
            return new BigDecimal(literal.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
