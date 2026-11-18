/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The shape of the spatial metamodel and its OCL invariants. */
class SpatialModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final SpatialPackage P = SpatialPackage.eINSTANCE;
    private static final SpatialFactory F = SpatialFactory.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void sixModelElementsAGeometryTypeAndTwelveEnumerations() {
        List<EClass> classes = List.of(P.getGeometryFeature(), P.getSpatialRelation(), P.getGridLevel(),
                P.getDistanceBand(), P.getReferenceSystem(), P.getGridSystem());
        for (EClass c : classes) {
            assertThat(c.getESuperTypes()).as(c.getName()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
            assertThat(c.isAbstract()).as(c.getName()).isFalse();
        }
        assertThat(P.getGeometryType().getESuperTypes()).as("the logical type of a geometry is a CWM data type")
                .containsExactly(CorePackage.Literals.DATA_TYPE);
        assertThat(P.getGeometryType().getEStructuralFeatures()).as("what a geometry holds says its feature")
                .isEmpty();
        assertThat(P.getEClassifiers().stream().filter(EClass.class::isInstance)).hasSize(7);
        assertThat(P.getEClassifiers().stream().filter(EEnum.class::isInstance)).hasSize(12);
    }

    /**
     * The order in which a column stores its coordinates and the epoch they hold for are facts of the coordinates: the
     * geometry feature holds them, the reference system only what its register defines.
     */
    @Test
    void theStoredAxisOrderAndTheEpochBelongToTheGeometryFeature() {
        assertThat(P.getGeometryFeature_AxisOrder().getEAttributeType()).isSameAs(P.getAxisOrder());
        assertThat(P.getGeometryFeature_AxisOrder().getLowerBound()).isEqualTo(1);
        assertThat(P.getGeometryFeature_CoordinateEpoch().getLowerBound()).isZero();
        assertThat(P.getReferenceSystem().getEStructuralFeature("coordinateEpoch")).as("not of the system").isNull();
        assertThat(P.getReferenceSystem_AxisOrder()).as("the order the register defines").isNotNull();
        ReferenceSystem wgs84 = F.createReferenceSystem();
        wgs84.setAxisOrder(AxisOrder.NORTH_EAST);
        GeometryFeature lonLat = F.createGeometryFeature();
        lonLat.setReferenceSystem(wgs84);
        GeometryFeature latLon = F.createGeometryFeature();
        latLon.setReferenceSystem(wgs84);
        latLon.setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(lonLat.getAxisOrder()).as("one system, two stored orders").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(latLon.getAxisOrder()).isEqualTo(AxisOrder.NORTH_EAST);
    }

    @Test
    void theFirstLiteralIsWhatAnUnsetValueReads() {
        assertThat(literals(P.getGeometryKind())).containsExactly("POINT", "LINE", "POLYGON", "MIXED");
        assertThat(literals(P.getGeometryStorage())).containsExactly("NATIVE", "WKB", "WKT", "GEOJSON");
        assertThat(literals(P.getSpatialPredicate())).containsExactly("WITHIN", "COVERED_BY", "INTERSECTS",
                "CENTROID_WITHIN", "NEAREST", "WITHIN_DISTANCE");
        assertThat(literals(P.getSpatialAssignment())).containsExactly("EXACTLY_ONE", "LARGEST_SHARE",
                "ALL_WEIGHTED", "ALL_EQUAL");
        assertThat(literals(P.getSpatialWeight())).containsExactly("AREA", "LENGTH");
        assertThat(literals(P.getUnmatchedPolicy())).as("a row keeps a null parent, cell or class unless said")
                .containsExactly("KEEP_NULL", "ERROR", "DROP");
        assertThat(literals(P.getTieBreak())).as("no tie break is a tie that is an error")
                .containsExactly("ERROR", "LOWEST_PARENT_KEY");
        assertThat(literals(P.getResultForm())).as("a table, which any database reads")
                .containsExactly("TABLE", "VIEW");
        assertThat(literals(P.getGeometryMultiplicity())).as("single or multi unless said otherwise")
                .containsExactly("EITHER", "SINGLE", "MULTI");
        assertThat(literals(P.getReferenceSystemAuthority())).containsExactly("EPSG", "OGC");
        assertThat(literals(P.getReferenceSystemKind())).containsExactly("GEOGRAPHIC", "PROJECTED", "GEOCENTRIC");
        assertThat(literals(P.getAxisOrder())).as("east first, as the databases hold it")
                .containsExactly("EAST_NORTH", "NORTH_EAST");
    }

    /**
     * A grid level and a distance band say what happens to a row they cannot place as a relation does: the same
     * enumeration, required, the same default (the first literal, a null cell or class).
     */
    @Test
    void aGridLevelAndABandTakeTheUnmatchedPolicyOfARelation() {
        for (EAttribute a : List.of(P.getSpatialRelation_Unmatched(), P.getGridLevel_Unmatched(),
                P.getDistanceBand_Unmatched())) {
            assertThat(a.getEAttributeType()).as(a.getEContainingClass().getName()).isSameAs(P.getUnmatchedPolicy());
            assertThat(a.getLowerBound()).as(a.getEContainingClass().getName()).isEqualTo(1);
            assertThat(a.getDefaultValueLiteral()).as(a.getEContainingClass().getName()).isNull();
        }
        assertThat(F.createSpatialRelation().getUnmatched()).isEqualTo(UnmatchedPolicy.KEEP_NULL);
        assertThat(F.createGridLevel().getUnmatched()).isEqualTo(UnmatchedPolicy.KEEP_NULL);
        assertThat(F.createDistanceBand().getUnmatched()).isEqualTo(UnmatchedPolicy.KEEP_NULL);
    }

    @Test
    void anOptionalWeightOrSystemIsToldFromItsDefault() {
        assertThat(P.getSpatialRelation_Weight().isUnsettable()).isTrue();
        assertThat(P.getSpatialRelation_WorkingSystem().getLowerBound()).as("none: the features' own").isZero();
        assertThat(P.getDistanceBand_WorkingSystem().getLowerBound()).isZero();
        SpatialRelation r = F.createSpatialRelation();
        assertThat(r.isSetWeight()).isFalse();
        r.setWeight(SpatialWeight.AREA);
        assertThat(r.isSetWeight()).as("AREA is the first literal, and set").isTrue();
        assertThat(r.getMinimumShare()).as("no minimum share is zero").isEqualTo("0");
        assertThat(P.getGridLevel_Resolution().isUnsettable()).as("0 is a resolution of H3").isTrue();
        GridLevel level = F.createGridLevel();
        assertThat(level.isSetResolution()).isFalse();
        level.setResolution(0);
        assertThat(level.isSetResolution()).as("0 and set").isTrue();
        assertThat(P.getDistanceBand_Bound().isOrdered()).isTrue();
        assertThat(P.getDistanceBand_Bound().isUnique()).as("a repeated bound is the validator's to name").isFalse();
    }

    @Test
    void referencesAreOneWayWithAnOppositeRoleName() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    assertThat(r.isContainment()).as(r.getName()).isFalse();
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(r.getName()).startsWith("spatial");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(r.getName()).startsWith("spatial");
                }
            }
        }
        assertThat(P.getGeometryFeature_ModelElement().getEType()).isSameAs(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getSpatialRelation_ChildKey().getEType()).isSameAs(CorePackage.Literals.ATTRIBUTE);
        assertThat(P.getSpatialRelation_Result().getEType()).isSameAs(CorePackage.Literals.CLASS);
        assertThat(P.getGeometryFeature_ReferenceSystem().getEType()).isSameAs(P.getReferenceSystem());
        assertThat(P.getGeometryFeature_ReferenceSystem().getLowerBound()).isEqualTo(1);
        assertThat(P.getSpatialRelation_WorkingSystem().getEType()).isSameAs(P.getReferenceSystem());
        assertThat(P.getDistanceBand_WorkingSystem().getEType()).isSameAs(P.getReferenceSystem());
        for (EReference key : List.of(P.getGridLevel_SourceKey(), P.getDistanceBand_SourceKey())) {
            assertThat(key.getEType()).as(key.getName()).isSameAs(CorePackage.Literals.ATTRIBUTE);
            assertThat(key.getLowerBound()).as("a resolver writes the source key first").isEqualTo(1);
        }
        for (EReference result : List.of(P.getGridLevel_Result(), P.getDistanceBand_Result())) {
            assertThat(result.getEType()).as(result.getName()).isSameAs(CorePackage.Literals.CLASS);
            assertThat(result.getLowerBound()).as("declared in advance or written").isZero();
        }
        assertThat(P.getGridLevel_System().getEType()).as("an instance of the model").isSameAs(P.getGridSystem());
        assertThat(P.getGridLevel_System().getLowerBound()).isEqualTo(1);
        assertThat(P.getGridLevel_ResultForm().getEType()).isSameAs(P.getResultForm());
        assertThat(P.getDistanceBand_ResultForm().getEType()).isSameAs(P.getResultForm());
        assertThat(P.getGridLevel().getEStructuralFeature("keyColumn")).as("the result holds the cell key").isNull();
        assertThat(P.getDistanceBand().getEStructuralFeature("bandColumn")).as("the result holds the class").isNull();
    }

    @Test
    void aReferenceSystemDefaultsToTwoStaticAxes() {
        ReferenceSystem system = F.createReferenceSystem();
        assertThat(system.getAxes()).isEqualTo(2);
        assertThat(system.isSetAxisOrder()).as("the axis order is stated, not defaulted").isFalse();
        assertThat(system.isDynamic()).isFalse();
        assertThat(system.getVersion()).as("none is 0").isEqualTo("0");
        GeometryFeature feature = F.createGeometryFeature();
        assertThat(feature.isHasHeight()).isFalse();
        assertThat(feature.getMultiplicity()).isEqualTo(GeometryMultiplicity.EITHER);
        assertThat(feature.getAxisOrder()).as("stored east first unless said").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(feature.getCoordinateEpoch()).isNull();
    }

    /**
     * The axis order of a reference system copies its register and has no default: a default would copy it wrongly
     * for every system of the other order. Required and unsettable, so that EMF's own check finds it unset, and the
     * first literal stated counts as stated.
     */
    @Test
    void aReferenceSystemStatesItsAxisOrder() {
        EAttribute axisOrder = P.getReferenceSystem_AxisOrder();
        assertThat(axisOrder.getLowerBound()).isEqualTo(1);
        assertThat(axisOrder.isUnsettable()).isTrue();
        assertThat(axisOrder.getDefaultValueLiteral()).isNull();
        ReferenceSystem system = F.createReferenceSystem();
        system.setCode("25832");
        assertThat(system.isSetAxisOrder()).isFalse();
        assertThat(Diagnostician.INSTANCE.validate(system).getChildren()).as("required and unset")
                .anyMatch(d -> d.getSeverity() == Diagnostic.ERROR && d.getData().contains(axisOrder));
        system.setAxisOrder(AxisOrder.EAST_NORTH);
        assertThat(system.isSetAxisOrder()).as("the first literal, stated").isTrue();
        assertThat(Diagnostician.INSTANCE.validate(system).getChildren()).as("stated")
                .noneMatch(d -> d.getData().contains(axisOrder));
    }

    @Test
    void attributesUseCwmDataTypesOrTheEnumerations() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EAttribute a : cls.getEAttributes()) {
                    assertThat(a.getEAttributeType().getEPackage()).as(a.getName()).isIn(CorePackage.eINSTANCE, P)
                            .isNotSameAs(EcorePackage.eINSTANCE);
                }
            }
        }
    }

    @Test
    void aReferenceSystemHasTwoOrThreeAxes() {
        ReferenceSystem system = F.createReferenceSystem();
        assertThat(severity((v, d) -> v.validateReferenceSystem_AxesTwoOrThree(system, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        system.setAxes(3);
        assertThat(severity((v, d) -> v.validateReferenceSystem_AxesTwoOrThree(system, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        system.setAxes(4);
        assertThat(severity((v, d) -> v.validateReferenceSystem_AxesTwoOrThree(system, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void anEpochOnlyInADynamicSystem() {
        ReferenceSystem system = F.createReferenceSystem();
        system.setDynamic(true);
        GeometryFeature feature = F.createGeometryFeature();
        feature.setReferenceSystem(system);
        feature.setCoordinateEpoch("2010.5");
        assertThat(severity((v, d) -> v.validateGeometryFeature_EpochOnlyWhenDynamic(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        system.setDynamic(false);
        assertThat(severity((v, d) -> v.validateGeometryFeature_EpochOnlyWhenDynamic(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        feature.setCoordinateEpoch(null);
        assertThat(severity((v, d) -> v.validateGeometryFeature_EpochOnlyWhenDynamic(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void geoJsonHoldsItsCoordinatesEastFirst() {
        GeometryFeature feature = F.createGeometryFeature();
        feature.setStorage(GeometryStorage.GEOJSON);
        assertThat(severity((v, d) -> v.validateGeometryFeature_GeoJsonEastFirst(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        feature.setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(severity((v, d) -> v.validateGeometryFeature_GeoJsonEastFirst(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        feature.setStorage(GeometryStorage.WKB);
        assertThat(severity((v, d) -> v.validateGeometryFeature_GeoJsonEastFirst(feature, d, new HashMap<>())))
                .as("binary may hold north first").isEqualTo(Diagnostic.OK);
    }

    @Test
    void aHeightNeedsAThirdAxis() {
        ReferenceSystem flat = F.createReferenceSystem();
        GeometryFeature feature = F.createGeometryFeature();
        feature.setReferenceSystem(flat);
        assertThat(severity((v, d) -> v.validateGeometryFeature_HeightNeedsThreeAxes(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        feature.setHasHeight(true);
        assertThat(severity((v, d) -> v.validateGeometryFeature_HeightNeedsThreeAxes(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        flat.setAxes(3);
        assertThat(severity((v, d) -> v.validateGeometryFeature_HeightNeedsThreeAxes(feature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void theMinimumShareIsAShareBelowOne() {
        SpatialRelation r = F.createSpatialRelation();
        assertThat(severity((v, d) -> v.validateSpatialRelation_MinimumShareInRange(r, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        r.setMinimumShare("0.01");
        assertThat(severity((v, d) -> v.validateSpatialRelation_MinimumShareInRange(r, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        r.setMinimumShare("1.5");
        assertThat(severity((v, d) -> v.validateSpatialRelation_MinimumShareInRange(r, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void aDistanceGoesWithWithinDistanceOnly() {
        SpatialRelation r = F.createSpatialRelation();
        r.setPredicate(SpatialPredicate.WITHIN);
        assertThat(severity((v, d) -> v.validateSpatialRelation_DistanceOnlyForWithinDistance(r, d,
                new HashMap<>()))).isEqualTo(Diagnostic.OK);
        r.setDistance("500");
        assertThat(severity((v, d) -> v.validateSpatialRelation_DistanceOnlyForWithinDistance(r, d,
                new HashMap<>()))).isEqualTo(Diagnostic.ERROR);
        r.setPredicate(SpatialPredicate.WITHIN_DISTANCE);
        assertThat(severity((v, d) -> v.validateSpatialRelation_DistanceOnlyForWithinDistance(r, d,
                new HashMap<>()))).isEqualTo(Diagnostic.OK);
        r.setDistance(null);
        assertThat(severity((v, d) -> v.validateSpatialRelation_DistanceOnlyForWithinDistance(r, d,
                new HashMap<>()))).isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void aGridLevelIsNotItsOwnParent() {
        GridLevel level = F.createGridLevel();
        GridLevel above = F.createGridLevel();
        level.setParent(above);
        assertThat(severity((v, d) -> v.validateGridLevel_ParentNotSelf(level, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        level.setParent(level);
        assertThat(severity((v, d) -> v.validateGridLevel_ParentNotSelf(level, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    /**
     * A grid system is an element of the model, named by the identifier a resolver knows it by, with its resolutions
     * and whether its cells nest (not unless said).
     */
    @Test
    void aGridSystemHoldsItsResolutionsAndItsNesting() {
        GridSystem system = F.createGridSystem();
        assertThat(system.isNested()).as("a parent that is a function of the cell unless said").isFalse();
        assertThat(P.getGridSystem_MinResolution().getLowerBound()).isEqualTo(1);
        assertThat(P.getGridSystem_MaxResolution().getLowerBound()).isEqualTo(1);
        system.setMaxResolution(12);
        assertThat(severity((v, d) -> v.validateGridSystem_ResolutionsInOrder(system, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        system.setMinResolution(13);
        assertThat(severity((v, d) -> v.validateGridSystem_ResolutionsInOrder(system, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        system.setMinResolution(-1);
        assertThat(severity((v, d) -> v.validateGridSystem_ResolutionsInOrder(system, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void aGridLevelLiesWithinTheResolutionsOfItsSystem() {
        GridSystem system = F.createGridSystem();
        system.setMinResolution(1);
        system.setMaxResolution(12);
        GridLevel level = F.createGridLevel();
        level.setResolution(13);
        assertThat(severity((v, d) -> v.validateGridLevel_ResolutionInRange(level, d, new HashMap<>())))
                .as("no system: nothing to hold it against").isEqualTo(Diagnostic.OK);
        level.setSystem(system);
        assertThat(severity((v, d) -> v.validateGridLevel_ResolutionInRange(level, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        level.setResolution(12);
        assertThat(severity((v, d) -> v.validateGridLevel_ResolutionInRange(level, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void aChainOfGridLevelsStaysInOneSystem() {
        GridSystem one = F.createGridSystem();
        GridSystem other = F.createGridSystem();
        GridLevel above = F.createGridLevel();
        above.setSystem(one);
        GridLevel level = F.createGridLevel();
        level.setSystem(one);
        level.setParent(above);
        assertThat(severity((v, d) -> v.validateGridLevel_ParentOfTheSameSystem(level, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        above.setSystem(other);
        assertThat(severity((v, d) -> v.validateGridLevel_ParentOfTheSameSystem(level, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static List<String> literals(EEnum e) {
        return e.getELiterals().stream().map(EEnumLiteral::getName).toList();
    }

    private static int severity(BiPredicate<SpatialValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostics = new BasicDiagnostic();
        check.test(SpatialValidator.INSTANCE, diagnostics);
        return diagnostics.getSeverity();
    }
}
