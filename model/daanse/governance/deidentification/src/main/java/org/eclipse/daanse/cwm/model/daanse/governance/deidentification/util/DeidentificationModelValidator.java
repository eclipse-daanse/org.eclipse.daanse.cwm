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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Anonymize;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Consistency;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DateShift;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DestroyLinkage;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DifferentialPrivacy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureControl;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Generalization;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.KAnonymity;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.LDiversity;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Masking;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.NoiseAddition;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PrivacyModel;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Pseudonymization;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Pseudonymize;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Rounding;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.TCloseness;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.TopBottomCoding;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Treatment;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Truncation;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Unchanged;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;

/**
 * Checks the de-identification policies, pseudonymization domains, de-identifying stages and
 * disclosure controls in a model; the findings are those of the lifecycle models ({@link Finding},
 * {@link Severity}).
 *
 * <ul>
 * <li>{@code D1} — a policy governs classifiers, claims an outcome and treats each of their features
 * once; a treatment of a feature they do not have, or a second one, is an error, a feature without
 * one or a treatment without a role a warning; the parameters of each technique parse and are in
 * range.</li>
 * <li>{@code D2} — a policy that claims an anonymous outcome keeps no identifying feature unchanged,
 * names a privacy model or a description of why the result is anonymous, and pseudonymizes only in
 * domains whose linkage a stage destroys.</li>
 * <li>{@code D3} — the parameters of the privacy models are in range, the sensitive feature of
 * l-diversity and t-closeness is treated as sensitive in the policy, a suppression limit is a share
 * between 0 and 1.</li>
 * <li>{@code D4} — a pseudonym that is the same in every data set lives in a domain.</li>
 * <li>{@code D5} (warning) — a domain keeps a mapping that no stage destroys.</li>
 * <li>{@code D6} — a stage that pseudonymizes or anonymizes applies its policy to what the policy
 * governs: the classifiers of the policy or their features; an anonymizing policy is anonymous; a
 * stage that destroys the linkage of a domain is in a schedule of the domain or of its mapping.</li>
 * <li>{@code D7} (warning) — the audience of a policy has no responsibility that says who it is.</li>
 * <li>{@code D8} — a disclosure control says something (a minimum cell count or a dominance rule) in
 * range; (warning) it governs something that is neither a class, as a cube, nor an attribute, as a
 * measure.</li>
 * <li>{@code D9} (warning) — an element that does not resolve; the body is not the text of the
 * policy or control.</li>
 * </ul>
 */
public final class DeidentificationModelValidator {

    private DeidentificationModelValidator() {
    }

    /** All findings in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        for (EObject o : all) {
            if (o instanceof DeidentificationPolicy p) {
                policy(p, findings);
            } else if (o instanceof PseudonymizationDomain d) {
                domain(d, findings);
            } else if (o instanceof Pseudonymize || o instanceof Anonymize) {
                stage((Stage) o, findings);
            } else if (o instanceof DestroyLinkage l) {
                linkage(l, findings);
            } else if (o instanceof DisclosureControl c) {
                disclosure(c, findings);
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void policy(DeidentificationPolicy policy, List<Finding> findings) {
        String self = "policy '" + policy.getName() + "'";
        if (!policy.isSetOutcome()) {
            findings.add(new Finding(Severity.ERROR, "D1", policy, self + " claims no outcome."));
        }
        Set<StructuralFeature> features = new LinkedHashSet<>();
        for (ModelElement e : policy.getConstrainedElement()) {
            if (e.eIsProxy()) {
                findings.add(new Finding(Severity.WARNING, "D9", policy, self + ": element " + proxy(e)
                        + " does not resolve."));
            } else if (e instanceof Classifier c) {
                for (Classifier k : LifecycleRules.lineage(c)) {
                    k.getFeature().stream().filter(StructuralFeature.class::isInstance)
                            .map(StructuralFeature.class::cast).forEach(features::add);
                }
            } else {
                findings.add(new Finding(Severity.ERROR, "D1", policy, self + " governs classifiers, not the "
                        + e.eClass().getName() + " " + LifecycleRules.path(e) + "."));
            }
        }
        Map<StructuralFeature, Integer> treated = new HashMap<>();
        for (Treatment t : policy.getTreatment()) {
            StructuralFeature f = t.getFeature();
            if (f == null) {
                findings.add(new Finding(Severity.ERROR, "D1", policy, self + ": a treatment names no feature."));
                continue;
            }
            if (f.eIsProxy()) {
                findings.add(new Finding(Severity.WARNING, "D9", policy, self + ": feature " + proxy(f)
                        + " does not resolve."));
                continue;
            }
            if (treated.merge(f, 1, Integer::sum) == 2) {
                findings.add(new Finding(Severity.ERROR, "D1", policy, self + " treats " + f.getName() + " twice."));
            }
            if (!features.contains(f)) {
                findings.add(new Finding(Severity.ERROR, "D1", policy, self + " treats " + LifecycleRules.path(f)
                        + ", which is no feature of the classifiers it governs."));
            }
            if (!t.isSetRole()) {
                findings.add(new Finding(Severity.WARNING, "D1", policy, self + ": the role of " + f.getName()
                        + " is not set; it counts as identifying."));
            }
            parameters(policy, t, findings);
            if (t instanceof Pseudonymization p && p.getConsistency() == Consistency.GLOBAL && p.getDomain() == null) {
                findings.add(new Finding(Severity.ERROR, "D4", policy, self + ": the pseudonyms of " + f.getName()
                        + " are the same in every data set, but live in no domain."));
            }
        }
        for (StructuralFeature f : features) {
            if (!treated.containsKey(f)) {
                findings.add(new Finding(Severity.WARNING, "D1", policy, self + " has no treatment of "
                        + LifecycleRules.path(f) + "."));
            }
        }
        if (policy.getOutcome() == Outcome.ANONYMOUS) {
            anonymous(policy, self, findings);
        }
        models(policy, self, findings);
        if (policy.getAudience() != null) {
            if (policy.getAudience().eIsProxy()) {
                findings.add(new Finding(Severity.WARNING, "D9", policy, self + ": audience "
                        + proxy(policy.getAudience()) + " does not resolve."));
            } else if (policy.getAudience().getResponsibility() == null
                    || policy.getAudience().getResponsibility().isBlank()) {
                findings.add(new Finding(Severity.WARNING, "D7", policy, self + ": the audience "
                        + policy.getAudience().getName() + " has no responsibility that says who it is."));
            }
        }
        String expected = Policies.render(policy);
        if (!Policies.hasBody(policy, expected)) {
            findings.add(new Finding(Severity.WARNING, "D9", policy, self + ": the body is not the text of the "
                    + "policy '" + expected + "' in " + Policies.LANGUAGE + "."));
        }
    }

    private static void parameters(DeidentificationPolicy policy, Treatment t, List<Finding> findings) {
        String what = "policy '" + policy.getName() + "': " + t.eClass().getName() + " of " + t.getFeature().getName();
        String problem = switch (t) {
            case Masking m -> m.getPattern() == null || m.getPattern().indexOf('X') < 0
                    ? "the pattern '" + m.getPattern() + "' masks nothing (X masks a character)" : null;
            case Truncation tr -> tr.getKeep() < 1 ? "keeps " + tr.getKeep() + " characters, not at least 1" : null;
            case Rounding r -> positive(r.getTo()) ? null : "rounds to '" + r.getTo() + "', no positive number";
            case TopBottomCoding c -> coding(c);
            case NoiseAddition n -> !positive(n.getScale()) ? "the scale '" + n.getScale() + "' is no positive number"
                    : n.getDistribution() == null || n.getDistribution().isBlank() ? "names no distribution" : null;
            case DateShift d -> !Periods.isValid(d.getMaximum()) ? "the maximum '" + d.getMaximum()
                    + "' is no ISO-8601 period" : null;
            case Generalization g -> generalization(g);
            default -> null;
        };
        if (problem != null) {
            findings.add(new Finding(Severity.ERROR, "D1", policy, what + " " + problem + "."));
        }
    }

    private static String coding(TopBottomCoding c) {
        BigDecimal low = Policies.number(c.getLow());
        BigDecimal high = Policies.number(c.getHigh());
        if (c.getLow() == null && c.getHigh() == null) {
            return "has neither a low nor a high end";
        }
        if (c.getLow() != null && low == null || c.getHigh() != null && high == null) {
            return "has an end that is no number";
        }
        return low != null && high != null && low.compareTo(high) >= 0 ? "has its low end " + low
                + " not below its high end " + high : null;
    }

    private static String generalization(Generalization g) {
        if (g.getWidth() == null && (g.getGranularity() == null || g.getGranularity().isBlank())) {
            return "has neither a width nor a granularity";
        }
        return g.getWidth() != null && !positive(g.getWidth()) ? "has the width '" + g.getWidth()
                + "', no positive number" : null;
    }

    private static void anonymous(DeidentificationPolicy policy, String self, List<Finding> findings) {
        for (Treatment t : policy.getTreatment()) {
            if (t instanceof Unchanged && t.getRole() == AttributeRole.IDENTIFYING && t.getFeature() != null) {
                findings.add(new Finding(Severity.ERROR, "D2", policy, self + " claims an anonymous outcome but keeps "
                        + "the identifying feature " + t.getFeature().getName() + " unchanged."));
            }
            if (t instanceof Pseudonymization p && p.getDomain() != null && !p.getDomain().eIsProxy()
                    && !destroyed(p.getDomain())) {
                String feature = t.getFeature() == null ? "?" : t.getFeature().getName();
                findings.add(new Finding(Severity.ERROR, "D2", policy, self + " claims an anonymous outcome but "
                        + "pseudonymizes " + feature + " in the domain " + p.getDomain().getName()
                        + ", whose linkage no stage destroys."));
            }
        }
        if (policy.getModel().isEmpty() && Descriptions.all(policy).isEmpty()) {
            findings.add(new Finding(Severity.ERROR, "D2", policy, self + " claims an anonymous outcome but names "
                    + "no privacy model and no description of why the result is anonymous."));
        }
    }

    private static void models(DeidentificationPolicy policy, String self, List<Finding> findings) {
        if (policy.getSuppressionLimit() != null) {
            BigDecimal limit = Policies.number(policy.getSuppressionLimit());
            if (limit == null || limit.signum() < 0 || limit.compareTo(BigDecimal.ONE) > 0) {
                findings.add(new Finding(Severity.ERROR, "D3", policy, self + ": the suppression limit '"
                        + policy.getSuppressionLimit() + "' is no share between 0 and 1."));
            }
        }
        for (PrivacyModel m : policy.getModel()) {
            String problem = switch (m) {
                case KAnonymity k -> k.getK() < 2 ? "k " + k.getK() + " is below 2, which every data set meets" : null;
                case LDiversity l -> l.getL() < 2 ? "l " + l.getL() + " is below 2, which every group meets"
                        : sensitive(policy, l.getSensitive());
                case TCloseness t -> {
                    BigDecimal v = Policies.number(t.getT());
                    yield v == null || v.signum() <= 0 || v.compareTo(BigDecimal.ONE) > 0 ? "t '" + t.getT()
                            + "' is not above 0 and at most 1" : sensitive(policy, t.getSensitive());
                }
                case DifferentialPrivacy d -> {
                    BigDecimal delta = Policies.number(d.getDelta());
                    yield !positive(d.getEpsilon()) ? "epsilon '" + d.getEpsilon() + "' is no positive number"
                            : d.getDelta() != null && (delta == null || delta.signum() < 0
                                    || delta.compareTo(BigDecimal.ONE) >= 0) ? "delta '" + d.getDelta()
                                            + "' is not at least 0 and below 1" : null;
                }
                default -> null;
            };
            if (problem != null) {
                findings.add(new Finding(Severity.ERROR, "D3", policy, self + ": " + PrivacyModels.render(m) + ": "
                        + problem + "."));
            }
        }
    }

    private static String sensitive(DeidentificationPolicy policy, StructuralFeature feature) {
        if (feature == null || feature.eIsProxy()) {
            return "the sensitive feature does not resolve";
        }
        return Policies.treatmentOf(policy, feature).filter(t -> t.getRole() == AttributeRole.SENSITIVE).isPresent()
                ? null : feature.getName() + " is not treated as sensitive in the policy";
    }

    private static void domain(PseudonymizationDomain domain, List<Finding> findings) {
        String self = "domain '" + domain.getName() + "'";
        if (domain.getKeyHolder() != null && domain.getKeyHolder().eIsProxy()) {
            findings.add(new Finding(Severity.WARNING, "D9", domain, self + ": key holder "
                    + proxy(domain.getKeyHolder()) + " does not resolve."));
        }
        if (domain.getMapping() != null && domain.getMapping().eIsProxy()) {
            findings.add(new Finding(Severity.WARNING, "D9", domain, self + ": mapping " + proxy(domain.getMapping())
                    + " does not resolve."));
        } else if (domain.getMapping() != null && !destroyed(domain)) {
            findings.add(new Finding(Severity.WARNING, "D5", domain, self + " keeps the mapping "
                    + domain.getMapping().getName() + ", which no stage destroys."));
        }
    }

    private static void stage(Stage stage, List<Finding> findings) {
        DeidentificationPolicy policy = stage instanceof Anonymize a ? a.getPolicy()
                : ((Pseudonymize) stage).getPolicy();
        String self = "stage " + stage.getName();
        if (!(stage.eContainer() instanceof RetentionSchedule schedule)) {
            return;
        }
        self += " of " + schedule.getName();
        if (policy == null) {
            return;
        }
        if (policy.eIsProxy()) {
            findings.add(new Finding(Severity.WARNING, "D9", schedule, self + ": policy " + proxy(policy)
                    + " does not resolve."));
            return;
        }
        if (stage instanceof Anonymize && policy.getOutcome() != Outcome.ANONYMOUS) {
            findings.add(new Finding(Severity.ERROR, "D6", schedule, self + " anonymizes by the policy "
                    + policy.getName() + ", whose outcome is " + policy.getOutcome().getLiteral() + "."));
        }
        for (ModelElement e : schedule.getConstrainedElement()) {
            if (e.eIsProxy()) {
                continue;
            }
            ModelElement classifier = e instanceof Feature f && f.getOwner() != null ? f.getOwner() : e;
            if (!policy.getConstrainedElement().contains(classifier)) {
                findings.add(new Finding(Severity.ERROR, "D6", schedule, self + " applies the policy "
                        + policy.getName() + " to " + LifecycleRules.path(e) + ", which the policy does not govern."));
            }
        }
    }

    /** D6: a stage that destroys the linkage of a domain is in a schedule of the domain or of its mapping. */
    private static void linkage(DestroyLinkage stage, List<Finding> findings) {
        if (!(stage.eContainer() instanceof RetentionSchedule schedule) || stage.getDomain() == null
                || stage.getDomain().eIsProxy() || ofTheDomain(stage)) {
            return;
        }
        findings.add(new Finding(Severity.ERROR, "D6", schedule, "stage " + stage.getName() + " of "
                + schedule.getName() + " destroys the linkage of " + stage.getDomain().getName() + ", but the schedule "
                + "governs neither the domain nor its mapping."));
    }

    /** Whether a stage in a schedule of the domain or of its mapping destroys its linkage. */
    private static boolean destroyed(PseudonymizationDomain domain) {
        return PseudonymizationDomains.destroyedBy(domain).stream()
                .anyMatch(DeidentificationModelValidator::ofTheDomain);
    }

    private static boolean ofTheDomain(DestroyLinkage stage) {
        return stage.eContainer() instanceof RetentionSchedule schedule
                && (schedule.getConstrainedElement().contains(stage.getDomain())
                        || stage.getDomain().getMapping() != null
                                && schedule.getConstrainedElement().contains(stage.getDomain().getMapping()));
    }

    private static void disclosure(DisclosureControl control, List<Finding> findings) {
        String self = "disclosure control '" + control.getName() + "'";
        for (ModelElement e : control.getConstrainedElement()) {
            if (e.eIsProxy()) {
                findings.add(new Finding(Severity.WARNING, "D9", control, self + ": element " + proxy(e)
                        + " does not resolve."));
            } else if (!(e instanceof org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class)
                    && !(e instanceof Attribute)) {
                findings.add(new Finding(Severity.WARNING, "D8", control, self + " governs the " + e.eClass().getName()
                        + " " + LifecycleRules.path(e) + ", neither a cube nor a measure."));
            }
        }
        if (!control.isSetMinimumCellCount() && control.getDominance() == null) {
            findings.add(new Finding(Severity.ERROR, "D8", control, self + " says nothing: no minimum cell count "
                    + "and no dominance rule."));
        }
        if (control.isSetMinimumCellCount() && control.getMinimumCellCount() < 1) {
            findings.add(new Finding(Severity.ERROR, "D8", control, self + ": a minimum cell count of "
                    + control.getMinimumCellCount() + " protects nothing."));
        }
        if (control.getDominance() != null) {
            BigDecimal k = Policies.number(control.getDominance().getK());
            if (control.getDominance().getN() < 1 || k == null || k.signum() <= 0
                    || k.compareTo(BigDecimal.valueOf(100)) > 0) {
                findings.add(new Finding(Severity.ERROR, "D8", control, self + ": the dominance rule ("
                        + control.getDominance().getN() + ", " + control.getDominance().getK() + ") needs n of at "
                        + "least 1 and k above 0 and at most 100."));
            }
        }
        String expected = DisclosureControls.render(control);
        if (!Policies.hasBody(control, expected)) {
            findings.add(new Finding(Severity.WARNING, "D9", control, self + ": the body is not the text of the "
                    + "control '" + expected + "' in " + Policies.LANGUAGE + "."));
        }
    }

    private static boolean positive(String literal) {
        BigDecimal d = Policies.number(literal);
        return d != null && d.signum() > 0;
    }

    private static String proxy(EObject e) {
        return String.valueOf(((InternalEObject) e).eProxyURI());
    }
}
