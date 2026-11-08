/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.mapping.sensinact;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.SensinactPackage;
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

/** The shape of the sensinact mapping metamodel. */
class SensinactMappingModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final SensinactMappingPackage P = SensinactMappingPackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void theMappingStandsOnCwmNamespacesAndModelElements() {
        assertThat(P.getProviderMapping().getESuperTypes()).containsExactly(CorePackage.Literals.NAMESPACE);
        assertThat(P.getServiceMapping().getESuperTypes()).containsExactly(CorePackage.Literals.NAMESPACE);
        assertThat(P.getResourceMapping().getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getAdminMapping().getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getValueSource().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
    }

    @Test
    void theMappingPointsIntoTheProvidersOfResourceSensinact() {
        assertThat(P.getNsURI()).endsWith("org.eclipse.daanse.cwm.model.daanse.mapping.sensinact");
        assertThat(P.getNsPrefix()).isEqualTo("sensinactmapping");
        assertThat(P.getProviderMapping_Provider().getEType()).isSameAs(SensinactPackage.Literals.PROVIDER);
        assertThat(P.getServiceMapping_Service().getEType()).isSameAs(SensinactPackage.Literals.SERVICE);
        assertThat(P.getResourceMapping_Resource().getEType()).isSameAs(SensinactPackage.Literals.RESOURCE);
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
        assertThat(Diagnostician.INSTANCE.validate(SensinactMappingFactory.eINSTANCE.createPathSource()).getSeverity())
                .isEqualTo(Diagnostic.ERROR);
        assertThat(Diagnostician.INSTANCE.validate(SensinactMappingFactory.eINSTANCE.createLiteralSource()).getSeverity())
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

    /** Lines of the XMI {@link #theBatteryExampleInXmi()} writes: the provider and one mapping onto it. */
    private static final String XMI = """
            xmlns:sensinact="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.sensinact"
            xmlns:sensinactmapping="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.mapping.sensinact"
            <ownedElement xsi:type="sensinact:Provider" name="battery-sensor" friendlyNameRequired="true">
            <ownedElement xsi:type="sensinact:Service" name="battery" displayName="Battery">
            <feature xsi:type="sensinact:Resource" name="level" type="_EDouble" displayName="Battery Level"/>
            <ownedElement xsi:type="sensinactmapping:ProviderMapping" name="dragino-battery-sensor" \
            provider="_battery-sensor" sourceClass="_DraginoLSE01Uplink">
            <ownedElement xsi:type="sensinactmapping:ServiceMapping" name="battery" service="_battery-sensor.battery">
            <ownedElement xsi:type="sensinactmapping:ResourceMapping" name="level" \
            resource="_battery-sensor.battery.level">
            <value xsi:type="sensinactmapping:PathSource" \
            path="_DraginoLSE01Uplink.object _dragino.DecodedObject.batV"/>
            <id xsi:type="sensinactmapping:PathSource" path="_UplinkMessage.deduplicationId">
            <fallback xsi:type="sensinactmapping:LiteralSource" literal="Dragino LSE01 Battery Sensor"/>
            <timestamp xsi:type="sensinactmapping:PathSource" path="_UplinkMessage.rxInfo _RxInfo.time" index="0"/>
            <admin name="admin">
            <friendlyName xsi:type="sensinactmapping:PathSource" \
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
