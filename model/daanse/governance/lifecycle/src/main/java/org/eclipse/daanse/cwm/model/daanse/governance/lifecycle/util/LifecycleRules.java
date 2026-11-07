/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleRule;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Which lifecycle rules apply to an element — for every kind of rule of the lifecycle models.
 *
 * <p>A rule names the elements it governs in {@code constrainedElement}; an element finds the
 * rules naming it in {@code ModelElement.constraint} (an opposite pair of CWM core, no inverse
 * index needed). The path from an element outward is the element itself, the owner of a feature
 * (its classifier), the namespaces outward, and for an element without namespace the model
 * element that contains it ({@link #next}). Of the rules of one kind and scope the nearest one
 * applies ({@link #nearest}); kinds whose rules all apply are collected along the whole path
 * ({@link #all}).</p>
 */
public final class LifecycleRules {

    /** The language of the {@code body} of a lifecycle rule. */
    public static final String LANGUAGE = "daanse.lifecycle";

    private LifecycleRules() {
    }

    /** The lifecycle rules that name {@code element} itself, in the order they were attached. */
    public static List<LifecycleRule> rulesOf(ModelElement element) {
        Objects.requireNonNull(element, "element");
        List<LifecycleRule> rules = new ArrayList<>();
        for (Constraint c : element.getConstraint()) {
            if (c instanceof LifecycleRule rule) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /**
     * The element whose rules apply next: the owner of a feature, else the namespace, else the
     * model element that contains it. The last covers CWM ownership that the Ecore form holds in a
     * containment of its own instead of {@code ownedElement} — the cubes of an OLAP schema
     * ({@code Schema.cube}), the dimensions of a multidimensional schema, the slots of an object,
     * the steps of a warehouse activity, the erasures of a processing — where {@code namespace}
     * stays empty.
     */
    public static ModelElement next(ModelElement element) {
        if (element instanceof Feature f && f.getOwner() != null) {
            return f.getOwner();
        }
        if (element.getNamespace() != null) {
            return element.getNamespace();
        }
        return element.eContainer() instanceof ModelElement container ? container : null;
    }

    /**
     * The nearest rule of {@code type} along the path from {@code element}, of any scope; empty
     * when none applies. Of several rules of the type at the same element the first counts (two
     * are a conflict the validators report).
     */
    public static <R extends LifecycleRule> Optional<R> nearest(ModelElement element, Class<R> type) {
        Objects.requireNonNull(type, "type");
        for (ModelElement e = Objects.requireNonNull(element, "element"); e != null; e = next(e)) {
            for (LifecycleRule rule : rulesOf(e)) {
                if (type.isInstance(rule)) {
                    return Optional.of(type.cast(rule));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The nearest rule of {@code type} and {@code scope} along the path from {@code element}. A
     * row rule never names a feature, so for a feature the nearest one is found at its owner or
     * further out.
     */
    public static <R extends LifecycleRule> Optional<R> nearest(ModelElement element, Class<R> type, DataScope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        for (ModelElement e = Objects.requireNonNull(element, "element"); e != null; e = next(e)) {
            for (LifecycleRule rule : rulesOf(e)) {
                if (type.isInstance(rule) && rule.getScope() == scope) {
                    return Optional.of(type.cast(rule));
                }
            }
        }
        return Optional.empty();
    }

    /** Every rule of {@code type} along the path from {@code element}, nearest first. */
    public static <R extends LifecycleRule> List<R> all(ModelElement element, Class<R> type) {
        Objects.requireNonNull(type, "type");
        List<R> found = new ArrayList<>();
        for (ModelElement e = Objects.requireNonNull(element, "element"); e != null; e = next(e)) {
            for (LifecycleRule rule : rulesOf(e)) {
                if (type.isInstance(rule) && !found.contains(rule)) {
                    found.add(type.cast(rule));
                }
            }
        }
        return found;
    }

    /**
     * Names {@code rule}, lets it govern {@code elements} and adds it to {@code owner}: what every
     * helper of the lifecycle models does before it writes the body.
     *
     * @throws IllegalArgumentException if no element is given
     */
    public static <R extends LifecycleRule> R attach(Namespace owner, String name, R rule, ModelElement... elements) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(elements, "elements");
        if (elements.length == 0) {
            throw new IllegalArgumentException("a rule constrains at least one element");
        }
        List.of(elements).forEach(e -> Objects.requireNonNull(e, "element"));
        rule.setName(name);
        rule.getConstrainedElement().addAll(List.of(elements));
        owner.getOwnedElement().add(rule);
        return rule;
    }

    /** Sets the {@code body} of a rule to {@code text} in the language {@value #LANGUAGE}. */
    public static void setBody(LifecycleRule rule, String text) {
        rule.setBody(Expressions.bool(LANGUAGE, Objects.requireNonNull(text, "text")));
    }

    /** Whether the {@code body} of a rule is {@code text} in the language {@value #LANGUAGE}. */
    public static boolean hasBody(LifecycleRule rule, String text) {
        return rule.getBody() != null && Objects.equals(text, rule.getBody().getBody())
                && LANGUAGE.equals(rule.getBody().getLanguage());
    }

    /**
     * The classifier and its ancestors, found through the generalizations owned next to it (the
     * CWM convention: a generalization lives in the namespace of its child).
     */
    public static Set<Classifier> lineage(Classifier start) {
        Set<Classifier> lineage = new LinkedHashSet<>();
        Deque<Classifier> todo = new ArrayDeque<>(List.of(start));
        while (!todo.isEmpty()) {
            Classifier c = todo.pop();
            if (!lineage.add(c) || c.getNamespace() == null) {
                continue;
            }
            for (ModelElement owned : c.getNamespace().getOwnedElement()) {
                EStructuralFeature child = owned.eClass().getEStructuralFeature("child");
                EStructuralFeature parent = owned.eClass().getEStructuralFeature("parent");
                if ("Generalization".equals(owned.eClass().getName()) && child != null && parent != null
                        && owned.eGet(child) == c && owned.eGet(parent) instanceof Classifier p) {
                    todo.push(p);
                }
            }
        }
        return lineage;
    }

    /** The names of an element and its named containers, dot separated. */
    public static String path(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
