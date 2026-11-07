/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Event;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecyclePackage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.DeletionClasses;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Obligations;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.ObligationsValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.RetentionHolds;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shape of the obligations metamodel, its OCL invariants, and duties in an XMI file. */
class ObligationsModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final ObligationsPackage P = ObligationsPackage.eINSTANCE;
    private static final LifecyclePackage L = LifecyclePackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void classesAndTheirSupertypes() {
        List<EClass> classes = P.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast)
                .toList();
        assertThat(classes).extracting(ENamedElement::getName).containsExactly("LegalSource", "Obligation",
                "RetentionObligation", "DeletionObligation", "RetentionHold", "DeletionClass");
        assertThat(classes).filteredOn(EClass::isAbstract).extracting(ENamedElement::getName).containsExactly("Obligation");
        assertThat(P.getLegalSource().getESuperTypes()).containsExactly(BusinessinformationPackage.Literals.DOCUMENT);
        assertThat(P.getObligation().getESuperTypes()).containsExactly(L.getLifecycleRule());
        assertThat(P.getRetentionObligation().getESuperTypes()).containsExactly(P.getObligation());
        assertThat(P.getDeletionObligation().getESuperTypes()).containsExactly(P.getObligation());
        assertThat(P.getRetentionHold().getESuperTypes()).containsExactly(L.getLifecycleRule());
        assertThat(P.getDeletionClass().getESuperTypes()).containsExactly(L.getLifecycleRule());
        assertThat(CorePackage.Literals.CONSTRAINT.isSuperTypeOf(P.getRetentionHold())).isTrue();
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void startsAreContainedAndTheHolderIsOneWay() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    if (r.isContainment()) {
                        assertThat(r.getEReferenceType()).as(r.getName()).isSameAs(L.getPeriodStart());
                    } else {
                        assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                                .as(r.getName()).startsWith("obligations");
                    }
                }
            }
        }
        assertThat(P.getObligation_Start().isContainment()).isTrue();
        assertThat(P.getDeletionClass_Start().isContainment()).isTrue();
        assertThat(P.getRetentionHold_Holder().isContainment()).isFalse();
    }

    @Test
    void attributesUseCwmDataTypesOrThePeriod() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EAttribute a : cls.getEAttributes()) {
                    EClassifier type = a.getEAttributeType();
                    assertThat(type == L.getPeriod() || type.getEPackage() == CorePackage.eINSTANCE).as(a.getName()).isTrue();
                }
            }
        }
    }

    @Test
    void untilAfterSince() {
        Model m = model();
        RetentionHold hold = RetentionHolds.create(rules(m), "lawsuit", "lawsuit 4711",
                Instant.parse("2026-11-01T00:00:00Z"), data(m));
        assertThat(severity((v, d) -> v.validateRetentionHold_UntilAfterSince(hold, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        RetentionHolds.release(hold, Instant.parse("2027-02-01T00:00:00Z"));
        assertThat(severity((v, d) -> v.validateRetentionHold_UntilAfterSince(hold, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        hold.setUntil("2026-10-01T00:00:00Z");
        assertThat(severity((v, d) -> v.validateRetentionHold_UntilAfterSince(hold, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void abstractStart() {
        Model m = model();
        Event ended = BehavioralFactory.eINSTANCE.createEvent();
        ended.setName("contractEnded");
        rules(m).getOwnedElement().add(ended);
        DeletionClass byEvent = DeletionClasses.create(rules(m), "contracts", PeriodStarts.event(ended), "P3Y", data(m));
        DeletionClass byValue = DeletionClasses.create(rules(m), "logs", PeriodStarts.valueTime(), "P90D", data(m));
        DeletionClass concrete = DeletionClasses.create(rules(m), "bound", PeriodStarts.event(ended, issuedOn(m)), "P3Y",
                data(m));
        assertThat(severity((v, d) -> v.validateDeletionClass_AbstractStart(byEvent, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateDeletionClass_AbstractStart(byValue, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateDeletionClass_AbstractStart(concrete, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void dutiesTravelInXmi(@TempDir Path dir) throws Exception {
        Model m = model();
        Package rules = rules(m);
        RetentionObligation books = Obligations.immutable(Obligations.retain(rules, "books-8y", "P8Y",
                PeriodStarts.aligned(PeriodStarts.valueTime(),
                        org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment.END_OF_YEAR, "Europe/Berlin"),
                data(m)));
        LegalSources.cite(LegalSources.validity(LegalSources.provision(
                LegalSources.create(rules, "ao-147", "https://www.gesetze-im-internet.de/ao_1977/__147.html",
                        LegalSources.STATUTE), "§ 147 (3)", "DE"), "2025-01-01", null), books);
        RetentionHolds.create(rules, "audit", "tax audit 2026", Instant.parse("2026-11-01T00:00:00Z"), data(m));

        URI uri = URI.createFileURI(dir.resolve("lab.xmi").toString());
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Resource out = rs.createResource(uri);
        out.getContents().add(m);
        out.save(Map.of());
        String xmi = Files.readString(dir.resolve("lab.xmi"), StandardCharsets.UTF_8);
        assertThat(xmi).contains("xsi:type=\"obligations:RetentionObligation\"", "xsi:type=\"obligations:LegalSource\"",
                "xsi:type=\"obligations:RetentionHold\"", "<start xsi:type=\"lifecycle:ValueTime\"");

        ResourceSet fresh = new ResourceSetImpl();
        fresh.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Model loaded = (Model) fresh.getResource(uri, true).getContents().get(0);
        Package loadedData = (Package) loaded.getOwnedElement().get(0);
        RetentionObligation found = org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance
                .retentions(loadedData).get(0);
        assertThat(found.getMinimum()).isEqualTo("P8Y");
        assertThat(found.isImmutable()).isTrue();
        assertThat(LegalSources.of(found)).singleElement().satisfies(s -> {
            assertThat(s.getProvision()).isEqualTo("§ 147 (3)");
            assertThat(s.getValidFrom()).isEqualTo("2025-01-01");
        });
        assertThat(RetentionHolds.active(loadedData, Instant.parse("2026-12-01T00:00:00Z"))).hasSize(1);
    }

    private static Model model() {
        Model m = CoreFactory.eINSTANCE.createModel();
        m.setName("lab");
        Package data = CoreFactory.eINSTANCE.createPackage();
        data.setName("data");
        m.getOwnedElement().add(data);
        Class invoice = CoreFactory.eINSTANCE.createClass();
        invoice.setName("Invoice");
        data.getOwnedElement().add(invoice);
        Attribute issuedOn = CoreFactory.eINSTANCE.createAttribute();
        issuedOn.setName("issuedOn");
        invoice.getFeature().add(issuedOn);
        Package rules = CoreFactory.eINSTANCE.createPackage();
        rules.setName("rules");
        m.getOwnedElement().add(rules);
        return m;
    }

    private static Attribute issuedOn(Model m) {
        return (Attribute) ((Class) data(m).getOwnedElement().get(0)).getFeature().get(0);
    }

    private static Package data(Model m) {
        return (Package) m.getOwnedElement().get(0);
    }

    private static Package rules(Model m) {
        return (Package) m.getOwnedElement().get(1);
    }

    private static int severity(BiPredicate<ObligationsValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(ObligationsValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
