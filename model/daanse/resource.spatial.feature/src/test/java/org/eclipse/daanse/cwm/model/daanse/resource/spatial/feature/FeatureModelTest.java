/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureValidator;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPackage;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The shape of the feature metamodel and some of its OCL invariants, each through the method the code generator makes
 * of it; {@code FeatureModelValidatorTest} holds a test of every rule, {@code FeatureRulesTest} the place of each.
 */
class FeatureModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final FeaturePackage P = FeaturePackage.eINSTANCE;
    private static final FeatureFactory F = FeatureFactory.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void aCollectionIsAClassAndAPropertyAnAttribute() {
        assertThat(P.getFeatureCollection().getESuperTypes()).containsExactly(CorePackage.Literals.CLASS);
        assertThat(P.getFeatureProperty().getESuperTypes()).containsExactly(CorePackage.Literals.ATTRIBUTE);
        assertThat(P.getEClassifiers()).as("two classes, no data types of its own").hasSize(2)
                .allMatch(EClass.class::isInstance);
    }

    @Test
    void theDefaultsAreWhatAnUnsetValueReads() {
        FeatureProperty property = F.createFeatureProperty();
        assertThat(property.isQueryable()).as("queryable unless said otherwise").isTrue();
        assertThat(property.isReturnable()).isTrue();
        assertThat(property.isSortable()).isFalse();
        FeatureCollection collection = F.createFeatureCollection();
        assertThat(collection.isEndInclusive()).isTrue();
        assertThat(collection.isOpenProperties()).as("only declared properties").isFalse();
        assertThat(collection.getTimeZone()).as("unset: UTC").isNull();
    }

    @Test
    void referencesAreOneWayWithAnOppositeRoleName() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EReference r : ((EClass) c).getEReferences()) {
                assertThat(r.getEOpposite()).as(r.getName()).isNull();
                assertThat(r.isContainment()).as(r.getName()).isFalse();
                assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName")).as(r.getName())
                        .startsWith("feature");
            }
        }
        assertThat(P.getFeatureCollection_FeatureKey().getLowerBound()).as("every collection has an id").isEqualTo(1);
        assertThat(P.getFeatureCollection_FeatureKey().getEType()).isSameAs(P.getFeatureProperty());
        assertThat(P.getFeatureCollection_Geometry().getLowerBound()).isZero();
        assertThat(P.getFeatureCollection_Geometry().getEType()).as("what the geometry is, held in properties")
                .isSameAs(SpatialPackage.Literals.GEOMETRY_FEATURE);
        assertThat(P.getFeatureCollection_ReferenceSystems().getEType())
                .isSameAs(SpatialPackage.Literals.REFERENCE_SYSTEM);
        assertThat(P.getFeatureCollection_ReferenceSystems().isOrdered()).as("the order of the list crs").isTrue();
    }

    @Test
    void attributesUseCwmDataTypes() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EAttribute a : ((EClass) c).getEAttributes()) {
                assertThat(a.getEAttributeType().getEPackage()).as(a.getName()).isSameAs(CorePackage.eINSTANCE)
                        .isNotSameAs(EcorePackage.eINSTANCE);
            }
        }
    }

    /** FC1 of a collection: it stands in a package and owns only feature properties, each name once. */
    @Test
    void aCollectionOwnsOnlyFeatureProperties() {
        FeatureCollection collection = F.createFeatureCollection();
        CoreFactory.eINSTANCE.createPackage().getOwnedElement().add(collection);
        collection.getFeature().add(F.createFeatureProperty());
        assertThat(severity((v, d) -> v.validateFeatureCollection_FC1(collection, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        collection.getFeature().add(CoreFactory.eINSTANCE.createAttribute());
        assertThat(severity((v, d) -> v.validateFeatureCollection_FC1(collection, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    /**
     * The time of a feature after the roles of OGC API Features Part 5: an instant, or the start and the end of an
     * interval, either of which may stand alone; that an instant excludes the two is FC18 of the validator.
     */
    @Test
    void theTimeHasTheRolesOfPart5() {
        assertThat(P.getFeatureCollection().getEStructuralFeatures()).extracting(f -> f.getName())
                .contains("instant", "intervalStart", "intervalEnd");
        assertThat(P.getFeatureCollection().getEStructuralFeature("timeStart")).isNull();
    }

    /** FC1 of a property: it is typed and belongs to a collection. */
    @Test
    void aPropertyIsOwnedByACollection() {
        FeatureProperty property = F.createFeatureProperty();
        property.setType(CoreFactory.eINSTANCE.createDataType());
        F.createFeatureCollection().getFeature().add(property);
        assertThat(severity((v, d) -> v.validateFeatureProperty_FC1(property, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        CoreFactory.eINSTANCE.createClass().getFeature().add(property);
        assertThat(severity((v, d) -> v.validateFeatureProperty_FC1(property, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        F.createFeatureCollection().getFeature().add(property);
        property.setType(null);
        assertThat(severity((v, d) -> v.validateFeatureProperty_FC1(property, d, new HashMap<>())))
                .as("untyped").isEqualTo(Diagnostic.ERROR);
    }

    /** Every classifier and feature of the package says what it is, for a reader who sees only the model. */
    @Test
    void everyElementIsDocumented() {
        List<EModelElement> elements = new ArrayList<>(List.of(P));
        for (EClassifier c : P.getEClassifiers()) {
            elements.add(c);
            elements.addAll(((EClass) c).getEStructuralFeatures());
        }
        assertThat(elements).hasSizeGreaterThan(15).allSatisfy(e -> assertThat(e.getEAnnotation(
                "http://www.eclipse.org/emf/2002/GenModel").getDetails().get("documentation")).as(e.toString())
                .isNotBlank());
    }

    private static int severity(BiPredicate<FeatureValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostics = new BasicDiagnostic();
        check.test(FeatureValidator.INSTANCE, diagnostics);
        return diagnostics.getSeverity();
    }
}
