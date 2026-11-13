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
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Dependencies;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.NamedColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;

/**
 * The order in which a script creates what needs something else first, and what a view reads. The
 * DDL generator orders its scripts by it, a migration orders its views by it and finds by it the views
 * a change of a table reaches.
 *
 * <p>
 * What a view reads: where the view is a client of a {@code core::Dependency} on one of the candidates
 * (or on a column of one), the dependencies decide; otherwise its query text is searched for the names
 * of the candidates. A name counts where it stands alone or behind the name of its schema; outside
 * quotes case does not count. String literals and comments are not searched.
 * </p>
 */
public final class CreateOrder {

    /** An identifier: double-quoted, back-quoted, bracketed, or plain. */
    private static final String IDENTIFIER = "\"(?:[^\"]|\"\")+\"|`[^`]+`|\\[[^\\]]+\\]|[\\p{L}_][\\p{L}\\p{N}_$#]*";

    /** A name with its qualifiers: identifiers joined by dots. */
    private static final Pattern NAME = Pattern
            .compile("(?:" + IDENTIFIER + ")(?:\\s*\\.\\s*(?:" + IDENTIFIER + "))*");

    private static final Pattern PART = Pattern.compile(IDENTIFIER);

    /** String literals and comments, which name nothing. */
    private static final Pattern NOT_CODE = Pattern.compile("'(?:[^']|'')*'|--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    private CreateOrder() {
    }

    /**
     * The keys of {@code needs} so that each comes after what it needs; ties keep the order of the map.
     * What is left when nothing can come next lies on a cycle or needs one.
     *
     * @param cycle makes the error for the elements on a cycle
     * @throws RuntimeException the one {@code cycle} makes
     */
    public static <T> List<T> ordered(Map<T, Set<T>> needs, Function<List<T>, RuntimeException> cycle) {
        List<T> ordered = new ArrayList<>();
        Set<T> pending = new LinkedHashSet<>(needs.keySet());
        while (!pending.isEmpty()) {
            Optional<T> next = pending.stream()
                    .filter(t -> needs.get(t).stream().noneMatch(n -> n != t && pending.contains(n))).findFirst();
            if (next.isEmpty()) {
                throw cycle.apply(pending.stream().filter(t -> reaches(t, t, needs, pending, new LinkedHashSet<>()))
                        .toList());
            }
            ordered.add(next.get());
            pending.remove(next.get());
        }
        return ordered;
    }

    /**
     * {@code views} in an order that creates each after the views it reads; otherwise in the order
     * given. Where a view of the list is a client of a {@code core::Dependency} on another view of the
     * list, the dependencies between the views decide for all of them.
     *
     * @throws IllegalStateException naming the views that read each other in a cycle
     */
    public static List<View> views(List<View> views) {
        boolean byDependencies = views.stream().anyMatch(view -> !suppliers(view, views).isEmpty());
        Map<View, Set<View>> reads = new LinkedHashMap<>();
        for (View view : views) {
            Set<View> read = new LinkedHashSet<>();
            (byDependencies ? suppliers(view, views) : named(view, views)).stream().map(View.class::cast)
                    .forEach(read::add);
            reads.put(view, read);
        }
        return ordered(reads, cycle -> new IllegalStateException("These views read each other in a cycle, as "
                + (byDependencies ? "their dependencies say" : "their queries name them") + ": " + names(cycle)
                + (byDependencies ? "" : "; model the views they read as core::Dependency to order them")));
    }

    /**
     * The elements of {@code candidates} that {@code view} reads: those it depends on by a
     * {@code core::Dependency}, where it has such a dependency on one of them, otherwise those whose
     * names its query text holds. {@code view} itself is never among them.
     */
    public static Set<NamedColumnSet> reads(View view, Collection<? extends NamedColumnSet> candidates) {
        Set<NamedColumnSet> suppliers = suppliers(view, candidates);
        return suppliers.isEmpty() ? named(view, candidates) : suppliers;
    }

    /** The elements of {@code candidates} {@code view} is a client of a {@code core::Dependency} on, or on a column of. */
    private static Set<NamedColumnSet> suppliers(View view, Collection<? extends NamedColumnSet> candidates) {
        Set<NamedColumnSet> suppliers = new LinkedHashSet<>();
        for (Dependency dependency : Dependencies.ofClient(view, null)) {
            for (ModelElement supplier : dependency.getSupplier()) {
                NamedColumnSet set = supplier instanceof Column column ? Columns.namedOwner(column).orElse(null)
                        : supplier instanceof NamedColumnSet ncs ? ncs : null;
                if (set != null && set != view && candidates.contains(set)) {
                    suppliers.add(set);
                }
            }
        }
        return suppliers;
    }

    /** The elements of {@code candidates} whose names {@code view}'s query text holds. */
    private static Set<NamedColumnSet> named(View view, Collection<? extends NamedColumnSet> candidates) {
        Set<NamedColumnSet> named = new LinkedHashSet<>();
        String query = NOT_CODE.matcher(Views.queryBody(view).orElse("")).replaceAll(" ");
        List<List<String>> names = new ArrayList<>();
        for (Matcher m = NAME.matcher(query); m.find();) {
            List<String> parts = new ArrayList<>();
            for (Matcher p = PART.matcher(m.group()); p.find();) {
                parts.add(p.group());
            }
            names.add(parts);
        }
        for (NamedColumnSet other : candidates) {
            if (other == view || other.getName() == null) {
                continue;
            }
            String schema = NamedColumnSets.findSchema(other).map(Schema::getName).orElse(null);
            for (List<String> parts : names) {
                if (same(parts.get(parts.size() - 1), other.getName())
                        && (parts.size() == 1 || schema == null || same(parts.get(parts.size() - 2), schema))) {
                    named.add(other);
                    break;
                }
            }
        }
        return named;
    }

    /** Whether the identifier as written names {@code name}: exactly in quotes, otherwise in any case. */
    private static boolean same(String written, String name) {
        char first = written.charAt(0);
        if (first == '"') {
            return written.substring(1, written.length() - 1).replace("\"\"", "\"").equals(name);
        }
        if (first == '`' || first == '[') {
            return written.substring(1, written.length() - 1).equals(name);
        }
        return written.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT));
    }

    private static <T> boolean reaches(T from, T target, Map<T, Set<T>> needs, Set<T> within, Set<T> seen) {
        for (T n : needs.get(from)) {
            if (n == target) {
                return true;
            }
            if (within.contains(n) && seen.add(n) && reaches(n, target, needs, within, seen)) {
                return true;
            }
        }
        return false;
    }

    /** The qualified names of {@code elements}, joined by commas. */
    public static String names(List<? extends ModelElement> elements) {
        return elements.stream().map(QualifiedNames::of).collect(Collectors.joining(", "));
    }
}
