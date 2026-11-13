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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Which elements of a model are meant: include rules and exclude rules, each
 * an {@link ElementRule}. A selection never changes; build one with
 * {@link #builder()} or {@link #parse} it from text.
 *
 * <p>
 * The owners of an element are the model elements that contain it, up the
 * containment tree. Three rules decide:
 * </p>
 * <ol>
 * <li>An element is <b>excluded</b> when it or one of its owners matches an
 * exclude rule. Exclude wins over include.</li>
 * <li>Otherwise it is <b>selected</b> when there is no include rule, or when it
 * or one of its owners matches one.</li>
 * <li>The owners of a selected element stay in a {@linkplain Selections#copy
 * copy} as its containers, even when they are not selected themselves. Their
 * other contents are judged on their own. The root of the copy always
 * stays.</li>
 * </ol>
 *
 * <p>
 * Examples on the model {@code shop}, which holds the packages {@code sales}
 * (classes {@code customer}, {@code orders} with the attribute {@code id},
 * and {@code order_log}) and {@code stock} (class {@code product}):
 * </p>
 * <table>
 * <caption>Selection examples</caption>
 * <tr><th>Rules</th><th>Element</th><th>{@link #selects}</th><th>In the copy</th><th>Because</th></tr>
 * <tr><td>none</td><td>{@code shop.sales.orders}</td><td>yes</td><td>yes</td>
 * <td>without include rules everything is selected (2)</td></tr>
 * <tr><td>{@code name:orders}</td><td>{@code shop.sales.orders}</td><td>yes</td><td>yes</td>
 * <td>the element matches an include rule (2)</td></tr>
 * <tr><td>{@code name:orders}</td><td>{@code shop.sales.orders.id}</td><td>yes</td><td>yes</td>
 * <td>an owner matches an include rule (2)</td></tr>
 * <tr><td>{@code name:orders}</td><td>{@code shop.sales.customer}</td><td>no</td><td>no</td>
 * <td>neither the element nor an owner matches (2)</td></tr>
 * <tr><td>{@code name:orders}</td><td>{@code shop.sales}</td><td>no</td><td>yes</td>
 * <td>it holds a selected element (3)</td></tr>
 * <tr><td>{@code name:orders}</td><td>{@code shop.stock}</td><td>no</td><td>no</td>
 * <td>it holds no selected element (3)</td></tr>
 * <tr><td>{@code name:sales}, {@code !name:order*}</td><td>{@code shop.sales.customer}</td><td>yes</td>
 * <td>yes</td><td>an owner matches an include rule, nothing excludes (2)</td></tr>
 * <tr><td>{@code name:sales}, {@code !name:order*}</td><td>{@code shop.sales.order_log}</td><td>no</td>
 * <td>no</td><td>the element is excluded; the include of its owner does not help (1)</td></tr>
 * <tr><td>{@code name:sales}, {@code !name:order*}</td><td>{@code shop.sales.orders.id}</td><td>no</td>
 * <td>no</td><td>an owner is excluded (1)</td></tr>
 * <tr><td>{@code name:orders}, {@code !name:sales}</td><td>{@code shop.sales.orders}</td><td>no</td>
 * <td>no</td><td>an excluded owner wins over the element's own include (1)</td></tr>
 * <tr><td>{@code name:orders}, {@code !kind:Attribute}</td><td>{@code shop.sales.orders}</td><td>yes</td>
 * <td>yes</td><td>the element matches an include rule (2)</td></tr>
 * <tr><td>{@code name:orders}, {@code !kind:Attribute}</td><td>{@code shop.sales.orders.id}</td><td>no</td>
 * <td>no</td><td>an exclude reaches below an include (1)</td></tr>
 * <tr><td>{@code !name:stock}</td><td>{@code shop.sales.customer}</td><td>yes</td><td>yes</td>
 * <td>only exclude rules: everything else is selected (2)</td></tr>
 * <tr><td>{@code !name:stock}</td><td>{@code shop.stock.product}</td><td>no</td><td>no</td>
 * <td>an owner is excluded (1)</td></tr>
 * </table>
 *
 * <h2>Text</h2>
 * <p>
 * {@link #parse} reads one rule per line, {@code <selector>:<pattern>} as the
 * {@link RuleVocabulary} defines it; a leading {@code !} makes the rule
 * exclude. Blank lines are skipped, and a {@code #} at the start of a line or
 * after a blank starts a comment:
 * </p>
 *
 * <pre>
 * path:shop.sales.**         # the package sales and all in it
 * !name:tmp_*                # but nothing named tmp_...
 * !kind:Attribute
 * !tag:documentary=true
 * </pre>
 */
public final class Selection {

    private static final Selection ALL = new Selection(List.of(), List.of());

    private final List<ElementRule> includes;
    private final List<ElementRule> excludes;

    private Selection(List<ElementRule> includes, List<ElementRule> excludes) {
        this.includes = List.copyOf(includes);
        this.excludes = List.copyOf(excludes);
    }

    /** The selection without rules: every element is selected. */
    public static Selection all() {
        return ALL;
    }

    /** A builder for a selection from rules in code. */
    public static Builder builder() {
        return new Builder();
    }

    /** The selection the rule texts {@code rules} describe, in the {@link RuleVocabulary#core() core vocabulary}. */
    public static Selection parse(String... rules) {
        return parse(RuleVocabulary.core(), rules);
    }

    /**
     * The selection the rule texts {@code rules} describe in {@code vocabulary}.
     * Each text may hold several lines, one rule each.
     *
     * @throws IllegalArgumentException for a rule the vocabulary does not know,
     *                                  see {@link RuleVocabulary#parse}
     */
    public static Selection parse(RuleVocabulary vocabulary, String... rules) {
        Objects.requireNonNull(rules, "rules");
        return parse(vocabulary, Arrays.asList(rules));
    }

    /** As {@link #parse(RuleVocabulary, String...)}, for rules from a list, such as a Maven parameter. */
    public static Selection parse(RuleVocabulary vocabulary, List<String> rules) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        Objects.requireNonNull(rules, "rules");
        Builder builder = builder();
        for (String text : rules) {
            Objects.requireNonNull(text, "rule");
            for (String line : text.split("\\R")) {
                String rule = withoutComment(line).strip();
                if (rule.isEmpty()) {
                    continue;
                }
                if (rule.startsWith("!")) {
                    String excluded = rule.substring(1).strip();
                    if (excluded.isEmpty()) {
                        throw new IllegalArgumentException("Rule '" + rule + "' excludes nothing");
                    }
                    builder.exclude(vocabulary.parse(excluded));
                } else {
                    builder.include(vocabulary.parse(rule));
                }
            }
        }
        return builder.build();
    }

    /** The include rules, in the order given. */
    public List<ElementRule> includes() {
        return includes;
    }

    /** The exclude rules, in the order given. */
    public List<ElementRule> excludes() {
        return excludes;
    }

    /** Whether {@code element} is selected (rules 1 and 2); {@code false} for {@code null}. */
    public boolean selects(ModelElement element) {
        return element != null && verdictOf(element).selected();
    }

    /**
     * The exclude rule that excludes {@code element} (rule 1): the first one
     * its outermost excluded owner matches, or the element itself when no
     * owner is excluded. That is the rule a {@linkplain Selections#copy copy}
     * reports for it. Empty when the element is not excluded.
     */
    public Optional<ElementRule> excludedBy(ModelElement element) {
        if (element == null || excludes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(verdictOf(element).excludedBy());
    }

    /**
     * Whether every element whose class is exactly {@code eClass} is excluded,
     * whatever its name, owner or tags. A reader of a model can then skip that
     * kind entirely. Answers {@code false} when unsure, see
     * {@link ElementRule#matchesAllOf}.
     */
    public boolean excludesKind(EClass eClass) {
        return eClass != null && excludes.stream().anyMatch(r -> r.matchesAllOf(eClass));
    }

    /**
     * Whether an include rule may match an element whose class is exactly
     * {@code eClass}: {@code true} without include rules, {@code false} when
     * every include rule matches no element of that class
     * ({@link ElementRule#matchesNoneOf}). A reader that meets an element no
     * include rule reaches can then tell whether something below it may still
     * be included, or whether it can leave the element out with all below it.
     * Answers {@code true} when unsure.
     */
    public boolean mayInclude(EClass eClass) {
        return eClass != null && (includes.isEmpty() || includes.stream().anyMatch(r -> !r.matchesNoneOf(eClass)));
    }

    /**
     * The rules, include rules first, that match no element of {@code root}'s
     * containment tree, {@code root} included. A rule that matches nothing is
     * usually a mistyped name: the caller can say so instead of returning an
     * empty result in silence. Empty for {@code null}.
     */
    public List<ElementRule> unmatched(ModelElement root) {
        if (root == null) {
            return List.of();
        }
        List<ElementRule> unmatched = new ArrayList<>();
        for (ElementRule rule : Stream.concat(includes.stream(), excludes.stream()).toList()) {
            if (!rule.test(root) && noneBelow(root, rule)) {
                unmatched.add(rule);
            }
        }
        return List.copyOf(unmatched);
    }

    /**
     * Throws when an include rule matches no element of {@code root}'s
     * containment tree, {@code root} included, naming every such rule. An
     * include rule that matches nothing is usually a mistyped name, and the
     * selection then holds less than meant, often nothing at all. Exclude
     * rules are not asked: one that matches nothing leaves nothing out, and a
     * selection shared by a model and a database may well exclude what only
     * one of them holds.
     *
     * @throws UnmatchedRulesException naming the include rules that match nothing
     */
    public void requireIncludesMatch(ModelElement root) {
        List<ElementRule> unmatched = unmatched(root).stream().filter(includes::contains).toList();
        if (!unmatched.isEmpty()) {
            throw new UnmatchedRulesException(root == null ? "nothing" : QualifiedNames.of(root), unmatched);
        }
    }

    /** The rules as text, exclude rules with a leading {@code !}. */
    @Override
    public String toString() {
        return Stream.concat(includes.stream().map(ElementRule::describe),
                excludes.stream().map(r -> "!" + r.describe()))
                .collect(Collectors.joining(", ", "Selection[", "]"));
    }

    private static boolean noneBelow(ModelElement root, ElementRule rule) {
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            if (it.next() instanceof ModelElement element && rule.test(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The verdict on {@code element}, reached from its outermost owner down. A
     * walk down a model, or a reader that builds one from the top, carries the
     * verdict along with {@link #below} instead.
     */
    public Verdict verdictOf(ModelElement element) {
        List<ModelElement> chain = new ArrayList<>(QualifiedNames.owners(element).toList());
        Collections.reverse(chain);
        chain.add(element);
        Verdict verdict = new Verdict(null, includes.isEmpty());
        for (ModelElement e : chain) {
            verdict = below(verdict, e);
        }
        return verdict;
    }

    /**
     * The verdict on {@code element}, given the verdict on its owner: what is
     * excluded above stays excluded without asking a rule, what is included
     * above is included without asking an include rule. A walk down the
     * containment tree thus asks the rules once per element, and a reader that
     * builds a model from the top need not build what is excluded above. The
     * element must already be in its place below the owner when a rule asks
     * about its owners.
     */
    public Verdict below(Verdict owner, ModelElement element) {
        if (owner.excluded()) {
            return owner;
        }
        for (ElementRule rule : excludes) {
            if (rule.test(element)) {
                return new Verdict(rule, owner.included());
            }
        }
        if (owner.included()) {
            return owner;
        }
        for (ElementRule rule : includes) {
            if (rule.test(element)) {
                return new Verdict(null, true);
            }
        }
        return owner;
    }

    /**
     * What the rules say about one element.
     *
     * @param excludedBy the exclude rule that excludes it, {@code null} when none does
     * @param included   whether it or an owner matches an include rule, or there is none
     */
    public record Verdict(ElementRule excludedBy, boolean included) {

        /** Whether an exclude rule reaches the element (rule 1). */
        public boolean excluded() {
            return excludedBy != null;
        }

        /** Whether the element is selected (rules 1 and 2). */
        public boolean selected() {
            return excludedBy == null && included;
        }
    }

    private static String withoutComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** Collects include and exclude rules; {@link #build()} may be called more than once. */
    public static final class Builder {

        private final List<ElementRule> includes = new ArrayList<>();
        private final List<ElementRule> excludes = new ArrayList<>();

        private Builder() {
        }

        /** Adds include rules. */
        public Builder include(ElementRule... rules) {
            add(includes, rules);
            return this;
        }

        /** Adds exclude rules. */
        public Builder exclude(ElementRule... rules) {
            add(excludes, rules);
            return this;
        }

        /** The selection of the rules added so far. */
        public Selection build() {
            return includes.isEmpty() && excludes.isEmpty() ? ALL : new Selection(includes, excludes);
        }

        private static void add(List<ElementRule> target, ElementRule... rules) {
            Objects.requireNonNull(rules, "rules");
            for (ElementRule rule : rules) {
                target.add(Objects.requireNonNull(rule, "rule"));
            }
        }
    }
}
