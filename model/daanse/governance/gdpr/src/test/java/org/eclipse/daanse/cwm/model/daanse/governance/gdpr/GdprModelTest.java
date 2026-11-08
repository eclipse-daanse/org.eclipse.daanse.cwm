/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.WarehouseoperationFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPackage;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.AssessmentFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.AssessmentPackage;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.Risk;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.RiskAssessment;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.RiskAssessmentKind;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.util.AssessmentValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingRecord;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.ProcessingPackage;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.util.ProcessingValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.util.DataProcessingRecords;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.util.DataProcessings;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.ObligationsPackage;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The shape of the gdpr metamodel after its port to a builder, and its OCL invariants. */
class GdprModelTest {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final ProcessingPackage PROCESSING = ProcessingPackage.eINSTANCE;
    private static final AssessmentPackage ASSESSMENT = AssessmentPackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void namespacesStayPrefixesLoseTheirDot() {
        // the root holds only the two subpackages, so no package class is generated for it: read it from the file
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        EPackage root = (EPackage) rs.getResource(URI.createFileURI(Path.of("model/gdpr.ecore").toAbsolutePath()
                .toString()), true).getContents().get(0);
        assertThat(root.getNsURI()).isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.gdpr");
        assertThat(root.getNsPrefix()).isEqualTo("gdpr");
        assertThat(root.getEClassifiers()).isEmpty();
        assertThat(root.getESubpackages()).extracting(EPackage::getName).containsExactly("processing", "assessment");
        assertThat(PROCESSING.getNsURI()).isEqualTo(root.getNsURI() + "/processing");
        assertThat(ASSESSMENT.getNsURI()).isEqualTo(root.getNsURI() + "/assessment");
        assertThat(List.of(PROCESSING, ASSESSMENT)).extracting(EPackage::getNsPrefix).containsExactly("gdprprocessing",
                "gdprassessment");
        for (EPackage p : List.of(PROCESSING, ASSESSMENT)) {
            assertThat(p.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails()
                    .get("hiddenOpposites")).as(p.getName()).isEqualTo("true");
            assertThat(p.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails()
                    .get("validationDelegates")).as(p.getName()).isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
        }
    }

    @Test
    void cwmDataTypesOneWayReferencesAndCaptionsEverywhere() {
        int captioned = 0;
        for (EPackage p : List.of(PROCESSING, ASSESSMENT)) {
            for (EClassifier c : p.getEClassifiers()) {
                captioned += captions(c);
                if (c instanceof EEnum e) {
                    for (var l : e.getELiterals()) {
                        captioned += captions(l);
                    }
                }
                if (c instanceof EClass cls) {
                    for (EStructuralFeature f : cls.getEStructuralFeatures()) {
                        captioned += captions(f);
                        if (f instanceof EAttribute a) {
                            EPackage typePackage = a.getEAttributeType().getEPackage();
                            assertThat(typePackage == CorePackage.eINSTANCE || typePackage == PROCESSING
                                    || typePackage == ASSESSMENT).as(f.getName()).isTrue();
                        } else if (f instanceof EReference r) {
                            assertThat(r.getEOpposite()).as(r.getName()).isNull();
                            if (!r.isContainment()) {
                                assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                                        .as(r.getName()).startsWith("gdpr");
                            }
                        }
                    }
                }
            }
        }
        assertThat(captioned).as("every classifier, feature and literal").isEqualTo(141);
    }

    @Test
    void anErasureIsADeletionObligationAndTheSourcesAreDocuments() {
        assertThat(PROCESSING.getDataErasure().getESuperTypes())
                .containsExactly(ObligationsPackage.Literals.DELETION_OBLIGATION);
        assertThat(PROCESSING.getDataErasure().getEStructuralFeatures()).isEmpty();
        assertThat(names(PROCESSING.getDataProcessing())).doesNotContain("legalBasis");
        assertThat(names(PROCESSING.getDataTransmission())).doesNotContain("legalBasis").contains("policy");
        assertThat(PROCESSING.getDataTransmission_Policy().getEReferenceType())
                .isSameAs(DeidentificationPackage.Literals.DEIDENTIFICATION_POLICY);
        assertThat(names(PROCESSING.getDataProcessingLawfulness())).contains("dataProcessingLawfulnessAnnotation");
    }

    @Test
    void aRecordHoldsProcessingsOnly() {
        Package governance = CoreFactory.eINSTANCE.createPackage();
        DataProcessingRecord record = DataProcessingRecords.create(governance, "record", party("controller"),
                party("office"), party("officer"));
        assertThat(severity((v, d) -> v.validateDataProcessingRecord_RecordHoldsProcessings(record, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
        DataProcessings.create(record, "billing", "send invoices", "2025-01-01");
        assertThat(severity((v, d) -> v.validateDataProcessingRecord_RecordHoldsProcessings(record, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateDataProcessingRecord_OnlyProcessingsInRecord(record, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        record.getOwnedElement().add(CoreFactory.eINSTANCE.createClass());
        assertThat(severity((v, d) -> v.validateDataProcessingRecord_OnlyProcessingsInRecord(record, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void aRiskHasOneInherentAssessmentAndResidualOnesNameMeasures() {
        Risk risk = AssessmentFactory.eINSTANCE.createRisk();
        RiskAssessment inherent = AssessmentFactory.eINSTANCE.createRiskAssessment();
        inherent.setKind(RiskAssessmentKind.INHERENT);
        risk.getAssessment().add(inherent);
        assertThat(assessed((v, d) -> v.validateRisk_ExactlyOneInherentAssessment(risk, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        RiskAssessment residual = AssessmentFactory.eINSTANCE.createRiskAssessment();
        residual.setKind(RiskAssessmentKind.RESIDUAL);
        risk.getAssessment().add(residual);
        assertThat(assessed((v, d) -> v.validateRisk_ResidualAssessmentNamesMeasures(risk, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
        residual.getAppliedMeasure().add(WarehouseoperationFactory.eINSTANCE.createChangeRequest());
        assertThat(assessed((v, d) -> v.validateRisk_ResidualAssessmentNamesMeasures(risk, d, ctx())))
                .isEqualTo(Diagnostic.OK);
        risk.getAssessment().get(1).setKind(RiskAssessmentKind.INHERENT);
        assertThat(assessed((v, d) -> v.validateRisk_ExactlyOneInherentAssessment(risk, d, ctx())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static int captions(EModelElement el) {
        EAnnotation a = el.getEAnnotation(GENMODEL);
        boolean both = a != null && a.getDetails().containsKey("caption.de")
                && a.getDetails().containsKey("caption.en");
        assertThat(both).as(((ENamedElement) el).getName()).isTrue();
        return 1;
    }

    private static List<String> names(EClass c) {
        List<String> out = new ArrayList<>();
        c.getEStructuralFeatures().forEach(f -> out.add(f.getName()));
        return out;
    }

    private static ResponsibleParty party(String name) {
        ResponsibleParty party = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        party.setName(name);
        return party;
    }

    private static Map<Object, Object> ctx() {
        return new HashMap<>();
    }

    private static int severity(BiPredicate<ProcessingValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(ProcessingValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }

    private static int assessed(BiPredicate<AssessmentValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(AssessmentValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
