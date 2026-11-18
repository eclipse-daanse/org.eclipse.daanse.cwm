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

import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;

/**
 * What a validator of a model found: the rule broken, how much it weighs, the element it is about and what is wrong.
 * Every validator of the models gives its results in this one form, so a reader takes them all alike.
 *
 * <p>
 * A rule is numbered within its module ({@code SP12}); the module keeps the codes of different validators apart, so
 * module and rule together name a rule among all models ({@link #id()}, {@code spatial/SP12}): one code in two modules
 * names two rules. A rule weighs the same at every place it is checked: a check whose finding weighs otherwise is a
 * rule of its own.
 * </p>
 *
 * @param module   the module the rule belongs to, lower-case letters and digits starting with a letter
 * @param rule     the code of the rule within its module, letters and digits
 * @param severity how much a finding of the rule weighs
 * @param element  the element the finding is about; null where it is about no one element
 * @param message  what is wrong, for a person; never blank
 */
public record Finding(String module, String rule, Severity severity, EObject element, String message) {

    /** How much a finding weighs. */
    public enum Severity {
        /** The model breaks a rule: a reader that keeps the rules does not take it. */
        ERROR,
        /** The model keeps the rules, but what it says is likely not what was meant. */
        WARNING,
        /** Worth knowing; nothing is wrong. */
        INFO
    }

    /** A module: lower-case letters and digits starting with a letter. */
    static final Pattern MODULE = Pattern.compile("[a-z][a-z0-9]*");
    /** The code of a rule: letters and digits. */
    static final Pattern RULE = Pattern.compile("[A-Za-z0-9]+");

    public Finding {
        Objects.requireNonNull(module, "the module of a finding");
        Objects.requireNonNull(rule, "the rule of a finding");
        Objects.requireNonNull(severity, "the severity of a finding of " + module + "/" + rule);
        Objects.requireNonNull(message, "the message of a finding of " + module + "/" + rule);
        if (!MODULE.matcher(module).matches()) {
            throw new IllegalArgumentException("the module '" + module + "' of a finding is not lower-case letters and"
                    + " digits starting with a letter");
        }
        if (!RULE.matcher(rule).matches()) {
            throw new IllegalArgumentException("the rule '" + rule + "' of a finding is not letters and digits");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("the finding of " + module + "/" + rule + " says nothing");
        }
    }

    /** An error of the rule. */
    public static Finding error(String module, String rule, EObject element, String message) {
        return new Finding(module, rule, Severity.ERROR, element, message);
    }

    /** A warning of the rule. */
    public static Finding warning(String module, String rule, EObject element, String message) {
        return new Finding(module, rule, Severity.WARNING, element, message);
    }

    /** A note of the rule. */
    public static Finding info(String module, String rule, EObject element, String message) {
        return new Finding(module, rule, Severity.INFO, element, message);
    }

    /** The rule with its module, as {@code spatial/SP12}. */
    public String id() {
        return module + "/" + rule;
    }

    /** Whether the finding is an error. */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** The rule and the message, as {@code SP12 the relation ...}: the line a validator's {@code validate} gives. */
    public String line() {
        return rule + " " + message;
    }

    /** Severity, id and message, as {@code ERROR spatial/SP12: the relation ...}. */
    @Override
    public String toString() {
        return severity + " " + id() + ": " + message;
    }
}
