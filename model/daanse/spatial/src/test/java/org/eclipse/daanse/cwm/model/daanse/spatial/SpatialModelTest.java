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

import java.util.ArrayList;
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
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
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

    /**
     * Six model elements, the holdings of a geometry (an abstract one and three kinds, owned and nameless, as are the
     * parts of a holding in parts), the logical type of a geometry and fourteen enumerations.
     */
    @Test
    void sixModelElementsTheHoldingsAGeometryTypeAndFourteenEnumerations() {
        List<EClass> classes = List.of(P.getGeometryFeature(), P.getSpatialRelation(), P.getGridLevel(),
                P.getDistanceBand(), P.getReferenceSystem(), P.getGridSystem());
        for (EClass c : classes) {
            assertThat(c.getESuperTypes()).as(c.getName()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
            assertThat(c.isAbstract()).as(c.getName()).isFalse();
        }
        assertThat(P.getGeometryHolding().isAbstract()).isTrue();
        assertThat(P.getGeometryHolding().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        for (EClass c : List.of(P.getWholeHolding(), P.getPartsHolding(), P.getListHolding())) {
            assertThat(c.getESuperTypes()).as(c.getName()).containsExactly(P.getGeometryHolding());
            assertThat(c.isAbstract()).as(c.getName()).isFalse();
        }
        assertThat(P.getCoordinatePart().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getGeometryType().getESuperTypes()).as("the logical type of a geometry is a CWM data type")
                .containsExactly(CorePackage.Literals.DATA_TYPE);
        assertThat(P.getGeometryType().getEStructuralFeatures()).as("what a geometry is says its feature")
                .isEmpty();
        assertThat(P.getEClassifiers().stream().filter(EClass.class::isInstance)).hasSize(12);
        assertThat(P.getEClassifiers().stream().filter(EEnum.class::isInstance)).hasSize(14);
    }

    /**
     * What a geometry is belongs to its feature, how it is held to its holdings: the feature has no attribute, no
     * storage and no stored axis order of its own. The stored order is a fact of a whole holding, the epoch a fact of
     * the coordinates and so of the feature, the reference system holds only what its register defines.
     */
    @Test
    void theStoredAxisOrderBelongsToAWholeHoldingTheEpochToTheFeature() {
        assertThat(P.getGeometryFeature().getEStructuralFeatures()).extracting(EStructuralFeature::getName)
                .containsExactly("kind", "multiplicity", "hasHeight", "referenceSystem", "coordinateEpoch",
                        "accuracy", "derivation", "of", "holding");
        assertThat(P.getWholeHolding().getEStructuralFeatures()).extracting(EStructuralFeature::getName)
                .containsExactly("attribute", "storage", "axisOrder");
        assertThat(P.getWholeHolding_AxisOrder().getEAttributeType()).isSameAs(P.getAxisOrder());
        assertThat(P.getWholeHolding_AxisOrder().getLowerBound()).isEqualTo(1);
        assertThat(P.getGeometryFeature_CoordinateEpoch().getLowerBound()).isZero();
        assertThat(P.getReferenceSystem().getEStructuralFeature("coordinateEpoch")).as("not of the system").isNull();
        assertThat(P.getReferenceSystem_AxisOrder()).as("the order the register defines").isNotNull();
        WholeHolding lonLat = F.createWholeHolding();
        WholeHolding latLon = F.createWholeHolding();
        latLon.setAxisOrder(AxisOrder.NORTH_EAST);
        GeometryFeature positions = F.createGeometryFeature();
        positions.getHolding().addAll(List.of(lonLat, latLon));
        assertThat(lonLat.getAxisOrder()).as("one geometry, two stored orders").isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(latLon.getAxisOrder()).isEqualTo(AxisOrder.NORTH_EAST);
    }

    /**
     * A geometry is held at least once; a holding in parts has a part at least, a list a role at least; the roles of a
     * list keep their order and a repeated one is for the validator to name.
     */
    @Test
    void theHoldingsAndTheirParts() {
        assertThat(P.getGeometryFeature_Holding().isContainment()).isTrue();
        assertThat(P.getGeometryFeature_Holding().getLowerBound()).isEqualTo(1);
        assertThat(P.getGeometryFeature_Holding().getUpperBound()).isEqualTo(-1);
        assertThat(P.getPartsHolding_Part().isContainment()).isTrue();
        assertThat(P.getPartsHolding_Part().getLowerBound()).isEqualTo(1);
        assertThat(P.getCoordinatePart_Role().getEAttributeType()).isSameAs(P.getOrdinate());
        assertThat(P.getListHolding_Ordinate().isOrdered()).isTrue();
        assertThat(P.getListHolding_Ordinate().isUnique()).as("a repeated role is the validator's to name").isFalse();
        assertThat(P.getListHolding_Separator().getLowerBound()).as("none: several values").isZero();
        for (EReference attribute : List.of(P.getWholeHolding_Attribute(), P.getCoordinatePart_Attribute(),
                P.getListHolding_Attribute())) {
            assertThat(attribute.getEType()).as("an attribute, not a class or a package")
                    .isSameAs(CorePackage.Literals.ATTRIBUTE);
            assertThat(attribute.getLowerBound()).isEqualTo(1);
        }
    }

    /** Unset, a geometry is one of its own; the first literal stated counts as a derivation. */
    @Test
    void aDerivationIsToldFromItsDefault() {
        assertThat(P.getGeometryFeature_Derivation().isUnsettable()).isTrue();
        assertThat(P.getGeometryFeature_Of().getEType()).isSameAs(P.getGeometryFeature());
        GeometryFeature feature = F.createGeometryFeature();
        assertThat(feature.isSetDerivation()).isFalse();
        feature.setDerivation(GeometryDerivation.ENVELOPE);
        assertThat(feature.isSetDerivation()).as("ENVELOPE is the first literal, and set").isTrue();
    }

    /** Every classifier, feature and literal of the package says what it is, for a reader who sees only the model. */
    @Test
    void everyElementIsDocumented() {
        List<EModelElement> elements = new ArrayList<>(List.of(P));
        for (EClassifier c : P.getEClassifiers()) {
            elements.add(c);
            if (c instanceof EClass cls) {
                elements.addAll(cls.getEStructuralFeatures());
            } else if (c instanceof EEnum e) {
                elements.addAll(e.getELiterals());
            }
        }
        assertThat(elements).hasSizeGreaterThan(130).allSatisfy(e -> assertThat(e.getEAnnotation(
                "http://www.eclipse.org/emf/2002/GenModel").getDetails().get("documentation")).as(e.toString())
                .isNotBlank());
    }

    @Test
    void theFirstLiteralIsWhatAnUnsetValueReads() {
        assertThat(literals(P.getGeometryKind())).containsExactly("POINT", "LINE", "POLYGON", "MIXED");
        assertThat(literals(P.getGeometryStorage())).as("of a whole holding; numbers have none")
                .containsExactly("NATIVE", "WKB", "WKT", "GEOJSON");
        assertThat(literals(P.getOrdinate())).as("by axis, the axes of a point, then the edges of an envelope")
                .containsExactly("EAST", "NORTH", "HEIGHT", "MIN_EAST", "MIN_NORTH", "MAX_EAST", "MAX_NORTH",
                        "MIN_HEIGHT", "MAX_HEIGHT");
        assertThat(literals(P.getGeometryDerivation())).containsExactly("ENVELOPE", "CORNER_SOUTH_WEST",
                "CORNER_NORTH_EAST", "CORNER_NORTH_WEST", "CORNER_SOUTH_EAST", "CENTROID", "POINT_ON_SURFACE");
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

    /** The only containments are the holdings of a feature and the parts of a holding; other references are one-way. */
    @Test
    void referencesAreOneWayWithAnOppositeRoleName() {
        List<String> containments = new ArrayList<>();
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    if (r.isContainment()) {
                        containments.add(cls.getName() + "." + r.getName());
                        continue;
                    }
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(r.getName()).startsWith("spatial");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(r.getName()).startsWith("spatial");
                }
            }
        }
        assertThat(containments).containsExactly("GeometryFeature.holding", "PartsHolding.part");
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
        assertThat(F.createWholeHolding().getAxisOrder()).as("stored east first unless said")
                .isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(F.createWholeHolding().getStorage()).as("the database's type unless said")
                .isEqualTo(GeometryStorage.NATIVE);
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
        WholeHolding holding = F.createWholeHolding();
        holding.setStorage(GeometryStorage.GEOJSON);
        assertThat(severity((v, d) -> v.validateWholeHolding_GeoJsonEastFirst(holding, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        holding.setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(severity((v, d) -> v.validateWholeHolding_GeoJsonEastFirst(holding, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        holding.setStorage(GeometryStorage.WKB);
        assertThat(severity((v, d) -> v.validateWholeHolding_GeoJsonEastFirst(holding, d, new HashMap<>())))
                .as("binary may hold north first").isEqualTo(Diagnostic.OK);
    }

    @Test
    void aHoldingOfNumbersNamesEachRoleOnce() {
        PartsHolding parts = F.createPartsHolding();
        for (Ordinate role : List.of(Ordinate.EAST, Ordinate.NORTH)) {
            CoordinatePart part = F.createCoordinatePart();
            part.setRole(role);
            parts.getPart().add(part);
        }
        assertThat(severity((v, d) -> v.validatePartsHolding_EachRoleOnce(parts, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        parts.getPart().get(1).setRole(Ordinate.EAST);
        assertThat(severity((v, d) -> v.validatePartsHolding_EachRoleOnce(parts, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        ListHolding list = F.createListHolding();
        list.getOrdinate().addAll(List.of(Ordinate.NORTH, Ordinate.EAST));
        assertThat(severity((v, d) -> v.validateListHolding_EachRoleOnce(list, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        list.getOrdinate().add(Ordinate.NORTH);
        assertThat(severity((v, d) -> v.validateListHolding_EachRoleOnce(list, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void aGeometryIsNotDerivedFromItself() {
        GeometryFeature shape = F.createGeometryFeature();
        GeometryFeature envelope = F.createGeometryFeature();
        envelope.setDerivation(GeometryDerivation.ENVELOPE);
        envelope.setOf(shape);
        assertThat(severity((v, d) -> v.validateGeometryFeature_NotDerivedFromItself(envelope, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        envelope.setOf(envelope);
        assertThat(severity((v, d) -> v.validateGeometryFeature_NotDerivedFromItself(envelope, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    /** An envelope is a polygon; a corner, a centroid and a point on the surface are points. */
    @Test
    void theKindFollowsTheDerivation() {
        GeometryFeature shape = F.createGeometryFeature();
        shape.setKind(GeometryKind.POLYGON);
        assertThat(severity((v, d) -> v.validateGeometryFeature_KindFollowsDerivation(shape, d, new HashMap<>())))
                .as("a geometry of its own").isEqualTo(Diagnostic.OK);
        GeometryFeature derived = F.createGeometryFeature();
        derived.setOf(shape);
        derived.setDerivation(GeometryDerivation.ENVELOPE);
        derived.setKind(GeometryKind.POLYGON);
        assertThat(severity((v, d) -> v.validateGeometryFeature_KindFollowsDerivation(derived, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        derived.setDerivation(GeometryDerivation.CENTROID);
        assertThat(severity((v, d) -> v.validateGeometryFeature_KindFollowsDerivation(derived, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        derived.setKind(GeometryKind.POINT);
        assertThat(severity((v, d) -> v.validateGeometryFeature_KindFollowsDerivation(derived, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
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
