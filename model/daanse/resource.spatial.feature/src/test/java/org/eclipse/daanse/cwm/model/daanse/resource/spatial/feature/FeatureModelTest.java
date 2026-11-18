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
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The shape of the feature metamodel and its OCL invariants. */
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

    @Test
    void aCollectionOwnsOnlyFeatureProperties() {
        FeatureCollection collection = F.createFeatureCollection();
        collection.getFeature().add(F.createFeatureProperty());
        assertThat(severity((v, d) -> v.validateFeatureCollection_OnlyFeatureProperties(collection, d,
                new HashMap<>()))).isEqualTo(Diagnostic.OK);
        collection.getFeature().add(CoreFactory.eINSTANCE.createAttribute());
        assertThat(severity((v, d) -> v.validateFeatureCollection_OnlyFeatureProperties(collection, d,
                new HashMap<>()))).isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void theKeyIsAPropertyOfTheCollection() {
        FeatureCollection collection = F.createFeatureCollection();
        FeatureProperty own = F.createFeatureProperty();
        collection.getFeature().add(own);
        collection.setFeatureKey(own);
        assertThat(severity((v, d) -> v.validateFeatureCollection_KeyIsOwnProperty(collection, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        FeatureCollection other = F.createFeatureCollection();
        FeatureProperty foreign = F.createFeatureProperty();
        other.getFeature().add(foreign);
        collection.setFeatureKey(foreign);
        assertThat(severity((v, d) -> v.validateFeatureCollection_KeyIsOwnProperty(collection, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    /**
     * The time of a feature after the roles of OGC API Features Part 5: an instant, or the start and the end of an
     * interval, either of which may stand alone; an instant excludes the two.
     */
    @Test
    void theTimeIsAnInstantOrAnInterval() {
        FeatureCollection collection = F.createFeatureCollection();
        FeatureProperty instant = F.createFeatureProperty();
        FeatureProperty start = F.createFeatureProperty();
        FeatureProperty end = F.createFeatureProperty();
        collection.getFeature().addAll(List.of(instant, start, end));
        collection.setIntervalStart(start);
        collection.setIntervalEnd(end);
        assertThat(severity((v, d) -> v.validateFeatureCollection_InstantOrInterval(collection, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        collection.setIntervalStart(null);
        assertThat(severity((v, d) -> v.validateFeatureCollection_InstantOrInterval(collection, d, new HashMap<>())))
                .as("an end alone is a half-bounded interval").isEqualTo(Diagnostic.OK);
        collection.setIntervalEnd(null);
        collection.setIntervalStart(start);
        assertThat(severity((v, d) -> v.validateFeatureCollection_InstantOrInterval(collection, d, new HashMap<>())))
                .as("a start alone is a half-bounded interval").isEqualTo(Diagnostic.OK);
        collection.setInstant(instant);
        assertThat(severity((v, d) -> v.validateFeatureCollection_InstantOrInterval(collection, d, new HashMap<>())))
                .as("an instant excludes the ends of an interval").isEqualTo(Diagnostic.ERROR);
        collection.setIntervalStart(null);
        assertThat(severity((v, d) -> v.validateFeatureCollection_InstantOrInterval(collection, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(P.getFeatureCollection().getEStructuralFeature("timeStart")).isNull();
    }

    @Test
    void aPropertyIsOwnedByACollection() {
        FeatureProperty property = F.createFeatureProperty();
        F.createFeatureCollection().getFeature().add(property);
        assertThat(severity((v, d) -> v.validateFeatureProperty_OwnedByCollection(property, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        CoreFactory.eINSTANCE.createClass().getFeature().add(property);
        assertThat(severity((v, d) -> v.validateFeatureProperty_OwnedByCollection(property, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static int severity(BiPredicate<FeatureValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostics = new BasicDiagnostic();
        check.test(FeatureValidator.INSTANCE, diagnostics);
        return diagnostics.getSeverity();
    }
}
