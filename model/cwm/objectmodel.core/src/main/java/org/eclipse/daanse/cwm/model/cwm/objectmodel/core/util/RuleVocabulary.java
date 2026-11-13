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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The words a rule text may use: selectors ({@code name:}, {@code path:}, ...)
 * that turn a pattern into an {@link ElementRule}, and named rules that
 * {@code is:} looks up. A rule text reads {@code <selector>:<pattern>}; the
 * {@code !} that makes it exclude belongs to {@link Selection#parse}.
 *
 * <p>
 * {@link #core()} knows the selectors every model has:
 * </p>
 * <table>
 * <caption>Core selectors</caption>
 * <tr><th>Text</th><th>Rule</th></tr>
 * <tr><td>{@code name:tmp_*}</td><td>{@link ElementRules#named}</td></tr>
 * <tr><td>{@code path:shop.sales.*}</td><td>{@link ElementRules#path}</td></tr>
 * <tr><td>{@code kind:Attribute}</td><td>{@link ElementRules#kind(String)}</td></tr>
 * <tr><td>{@code tag:x}, {@code tag:x=v}</td><td>{@link ElementRules#tagged(String)},
 * {@link ElementRules#tagged(String, String)}</td></tr>
 * <tr><td>{@code stereotype:databaseRole}</td><td>{@link ElementRules#stereotyped}</td></tr>
 * <tr><td>{@code is:<name>}</td><td>the rule registered under that name</td></tr>
 * </table>
 *
 * <p>
 * A model module adds its own words with {@link #withSelector},
 * {@link #withRule} and {@link #with}; a vocabulary never changes, each of
 * these returns a new one. A word defined twice with two different meanings
 * is an error, the same definition reached twice is not. A parsed rule reads in
 * reports exactly as written, {@code selector:pattern}, without surrounding
 * blanks.
 * </p>
 */
public final class RuleVocabulary {

    private static final String IS = "is";

    private static final RuleVocabulary EMPTY = new RuleVocabulary(Map.of(), Map.of());

    private static final RuleVocabulary CORE = EMPTY
            .withSelector("name", ElementRules::named)
            .withSelector("path", ElementRules::path)
            .withSelector("kind", pattern -> ElementRules.kind(pattern))
            .withSelector("tag", RuleVocabulary::tag)
            .withSelector("stereotype", ElementRules::stereotyped);

    private final Map<String, Function<String, ? extends ElementRule>> selectors;
    private final Map<String, ElementRule> rules;

    private RuleVocabulary(Map<String, Function<String, ? extends ElementRule>> selectors,
            Map<String, ElementRule> rules) {
        this.selectors = Collections.unmodifiableMap(new TreeMap<>(selectors));
        this.rules = Collections.unmodifiableMap(new TreeMap<>(rules));
    }

    /** A vocabulary with no selector but {@code is:} and no named rule. */
    public static RuleVocabulary empty() {
        return EMPTY;
    }

    /** {@code name:}, {@code path:}, {@code kind:}, {@code tag:}, {@code stereotype:} and {@code is:}. */
    public static RuleVocabulary core() {
        return CORE;
    }

    /**
     * This vocabulary and the selector {@code selector}, which turns a pattern
     * into a rule. A selector name is a word without {@code :}, {@code !} or
     * blanks; {@code is} is taken.
     */
    public RuleVocabulary withSelector(String selector, Function<String, ? extends ElementRule> factory) {
        Objects.requireNonNull(factory, "factory");
        requireWord(selector, "selector");
        if (IS.equals(selector)) {
            throw new IllegalArgumentException("The selector 'is' is built in; add named rules with withRule");
        }
        Map<String, Function<String, ? extends ElementRule>> merged = new TreeMap<>(selectors);
        put(merged, selector, factory, "Selector");
        return new RuleVocabulary(merged, rules);
    }

    /** This vocabulary and the rule {@code rule}, written {@code is:<name>}. */
    public RuleVocabulary withRule(String name, ElementRule rule) {
        Objects.requireNonNull(rule, "rule");
        requireWord(name, "rule name");
        Map<String, ElementRule> merged = new TreeMap<>(rules);
        put(merged, name, rule, "Rule");
        return new RuleVocabulary(selectors, merged);
    }

    /** The words of this vocabulary and of {@code other}. */
    public RuleVocabulary with(RuleVocabulary other) {
        Objects.requireNonNull(other, "other");
        Map<String, Function<String, ? extends ElementRule>> mergedSelectors = new TreeMap<>(selectors);
        other.selectors.forEach((k, v) -> put(mergedSelectors, k, v, "Selector"));
        Map<String, ElementRule> mergedRules = new TreeMap<>(rules);
        other.rules.forEach((k, v) -> put(mergedRules, k, v, "Rule"));
        return new RuleVocabulary(mergedSelectors, mergedRules);
    }

    /** The selector names, {@code is} included, sorted. */
    public Set<String> selectors() {
        Set<String> names = new TreeSet<>(selectors.keySet());
        names.add(IS);
        return Collections.unmodifiableSet(names);
    }

    /** The names {@code is:} accepts, sorted. */
    public Set<String> rules() {
        return rules.keySet();
    }

    /** The rule registered under {@code name}. */
    public Optional<ElementRule> rule(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(rules.get(name));
    }

    /**
     * The rule a text {@code <selector>:<pattern>} stands for, without a
     * leading {@code !}.
     *
     * @throws IllegalArgumentException when the text has no selector, an
     *                                  unknown selector, an empty pattern or,
     *                                  after {@code is:}, an unknown name; the
     *                                  message names the text and what is known
     */
    public ElementRule parse(String text) {
        Objects.requireNonNull(text, "text");
        String rule = text.strip();
        int colon = rule.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Rule '" + rule
                    + "' names no selector; write <selector>:<pattern> with a selector of: " + known(selectors()));
        }
        String selector = rule.substring(0, colon).strip();
        String pattern = rule.substring(colon + 1).strip();
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Rule '" + rule + "' has an empty pattern");
        }
        String canonical = selector + ":" + pattern;
        ElementRule parsed;
        if (IS.equals(selector)) {
            parsed = rules.get(pattern);
            if (parsed == null) {
                throw new IllegalArgumentException(
                        "Unknown rule '" + canonical + "'; known after 'is:': " + known(rules.keySet()));
            }
        } else {
            Function<String, ? extends ElementRule> factory = selectors.get(selector);
            if (factory == null) {
                throw new IllegalArgumentException("Unknown selector '" + selector + "' in rule '" + rule
                        + "'; known: " + known(selectors()));
            }
            parsed = factory.apply(pattern);
        }
        return canonical.equals(parsed.describe()) ? parsed : parsed.describedAs(canonical);
    }

    @Override
    public String toString() {
        return "RuleVocabulary[selectors=" + selectors() + ", rules=" + rules() + "]";
    }

    private static ElementRule tag(String pattern) {
        int equals = pattern.indexOf('=');
        String tag = (equals < 0 ? pattern : pattern.substring(0, equals)).strip();
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Rule 'tag:" + pattern + "' names no tag");
        }
        return equals < 0 ? ElementRules.tagged(tag) : ElementRules.tagged(tag, pattern.substring(equals + 1).strip());
    }

    private static <V> void put(Map<String, V> map, String key, V value, String what) {
        V present = map.putIfAbsent(key, value);
        if (present != null && present != value) {
            throw new IllegalArgumentException(what + " '" + key + "' is defined twice");
        }
    }

    private static void requireWord(String word, String what) {
        Objects.requireNonNull(word, what);
        if (word.isEmpty() || word.chars().anyMatch(c -> c == ':' || c == '!' || Character.isWhitespace(c))) {
            throw new IllegalArgumentException("A " + what + " is a word without ':', '!' or blanks: '" + word + "'");
        }
    }

    private static String known(Set<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
