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

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.emf.ecore.EClass;

/**
 * A yes/no question about one model element, the building block of a
 * {@link Selection}. The prepared rules are in {@link ElementRules}; a lambda
 * {@code e -> ...} is a rule as well, and {@link #of(String, Predicate)} gives
 * it a name for reports.
 *
 * <p>
 * A rule answers for the element it is given. Whether the owners of an
 * element count as well is decided by the {@link Selection} that holds the
 * rule, not by the rule.
 * </p>
 *
 * <p>
 * {@link #matchesAllOf(EClass)} and {@link #matchesNoneOf(EClass)} answer for
 * a whole class of elements, before any of them exists: a reader of a database
 * asks a selection whether it excludes, say, every trigger, and then does not
 * read triggers at all. Both answer {@code false} unless the rule knows better,
 * which is always safe. Rules on kinds know; rules on names, paths, tagged
 * values or stereotypes depend on the element and cannot.
 * </p>
 */
@FunctionalInterface
public interface ElementRule {

    /** Whether {@code element} matches. Every prepared rule answers {@code false} for {@code null}. */
    boolean test(ModelElement element);

    /**
     * How the rule reads in a report. A rule with a text form reads as that
     * text ({@code name:tmp_*}, {@code kind:Attribute}); a combination reads
     * as a function of its parts ({@code all(name:a*, kind:Class)}). A plain
     * lambda reads as {@code custom rule}; {@link #of} and
     * {@link #describedAs} name it.
     */
    default String describe() {
        return "custom rule";
    }

    /**
     * Whether the rule matches every element whose {@code eClass()} is exactly
     * {@code eClass}, whatever else is true of it.
     */
    default boolean matchesAllOf(EClass eClass) {
        return false;
    }

    /**
     * Whether the rule matches no element whose {@code eClass()} is exactly
     * {@code eClass}, whatever else is true of it.
     */
    default boolean matchesNoneOf(EClass eClass) {
        return false;
    }

    /** A rule that matches where this one and {@code other} both match. */
    default ElementRule and(ElementRule other) {
        return ElementRules.all(this, other);
    }

    /** A rule that matches where this one or {@code other} matches. */
    default ElementRule or(ElementRule other) {
        return ElementRules.any(this, other);
    }

    /** A rule that matches where this one does not; it reads as {@code not(...)}. */
    default ElementRule negate() {
        return ElementRules.not(this);
    }

    /**
     * The same rule under another name: it matches what this one matches and
     * reads as {@code description}. A vocabulary gives a named rule its text
     * this way, so {@code is:constraint} reads as {@code is:constraint}
     * rather than as the combination behind it.
     */
    default ElementRule describedAs(String description) {
        return ElementRules.described(description, this);
    }

    /** A rule from a predicate, reading as {@code description} in reports. */
    static ElementRule of(String description, Predicate<? super ModelElement> test) {
        Objects.requireNonNull(test, "test");
        return ElementRules.custom(description, test);
    }
}
