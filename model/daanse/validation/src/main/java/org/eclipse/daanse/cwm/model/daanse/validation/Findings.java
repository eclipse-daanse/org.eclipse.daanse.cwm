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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;

/**
 * Helpers over {@link Finding}s: what the EMF check of a model ({@code Diagnostician}) says, as findings; the errors as
 * the lines a validator's {@code validate} gives.
 */
public final class Findings {

    /**
     * The module of a finding that the EMF check makes by itself, not from a named constraint of a model: a required
     * feature left unset, a value of the wrong type, a reference to an element outside the resources, and the like.
     */
    public static final String ECORE = "ecore";

    /**
     * The message EMF gives a constraint of a model that is not kept: {@code The 'X1' constraint is violated on
     * 'Town Weimar'}. The name of the constraint is the code of its rule.
     */
    private static final Pattern VIOLATED = Pattern.compile("The '([A-Za-z0-9]+)' constraint is violated on .*",
            Pattern.DOTALL);

    private Findings() {
    }

    /**
     * The findings of an EMF check, one per diagnostic without children that is not {@code OK}. A diagnostic of a
     * constraint the model names by its rule ({@code X1}) is a finding of that rule in {@code module}; any other is a
     * finding of the module {@link #ECORE} whose rule is {@code EMF} and the diagnostic's code ({@code EMF1}: a required
     * feature unset). The element is the diagnostic's first datum where that is an element; the message is the
     * diagnostic's. Errors and cancellations are errors, warnings warnings, information notes.
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
        Matcher named = VIOLATED.matcher(message);
        boolean ofAConstraint = named.matches();
        String inModule = ofAConstraint ? module : ECORE;
        String rule = ofAConstraint ? named.group(1) : "EMF" + Math.max(0, diagnostic.getCode());
        Object first = diagnostic.getData().isEmpty() ? null : diagnostic.getData().get(0);
        return new Finding(inModule, rule, severity(diagnostic.getSeverity()), first instanceof EObject e ? e : null,
                message);
    }

    private static Severity severity(int severity) {
        return switch (severity) {
        case Diagnostic.WARNING -> Severity.WARNING;
        case Diagnostic.INFO -> Severity.INFO;
        default -> Severity.ERROR;
        };
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
