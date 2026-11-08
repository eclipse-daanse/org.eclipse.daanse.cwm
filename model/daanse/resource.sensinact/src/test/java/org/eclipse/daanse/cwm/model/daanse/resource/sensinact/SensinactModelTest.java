/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.sensinact;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Providers;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Resources;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Services;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The shape of the sensinact structure metamodel and its OCL invariants. */
class SensinactModelTest {

    private static final SensinactPackage P = SensinactPackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void theStructureIsCwmClassesAndAttributes() {
        assertThat(P.getNsURI()).endsWith("org.eclipse.daanse.cwm.model.daanse.resource.sensinact");
        assertThat(P.getEClassifiers()).containsExactly(P.getProvider(), P.getService(), P.getResource());
        assertThat(P.getProvider().getESuperTypes()).containsExactly(CorePackage.Literals.CLASS);
        assertThat(P.getService().getESuperTypes()).containsExactly(CorePackage.Literals.CLASS);
        assertThat(P.getResource().getESuperTypes()).containsExactly(CorePackage.Literals.ATTRIBUTE);
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void attributesUseCwmDataTypes() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EStructuralFeature f : ((EClass) c).getEStructuralFeatures()) {
                assertThat(f).as(c.getName() + "." + f.getName()).isInstanceOf(EAttribute.class);
                assertThat(((EAttribute) f).getEAttributeType().getEPackage()).as(f.getName())
                        .isSameAs(CorePackage.eINSTANCE).isNotSameAs(EcorePackage.eINSTANCE);
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

    /** Lines of the XMI {@link #theBatterySensorInXmi()} writes. */
    private static final String XMI = """
            xmlns:sensinact="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.sensinact"
            <ownedElement xsi:type="sensinact:Provider" name="battery-sensor" friendlyNameRequired="true">
            <ownedElement xsi:type="sensinact:Service" name="battery" displayName="Battery">
            <feature xsi:type="sensinact:Resource" name="level" type="_Double" displayName="Battery Level"/>
            """;

    @Test
    void theBatterySensorInXmi() throws IOException {
        Model model = model();
        DataType decimal = dataType(model);
        Package providers = CoreFactory.eINSTANCE.createPackage();
        providers.setName("providers");
        model.getOwnedElement().add(providers);
        Provider sensor = Providers.create(providers, "battery-sensor");
        sensor.setFriendlyNameRequired(true);
        Service battery = Services.create(sensor, "battery");
        battery.setDisplayName("Battery");
        Resources.create(battery, "level", decimal).setDisplayName("Battery Level");

        String xmi = xmi(model, "battery-sensor.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
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

    /** {@code root} as XMI with short ids and without the ids. */
    private static String xmi(Model root, String file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        org.eclipse.emf.ecore.resource.Resource r = rs.createResource(URI.createURI(file));
        r.getContents().add(root);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        return out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
    }
}
