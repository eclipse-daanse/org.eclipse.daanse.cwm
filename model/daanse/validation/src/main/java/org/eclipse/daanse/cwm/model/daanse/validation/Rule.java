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

import org.eclipse.emf.ecore.EObject;

/**
 * A rule of a validator of a model, stated as data: the module it belongs to, its code within the module, how much a
 * finding of it weighs and what it asks of a model. A validator keeps each of its rules as a constant and all of them
 * in a list, and reports a finding through the rule it breaks ({@link #at}), so whoever weighs the findings or names
 * the rules in a policy reads them from the validator instead of keeping them again. A rule weighs the same wherever
 * it is checked: a check whose finding weighs otherwise is a rule of its own, with a code of its own.
 *
 * @param module   the module the rule belongs to, lower-case letters and digits starting with a letter
 * @param code     the code of the rule within its module, letters and digits
 * @param severity how much a finding of the rule weighs
 * @param says     what the rule asks of a model, a sentence for a person; never blank
 */
public record Rule(String module, String code, Finding.Severity severity, String says) {

    public Rule {
        Objects.requireNonNull(module, "the module of a rule");
        Objects.requireNonNull(code, "the code of a rule");
        Objects.requireNonNull(severity, "the severity of the rule " + module + "/" + code);
        Objects.requireNonNull(says, "what the rule " + module + "/" + code + " says");
        if (!Finding.MODULE.matcher(module).matches()) {
            throw new IllegalArgumentException("the module '" + module + "' of a rule is not lower-case letters and"
                    + " digits starting with a letter");
        }
        if (!Finding.RULE.matcher(code).matches()) {
            throw new IllegalArgumentException("the code '" + code + "' of a rule is not letters and digits");
        }
        if (says.isBlank()) {
            throw new IllegalArgumentException("the rule " + module + "/" + code + " says nothing");
        }
    }

    /** A rule whose findings are errors. */
    public static Rule error(String module, String code, String says) {
        return new Rule(module, code, Finding.Severity.ERROR, says);
    }

    /** A rule whose findings are warnings. */
    public static Rule warning(String module, String code, String says) {
        return new Rule(module, code, Finding.Severity.WARNING, says);
    }

    /** A rule whose findings are notes. */
    public static Rule info(String module, String code, String says) {
        return new Rule(module, code, Finding.Severity.INFO, says);
    }

    /**
     * A finding of this rule.
     *
     * @param element the element the finding is about; null where it is about no one element
     * @param message what is wrong there, for a person; never blank
     * @return the finding, of this rule's module, code and severity
     */
    public Finding at(EObject element, String message) {
        return new Finding(module, code, severity, element, message);
    }

    /** The rule with its module, as {@code spatial/SP12}: the id a finding of it carries. */
    public String id() {
        return module + "/" + code;
    }
}
