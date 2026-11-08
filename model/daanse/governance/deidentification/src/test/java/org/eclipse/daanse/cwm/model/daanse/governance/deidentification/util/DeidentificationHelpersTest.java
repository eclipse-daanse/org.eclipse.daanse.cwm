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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Consistency;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureControl;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DiversityVariant;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Treatment;
import org.junit.jupiter.api.Test;

/** The helpers that create policies, treatments, models, domains and controls, and the text of each. */
class DeidentificationHelpersTest {

    private final Clinic clinic = new Clinic();

    @Test
    void aPolicyIsAConstraintOnItsClassifiers() {
        DeidentificationPolicy policy = Policies.create(clinic.governance, "patients-research", Outcome.PSEUDONYMOUS,
                clinic.patient);
        assertThat(policy.getNamespace()).isSameAs(clinic.governance);
        assertThat(policy.getConstrainedElement()).containsExactly(clinic.patient);
        assertThat(clinic.patient.getConstraint()).containsExactly(policy);
        assertThat(Policies.of(clinic.patient)).containsExactly(policy);
        assertThat(policy.getBody().getLanguage()).isEqualTo(Policies.LANGUAGE);
        assertThat(policy.getBody().getBody()).isEqualTo("pseudonymous");
        Policies.suppressionLimit(Policies.audience(policy, clinic.research), 0.05);
        assertThat(policy.getBody().getBody()).isEqualTo("pseudonymous for research; suppression up to 0.05");
        assertThatThrownBy(() -> Policies.suppressionLimit(policy, 1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Policies.create(clinic.governance, "none", Outcome.ANONYMOUS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTechniqueAndItsText() {
        DeidentificationPolicy policy = Policies.create(clinic.governance, "patients", Outcome.PSEUDONYMOUS,
                clinic.patient);
        PseudonymizationDomain study = PseudonymizationDomains.create(clinic.governance, "study", clinic.trustCentre);
        Treatments.pseudonymize(policy, clinic.insuranceNo, AttributeRole.IDENTIFYING, PseudonymMethod.KEYED_HASH,
                Consistency.GLOBAL, study);
        Treatments.remove(policy, clinic.name, AttributeRole.IDENTIFYING);
        Treatments.generalizeTo(policy, clinic.birthDate, AttributeRole.QUASI_IDENTIFYING, "year");
        Treatments.truncate(policy, clinic.zip, AttributeRole.QUASI_IDENTIFYING, 3);
        Treatments.unchanged(policy, clinic.sex, AttributeRole.QUASI_IDENTIFYING);
        Treatments.mask(policy, clinic.diagnosis, AttributeRole.SENSITIVE, "XXX#");
        Treatments.code(policy, clinic.income, AttributeRole.SENSITIVE, null, 200000.0);
        assertThat(policy.getTreatment()).extracting(Treatments::render).containsExactly(
                "insuranceNo pseudonymized by keyedHash global in study", "name removed",
                "birthDate generalized to year", "zip truncated to 3", "sex unchanged", "diagnosis masked as XXX#",
                "income coded to 200000");
        assertThat(policy.getBody().getBody()).startsWith("pseudonymous; insuranceNo pseudonymized by keyedHash");
        assertThat(Policies.treatmentOf(policy, clinic.zip)).map(Treatment::getRole)
                .contains(AttributeRole.QUASI_IDENTIFYING);

        DeidentificationPolicy other = Policies.create(clinic.governance, "visits", Outcome.ANONYMOUS, clinic.visit);
        assertThat(Treatments.render(Treatments.shiftDates(other, clinic.visitedOn, AttributeRole.QUASI_IDENTIFYING,
                "P30D", Consistency.PER_DATASET))).isEqualTo("visitedOn shifted by up to P30D perDataset");
        assertThat(Treatments.render(Treatments.round(policy, clinic.income, AttributeRole.SENSITIVE, 1000)))
                .isEqualTo("income rounded to 1000");
        assertThat(Treatments.render(Treatments.noise(policy, clinic.income, AttributeRole.SENSITIVE, "laplace", 0.5)))
                .isEqualTo("income with laplace noise of scale 0.5");
        assertThat(Treatments.render(Treatments.generalizeBy(policy, clinic.income, AttributeRole.SENSITIVE, 10000)))
                .isEqualTo("income generalized to intervals of 10000");
        assertThatThrownBy(() -> Treatments.shiftDates(other, clinic.visitedOn, AttributeRole.QUASI_IDENTIFYING,
                "a month", Consistency.GLOBAL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privacyModelsAndTheirText() {
        DeidentificationPolicy policy = Policies.create(clinic.governance, "statistics", Outcome.ANONYMOUS,
                clinic.patient);
        PrivacyModels.kAnonymity(policy, 5);
        PrivacyModels.lDiversity(policy, 3, DiversityVariant.DISTINCT, clinic.diagnosis);
        PrivacyModels.tCloseness(policy, 0.2, clinic.income);
        PrivacyModels.differentialPrivacy(policy, 1, 0.00001);
        assertThat(policy.getModel()).extracting(PrivacyModels::render).containsExactly("5-anonymity",
                "distinct 3-diversity of diagnosis", "0.2-closeness of income",
                "differential privacy with epsilon 1 and delta 0.00001");
        assertThat(policy.getBody().getBody()).isEqualTo("anonymous; 5-anonymity, distinct 3-diversity of diagnosis, "
                + "0.2-closeness of income, differential privacy with epsilon 1 and delta 0.00001");
    }

    @Test
    void domainsKnowTheirPseudonymsAndTheirEnd() {
        PseudonymizationDomain study = PseudonymizationDomains.mapping(
                PseudonymizationDomains.create(clinic.governance, "study", clinic.trustCentre), clinic.pseudonym);
        assertThat(study.getKeyHolder()).isSameAs(clinic.trustCentre);
        assertThat(study.getMapping()).isSameAs(clinic.pseudonym);
        DeidentificationPolicy policy = Policies.create(clinic.governance, "patients", Outcome.PSEUDONYMOUS,
                clinic.patient);
        var pseudonymization = Treatments.pseudonymize(policy, clinic.insuranceNo, AttributeRole.IDENTIFYING,
                PseudonymMethod.COUNTER, Consistency.GLOBAL, study);
        assertThat(PseudonymizationDomains.pseudonymizations(study)).containsExactly(pseudonymization);
        assertThat(PseudonymizationDomains.destroyedBy(study)).isEmpty();
        var keys = org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules.create(
                clinic.governance, "keys",
                org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts.valueTime(), study);
        var destroy = DeidentificationStages.destroyLinkage(keys, "destroy", "P10Y", study);
        assertThat(PseudonymizationDomains.destroyedBy(study)).containsExactly(destroy);
    }

    @Test
    void disclosureControlOfAMeasureElseOfItsCube() {
        Class cube = CoreFactory.eINSTANCE.createClass();
        cube.setName("admissions");
        clinic.data.getOwnedElement().add(cube);
        Attribute cases = CoreFactory.eINSTANCE.createAttribute();
        cases.setName("cases");
        cube.getFeature().add(cases);
        Attribute days = CoreFactory.eINSTANCE.createAttribute();
        days.setName("days");
        cube.getFeature().add(days);
        DisclosureControl cells = DisclosureControls.minimumCellCount(DisclosureControls.dominance(
                DisclosureControls.create(clinic.governance, "cells", DisclosureMethod.SUPPRESSION, cube), 2, 85), 3);
        assertThat(cells.getBody().getBody()).isEqualTo("at least 3 per cell; (2, 85) dominance; suppression");
        DisclosureControl rounded = DisclosureControls.minimumCellCount(
                DisclosureControls.create(clinic.governance, "rounded", DisclosureMethod.ROUNDING, days), 5);
        assertThat(DisclosureControls.effective(cases)).containsSame(cells);
        assertThat(DisclosureControls.effective(days)).containsSame(rounded);
        assertThat(DisclosureControls.effective(cube)).containsSame(cells);
        assertThat(DisclosureControls.effective(clinic.patient)).isEmpty();
    }
}
