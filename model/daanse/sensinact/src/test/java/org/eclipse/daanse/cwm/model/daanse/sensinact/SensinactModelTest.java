/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.daanse.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.sensinact.util.Providers;
import org.eclipse.daanse.cwm.model.daanse.sensinact.util.Resources;
import org.eclipse.daanse.cwm.model.daanse.sensinact.util.SensinactValidator;
import org.eclipse.daanse.cwm.model.daanse.sensinact.util.Services;
import org.eclipse.daanse.cwm.model.daanse.sensinact.util.ValueSources;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The shape of the sensinact metamodel and its OCL invariants. */
class SensinactModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final SensinactPackage P = SensinactPackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void theTargetStructureIsCwmClassesAndAttributes() {
        assertThat(P.getProvider().getESuperTypes()).containsExactly(CorePackage.Literals.CLASS);
        assertThat(P.getService().getESuperTypes()).containsExactly(CorePackage.Literals.CLASS);
        assertThat(P.getResource().getESuperTypes()).containsExactly(CorePackage.Literals.ATTRIBUTE);
        assertThat(P.getProviderMapping().getESuperTypes()).containsExactly(CorePackage.Literals.NAMESPACE);
        assertThat(P.getServiceMapping().getESuperTypes()).containsExactly(CorePackage.Literals.NAMESPACE);
        assertThat(P.getResourceMapping().getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getAdminMapping().getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getValueSource().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
    }

    @Test
    void theClassOfAValueSourceSaysWhereTheValueComesFrom() {
        assertThat(P.getValueSource().isAbstract()).isTrue();
        assertThat(List.of(P.getPathSource(), P.getLiteralSource(), P.getArrivalTimeSource()))
                .allSatisfy(c -> assertThat(c.getESuperTypes()).containsExactly(P.getValueSource()));
        assertThat(P.getPathSource_Path().getLowerBound()).isEqualTo(1);
        assertThat(P.getPathSource_Fallback().getEType()).isSameAs(P.getValueSource());
        assertThat(P.getLiteralSource_Literal().getLowerBound()).isEqualTo(1);
        assertThat(P.getArrivalTimeSource().getEStructuralFeatures()).isEmpty();
        assertThat(P.getServiceMapping_Selector().getEType()).isSameAs(P.getPathSource());
        // a path without a step and a literal without a value are no value sources
        assertThat(Diagnostician.INSTANCE.validate(SensinactFactory.eINSTANCE.createPathSource()).getSeverity())
                .isEqualTo(Diagnostic.ERROR);
        assertThat(Diagnostician.INSTANCE.validate(SensinactFactory.eINSTANCE.createLiteralSource()).getSeverity())
                .isEqualTo(Diagnostic.ERROR);
        assertThat(Diagnostician.INSTANCE.validate(ValueSources.literal("x")).getSeverity()).isEqualTo(Diagnostic.OK);
        assertThat(Diagnostician.INSTANCE.validate(ValueSources.arrivalTime()).getSeverity()).isEqualTo(Diagnostic.OK);
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void referencesIntoCwmAreOneWayWithAnOppositeRoleName() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EReference r : ((EClass) c).getEReferences()) {
                assertThat(r.getEOpposite()).as(r.getName()).isNull();
                if (!r.isContainment()) {
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(c.getName() + "." + r.getName()).startsWith("sensinact");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(c.getName() + "." + r.getName()).startsWith("sensinact");
                }
            }
        }
    }

    @Test
    void attributesUseCwmDataTypesAndOnlyThePathIsOrdered() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EStructuralFeature f : ((EClass) c).getEStructuralFeatures()) {
                if (f instanceof EAttribute a) {
                    assertThat(a.getEAttributeType().getEPackage()).as(a.getName()).isSameAs(CorePackage.eINSTANCE)
                            .isNotSameAs(EcorePackage.eINSTANCE);
                }
                if (f.isMany()) {
                    assertThat(f.isOrdered()).as(c.getName() + "." + f.getName()).isEqualTo(f == P.getPathSource_Path());
                }
            }
        }
    }

    @Test
    void ocl_providerHasNoFeaturesOfItsOwn() {
        Model model = model();
        Provider provider = Providers.create(model, "p");
        assertThat(severity((v, d) -> v.validateProvider_NoDirectFeatures(provider, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        provider.getFeature().add(CoreFactory.eINSTANCE.createAttribute());
        assertThat(severity((v, d) -> v.validateProvider_NoDirectFeatures(provider, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void ocl_serviceIsOwnedByAProviderAndHoldsResources() {
        Model model = model();
        Service service = Services.create(Providers.create(model, "p"), "s");
        Resources.create(service, "r", dataType(model));
        assertThat(severity((v, d) -> v.validateService_OwnedByProvider(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateService_OnlyResources(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        model.getOwnedElement().add(service);
        service.getFeature().add(CoreFactory.eINSTANCE.createAttribute());
        assertThat(severity((v, d) -> v.validateService_OwnedByProvider(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        assertThat(severity((v, d) -> v.validateService_OnlyResources(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void ocl_resourceIsTypedByADataType() {
        Model model = model();
        Resource resource = Resources.create(Services.create(Providers.create(model, "p"), "s"), "r", dataType(model));
        assertThat(severity((v, d) -> v.validateResource_TypedByDataType(resource, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        resource.setType(CoreFactory.eINSTANCE.createClass());
        assertThat(severity((v, d) -> v.validateResource_TypedByDataType(resource, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static Model model() {
        Model model = CoreFactory.eINSTANCE.createModel();
        model.setName("m");
        return model;
    }

    private static DataType dataType(Model model) {
        DataType t = CoreFactory.eINSTANCE.createDataType();
        t.setName("Double");
        model.getOwnedElement().add(t);
        return t;
    }

    private static int severity(BiPredicate<SensinactValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(SensinactValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }

    /** Lines of the XMI {@link #theBatteryExampleInXmi()} writes: the provider and one mapping onto it. */
    private static final String XMI = """
            <ownedElement xsi:type="sensinact:Provider" name="battery-sensor" friendlyNameRequired="true">
            <ownedElement xsi:type="sensinact:Service" name="battery" displayName="Battery">
            <feature xsi:type="sensinact:Resource" name="level" type="_EDouble" displayName="Battery Level"/>
            <ownedElement xsi:type="sensinact:ProviderMapping" name="dragino-battery-sensor" \
            provider="_battery-sensor" sourceClass="_DraginoLSE01Uplink">
            <ownedElement xsi:type="sensinact:ServiceMapping" name="battery" service="_battery-sensor.battery">
            <ownedElement xsi:type="sensinact:ResourceMapping" name="level" \
            resource="_battery-sensor.battery.level">
            <value xsi:type="sensinact:PathSource" \
            path="_DraginoLSE01Uplink.object _dragino.DecodedObject.batV"/>
            <id xsi:type="sensinact:PathSource" path="_UplinkMessage.deduplicationId">
            <fallback xsi:type="sensinact:LiteralSource" literal="Dragino LSE01 Battery Sensor"/>
            <timestamp xsi:type="sensinact:PathSource" path="_UplinkMessage.rxInfo _RxInfo.time" index="0"/>
            <admin name="admin">
            <friendlyName xsi:type="sensinact:PathSource" \
            path="_UplinkMessage.deviceInfo _DeviceInfo.deviceProfileName"/>
            """;

    @Test
    void theBatteryExampleInXmi() throws IOException {

        String xmi = xmi(Examples.battery().model(), "battery.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** {@code root} as XMI with short ids, written to {@code target/<file>} as well. */
    private static String xmi(EObject root, String file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        org.eclipse.emf.ecore.resource.Resource r = rs.createResource(URI.createURI(file));
        r.getContents().add(root);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target", file), xmi, StandardCharsets.UTF_8);
        return xmi;
    }
}
