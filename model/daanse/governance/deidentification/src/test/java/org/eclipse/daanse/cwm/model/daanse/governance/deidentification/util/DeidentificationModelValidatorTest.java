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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.IDENTIFYING;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.INSENSITIVE;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.QUASI_IDENTIFYING;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.SENSITIVE;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Consistency;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureControl;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DiversityVariant;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** One positive and at least one negative case per rule of the validator. */
class DeidentificationModelValidatorTest {

    private final Clinic clinic = new Clinic();

    /** An anonymous policy on Patient that treats every feature, with two privacy models. */
    private DeidentificationPolicy statistics() {
        DeidentificationPolicy p = Policies.create(clinic.governance, "statistics", Outcome.ANONYMOUS, clinic.patient);
        Treatments.remove(p, clinic.insuranceNo, IDENTIFYING);
        Treatments.remove(p, clinic.name, IDENTIFYING);
        Treatments.generalizeTo(p, clinic.birthDate, QUASI_IDENTIFYING, "year");
        Treatments.truncate(p, clinic.zip, QUASI_IDENTIFYING, 3);
        Treatments.unchanged(p, clinic.sex, QUASI_IDENTIFYING);
        Treatments.unchanged(p, clinic.diagnosis, SENSITIVE);
        Treatments.round(p, clinic.income, SENSITIVE, 1000);
        PrivacyModels.kAnonymity(p, 5);
        PrivacyModels.lDiversity(p, 2, DiversityVariant.DISTINCT, clinic.diagnosis);
        return p;
    }

    private List<Finding> findings() {
        return DeidentificationModelValidator.check(clinic.model);
    }

    private List<String> rules(Severity severity) {
        return findings().stream().filter(f -> f.severity() == severity).map(Finding::rule).toList();
    }

    @Test
    void aCompletePolicyHasNoFinding() {
        Policies.audience(statistics(), clinic.research);
        assertThat(findings()).isEmpty();
        assertThat(DeidentificationModelValidator.validate(clinic.model)).isEmpty();
    }

    @Test
    void d1_aPolicyOnAnAttribute() {
        Policies.create(clinic.governance, "names", Outcome.PSEUDONYMOUS, clinic.patient).getConstrainedElement()
                .add(clinic.name);
        assertThat(findings()).filteredOn(f -> f.rule().equals("D1") && f.severity() == Severity.ERROR)
                .extracting(Finding::message).anySatisfy(m -> assertThat(m).contains("governs classifiers, not the "
                        + "Attribute clinic.data.Patient.name"));
    }

    @Test
    void d1_everyFeatureOnceAndOnlyFeaturesOfTheClassifiers() {
        DeidentificationPolicy p = statistics();
        assertThat(rules(Severity.WARNING)).isEmpty();
        p.getTreatment().removeIf(t -> t.getFeature() == clinic.income);
        Policies.refreshed(p);
        assertThat(findings()).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.message()).isEqualTo("policy 'statistics' has no treatment of clinic.data.Patient.income.");
        });
        Treatments.unchanged(p, clinic.visitedOn, INSENSITIVE);
        Treatments.remove(p, clinic.name, IDENTIFYING);
        assertThat(findings()).filteredOn(f -> f.severity() == Severity.ERROR).extracting(Finding::message)
                .containsExactly("policy 'statistics' treats clinic.data.Visit.visitedOn, which is no feature of the "
                        + "classifiers it governs.", "policy 'statistics' treats name twice.");
    }

    @Test
    void d1_anOutcomeAndARoleAreSet() {
        DeidentificationPolicy p = statistics();
        p.getTreatment().get(0).unsetRole();
        p.unsetOutcome();
        Policies.refreshed(p);
        assertThat(findings()).extracting(Finding::severity, Finding::message).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Severity.ERROR, "policy 'statistics' claims no outcome."),
                org.assertj.core.groups.Tuple.tuple(Severity.WARNING, "policy 'statistics': the role of insuranceNo is "
                        + "not set; it counts as identifying."));
    }

    @Test
    void d1_theParametersOfATechnique() {
        DeidentificationPolicy p = Policies.create(clinic.governance, "patients", Outcome.PSEUDONYMOUS, clinic.patient);
        Treatments.truncate(p, clinic.zip, QUASI_IDENTIFYING, 0);
        Treatments.mask(p, clinic.insuranceNo, IDENTIFYING, "####");
        Treatments.code(p, clinic.income, SENSITIVE, 5000.0, 1000.0);
        Treatments.generalizeTo(p, clinic.birthDate, QUASI_IDENTIFYING, "year").setGranularity(" ");
        Policies.refreshed(p);
        assertThat(findings()).filteredOn(f -> f.severity() == Severity.ERROR).extracting(Finding::message)
                .containsExactly("policy 'patients': Truncation of zip keeps 0 characters, not at least 1.",
                        "policy 'patients': Masking of insuranceNo the pattern '####' masks nothing (X masks a "
                                + "character).",
                        "policy 'patients': TopBottomCoding of income has its low end 5000 not below its high end 1000.",
                        "policy 'patients': Generalization of birthDate has neither a width nor a granularity.");
    }

    @Test
    void d2_anAnonymousOutcomeKeepsNoIdentifierAndSaysWhy() {
        DeidentificationPolicy p = statistics();
        Treatments.unchanged(p, clinic.name, IDENTIFYING);
        p.getTreatment().removeIf(t -> t.getFeature() == clinic.name && t instanceof
                org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Removal);
        p.getModel().clear();
        Policies.refreshed(p);
        assertThat(findings()).filteredOn(f -> f.rule().equals("D2")).extracting(Finding::message).containsExactly(
                "policy 'statistics' claims an anonymous outcome but keeps the identifying feature name unchanged.",
                "policy 'statistics' claims an anonymous outcome but names no privacy model and no description of "
                        + "why the result is anonymous.");
        Description why = BusinessinformationFactory.eINSTANCE.createDescription();
        why.setName("why-anonymous");
        why.setType("documentation");
        why.setBody("the data is released only as counts of at least 10");
        why.getModelElement().add(p);
        clinic.governance.getOwnedElement().add(why);
        assertThat(findings()).filteredOn(f -> f.rule().equals("D2")).hasSize(1);
    }

    @Test
    void d2_pseudonymsOfAnAnonymousOutcomeLoseTheirLink() {
        DeidentificationPolicy p = statistics();
        PseudonymizationDomain study = PseudonymizationDomains.create(clinic.governance, "study", clinic.trustCentre);
        p.getTreatment().removeIf(t -> t.getFeature() == clinic.insuranceNo);
        Treatments.pseudonymize(p, clinic.insuranceNo, IDENTIFYING, PseudonymMethod.KEYED_HASH, Consistency.GLOBAL,
                study);
        assertThat(rules(Severity.ERROR)).containsExactly("D2");
        RetentionSchedule keys = RetentionSchedules.create(clinic.governance, "keys", PeriodStarts.valueTime(), study);
        DeidentificationStages.destroyLinkage(keys, "destroy", "P1Y", study);
        assertThat(rules(Severity.ERROR)).isEmpty();
    }

    @Test
    void d3_privacyModelsInRange() {
        DeidentificationPolicy p = statistics();
        p.getModel().clear();
        PrivacyModels.kAnonymity(p, 1);
        PrivacyModels.lDiversity(p, 2, DiversityVariant.ENTROPY, clinic.zip);
        PrivacyModels.tCloseness(p, 0, clinic.income);
        PrivacyModels.differentialPrivacy(p, 0, null);
        p.setSuppressionLimit("2");
        Policies.refreshed(p);
        assertThat(findings()).filteredOn(f -> f.rule().equals("D3")).extracting(Finding::message).containsExactly(
                "policy 'statistics': the suppression limit '2' is no share between 0 and 1.",
                "policy 'statistics': 1-anonymity: k 1 is below 2, which every data set meets.",
                "policy 'statistics': entropy 2-diversity of zip: zip is not treated as sensitive in the policy.",
                "policy 'statistics': 0-closeness of income: t '0' is not above 0 and at most 1.",
                "policy 'statistics': differential privacy with epsilon 0: epsilon '0' is no positive number.");
    }

    @Test
    void d4_aGlobalPseudonymLivesInADomain() {
        DeidentificationPolicy p = Policies.create(clinic.governance, "patients", Outcome.PSEUDONYMOUS, clinic.patient);
        Treatments.pseudonymize(p, clinic.insuranceNo, IDENTIFYING, PseudonymMethod.HASH, Consistency.PER_DATASET, null);
        assertThat(rules(Severity.ERROR)).isEmpty();
        Treatments.pseudonymize(p, clinic.name, IDENTIFYING, PseudonymMethod.HASH, Consistency.GLOBAL, null);
        assertThat(rules(Severity.ERROR)).containsExactly("D4");
    }

    @Test
    void d5_aMappingKeptWithoutEnd() {
        PseudonymizationDomain study = PseudonymizationDomains.mapping(
                PseudonymizationDomains.create(clinic.governance, "study", clinic.trustCentre), clinic.pseudonym);
        assertThat(findings()).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("D5");
            assertThat(f.message()).isEqualTo("domain 'study' keeps the mapping Pseudonym, which no stage destroys.");
        });
        RetentionSchedule keys = RetentionSchedules.create(clinic.governance, "keys", PeriodStarts.valueTime(), study);
        DeidentificationStages.destroyLinkage(keys, "destroy", "P10Y", study);
        assertThat(findings()).isEmpty();
    }

    @Test
    void d6_aStageAppliesItsPolicyToWhatItGoverns() {
        DeidentificationPolicy statistics = statistics();
        DeidentificationPolicy research = Policies.create(clinic.governance, "research", Outcome.PSEUDONYMOUS,
                clinic.patient);
        RetentionSchedule onAFeature = RetentionSchedules.create(clinic.governance, "names", PeriodStarts.valueTime(),
                clinic.name);
        DeidentificationStages.anonymize(onAFeature, "anonymize", "P1Y", statistics);
        assertThat(rules(Severity.ERROR)).isEmpty();
        RetentionSchedule visits = RetentionSchedules.create(clinic.governance, "visits", PeriodStarts.valueTime(),
                clinic.visit);
        DeidentificationStages.pseudonymize(visits, "pseudonymize", "P1Y", research);
        DeidentificationStages.anonymize(visits, "anonymize", "P2Y", research);
        assertThat(findings()).filteredOn(f -> f.rule().equals("D6")).extracting(Finding::message).containsExactly(
                "stage pseudonymize of visits applies the policy research to clinic.data.Visit, which the policy does "
                        + "not govern.",
                "stage anonymize of visits anonymizes by the policy research, whose outcome is pseudonymous.",
                "stage anonymize of visits applies the policy research to clinic.data.Visit, which the policy does "
                        + "not govern.");
    }

    @Test
    void d6_aLinkageIsDestroyedInTheScheduleOfItsDomain() {
        PseudonymizationDomain study = PseudonymizationDomains.create(clinic.governance, "study", clinic.trustCentre);
        RetentionSchedule visits = RetentionSchedules.create(clinic.governance, "visits", PeriodStarts.valueTime(),
                clinic.visit);
        DeidentificationStages.destroyLinkage(visits, "destroy", "P1Y", study);
        assertThat(findings()).extracting(Finding::message).containsExactly("stage destroy of visits destroys the "
                + "linkage of study, but the schedule governs neither the domain nor its mapping.");
        PseudonymizationDomains.mapping(study, clinic.pseudonym);
        assertThat(rules(Severity.WARNING)).containsExactly("D5");
        RetentionSchedule pseudonyms = RetentionSchedules.create(clinic.governance, "pseudonyms",
                PeriodStarts.valueTime(), clinic.pseudonym);
        DeidentificationStages.destroyLinkage(pseudonyms, "destroy", "P1Y", study);
        assertThat(rules(Severity.WARNING)).isEmpty();
        assertThat(rules(Severity.ERROR)).containsExactly("D6");
    }

    @Test
    void d7_anAudienceSaysWhoItIs() {
        ResponsibleParty someone = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        someone.setName("someone");
        clinic.governance.getOwnedElement().add(someone);
        Policies.audience(statistics(), someone);
        assertThat(rules(Severity.WARNING)).containsExactly("D7");
    }

    @Test
    void d8_aDisclosureControlOnACubeOrAMeasureThatSaysSomething() {
        DisclosureControl control = DisclosureControls.create(clinic.governance, "cells", DisclosureMethod.CELL_KEY,
                clinic.patient, clinic.income);
        assertThat(rules(Severity.ERROR)).containsExactly("D8");
        DisclosureControls.minimumCellCount(control, 3);
        assertThat(findings()).isEmpty();
        control.getConstrainedElement().add(clinic.data);
        DisclosureControls.dominance(control, 2, 120);
        assertThat(findings()).extracting(Finding::severity, Finding::rule).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Severity.WARNING, "D8"),
                org.assertj.core.groups.Tuple.tuple(Severity.ERROR, "D8"));
    }

    @Test
    void d9_anElementThatDoesNotResolveAndABodyEditedByHand() {
        DeidentificationPolicy p = statistics();
        Attribute proxy = CoreFactory.eINSTANCE.createAttribute();
        ((InternalEObject) proxy).eSetProxyURI(URI.createURI("other.xmi#//elsewhere"));
        p.getConstrainedElement().add(proxy);
        p.getBody().setBody("anonymous");
        assertThat(rules(Severity.WARNING)).containsExactly("D9", "D9");
        assertThat(rules(Severity.ERROR)).isEmpty();
    }
}
