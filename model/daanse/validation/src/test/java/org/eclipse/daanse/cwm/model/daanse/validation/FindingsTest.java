/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.daanse.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What the EMF check says becomes findings: a constraint named by its rule is a finding of that rule, any other
 * diagnostic one of the module {@code ecore}. A town of a dynamic model has a constraint {@code X1} (a town has at
 * least one inhabitant) checked by a delegate, and a required name.
 */
class FindingsTest {

    private static final String DELEGATE = "https://www.daanse.org/test/findings/delegate";
    private static final EPackage TOWNS = EcoreFactory.eINSTANCE.createEPackage();
    private static final EClass TOWN = EcoreFactory.eINSTANCE.createEClass();
    private static final EAttribute NAME = EcoreFactory.eINSTANCE.createEAttribute();
    private static final EAttribute INHABITANTS = EcoreFactory.eINSTANCE.createEAttribute();

    /** Keeps the rule X1: a town has at least one inhabitant. */
    private static final EValidator.ValidationDelegate X1 = new EValidator.ValidationDelegate() {

        @Override
        public boolean validate(EClass eClass, EObject eObject, Map<Object, Object> context, EOperation invariant,
                String expression) {
            return true;
        }

        @Override
        public boolean validate(EClass eClass, EObject eObject, Map<Object, Object> context, String constraint,
                String expression) {
            return !"X1".equals(constraint) || ((Integer) eObject.eGet(INHABITANTS)) > 0;
        }

        @Override
        public boolean validate(EDataType eDataType, Object value, Map<Object, Object> context, String constraint,
                String expression) {
            return true;
        }
    };

    @BeforeAll
    static void towns() {
        TOWNS.setName("towns");
        TOWNS.setNsPrefix("towns");
        TOWNS.setNsURI("https://www.daanse.org/test/findings/towns");
        annotate(TOWNS, EcorePackage.eNS_URI, "validationDelegates", DELEGATE);
        TOWN.setName("Town");
        NAME.setName("name");
        NAME.setEType(EcorePackage.Literals.ESTRING);
        NAME.setLowerBound(1);
        INHABITANTS.setName("inhabitants");
        INHABITANTS.setEType(EcorePackage.Literals.EINT);
        TOWN.getEStructuralFeatures().add(NAME);
        TOWN.getEStructuralFeatures().add(INHABITANTS);
        annotate(TOWN, EcorePackage.eNS_URI, "constraints", "X1");
        annotate(TOWN, DELEGATE, "X1", "inhabitants > 0");
        TOWNS.getEClassifiers().add(TOWN);
        EPackage.Registry.INSTANCE.put(TOWNS.getNsURI(), TOWNS);
        EValidator.ValidationDelegate.Registry.INSTANCE.put(DELEGATE, X1);
    }

    @AfterAll
    static void away() {
        EPackage.Registry.INSTANCE.remove(TOWNS.getNsURI());
        EValidator.ValidationDelegate.Registry.INSTANCE.remove(DELEGATE);
    }

    @Test
    void aTownThatKeepsEveryRuleGivesNothing() {
        assertThat(Findings.of("towns", Diagnostician.INSTANCE.validate(town("Gotha", 128)))).as("findings").isEmpty();
    }

    @Test
    void aConstraintNamedByItsRuleIsAFindingOfThatRule() {
        EObject weimar = town("Weimar", 0);

        assertThat(Findings.of("towns", Diagnostician.INSTANCE.validate(weimar))).as("findings").singleElement()
                .satisfies(finding -> {
                    assertThat(finding.id()).as("id").isEqualTo("towns/X1");
                    assertThat(finding.severity()).as("severity").isEqualTo(Severity.ERROR);
                    assertThat(finding.element()).as("element").isSameAs(weimar);
                });
    }

    @Test
    void aRequiredFeatureLeftUnsetIsAFindingOfTheEmfCheck() {
        EObject unnamed = town(null, 64);

        assertThat(Findings.of("towns", Diagnostician.INSTANCE.validate(unnamed))).as("findings").singleElement()
                .satisfies(finding -> {
                    assertThat(finding.id()).as("id").isEqualTo("ecore/EMF1");
                    assertThat(finding.element()).as("element").isSameAs(unnamed);
                });
    }

    @Test
    void bothAreFoundOnOneTown() {
        assertThat(Findings.of("towns", Diagnostician.INSTANCE.validate(town(null, 0)))).as("findings")
                .extracting(Finding::id).containsExactlyInAnyOrder("towns/X1", "ecore/EMF1");
    }

    @Test
    void aWarningAndANoteKeepTheirWeight() {
        BasicDiagnostic root = new BasicDiagnostic(Diagnostic.WARNING, "test", 0, "root", null);
        EObject gera = town("Gera", 32);
        root.add(new BasicDiagnostic(Diagnostic.WARNING, "test", 0, "The 'X2' constraint is violated on 'Town Gera'",
                new Object[] { gera }));
        root.add(new BasicDiagnostic(Diagnostic.INFO, "test", 4, "Gera is a town", new Object[] { "not an element" }));
        root.add(new BasicDiagnostic(Diagnostic.OK, "test", 0, "Gera is fine", new Object[] { gera }));

        assertThat(Findings.of("towns", root)).as("findings").satisfiesExactly(warning -> {
            assertThat(warning.id()).as("warning id").isEqualTo("towns/X2");
            assertThat(warning.severity()).as("warning").isEqualTo(Severity.WARNING);
        }, note -> {
            assertThat(note.id()).as("note id").isEqualTo("ecore/EMF4");
            assertThat(note.severity()).as("note").isEqualTo(Severity.INFO);
            assertThat(note.element()).as("no element where the datum is none").isNull();
        });
    }

    private static EObject town(String name, int inhabitants) {
        EObject town = EcoreUtil.create(TOWN);
        if (name != null) {
            town.eSet(NAME, name);
        }
        town.eSet(INHABITANTS, inhabitants);
        return town;
    }

    private static void annotate(org.eclipse.emf.ecore.EModelElement element, String source, String key,
            String value) {
        EAnnotation annotation = element.getEAnnotation(source);
        if (annotation == null) {
            annotation = EcoreFactory.eINSTANCE.createEAnnotation();
            annotation.setSource(source);
            element.getEAnnotations().add(annotation);
        }
        annotation.getDetails().put(key, value);
    }
}
