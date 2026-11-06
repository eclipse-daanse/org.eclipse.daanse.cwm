/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.units.examples.Elements;
import org.eclipse.daanse.cwm.model.daanse.units.util.Dimensions;
import org.eclipse.daanse.cwm.model.daanse.units.util.Prefixes;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;
import org.eclipse.daanse.cwm.model.daanse.units.util.QuantityKinds;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.eclipse.daanse.cwm.model.daanse.units.util.Units;
import org.eclipse.daanse.cwm.model.daanse.units.util.UnitsValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shape of the units metamodel, its OCL invariants, and quantities in an XMI file. */
class UnitsModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final UnitsPackage P = UnitsPackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void theClassOfAUnitSaysHowItIsDefined() {
        assertThat(P.getUnit().isAbstract()).isTrue();
        assertThat(P.getReferringUnit().isAbstract()).isTrue();
        assertThat(List.of(P.getBaseUnit(), P.getDerivedUnit(), P.getReferringUnit()))
                .allSatisfy(c -> assertThat(c.getESuperTypes()).containsExactly(P.getUnit()));
        assertThat(List.of(P.getScaledUnit(), P.getPrefixedUnit(), P.getAlternateUnit(), P.getLogarithmicUnit()))
                .allSatisfy(c -> assertThat(c.getESuperTypes()).containsExactly(P.getReferringUnit()));
        assertThat(P.getSystemOfUnits().getESuperTypes()).containsExactly(CorePackage.Literals.PACKAGE);
        assertThat(P.getUnitFactor().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        for (EClass c : List.of(P.getDimension(), P.getQuantityKind(), P.getPrefix(), P.getUnit(), P.getQuantity())) {
            assertThat(c.getESuperTypes()).as(c.getName()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        }
        assertThat(P.getEClassifiers().stream().filter(EClass.class::isInstance)).hasSize(14);
        assertThat(P.getScale().getELiterals()).extracting(l -> l.getName()).containsExactly("absolute", "relative");
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void referencesAreOneWayWithAnOppositeRoleNameAndOnlyTheFactorsAreOwned() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    if (r.isContainment()) {
                        assertThat(r).isSameAs(P.getDerivedUnit_Factor());
                        assertThat(r.isOrdered()).isTrue();
                        continue;
                    }
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(r.getName()).startsWith("units");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(r.getName()).startsWith("units");
                    if (r.isMany()) {
                        assertThat(r.isOrdered()).as(r.getName()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void attributesUseCwmDataTypesOrTheScale() {
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
    void prefixesHaveBaseTwoOrTenAndAnExponent() {
        Model model = Elements.model("m");
        Prefix kilo = Prefixes.create(model, "kilo", "k", 10, 3);
        Prefix kibi = Prefixes.create(model, "kibi", "Ki", 2, 10);
        Prefix odd = Prefixes.create(model, "odd", "o", 7, 1);
        Prefix none = Prefixes.create(model, "none", "n", 10, 0);
        assertThat(severity((v, d) -> v.validatePrefix_BaseTwoOrTen(kilo, d, new HashMap<>()))).isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validatePrefix_BaseTwoOrTen(kibi, d, new HashMap<>()))).isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validatePrefix_BaseTwoOrTen(odd, d, new HashMap<>()))).isEqualTo(Diagnostic.ERROR);
        assertThat(severity((v, d) -> v.validatePrefix_ExponentNotZero(kilo, d, new HashMap<>()))).isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validatePrefix_ExponentNotZero(none, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void factorsHaveAnExponentOtherThanZero() {
        Model model = Elements.model("m");
        Unit metre = Units.base(model, "metre", "m", Dimensions.ownedIn(model, Dimensions.base(Dimensions.LENGTH, 1)));
        DerivedUnit squareMetre = Units.derived(model, "squareMetre", "m2", Units.factor(metre, 2));
        assertThat(severity((v, d) -> v.validateDerivedUnit_FactorExponentNotZero(squareMetre, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        squareMetre.getFactor().get(0).setExponent(0);
        assertThat(severity((v, d) -> v.validateDerivedUnit_FactorExponentNotZero(squareMetre, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void noUnitIsDefinedThroughItselfAndNoKindIsItsOwnBroader() {
        Model model = Elements.model("m");
        Dimension temperature = Dimensions.ownedIn(model, Dimensions.base(Dimensions.TEMPERATURE, 1));
        Unit kelvin = Units.base(model, "kelvin", "K", temperature);
        ScaledUnit celsius = Units.scaled(model, "degreeCelsius", "°C", kelvin, "1", "273.15");
        assertThat(severity((v, d) -> v.validateReferringUnit_NotSelfReferring(celsius, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        celsius.setReference(celsius);
        assertThat(severity((v, d) -> v.validateReferringUnit_NotSelfReferring(celsius, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);

        QuantityKind kind = QuantityKinds.create(model, "Temperature", temperature);
        QuantityKind celsiusTemperature = QuantityKinds.broader(QuantityKinds.create(model, "CelsiusTemperature",
                temperature), kind);
        assertThat(severity((v, d) -> v.validateQuantityKind_BroaderNotSelf(kind, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateQuantityKind_BroaderNotSelf(celsiusTemperature, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        kind.setBroader(kind);
        assertThat(severity((v, d) -> v.validateQuantityKind_BroaderNotSelf(kind, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void quantitiesTravelInXmiAndAreFoundFromTheElement(@TempDir Path dir) throws Exception {
        Model model = Elements.model("weather");
        SystemOfUnits si = SI.create(model);
        Class station = CoreFactory.eINSTANCE.createClass();
        station.setName("Station");
        model.getOwnedElement().add(station);
        Attribute temperature = CoreFactory.eINSTANCE.createAttribute();
        temperature.setName("temperature");
        station.getFeature().add(temperature);
        Quantities.kind(Quantities.create(Elements.pkg(model, "quantities"), "temperature",
                SI.unit(si, SI.DEGREE_CELSIUS), temperature), SI.kind(si, "Temperature"));

        URI uri = URI.createFileURI(dir.resolve("weather.xmi").toString());
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Resource out = rs.createResource(uri);
        out.getContents().add(model);
        out.save(null);

        for (boolean indexed : new boolean[] { false, true }) {
            ResourceSet fresh = new ResourceSetImpl();
            fresh.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
            Resource in = fresh.getResource(uri, true);
            if (indexed) {
                InverseReferences.install(fresh);
            }
            Model loaded = (Model) in.getContents().get(0);
            assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(in)).isEmpty();
            Class s = (Class) loaded.getOwnedElement().stream().filter(Class.class::isInstance).findFirst().orElseThrow();
            Attribute t = (Attribute) s.getFeature().get(0);
            assertThat(Quantities.unitOf(t)).get().extracting(Unit::getSymbol).isEqualTo("°C");
            assertThat(Quantities.of(t)).get().extracting(q -> q.getQuantityKind().getName()).isEqualTo("Temperature");
        }
    }

    private static int severity(BiPredicate<UnitsValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(UnitsValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
