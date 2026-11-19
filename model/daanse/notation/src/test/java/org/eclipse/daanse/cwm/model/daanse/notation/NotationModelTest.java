/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.notation.util.Codec;
import org.eclipse.daanse.cwm.model.daanse.notation.util.NotationValidator;
import org.eclipse.daanse.cwm.model.daanse.notation.util.Notations;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
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

/** The shape of the notation metamodel, its OCL invariants, and notations in an XMI file. */
class NotationModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final NotationPackage P = NotationPackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void aNotationHoldsOneFormOfAClosedSet() {
        assertThat(P.getNotation().getESuperTypes()).as("a model element")
                .containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getForm().isAbstract()).as("a form is abstract").isTrue();
        assertThat(P.getForm().getESuperTypes()).as("an element without a name")
                .containsExactly(CorePackage.Literals.ELEMENT);
        List<EClass> forms = P.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast)
                .filter(c -> c.getESuperTypes().contains(P.getForm())).toList();
        assertThat(forms).as("the forms").extracting(EClass::getName).containsExactly("TemporalText",
                "TemporalNumber", "NumberText", "BooleanText", "PatternText");
        assertThat(P.getNotation_Form().isContainment()).as("the form is owned").isTrue();
        assertThat(P.getNotation_Form().getUpperBound()).as("at most one").isEqualTo(1);
        assertThat(P.getEClassifiers().stream().filter(EClass.class::isInstance)).as("seven classes").hasSize(7);
        assertThat(P.getTrim().getELiterals()).as("as the trim of CSV on the Web").extracting(l -> l.getName())
                .containsExactly("both", "start", "end", "none");
        assertThat(P.getTemporalForm().getELiterals()).as("ten ISO 8601 forms").hasSize(10);
        assertThat(P.getTemporalText_Named().isUnsettable()).as("an unset named form is no first literal").isTrue();
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .as("hidden opposites").isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .as("OCL validates").isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void referencesAreOneWayWithAnOppositeRoleNameAndOnlyTheFormIsOwned() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    if (r.isContainment()) {
                        assertThat(r).as("the only containment").isSameAs(P.getNotation_Form());
                        continue;
                    }
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(r.getName()).startsWith("notation");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(r.getName()).startsWith("notation");
                    if (r.isMany()) {
                        assertThat(r.isOrdered()).as(r.getName() + " is a set").isFalse();
                    }
                }
            }
        }
    }

    @Test
    void attributesUseCwmDataTypesOrTheEnumerationsOfThePackage() {
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
    void numberTextOcl() {
        Notation r = notation();
        NumberText n = Notations.numberText(r, ",", ".");
        assertThat(severity((v, d) -> v.validateNumberText_DecimalCharIsOneCharacter(n, d, new HashMap<>())))
                .as("one decimal character").isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateNumberText_DecimalAndGroupDiffer(n, d, new HashMap<>())))
                .as("they differ").isEqualTo(Diagnostic.OK);
        n.setGroupChar(",");
        n.setDecimalChar(",,");
        assertThat(severity((v, d) -> v.validateNumberText_DecimalCharIsOneCharacter(n, d, new HashMap<>())))
                .as("two characters").isEqualTo(Diagnostic.ERROR);
        n.setDecimalChar(",");
        assertThat(severity((v, d) -> v.validateNumberText_DecimalAndGroupDiffer(n, d, new HashMap<>())))
                .as("the same").isEqualTo(Diagnostic.ERROR);
        n.setGroupChar(null);
        assertThat(severity((v, d) -> v.validateNumberText_GroupCharIsOneCharacter(n, d, new HashMap<>())))
                .as("no group character").isEqualTo(Diagnostic.OK);
        n.setGroupChar("..");
        assertThat(severity((v, d) -> v.validateNumberText_GroupCharIsOneCharacter(n, d, new HashMap<>())))
                .as("two group characters").isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void wordsOcl() {
        Notation r = notation();
        BooleanText b = Notations.booleanText(r, "J", "N");
        Notations.missing(r, "-");
        assertThat(severity((v, d) -> v.validateBooleanText_NoWordTrueAndFalse(b, d, new HashMap<>())))
                .as("distinct words").isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateNotation_NoMissingWordIsTrueOrFalse(r, d, new HashMap<>())))
                .as("a missing word apart").isEqualTo(Diagnostic.OK);
        b.getFalseWords().add("J");
        r.getMissingWords().add("N");
        assertThat(severity((v, d) -> v.validateBooleanText_NoWordTrueAndFalse(b, d, new HashMap<>())))
                .as("J true and false").isEqualTo(Diagnostic.ERROR);
        assertThat(severity((v, d) -> v.validateNotation_NoMissingWordIsTrueOrFalse(r, d, new HashMap<>())))
                .as("N missing and false").isEqualTo(Diagnostic.ERROR);
        Notation other = notation();
        Notations.numberText(other, ".");
        Notations.missing(other, "N");
        assertThat(severity((v, d) -> v.validateNotation_NoMissingWordIsTrueOrFalse(other, d, new HashMap<>())))
                .as("no boolean form").isEqualTo(Diagnostic.OK);
    }

    @Test
    void theUnitOfATemporalNumberIsAUnitOfTimeOcl() {
        Notation r = notation();
        SystemOfUnits si = SI.create((Model) r.getNamespace().getNamespace());
        TemporalNumber n = Notations.temporalNumber(r, Notations.UNIX_EPOCH, SI.unit(si, SI.SECOND));
        assertThat(severity((v, d) -> v.validateTemporalNumber_UnitOfTime(n, d, new HashMap<>()))).as("seconds")
                .isEqualTo(Diagnostic.OK);
        n.setUnit(SI.unit(si, SI.METRE));
        assertThat(severity((v, d) -> v.validateTemporalNumber_UnitOfTime(n, d, new HashMap<>()))).as("metres")
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void notationsTravelInXmiAndAreFoundFromTheElement(@TempDir Path dir) throws Exception {
        Model model = CoreFactory.eINSTANCE.createModel();
        model.setName("weather");
        Package design = CoreFactory.eINSTANCE.createPackage();
        design.setName("design");
        model.getOwnedElement().add(design);
        DataType text = CoreFactory.eINSTANCE.createDataType();
        text.setName("Text");
        design.getOwnedElement().add(text);
        Class station = CoreFactory.eINSTANCE.createClass();
        station.setName("Station");
        design.getOwnedElement().add(station);
        Attribute opened = CoreFactory.eINSTANCE.createAttribute();
        opened.setName("opened");
        opened.setType(text);
        station.getFeature().add(opened);
        Package notes = CoreFactory.eINSTANCE.createPackage();
        notes.setName("notes");
        model.getOwnedElement().add(notes);
        Notation r = Notations.missing(Notations.create(notes, "text", text), "-");
        Notations.language(Notations.temporalText(r, "d. MMMM yyyy"), "de");

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
            assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(in)).as("every reference resolves").isEmpty();
            Package d = (Package) loaded.getOwnedElement().get(0);
            Class s = (Class) d.getOwnedElement().stream().filter(Class.class::isInstance).findFirst().orElseThrow();
            Attribute o = (Attribute) s.getFeature().get(0);
            Notation found = Notations.of(o).orElseThrow();
            assertThat(found.getForm()).as("the form travelled, indexed " + indexed).isInstanceOf(TemporalText.class);
            assertThat(Codec.of(found).read("1. Mai 1998")).as("and reads, indexed " + indexed)
                    .isEqualTo(LocalDate.of(1998, 5, 1));
            assertThat(Codec.of(found).read("-")).as("a missing word").isNull();
        }
    }

    private static Notation notation() {
        Model model = CoreFactory.eINSTANCE.createModel();
        model.setName("m");
        Package notes = CoreFactory.eINSTANCE.createPackage();
        notes.setName("notes");
        model.getOwnedElement().add(notes);
        DataType text = CoreFactory.eINSTANCE.createDataType();
        text.setName("Text");
        model.getOwnedElement().add(text);
        return Notations.create(notes, "r", text);
    }

    private static int severity(BiPredicate<NotationValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(NotationValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
