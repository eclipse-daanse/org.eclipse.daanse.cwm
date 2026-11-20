/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.TypeLibraries;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.Typings;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shape of the model: its classes, its references, its OCL rules and a file of a library and typings. */
class SemanticTypeModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final SemanticTypePackage P = SemanticTypePackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void fourClassesAndOneTypingForEveryElement() {
        assertThat(P.getEClassifiers()).as("the classes").extracting(EClassifier::getName)
                .containsExactly("TypeLibrary", "SemanticType", "ExternalType", "Typing");
        assertThat(P.getEClassifiers()).as("no class is abstract, no enumeration")
                .allSatisfy(c -> assertThat(((EClass) c).isAbstract()).isFalse());
        assertThat(P.getSemanticType().getEStructuralFeatures()).as("a type says no kind")
                .extracting(f -> f.getName()).containsExactly("uri", "sourceName", "sourceNumber", "description",
                        "broader", "sameAs", "sameAsExternal");
        assertThat(P.getTypeLibrary().getESuperTypes()).as("a library is a package")
                .containsExactly(CorePackage.Literals.PACKAGE);
        assertThat(List.of(P.getSemanticType(), P.getTyping())).as("model elements")
                .allSatisfy(c -> assertThat(c.getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT));
        assertThat(P.getExternalType().getESuperTypes()).as("an external type is an element")
                .containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getTyping_Type().getEType()).as("the type of a typing").isSameAs(P.getSemanticType());
        assertThat(P.getTyping_Type().getLowerBound()).as("a typing has a type").isEqualTo(1);
        assertThat(P.getTyping_ModelElement().getEType()).as("what a typing names")
                .isSameAs(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getSemanticType_SourceNumber().isUnsettable()).as("a number there may be absent").isTrue();
    }

    @Test
    void referencesAreOneWayWithAnOppositeRoleNameAndOnlyExternalTypesAreOwned() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EReference r : ((EClass) c).getEReferences()) {
                assertThat(r.getEOpposite()).as("opposite of " + r.getName()).isNull();
                if (r.isMany()) {
                    assertThat(r.isOrdered()).as("order of " + r.getName()).isFalse();
                }
                if (r.isContainment()) {
                    assertThat(r).as("the one containment").isSameAs(P.getSemanticType_SameAsExternal());
                    continue;
                }
                assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                        .as("opposite role of " + r.getName()).startsWith("semantictype");
                assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                        .as("opposite role body of " + r.getName()).startsWith("semantictype");
            }
        }
    }

    @Test
    void attributesUseCwmDataTypes() {
        for (EClassifier c : P.getEClassifiers()) {
            for (EAttribute a : ((EClass) c).getEAttributes()) {
                assertThat(a.getEAttributeType().getEPackage()).as("type of " + a.getName())
                        .isSameAs(CorePackage.eINSTANCE).isNotSameAs(EcorePackage.eINSTANCE);
            }
        }
    }

    @Test
    void theOclRulesNamedByTheirRule() {
        Model model = Models.create("register");
        Package types = Packages.create(model, "types");
        TypeLibrary harbour = TypeLibraries.create(model, "harbour", "https://types.example.org/harbour");
        TypeLibrary coast = TypeLibraries.create(model, "coast", "https://types.example.org/coast");
        SemanticType place = SemanticTypes.create(harbour, "Place");
        SemanticType town = SemanticTypes.broader(SemanticTypes.create(harbour, "Town"), place);
        SemanticType harbourType = SemanticTypes.create(harbour, "Harbour");
        SemanticType lighthouse = SemanticTypes.create(coast, "Lighthouse");
        SemanticTypes.sameAs(town, lighthouse);
        Class port = cls(model, "port");
        Attribute name = attribute(port, "name");
        Row quays = RelationalFactory.eINSTANCE.createRow();
        quays.setName("quays");
        quays.setClassifier(port);
        Packages.create(model, "ledger").getOwnedElement().add(quays);
        Typings.create(types, "harbours", harbourType, port, name, quays);
        Typing names = Typings.create(types, "names", town, name);
        assertThat(messages(model)).as("a model that keeps the OCL rules")
                .noneMatch(m -> m.contains("constraint is violated"));

        place.getBroader().add(place);
        town.getSameAs().add(place);
        Package loose = Packages.create(model, "loose");
        loose.getOwnedElement().add(SemanticTypes.create(harbour, "Revenue"));
        assertThat(messages(model)).as("the OCL rules broken").anyMatch(m -> m.contains("'ST3' constraint"))
                .anyMatch(m -> m.contains("'ST5' constraint")).anyMatch(m -> m.contains("'ST7' constraint"));
        assertThat(names.getType()).as("the typing keeps its type").isSameAs(town);
    }

    @Test
    void aFileOfALibraryAndTypingsReadsBack(@TempDir Path dir) throws Exception {
        Model model = Models.create("register");
        TypeLibrary harbour = TypeLibraries.create(model, "harbour", "https://types.example.org/harbour");
        SemanticType place = SemanticTypes.create(harbour, "Place");
        SemanticType town = SemanticTypes.source(SemanticTypes.broader(SemanticTypes.create(harbour, "Town"), place),
                "Town", 0);
        SemanticTypes.sameAsExternal(town, "https://types.example.org/gazetteer", "Settlement", 4L);
        Class port = cls(model, "port");
        Attribute name = attribute(port, "town");
        Typings.create(Packages.create(model, "types"), "town", town, name);
        URI file = URI.createFileURI(dir.resolve("register.xmi").toString());
        Resource resource = resourceSet().createResource(file);
        resource.getContents().add(model);
        resource.save(Map.of());
        assertThat(Files.readString(dir.resolve("register.xmi"))).as("the file")
                .contains("uri=\"https://types.example.org/harbour\"").contains("sourceNumber=\"0\"")
                .contains("libraryUri=\"https://types.example.org/gazetteer\"");

        for (boolean indexed : new boolean[] { false, true }) {
            ResourceSet in = resourceSet();
            Resource read = in.getResource(file, true);
            if (indexed) {
                InverseReferences.install(in);
            }
            assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(read)).as("unresolved references").isEmpty();
            Model loaded = (Model) read.getContents().get(0);
            TypeLibrary library = TypeLibraries.byUri(loaded, "https://types.example.org/harbour").orElseThrow();
            SemanticType placeRead = SemanticTypes.named(library, "Place").orElseThrow();
            SemanticType townRead = SemanticTypes.named(library, "Town").orElseThrow();
            assertThat(placeRead.isSetSourceNumber()).as("no number there stays none").isFalse();
            assertThat(townRead.isSetSourceNumber()).as("the number 0 stays set").isTrue();
            assertThat(townRead.getSameAsExternal()).singleElement().extracting(ExternalType::getSourceNumber)
                    .as("the number of the external type").isEqualTo(4L);
            Class portRead = (Class) loaded.getOwnedElement().stream().filter(Class.class::isInstance).findFirst()
                    .orElseThrow();
            assertThat(Typings.typeOf((Attribute) portRead.getFeature().get(0)))
                    .as("the type of the column read back, indexed " + indexed).contains(townRead);
            assertThat(SemanticTypes.isA(townRead, placeRead)).as("broader read back").isTrue();
        }
    }

    private static Class cls(Model model, String name) {
        Class c = CoreFactory.eINSTANCE.createClass();
        c.setName(name);
        model.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attribute(Class owner, String name) {
        Attribute a = CoreFactory.eINSTANCE.createAttribute();
        a.setName(name);
        owner.getFeature().add(a);
        return a;
    }

    private static List<String> messages(EObject root) {
        List<String> messages = new ArrayList<>();
        collect(Diagnostician.INSTANCE.validate(root), messages);
        return messages;
    }

    private static void collect(Diagnostic diagnostic, List<String> messages) {
        if (diagnostic.getSeverity() != Diagnostic.OK && diagnostic.getChildren().isEmpty()) {
            messages.add(diagnostic.getMessage());
        }
        diagnostic.getChildren().forEach(child -> collect(child, messages));
    }

    private static ResourceSet resourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        rs.getPackageRegistry().put(P.getNsURI(), P);
        rs.getPackageRegistry().put(CorePackage.eNS_URI, CorePackage.eINSTANCE);
        return rs;
    }
}
