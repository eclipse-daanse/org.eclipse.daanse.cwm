/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.DeidentificationStages;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.DeidentificationValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.DisclosureControls;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Policies;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.PrivacyModels;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.PseudonymizationDomains;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Treatments;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecyclePackage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shape of the de-identification metamodel, its OCL invariants, and its stages in an XMI file. */
class DeidentificationModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final DeidentificationPackage P = DeidentificationPackage.eINSTANCE;
    private static final LifecyclePackage L = LifecyclePackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void classesEnumerationsAndTheirSupertypes() {
        List<EClass> classes = P.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast)
                .toList();
        assertThat(classes).hasSize(23);
        assertThat(classes).filteredOn(EClass::isAbstract).extracting(ENamedElement::getName)
                .containsExactly("Treatment", "PrivacyModel");
        assertThat(P.getEClassifiers()).filteredOn(EEnum.class::isInstance).extracting(ENamedElement::getName)
                .containsExactly("AttributeRole", "PseudonymMethod", "Consistency", "Outcome", "DiversityVariant",
                        "DisclosureMethod");
        assertThat(P.getDeidentificationPolicy().getESuperTypes()).containsExactly(CorePackage.Literals.CONSTRAINT);
        assertThat(P.getDisclosureControl().getESuperTypes()).containsExactly(CorePackage.Literals.CONSTRAINT);
        assertThat(P.getTreatment().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getPrivacyModel().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getDominanceRule().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getPseudonymizationDomain().getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getPseudonymize().getESuperTypes()).containsExactly(L.getDestructiveStage());
        assertThat(P.getAnonymize().getESuperTypes()).containsExactly(L.getTerminalStage());
        assertThat(P.getDestroyLinkage().getESuperTypes()).containsExactly(L.getTerminalStage());
        for (EClass c : List.of(P.getUnchanged(), P.getRemoval(), P.getMasking(), P.getTruncation(), P.getRounding(),
                P.getTopBottomCoding(), P.getNoiseAddition(), P.getDateShift(), P.getGeneralization(),
                P.getPseudonymization())) {
            assertThat(c.getESuperTypes()).as(c.getName()).containsExactly(P.getTreatment());
        }
        for (EClass c : List.of(P.getKAnonymity(), P.getLDiversity(), P.getTCloseness(), P.getDifferentialPrivacy())) {
            assertThat(c.getESuperTypes()).as(c.getName()).containsExactly(P.getPrivacyModel());
        }
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getNsPrefix()).isEqualTo("deid");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void ownReferencesAreOneWayAndListsUnordered() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    if (r.isMany()) {
                        assertThat(r.isOrdered()).as(r.getName()).isFalse();
                    }
                    if (!r.isContainment()) {
                        assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                                .as(r.getName()).startsWith("deid");
                    }
                }
            }
        }
        assertThat(P.getDeidentificationPolicy_Treatment().isContainment()).isTrue();
        assertThat(P.getDeidentificationPolicy_Model().isContainment()).isTrue();
        assertThat(P.getDisclosureControl_Dominance().isContainment()).isTrue();
    }

    @Test
    void attributesUseCwmDataTypesThePeriodOrOwnEnumerations() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EAttribute a : cls.getEAttributes()) {
                    EClassifier type = a.getEAttributeType();
                    assertThat(type == L.getPeriod() || type.getEPackage() == CorePackage.eINSTANCE
                            || type.getEPackage() == P).as(a.getName()).isTrue();
                }
            }
        }
    }

    @Test
    void constrainsClassifiers() {
        Fixture f = new Fixture();
        DeidentificationPolicy policy = Policies.create(f.rules, "patients", Outcome.PSEUDONYMOUS, f.patient);
        assertThat(severity((v, d) -> v.validateDeidentificationPolicy_ConstrainsClassifiers(policy, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        policy.getConstrainedElement().add(f.name);
        assertThat(severity((v, d) -> v.validateDeidentificationPolicy_ConstrainsClassifiers(policy, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void oneTreatmentPerFeature() {
        Fixture f = new Fixture();
        DeidentificationPolicy policy = Policies.create(f.rules, "patients", Outcome.PSEUDONYMOUS, f.patient);
        Treatments.remove(policy, f.name, AttributeRole.IDENTIFYING);
        assertThat(severity((v, d) -> v.validateDeidentificationPolicy_OneTreatmentPerFeature(policy, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        Treatments.mask(policy, f.name, AttributeRole.IDENTIFYING, "X###");
        assertThat(severity((v, d) -> v.validateDeidentificationPolicy_OneTreatmentPerFeature(policy, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void policyIsAnonymous() {
        Fixture f = new Fixture();
        DeidentificationPolicy anonymous = Policies.create(f.rules, "statistics", Outcome.ANONYMOUS, f.patient);
        DeidentificationPolicy pseudonymous = Policies.create(f.rules, "research", Outcome.PSEUDONYMOUS, f.patient);
        RetentionSchedule schedule = RetentionSchedules.create(f.rules, "patients", PeriodStarts.valueTime(), f.patient);
        Anonymize right = DeidentificationStages.anonymize(schedule, "anonymize", "P1Y", anonymous);
        assertThat(severity((v, d) -> v.validateAnonymize_PolicyIsAnonymous(right, d, ctx()))).isEqualTo(Diagnostic.OK);
        right.setPolicy(pseudonymous);
        assertThat(severity((v, d) -> v.validateAnonymize_PolicyIsAnonymous(right, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void widthOrGranularity() {
        Fixture f = new Fixture();
        DeidentificationPolicy policy = Policies.create(f.rules, "patients", Outcome.PSEUDONYMOUS, f.patient);
        Generalization g = Treatments.generalizeTo(policy, f.name, AttributeRole.QUASI_IDENTIFYING, "year");
        assertThat(severity((v, d) -> v.validateGeneralization_WidthOrGranularity(g, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        g.setGranularity(null);
        assertThat(severity((v, d) -> v.validateGeneralization_WidthOrGranularity(g, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void saysSomething() {
        Fixture f = new Fixture();
        DisclosureControl control = DisclosureControls.create(f.rules, "cells", DisclosureMethod.SUPPRESSION,
                f.patient);
        assertThat(severity((v, d) -> v.validateDisclosureControl_SaysSomething(control, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
        DisclosureControls.minimumCellCount(control, 3);
        assertThat(severity((v, d) -> v.validateDisclosureControl_SaysSomething(control, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        control.unsetMinimumCellCount();
        DisclosureControls.dominance(control, 2, 85);
        assertThat(severity((v, d) -> v.validateDisclosureControl_SaysSomething(control, d, ctx())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void stagesOfThisModelTravelInASchedule(@TempDir Path dir) throws Exception {
        Fixture f = new Fixture();
        DeidentificationPolicy research = Policies.create(f.rules, "research", Outcome.PSEUDONYMOUS, f.patient);
        DeidentificationPolicy statistics = Policies.create(f.rules, "statistics", Outcome.ANONYMOUS, f.patient);
        PrivacyModels.kAnonymity(statistics, 5);
        PseudonymizationDomain domain = PseudonymizationDomains.create(f.rules, "study", f.trustCentre);
        Treatments.pseudonymize(research, f.name, AttributeRole.IDENTIFYING, PseudonymMethod.KEYED_HASH,
                Consistency.GLOBAL, domain);
        RetentionSchedule patients = RetentionSchedules.create(f.rules, "patients", PeriodStarts.valueTime(), f.patient);
        DeidentificationStages.pseudonymize(patients, "pseudonymize", "P6M", research);
        DeidentificationStages.anonymize(patients, "anonymize", "P10Y", statistics);
        assertThat(patients.getBody().getBody()).isEqualTo("from the time of the value: pseudonymize research after "
                + "P6M, anonymize statistics after P10Y");
        RetentionSchedule keys = RetentionSchedules.create(f.rules, "keys", PeriodStarts.valueTime(), domain);
        DeidentificationStages.destroyLinkage(keys, "destroy", "P10Y", domain);
        assertThat(keys.getBody().getBody()).isEqualTo("from the time of the value: destroy linkage study after P10Y");

        URI uri = URI.createFileURI(dir.resolve("clinic.xmi").toString());
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Resource out = rs.createResource(uri);
        out.getContents().add(f.model);
        out.save(Map.of());
        String xmi = Files.readString(dir.resolve("clinic.xmi"), StandardCharsets.UTF_8);
        assertThat(xmi).contains("xsi:type=\"lifecycle:RetentionSchedule\"", "<stage xsi:type=\"deid:Pseudonymize\"",
                "<stage xsi:type=\"deid:Anonymize\"", "<stage xsi:type=\"deid:DestroyLinkage\"",
                "xsi:type=\"deid:DeidentificationPolicy\"", "<treatment xsi:type=\"deid:Pseudonymization\"",
                "<model xsi:type=\"deid:KAnonymity\"");

        for (boolean indexed : new boolean[] {false, true}) {
            ResourceSet fresh = new ResourceSetImpl();
            fresh.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
            Model loaded = (Model) fresh.getResource(uri, true).getContents().get(0);
            if (indexed) {
                InverseReferences.install(fresh);
            }
            Package rules = (Package) loaded.getOwnedElement().get(1);
            PseudonymizationDomain loadedDomain = rules.getOwnedElement().stream()
                    .filter(PseudonymizationDomain.class::isInstance).map(PseudonymizationDomain.class::cast)
                    .findFirst().orElseThrow();
            assertThat(PseudonymizationDomains.destroyedBy(loadedDomain)).as("indexed " + indexed).hasSize(1);
            assertThat(PseudonymizationDomains.pseudonymizations(loadedDomain)).as("indexed " + indexed).hasSize(1);
            RetentionSchedule loadedSchedule = rules.getOwnedElement().stream()
                    .filter(RetentionSchedule.class::isInstance).map(RetentionSchedule.class::cast)
                    .filter(s -> s.getName().equals("patients")).findFirst().orElseThrow();
            assertThat(loadedSchedule.getStage().get(1)).isInstanceOfSatisfying(Anonymize.class,
                    a -> assertThat(a.getPolicy().getOutcome()).isEqualTo(Outcome.ANONYMOUS));
        }
    }

    private static Map<Object, Object> ctx() {
        return new HashMap<>();
    }

    private static int severity(BiPredicate<DeidentificationValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(DeidentificationValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }

    /** Model {@code clinic} › package {@code data} › class {@code Patient}; package {@code rules}. */
    private static final class Fixture {
        final Model model = CoreFactory.eINSTANCE.createModel();
        final Package data = CoreFactory.eINSTANCE.createPackage();
        final Package rules = CoreFactory.eINSTANCE.createPackage();
        final Class patient = CoreFactory.eINSTANCE.createClass();
        final Attribute name = CoreFactory.eINSTANCE.createAttribute();
        final ResponsibleParty trustCentre = BusinessinformationFactory.eINSTANCE.createResponsibleParty();

        Fixture() {
            model.setName("clinic");
            data.setName("data");
            rules.setName("rules");
            model.getOwnedElement().add(data);
            model.getOwnedElement().add(rules);
            patient.setName("Patient");
            data.getOwnedElement().add(patient);
            name.setName("name");
            patient.getFeature().add(name);
            trustCentre.setName("trustCentre");
            trustCentre.setResponsibility("trusted third party");
            rules.getOwnedElement().add(trustCentre);
        }
    }
}
