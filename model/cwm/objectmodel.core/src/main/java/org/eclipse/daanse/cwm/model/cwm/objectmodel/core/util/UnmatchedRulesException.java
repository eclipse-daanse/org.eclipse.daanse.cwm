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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Include rules of a {@link Selection} that match nothing in a model, see
 * {@link Selection#requireIncludesMatch}. A rule that matches nothing is
 * usually a mistyped name, a schema {@code salse} for {@code sales}: what the
 * selection holds would then be less than meant, often nothing at all, without
 * a word.
 */
public final class UnmatchedRulesException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient List<ElementRule> rules;

    /**
     * @param model the qualified name of the model the rules were applied to
     * @param rules the rules that match nothing in it, not empty
     */
    public UnmatchedRulesException(String model, List<ElementRule> rules) {
        super(message(model, rules));
        this.rules = List.copyOf(rules);
    }

    /** The rules that match nothing, in the order of the selection. */
    public List<ElementRule> rules() {
        return rules;
    }

    private static String message(String model, List<ElementRule> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        String where = model == null || model.isEmpty() ? "the model" : model;
        return (rules.size() == 1 ? "This rule matches" : "These rules match") + " nothing in " + where + ": "
                + rules.stream().map(ElementRule::describe).collect(Collectors.joining(", "));
    }
}
