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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.util.EObjectValidator;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Helpers over {@link Finding}s: what the EMF check of a model ({@code Diagnostician}) says, as findings; the findings
 * of the invariants a metamodel states; the errors as the lines a validator's {@code validate} gives.
 *
 * <p>
 * A metamodel names each of its invariants by the code of its rule ({@code SP23}) and states what the rule weighs and
 * says beside it, in the annotation {@value #CONSTRAINTS} after the namespace URI of its package on the class that
 * declares it: {@code SP23.level} ({@code error}, {@code warning} or {@code info}), {@code SP23.description} (the
 * sentence of the rule) and {@code SP23.name} (a short name). A finding of such an invariant weighs as its level says
 * and says the sentence, whatever the EMF check made of it: the check reports every invariant that is not kept as an
 * error.
 * </p>
 */
public final class Findings {

    /**
     * The module of a finding that the EMF check makes by itself, not from a named constraint of a model: a required
     * feature left unset, a value of the wrong type, a reference to an element outside the resources, and the like.
     */
    public static final String ECORE = "ecore";

    /**
     * The source of the annotation with the metadata of the constraints of a class, after the namespace URI of its
     * package: {@code <name>.level}, {@code <name>.description} and {@code <name>.name} of each constraint.
     */
    public static final String CONSTRAINTS = "/constraints";

    /**
     * The message EMF gives a constraint of a model that is not kept: {@code The 'X1' constraint is violated on
     * 'Town Weimar'}. The name of the constraint is the code of its rule, the second group what the check calls the
     * element.
     */
    private static final Pattern VIOLATED = Pattern.compile("The '([A-Za-z0-9]+)' constraint is violated on '(.*)'",
            Pattern.DOTALL);

    /**
     * The message EMF gives a constraint whose delegate failed to evaluate it: {@code An exception occurred while
     * delegating evaluation of the 'X1' constraint on 'Town Weimar': ...}. The rule could not be checked: a finding of
     * it, so that it does not pass unseen.
     */
    private static final Pattern NOT_EVALUATED = Pattern.compile(
            "An exception occurred while delegating evaluation of the '([A-Za-z0-9]+)' constraint on .*",
            Pattern.DOTALL);

    private Findings() {
    }

    /**
     * The findings of an EMF check, one per diagnostic without children that is not {@code OK}. A diagnostic of a
     * constraint the model names by its rule ({@code X1}), kept or failed to evaluate, is a finding of that rule in
     * {@code module}; any other is a finding of the module {@link #ECORE} whose rule is {@code EMF} and the diagnostic's
     * code ({@code EMF1}: a required feature unset). The element is the diagnostic's first datum where that is an
     * element. Errors and cancellations are errors, warnings warnings, information notes; the message is the
     * diagnostic's. Where the class that declares the constraint states its metadata ({@link #CONSTRAINTS}), the
     * finding weighs as its level says, and a constraint not kept says what the check calls the element and the
     * sentence of the rule: {@code Town Weimar: A town has at least one inhabitant.}
     *
     * @param module     the module of the rules the model's constraints are named by
     * @param diagnostic the result of the check
     */
    public static List<Finding> of(String module, Diagnostic diagnostic) {
        List<Finding> found = new ArrayList<>();
        collect(module, diagnostic, found);
        return found;
    }

    private static void collect(String module, Diagnostic diagnostic, List<Finding> found) {
        if (diagnostic.getChildren().isEmpty()) {
            if (diagnostic.getSeverity() != Diagnostic.OK) {
                found.add(finding(module, diagnostic));
            }
            return;
        }
        for (Diagnostic child : diagnostic.getChildren()) {
            collect(module, child, found);
        }
    }

    private static Finding finding(String module, Diagnostic diagnostic) {
        String message = diagnostic.getMessage() == null || diagnostic.getMessage().isBlank()
                ? "the EMF check reports code " + diagnostic.getCode() + " of " + diagnostic.getSource()
                : diagnostic.getMessage();
        Object first = diagnostic.getData().isEmpty() ? null : diagnostic.getData().get(0);
        EObject element = first instanceof EObject e ? e : null;
        Matcher violated = VIOLATED.matcher(message);
        Matcher failed = NOT_EVALUATED.matcher(message);
        if (!violated.matches() && !failed.matches()) {
            return new Finding(ECORE, "EMF" + Math.max(0, diagnostic.getCode()), severity(diagnostic.getSeverity()),
                    element, message);
        }
        String rule = violated.matches() ? violated.group(1) : failed.group(1);
        Map<String, String> metadata = metadata(element, rule);
        Severity severity = switch (String.valueOf(metadata.get(rule + ".level"))) {
        case "error" -> Severity.ERROR;
        case "warning" -> Severity.WARNING;
        case "info" -> Severity.INFO;
        default -> severity(diagnostic.getSeverity());
        };
        String says = metadata.get(rule + ".description");
        boolean saysIt = violated.matches() && says != null && !says.isBlank();
        return new Finding(module, rule, severity, element, saysIt ? violated.group(2) + ": " + says : message);
    }

    /**
     * The metadata of the constraint {@code name} of {@code element}: the details of the annotation {@link #CONSTRAINTS}
     * of the nearest class of the element that declares the constraint; none where it states none.
     */
    private static Map<String, String> metadata(EObject element, String name) {
        if (element == null) {
            return Map.of();
        }
        List<EClass> classes = new ArrayList<>(List.of(element.eClass()));
        classes.addAll(element.eClass().getEAllSuperTypes());
        for (EClass c : classes) {
            if (EcoreUtil.getConstraints(c).contains(name)) {
                EAnnotation annotation = c.getEPackage() == null ? null
                        : c.getEAnnotation(c.getEPackage().getNsURI() + CONSTRAINTS);
                return annotation == null ? Map.of() : annotation.getDetails().map();
            }
        }
        return Map.of();
    }

    private static Severity severity(int severity) {
        return switch (severity) {
        case Diagnostic.WARNING -> Severity.WARNING;
        case Diagnostic.INFO -> Severity.INFO;
        default -> Severity.ERROR;
        };
    }

    /**
     * The findings of the invariants {@code ePackage} states, on those of {@code elements} that are instances of its
     * classes. Each is checked by the validator registered for the package (the plain EMF one where there is none),
     * and of what that says the findings of the constraints the classes of the package declare are kept: a required
     * feature left unset, a dangling reference or a constraint a class inherits from another package are matters of
     * other checks. A finding weighs and says what the metadata of its constraint states ({@link #of}).
     *
     * <p>
     * Where the invariants are written in OCL, the delegate the package names has to be installed before: a plain
     * Java program has no plugin registry that does so.
     * </p>
     *
     * @param module   the module of the rules the package's constraints are named by
     * @param ePackage the package whose invariants are checked
     * @param elements the elements to check; those of the classes of other packages are left out
     * @param label    what a message calls an element, such as the names from the root down to it
     */
    public static List<Finding> ofInvariants(String module, EPackage ePackage, Iterable<? extends EObject> elements,
            Function<EObject, String> label) {
        EValidator registered = EValidator.Registry.INSTANCE.getEValidator(ePackage);
        EValidator validator = registered == null ? EObjectValidator.INSTANCE : registered;
        Map<Object, Object> context = new HashMap<>();
        context.put(EValidator.SubstitutionLabelProvider.class, new EValidator.SubstitutionLabelProvider() {

            @Override
            public String getObjectLabel(EObject eObject) {
                return label.apply(eObject);
            }

            @Override
            public String getFeatureLabel(EStructuralFeature eStructuralFeature) {
                return eStructuralFeature.getName();
            }

            @Override
            public String getValueLabel(EDataType eDataType, Object value) {
                return EcoreUtil.convertToString(eDataType, value);
            }
        });
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        for (EObject element : elements) {
            if (element.eClass().getEPackage() == ePackage) {
                validator.validate(element.eClass(), element, diagnostic, context);
            }
        }
        Set<String> stated = new LinkedHashSet<>();
        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass c) {
                stated.addAll(EcoreUtil.getConstraints(c));
            }
        }
        return of(module, diagnostic).stream().filter(f -> f.module().equals(module) && stated.contains(f.rule()))
                .toList();
    }

    /** The errors among the findings as lines of rule and message ({@link Finding#line()}), in their order. */
    public static List<String> errorLines(Collection<Finding> findings) {
        return findings.stream().filter(Finding::isError).map(Finding::line).toList();
    }

    /** The findings as lines of rule and message ({@link Finding#line()}), errors and warnings alike, in their order. */
    public static List<String> lines(Collection<Finding> findings) {
        return findings.stream().map(Finding::line).toList();
    }
}
