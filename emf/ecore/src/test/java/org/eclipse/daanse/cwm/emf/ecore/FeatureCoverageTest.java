/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The corpus covers the mapping: every Ecore form the conversion writes and every note rule it
 * reports occurs at least once in the expected output of the corpus cases. A new mapping rule
 * without a corpus case fails here.
 */
class FeatureCoverageTest {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String OCL = Options.DEFAULT_OCL_DELEGATE_URI;

    private static final List<EObject> ELEMENTS = new ArrayList<>();
    private static final Set<String> RULES = new TreeSet<>();
    private static final StringBuilder TEXT = new StringBuilder();
    private static final Set<String> FILES = new TreeSet<>();

    @BeforeAll
    static void load() throws IOException {
        CwmPackages.registerKnown();
        try (Stream<Path> dirs = Files.list(CorpusCase.dir().resolve("expected"))) {
            for (Path dir : dirs.sorted().toList()) {
                ResourceSet rs = CwmPackages.newResourceSet();
                for (String name : CorpusTest.fileNames(dir)) {
                    Path f = dir.resolve(name);
                    FILES.add(name.substring(name.lastIndexOf('.') + 1));
                    if (name.equals("notes.txt")) {
                        Files.readAllLines(f).forEach(line -> RULES.add(line.split(" ")[1]));
                    } else if (name.endsWith(".ecore")) {
                        TEXT.append(Files.readString(f));
                        EPackage p = (EPackage) CwmPackages.load(rs, f).getContents().get(0);
                        ELEMENTS.add(p);
                        p.eAllContents().forEachRemaining(ELEMENTS::add);
                    }
                }
                EcoreUtil.resolveAll(rs);
            }
        }
    }

    @Test
    void classifiers() {
        assertThat(any(EClass.class, EClass::isAbstract)).as("abstract class").isTrue();
        assertThat(any(EClass.class, EClass::isInterface)).as("interface").isTrue();
        assertThat(any(EClass.class, c -> c.getESuperTypes().size() > 1)).as("several super types").isTrue();
        assertThat(any(EClass.class, c -> c.getESuperTypes().stream().anyMatch(s -> s.getEPackage() == EcorePackage.eINSTANCE)))
                .as("a super type of another metamodel").isTrue();
        assertThat(any(EClass.class, c -> "java.util.Map$Entry".equals(c.getInstanceClassName()))).as("map entry class")
                .isTrue();
        assertThat(any(EDataType.class, d -> !(d instanceof EEnum) && d.getInstanceClassName() != null
                && !d.getInstanceClassName().startsWith("java.lang."))).as("data type of a non-lang class").isTrue();
        assertThat(any(EDataType.class, d -> d.getInstanceTypeName() != null && d.getInstanceTypeName().contains("<")))
                .as("parameterized instance type").isTrue();
        assertThat(any(EDataType.class, d -> !d.isSerializable())).as("not serializable").isTrue();
        assertThat(any(EEnum.class, e -> e.getELiterals().stream().anyMatch(l -> l.getValue() != e.getELiterals().indexOf(l))))
                .as("literal value other than the position").isTrue();
        assertThat(any(EPackage.class, p -> !p.getESubpackages().isEmpty())).as("sub-package").isTrue();
    }

    @Test
    void features() {
        assertThat(any(EAttribute.class, a -> a.getDefaultValueLiteral() != null && !(a.getEType() instanceof EEnum)))
                .as("default value of a data type").isTrue();
        assertThat(any(EAttribute.class, a -> a.getDefaultValueLiteral() != null && a.getEType() instanceof EEnum))
                .as("default value of an enumeration").isTrue();
        assertThat(any(EAttribute.class, EAttribute::isID)).as("iD").isTrue();
        assertThat(any(EStructuralFeature.class, f -> f.isMany() && f.isOrdered())).as("ordered").isTrue();
        assertThat(any(EStructuralFeature.class, f -> !f.isChangeable() && !f.isDerived())).as("frozen").isTrue();
        assertThat(any(EStructuralFeature.class, f -> f.getLowerBound() > 1 && f.getUpperBound() > 1))
                .as("bounds other than 0..1, 1 and *").isTrue();
        assertThat(any(EReference.class, r -> r.isContainment() && r.getEAnnotation(CwmAnnotations.ASSOCIATION) == null))
                .as("containment from a class-typed attribute").isTrue();
        assertThat(any(EReference.class, r -> r.isContainment() && r.getEAnnotation(CwmAnnotations.ASSOCIATION) != null))
                .as("containment from a composite").isTrue();
        assertThat(any(EReference.class, EReference::isContainer)).as("container").isTrue();
        assertThat(any(EReference.class, r -> r.getEOpposite() != null)).as("opposite").isTrue();
        assertThat(any(EReference.class, r -> r.getEType() == r.getEContainingClass() && r.getEOpposite() != null))
                .as("reflexive").isTrue();
        assertThat(any(EReference.class, r -> EcoreUtil.getAnnotation(r, CwmAnnotations.EMOF,
                CwmAnnotations.KEY_OPPOSITE_ROLE_NAME) != null)).as("hidden opposite").isTrue();
        assertThat(any(EReference.class, r -> r.isTransient() && !r.isDerived())).as("transient").isTrue();
        assertThat(any(EStructuralFeature.class, f -> f.isDerived() && f.isVolatile() && f.isTransient()))
                .as("derived").isTrue();
        assertThat(any(EReference.class, r -> r.getEType().getEPackage() == EcorePackage.eINSTANCE))
                .as("reference to another metamodel").isTrue();
        assertThat(any(EReference.class, r -> r.isContainment() && r.isMany()
                && "java.util.Map$Entry".equals(r.getEType().getInstanceClassName()))).as("map").isTrue();
        assertThat(TEXT).as("reference into another written file").containsPattern("eType=\"ecore:EClass [\\w-]+\\.ecore#//");
    }

    @Test
    void behaviour() {
        assertThat(any(EParameter.class, p -> p.getLowerBound() == 1)).as("parameter lower bound").isTrue();
        assertThat(any(EParameter.class, p -> p.getEType() != null && p.getEType().getEPackage() == EcorePackage.eINSTANCE))
                .as("parameter typed by another metamodel").isTrue();
        assertThat(any(EOperation.class, o -> o.getEContainingClass().getEAllSuperTypes().stream()
                .anyMatch(s -> s.getEOperations().stream().anyMatch(o::isOverrideOf)))).as("override").isTrue();
        assertThat(any(EOperation.class, EcoreUtil::isInvariant)).as("invariant operation").isTrue();
        assertThat(annotated(EOperation.class, OCL, "body")).as("OCL body").isTrue();
        assertThat(annotated(EOperation.class, GENMODEL, "body")).as("Java body").isTrue();
        assertThat(annotated(EStructuralFeature.class, OCL, "derivation")).as("OCL derivation").isTrue();
        assertThat(annotated(EStructuralFeature.class, GENMODEL, "get")).as("Java derivation").isTrue();
        assertThat(any(EClass.class, c -> !EcoreUtil.getConstraints(c).isEmpty())).as("invariant").isTrue();
        assertThat(any(EPackage.class, p -> !EcoreUtil.getValidationDelegates(p).isEmpty())).as("validation delegate")
                .isTrue();
        assertThat(any(EPackage.class, p -> !EcoreUtil.getInvocationDelegates(p).isEmpty())).as("invocation delegate")
                .isTrue();
        assertThat(any(EPackage.class, p -> !EcoreUtil.getSettingDelegates(p).isEmpty())).as("setting delegate").isTrue();
    }

    @Test
    void annotations() {
        assertThat(annotated(EModelElement.class, CwmAnnotations.ELEMENT, CwmAnnotations.KEY_ID)).isTrue();
        assertThat(annotated(EReference.class, CwmAnnotations.AGGREGATION, "kind")).isTrue();
        assertThat(annotated(EClass.class, CwmAnnotations.ASSOCIATION, null)).as("association without navigable end")
                .isTrue();
        assertThat(annotated(EStructuralFeature.class, CwmAnnotations.SCOPE, "ownerScope")).isTrue();
        assertThat(annotated(EStructuralFeature.class, CwmAnnotations.CHANGEABILITY, "value")).isTrue();
        assertThat(annotated(EStructuralFeature.class, CwmAnnotations.MULTIPLICITY_RANGES, "extra")).isTrue();
        assertThat(annotated(EParameter.class, CwmAnnotations.PARAMETER, "kind")).isTrue();
        assertThat(annotated(EParameter.class, CwmAnnotations.PARAMETER, "default")).isTrue();
        assertThat(annotated(EOperation.class, CwmAnnotations.OPERATION, "isAbstract")).isTrue();
        assertThat(annotated(EOperation.class, CwmAnnotations.OPERATION, "isQuery")).isTrue();
        assertThat(annotated(EEnum.class, CwmAnnotations.ENUMERATION, "isOrdered")).isTrue();
        assertThat(annotated(EModelElement.class, CwmAnnotations.STEREOTYPE, "name")).isTrue();
        assertThat(annotated(EModelElement.class, CwmAnnotations.TAGGED_VALUE, null)).isTrue();
        assertThat(annotated(EModelElement.class, CwmAnnotations.VISIBILITY, "value")).isTrue();
        assertThat(annotated(EClass.class, CwmAnnotations.CONSTRAINT, null)).isTrue();
        assertThat(annotated(EStructuralFeature.class, CwmAnnotations.CONSTRAINT, null)).as("derivation source").isTrue();
        assertThat(annotated(EOperation.class, CwmAnnotations.METHOD, "OCL")).isTrue();
        assertThat(annotated(EOperation.class, CwmAnnotations.METHOD, "Java")).isTrue();
        assertThat(annotated(EPackage.class, CwmAnnotations.PACKAGE, CwmAnnotations.KEY_SUPER_PACKAGE)).isTrue();
    }

    @Test
    void genModel() {
        assertThat(annotated(EModelElement.class, GENMODEL, "documentation")).isTrue();
        assertThat(any(EModelElement.class, e -> e instanceof EEnumLiteral
                && EcoreUtil.getDocumentation(e) != null)).as("documented literal").isTrue();
        assertThat(annotated(EPackage.class, GENMODEL, "prefix")).isTrue();
        assertThat(annotated(EPackage.class, GENMODEL, "basePackage")).isTrue();
        assertThat(annotated(EPackage.class, GENMODEL, "oSGiCompatible")).isTrue();
        assertThat(annotated(EPackage.class, GENMODEL, "copyrightText")).isTrue();
        assertThat(FILES).as("a written genmodel").contains("genmodel");
    }

    @Test
    void notes() {
        assertThat(RULES).containsAll(List.of("TYPE", "UNRESOLVED", "N_ARY", "ASSOCIATION_CLASS", "AGGREGATION",
                "CYCLE", "METHOD", "METHOD_LANGUAGE", "CONSTRAINT", "CONSTRAINT_LANGUAGE", "NAME", "ENUM_VALUE",
                "MULTIPLICITY", "SCOPE", "NOT_NAVIGABLE", "GENMODEL", "IMPORT", "DEFAULT", "TYPE_ALIAS", "TAG",
                "EXTERNAL"));
    }

    private static <T> boolean any(Class<T> type, Predicate<T> test) {
        return ELEMENTS.stream().filter(type::isInstance).map(type::cast).anyMatch(test);
    }

    /** Whether an element of the type carries the annotation, with the detail key if one is given. */
    private static <T extends EModelElement> boolean annotated(Class<T> type, String source, String key) {
        return any(type, e -> e.getEAnnotation(source) != null
                && (key == null || e.getEAnnotation(source).getDetails().containsKey(key)));
    }
}
