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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.emf.ecore.EClass;

/**
 * The prepared {@link ElementRule}s. A rule with a text form reads in reports
 * exactly as {@link Selection#parse} accepts it: {@code named("tmp_*")} reads
 * {@code name:tmp_*}, as does the text rule {@code name:tmp_*}.
 *
 * <p>
 * Patterns are globs and case-sensitive. In a name, {@code *} stands for any
 * run of characters and {@code ?} for one character. A path is dotted (see
 * {@link QualifiedNames}): there {@code *} and {@code ?} stay within one
 * segment and a segment {@code **} stands for any number of segments, none
 * included, so {@code shop.**} is {@code shop} and everything below it.
 * Every other character stands for itself; there is no escape.
 * </p>
 *
 * <p>
 * A kind is a metamodel class. {@code kind} matches the class and its
 * subclasses, {@code kindExactly} the class alone. By name, a kind matches when
 * the {@code eClass()} of the element or one of its supertypes has that simple
 * name, whatever package declares it; a name no class carries matches nothing.
 * </p>
 */
public final class ElementRules {

    private ElementRules() {
    }

    /** Elements whose name matches {@code glob}; an element without a name matches no glob. */
    public static ElementRule named(String glob) {
        Objects.requireNonNull(glob, "glob");
        Pattern pattern = Globs.name(glob);
        return rule("name:" + glob, e -> e.getName() != null && pattern.matcher(e.getName()).matches());
    }

    /** Elements whose {@linkplain QualifiedNames#of qualified name} matches the dotted {@code glob}. */
    public static ElementRule path(String glob) {
        Objects.requireNonNull(glob, "glob");
        Pattern pattern = Globs.path(glob);
        return rule("path:" + glob, e -> pattern.matcher(QualifiedNames.of(e)).matches());
    }

    /** Instances of {@code type}, subclasses included. */
    public static ElementRule kind(Class<? extends ModelElement> type) {
        Objects.requireNonNull(type, "type");
        return kindRule("kind:" + type.getSimpleName(),
                c -> c.getInstanceClass() != null && type.isAssignableFrom(c.getInstanceClass()));
    }

    /** Instances of {@code eClass}, subclasses included. */
    public static ElementRule kind(EClass eClass) {
        Objects.requireNonNull(eClass, "eClass");
        return kindRule("kind:" + eClass.getName(), eClass::isSuperTypeOf);
    }

    /** Elements whose class or one of its supertypes is named {@code name}. */
    public static ElementRule kind(String name) {
        Objects.requireNonNull(name, "name");
        return kindRule("kind:" + name, c -> name.equals(c.getName())
                || c.getEAllSuperTypes().stream().anyMatch(s -> name.equals(s.getName())));
    }

    /** Elements whose class is {@code type} itself, not a subclass. */
    public static ElementRule kindExactly(Class<? extends ModelElement> type) {
        Objects.requireNonNull(type, "type");
        return kindRule("kindExactly(" + type.getSimpleName() + ")", c -> c.getInstanceClass() == type);
    }

    /** Elements whose class is {@code eClass} itself, not a subclass. */
    public static ElementRule kindExactly(EClass eClass) {
        Objects.requireNonNull(eClass, "eClass");
        return kindRule("kindExactly(" + eClass.getName() + ")", c -> c == eClass);
    }

    /** Elements whose class itself, not a supertype, is named {@code name}. */
    public static ElementRule kindExactly(String name) {
        Objects.requireNonNull(name, "name");
        return kindRule("kindExactly(" + name + ")", c -> name.equals(c.getName()));
    }

    /** Elements that carry a tagged value {@code tag}, whatever its value. */
    public static ElementRule tagged(String tag) {
        Objects.requireNonNull(tag, "tag");
        return rule("tag:" + tag, e -> TaggedValues.find(e, tag).isPresent());
    }

    /** Elements that carry the tagged value {@code tag} = {@code value}. */
    public static ElementRule tagged(String tag, String value) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(value, "value");
        return rule("tag:" + tag + "=" + value, e -> TaggedValues.value(e, tag).filter(value::equals).isPresent());
    }

    /** Elements extended by a stereotype named {@code name}, see {@link Stereotypes}. */
    public static ElementRule stereotyped(String name) {
        Objects.requireNonNull(name, "name");
        return rule("stereotype:" + name, e -> Stereotypes.has(e, name));
    }

    /**
     * Elements of which an owner (any container up the containment tree, not
     * the element itself) matches {@code rule}.
     */
    public static ElementRule within(ElementRule rule) {
        Objects.requireNonNull(rule, "rule");
        return rule("within(" + rule.describe() + ")", e -> QualifiedNames.owners(e).anyMatch(rule::test));
    }

    /**
     * Elements that are the client of a dependency of {@code kind} ({@code null}
     * for any kind) with a supplier that matches {@code rule}.
     */
    public static ElementRule dependsOn(ElementRule rule, String kind) {
        Objects.requireNonNull(rule, "rule");
        String description = kind == null
                ? "dependsOn(" + rule.describe() + ")"
                : "dependsOn(" + rule.describe() + ", " + kind + ")";
        return rule(description, e -> Dependencies.ofClientStream(e, kind)
                .flatMap(d -> d.getSupplier().stream())
                .anyMatch(rule::test));
    }

    /** Elements that match at least one of {@code rules}; without rules, none. */
    public static ElementRule any(ElementRule... rules) {
        List<ElementRule> parts = parts(rules);
        return new Rule("any(" + join(parts) + ")",
                e -> parts.stream().anyMatch(r -> r.test(e)),
                c -> parts.stream().anyMatch(r -> r.matchesAllOf(c)),
                c -> parts.stream().allMatch(r -> r.matchesNoneOf(c)));
    }

    /** Elements that match every one of {@code rules}; without rules, all. */
    public static ElementRule all(ElementRule... rules) {
        List<ElementRule> parts = parts(rules);
        return new Rule("all(" + join(parts) + ")",
                e -> parts.stream().allMatch(r -> r.test(e)),
                c -> parts.stream().allMatch(r -> r.matchesAllOf(c)),
                c -> parts.stream().anyMatch(r -> r.matchesNoneOf(c)));
    }

    /** Elements that match none of {@code rules}; without rules, all. */
    public static ElementRule none(ElementRule... rules) {
        List<ElementRule> parts = parts(rules);
        return new Rule("none(" + join(parts) + ")",
                e -> parts.stream().noneMatch(r -> r.test(e)),
                c -> parts.stream().allMatch(r -> r.matchesNoneOf(c)),
                c -> parts.stream().anyMatch(r -> r.matchesAllOf(c)));
    }

    static ElementRule not(ElementRule rule) {
        Objects.requireNonNull(rule, "rule");
        return new Rule("not(" + rule.describe() + ")", e -> !rule.test(e), rule::matchesNoneOf,
                rule::matchesAllOf);
    }

    static ElementRule described(String description, ElementRule rule) {
        Objects.requireNonNull(rule, "rule");
        return new Rule(description, rule::test, rule::matchesAllOf, rule::matchesNoneOf);
    }

    static ElementRule custom(String description, Predicate<? super ModelElement> test) {
        return new Rule(description, test::test, c -> false, c -> false);
    }

    private static ElementRule rule(String description, Predicate<ModelElement> test) {
        return new Rule(description, test, c -> false, c -> false);
    }

    /**
     * A rule that depends on the class of the element alone: what it answers
     * for the class is exactly what it answers for every element of it.
     */
    private static ElementRule kindRule(String description, Predicate<EClass> matchesClass) {
        return new Rule(description, e -> matchesClass.test(e.eClass()), matchesClass, matchesClass.negate());
    }

    private static List<ElementRule> parts(ElementRule... rules) {
        Objects.requireNonNull(rules, "rules");
        Arrays.stream(rules).forEach(r -> Objects.requireNonNull(r, "rule"));
        return List.of(rules);
    }

    private static String join(List<ElementRule> rules) {
        return rules.stream().map(ElementRule::describe).collect(Collectors.joining(", "));
    }

    /** The one implementation behind every prepared rule. */
    private static final class Rule implements ElementRule {

        private final String description;
        private final Predicate<ModelElement> test;
        private final Predicate<EClass> allOf;
        private final Predicate<EClass> noneOf;

        Rule(String description, Predicate<ModelElement> test, Predicate<EClass> allOf, Predicate<EClass> noneOf) {
            this.description = Objects.requireNonNull(description, "description");
            this.test = test;
            this.allOf = allOf;
            this.noneOf = noneOf;
        }

        @Override
        public boolean test(ModelElement element) {
            return element != null && test.test(element);
        }

        @Override
        public String describe() {
            return description;
        }

        @Override
        public boolean matchesAllOf(EClass eClass) {
            return eClass != null && allOf.test(eClass);
        }

        @Override
        public boolean matchesNoneOf(EClass eClass) {
            return eClass != null && noneOf.test(eClass);
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
