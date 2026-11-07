/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.criticality;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.ChangeRequest;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.Measurement;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.WarehouseoperationFactory;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.WarehouseActivity;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.WarehouseprocessFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.criticality.util.CriticalityValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shape of the criticality model and its OCL invariant, and one generic rating that rates any
 * {@code core::ModelElement}: written to XMI beside the rated elements and found again from them.
 */
class CriticalityModelTest {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final CriticalityPackage P = CriticalityPackage.eINSTANCE;

    private final CriticalityFactory cf = CriticalityFactory.eINSTANCE;
    private final CoreFactory core = CoreFactory.eINSTANCE;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void theNamespaceStaysAndThePackageDeclaresHiddenOppositesAndDelegates() {
        assertThat(P.getNsURI())
                .isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.criticality");
        assertThat(P.getNsPrefix()).isEqualTo("criticality");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void everyReferenceIsOneWayAndNamesItsOppositeRole() {
        int hidden = 0;
        for (EClassifier classifier : P.getEClassifiers()) {
            if (!(classifier instanceof EClass ec)) {
                continue;
            }
            for (EReference ref : ec.getEReferences()) {
                assertThat(ref.getEOpposite()).as("%s.%s carries an eOpposite", ec.getName(), ref.getName())
                        .isNull();
                if (ref.isContainment()) {
                    continue;
                }
                EAnnotation a = ref.getEAnnotation(EMOF);
                assertThat(a).as("%s.%s without Property.oppositeRoleName", ec.getName(), ref.getName())
                        .isNotNull();
                assertThat(a.getDetails().get("Property.oppositeRoleName")).startsWith("criticality");
                assertThat(ref.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                        .isEqualTo(a.getDetails().get("Property.oppositeRoleName"));
                hidden++;
            }
        }
        // element, measure, changeElement, affectedRating, measurement
        assertThat(hidden).isEqualTo(5);
    }

    @Test
    void cwmDataTypesAndCaptionsEverywhere() {
        int captioned = 0;
        for (EClassifier c : P.getEClassifiers()) {
            captioned += captions(c);
            if (c instanceof EEnum e) {
                for (EEnumLiteral l : e.getELiterals()) {
                    captioned += captions(l);
                }
            } else if (c instanceof EClass cls) {
                for (EStructuralFeature f : cls.getEStructuralFeatures()) {
                    captioned += captions(f);
                    if (f instanceof EAttribute a) {
                        EClassifier type = a.getEAttributeType();
                        assertThat(type.getEPackage() == CorePackage.eINSTANCE || type.getEPackage() == P)
                                .as(f.getName()).isTrue();
                    }
                }
            }
        }
        assertThat(captioned).as("every classifier, feature and literal").isEqualTo(101);
    }

    @Test
    void bsiLevelsAreThreeStepPlusUnassessed() {
        assertThat(CriticalityLevel.UNASSESSED.getValue()).isZero();
        assertThat(CriticalityLevel.NORMAL.getValue()).isEqualTo(1);
        assertThat(CriticalityLevel.HIGH.getValue()).isEqualTo(2);
        assertThat(CriticalityLevel.VERY_HIGH.getValue()).isEqualTo(3);
        // the classification of classified information is an axis of its own, beside the level
        assertThat(Classification.values()).hasSize(5);
    }

    @Test
    void anAssessmentHoldsGovernanceContentOnly() {
        CriticalityAssessment assessment = cf.createCriticalityAssessment();
        assessment.getOwnedElement().add(cf.createCriticalityRating());
        assertThat(severity(assessment)).isEqualTo(Diagnostic.OK);
        assessment.getOwnedElement().add(core.createClass());
        assertThat(severity(assessment)).isEqualTo(Diagnostic.ERROR);
    }

    /**
     * One {@code CriticalityRating} rates a class, an attribute, an association and a warehouse
     * process at once: all four are {@code core::ModelElement}s.
     */
    @Test
    void oneGenericRatingHitsClassFieldAssociationAndProcess() {
        Class clazz = core.createClass();
        clazz.setName("RESERVOIR_LOCATION");
        Attribute field = core.createAttribute();
        field.setName("GEO_COORDINATE");
        Association assoc = RelationshipsFactory.eINSTANCE.createAssociation();
        assoc.setName("SUPPLIES");
        WarehouseActivity process = WarehouseprocessFactory.eINSTANCE.createWarehouseActivity();
        process.setName("NIGHTLY_READING_ETL");

        CriticalityRating rating = cf.createCriticalityRating();
        rating.setName("criticality-waterworks");
        rating.setOverallLevel(CriticalityLevel.VERY_HIGH);
        rating.setClassification(Classification.CONFIDENTIAL);
        rating.setRationale("Location data of a critical installation; disclosure raises attack risk.");
        rating.getElement().addAll(List.of(clazz, field, assoc, process));
        rating.getDimensionScore().add(score(CriticalityDimension.CONFIDENTIALITY, CriticalityLevel.VERY_HIGH));
        rating.getDimensionScore().add(score(CriticalityDimension.DISCLOSURE, CriticalityLevel.HIGH));

        assertThat(rating.getElement()).containsExactly(clazz, field, assoc, process);
        EReference element = P.getCriticalityRating_Element();
        assertThat(element.getEType()).isSameAs(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(element.getEOpposite()).isNull();
    }

    @Test
    void savesAndReloadsWithoutUnresolvedProxies() throws Exception {
        // the CWM objects in one resource, the assessment in another
        Package schema = core.createPackage();
        schema.setName("WATERWORKS");
        Class table = core.createClass();
        table.setName("RESERVOIR_LOCATION");
        schema.getOwnedElement().add(table);

        // the measure is a warehouseoperation::ChangeRequest that affects the protected element
        ChangeRequest encryption = WarehouseoperationFactory.eINSTANCE.createChangeRequest();
        encryption.setName("Encryption at rest");
        encryption.setChangeDescription("AES-256 on all location tables.");
        encryption.setChangeReason("Protect the location data of a critical installation.");
        encryption.setStatus("implemented");
        encryption.setCompleted(true);
        encryption.getModelElement().add(table);

        CriticalityAssessment assessment = cf.createCriticalityAssessment();
        assessment.setName("kritis-water-2026");
        assessment.setValidFrom("2026-01-01");

        SectorProfile sector = cf.createSectorProfile();
        sector.setName("profile-water");
        sector.setSector(Sector.WATER);
        sector.setCategory(EntityCategory.CRITICAL_INSTALLATION);

        RegimeProfile regime = cf.createRegimeProfile();
        regime.setName("profile-bsig");
        regime.setRegime(LegalRegime.BSIG);

        // the score rests on a measurement that carries the raw value
        Measurement measurement = WarehouseoperationFactory.eINSTANCE.createMeasurement();
        measurement.setName("confidentiality-measurement");
        measurement.setValue("3.0");
        measurement.setUnit("BSI protection need");
        measurement.setType("estimate");
        measurement.setModelElement(table);

        DimensionScore confidentiality = score(CriticalityDimension.CONFIDENTIALITY, CriticalityLevel.VERY_HIGH);
        confidentiality.setMeasurement(measurement);

        CriticalityRating rating = cf.createCriticalityRating();
        rating.setName("rating-elevated-tank");
        rating.setOverallLevel(CriticalityLevel.VERY_HIGH);
        rating.setClassification(Classification.CONFIDENTIAL);
        rating.setRationale("Critical installation.");
        rating.getElement().add(table);
        rating.getDimensionScore().add(confidentiality);

        MeasureAssignment assignment = cf.createMeasureAssignment();
        assignment.setName("assignment-encryption");
        assignment.setMeasure(encryption);
        assignment.setRole(MeasureRole.PREVENTIVE);
        assignment.setEffectiveness(CriticalityLevel.HIGH);
        rating.getMeasureAssignment().add(assignment);

        // the measurement and the measure live in the assessment, as its invariant allows
        assessment.getOwnedElement().addAll(List.of(sector, regime, rating, measurement, encryption));
        assertThat(severity(assessment)).isEqualTo(Diagnostic.OK);

        ResourceSet set = newResourceSet();
        Resource cwm = set.createResource(URI.createFileURI(tempDir.resolve("waterworks.xmi").toString()));
        Resource crit = set.createResource(URI.createFileURI(tempDir.resolve("kritis.xmi").toString()));
        cwm.getContents().add(schema);
        crit.getContents().add(assessment);
        cwm.save(Map.of());
        crit.save(Map.of());

        ResourceSet reload = newResourceSet();
        Resource back = reload.getResource(crit.getURI(), true);
        EcoreUtil.resolveAll(reload);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(reload)).isEmpty();

        CriticalityAssessment loaded = (CriticalityAssessment) back.getContents().get(0);
        assertThat(loaded.getValidFrom()).isEqualTo("2026-01-01");
        CriticalityRating loadedRating = loaded.getOwnedElement().stream().filter(CriticalityRating.class::isInstance)
                .map(CriticalityRating.class::cast).findFirst().orElseThrow();
        assertThat(loadedRating.getOverallLevel()).isEqualTo(CriticalityLevel.VERY_HIGH);
        assertThat(((Class) loadedRating.getElement().get(0)).getName()).isEqualTo("RESERVOIR_LOCATION");
        ChangeRequest loadedMeasure = loadedRating.getMeasureAssignment().get(0).getMeasure();
        assertThat(loadedMeasure.getStatus()).isEqualTo("implemented");
        assertThat(loadedMeasure.isCompleted()).isTrue();
        Measurement loadedMeasurement = loadedRating.getDimensionScore().get(0).getMeasurement();
        assertThat(loadedMeasurement.getValue()).isEqualTo("3.0");
        assertThat(loadedMeasurement.getType()).isEqualTo("estimate");
    }

    /**
     * The reference from a rating to what it rates is one way: from the rated element back to its
     * rating only a cross-reference index leads, without one the way back finds nothing.
     */
    @Test
    void backwardNavigationFromRatedElementNeedsTheIndex() {
        Class table = core.createClass();
        table.setName("SECURITIES_POSITION");
        CriticalityRating rating = cf.createCriticalityRating();
        rating.setName("r");
        rating.setOverallLevel(CriticalityLevel.HIGH);
        rating.setRationale("Market stability.");
        rating.getElement().add(table);

        ResourceSet set = newResourceSet();
        Resource cwm = set.createResource(URI.createFileURI("mem:/cwm.xmi"));
        Resource crit = set.createResource(URI.createFileURI("mem:/crit.xmi"));
        cwm.getContents().add(table);
        crit.getContents().add(rating);

        EReference element = P.getCriticalityRating_Element();
        assertThat(referencing(set, table, element)).isEmpty();

        InverseReferences.install(set);
        assertThat(referencing(set, table, element)).containsExactly(rating);
    }

    /** Lines of the XMI {@link #inXmi()} writes: an assessment with a sector profile and a rating of a class. */
    private static final String XMI = """
            <ownedElement xsi:type="core:Class" xmi:id="Reservoir" name="Reservoir"/>
            <ownedElement xsi:type="criticality:CriticalityAssessment" name="kritis-water-2026" \
            validFrom="2026-01-01">
            <ownedElement xsi:type="criticality:SectorProfile" name="water" sector="WATER" \
            category="CRITICAL_INSTALLATION"/>
            <ownedElement xsi:type="criticality:CriticalityRating" name="reservoir" element="Reservoir" \
            overallLevel="VERY_HIGH" classification="CONFIDENTIAL" \
            rationale="Location of a critical installation.">
            <dimensionScore level="VERY_HIGH"/>
            <dimensionScore dimension="AVAILABILITY" level="HIGH"/>
            """;

    @Test
    void inXmi() throws IOException {
        Package waterworks = core.createPackage();
        waterworks.setName("waterworks");
        Class reservoir = core.createClass();
        reservoir.setName("Reservoir");
        waterworks.getOwnedElement().add(reservoir);

        CriticalityAssessment assessment = cf.createCriticalityAssessment();
        assessment.setName("kritis-water-2026");
        assessment.setValidFrom("2026-01-01");
        SectorProfile sector = cf.createSectorProfile();
        sector.setName("water");
        sector.setSector(Sector.WATER);
        sector.setCategory(EntityCategory.CRITICAL_INSTALLATION);
        CriticalityRating rating = cf.createCriticalityRating();
        rating.setName("reservoir");
        rating.setOverallLevel(CriticalityLevel.VERY_HIGH);
        rating.setClassification(Classification.CONFIDENTIAL);
        rating.setRationale("Location of a critical installation.");
        rating.getElement().add(reservoir);
        rating.getDimensionScore().add(score(CriticalityDimension.CONFIDENTIALITY, CriticalityLevel.VERY_HIGH));
        rating.getDimensionScore().add(score(CriticalityDimension.AVAILABILITY, CriticalityLevel.HIGH));
        assessment.getOwnedElement().addAll(List.of(sector, rating));
        waterworks.getOwnedElement().add(assessment);
        assertThat(severity(assessment)).isEqualTo(Diagnostic.OK);

        Resource r = newResourceSet().createResource(URI.createURI("waterworks.xmi"));
        r.getContents().add(waterworks);
        ((XMLResource) r).setID(reservoir, "Reservoir");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8);
        Files.writeString(Path.of(System.getProperty("user.dir"), "target", "waterworks.xmi"), xmi,
                StandardCharsets.UTF_8);
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    private DimensionScore score(CriticalityDimension dimension, CriticalityLevel level) {
        DimensionScore s = cf.createDimensionScore();
        s.setDimension(dimension);
        s.setLevel(level);
        return s;
    }

    private static int captions(EModelElement el) {
        EAnnotation a = el.getEAnnotation(GENMODEL);
        assertThat(a != null && a.getDetails().containsKey("caption.de") && a.getDetails().containsKey("caption.en"))
                .as(el.toString()).isTrue();
        return 1;
    }

    private static int severity(CriticalityAssessment assessment) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        CriticalityValidator.INSTANCE.validateCriticalityAssessment_OnlyGovernanceContent(assessment, diagnostic,
                new HashMap<>());
        return diagnostic.getSeverity();
    }

    private static List<EObject> referencing(ResourceSet set, EObject target, EReference ref) {
        ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(set);
        if (adapter == null) {
            return List.of();
        }
        return adapter.getInverseReferences(target, true).stream().filter(s -> s.getEStructuralFeature() == ref)
                .map(EStructuralFeature.Setting::getEObject).toList();
    }

    private static ResourceSet newResourceSet() {
        ResourceSet set = new ResourceSetImpl();
        set.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        return set;
    }
}
