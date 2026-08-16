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
package org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ModelElements;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;

public final class Descriptions {

    private Descriptions() {
    }

    /** Stream of every Description attached to {@code element}. */
    public static Stream<Description> stream(ModelElement element) {
        return InverseReferences.referencing(element, BusinessinformationPackage.Literals.DESCRIPTION__MODEL_ELEMENT, Description.class);
    }

    /** Every Description attached to {@code element}. */
    public static List<Description> all(ModelElement element) {
        return stream(element).toList();
    }

    /** Stream of Descriptions of the given {@code type} attached to {@code element}. */
    public static Stream<Description> stream(ModelElement element, String type) {
        return type == null ? Stream.empty() : stream(element).filter(d -> type.equals(d.getType()));
    }

    /** Descriptions of the given {@code type} attached to {@code element}. */
    public static List<Description> all(ModelElement element, String type) {
        return stream(element, type).toList();
    }

    /** First Description of the given {@code type} with a non-blank body. */
    public static Optional<Description> find(ModelElement element, String type) {
        return stream(element, type).filter(Descriptions::hasBody).findFirst();
    }

    /** First Description of the given {@code type} in {@code language}; exact match. */
    public static Optional<Description> find(ModelElement element, String type, String language) {
        return stream(element, type).filter(Descriptions::hasBody)
                .filter(d -> language == null ? d.getLanguage() == null : language.equals(d.getLanguage()))
                .findFirst();
    }

    /** Like {@link #find(ModelElement, String)}, but also consults the immediately enclosing namespace. */
    public static Optional<Description> findInherited(ModelElement element, String type) {
        return type == null ? Optional.empty()
                : ModelElements.findInherited(element, Descriptions::stream, d -> type.equals(d.getType()) && hasBody(d));
    }

    /** Elements this Description applies to. */
    public static List<ModelElement> elements(Description description) {
        return ModelElements.references(description, Description::getModelElement);
    }

    /** Whether the Description carries a non-blank {@code body}. */
    public static boolean hasBody(Description description) {
        return description != null && description.getBody() != null && !description.getBody().isBlank();
    }

    /** Whether the Description describes itself, which C-3-1 forbids. */
    public static boolean isSelfReferencing(Description description) {
        return ModelElements.isSelfReferencing(description, Description::getModelElement);
    }

    /** Stream of Descriptions directly owned by {@code namespace}. */
    public static Stream<Description> ownedStream(Namespace namespace) {
        return Namespaces.ownedElementStream(namespace, Description.class);
    }

    /** Descriptions directly owned by {@code namespace}. */
    public static List<Description> owned(Namespace namespace) {
        return ownedStream(namespace).toList();
    }

    /** Stream of Descriptions owned by {@code namespace} that describe no element. */
    public static Stream<Description> orphanStream(Namespace namespace) {
        return ModelElements.unreferencedStream(namespace, Description.class, Description::getModelElement);
    }

    /** Descriptions directly owned by {@code namespace} that describe nothing. */
    public static List<Description> orphans(Namespace namespace) {
        return orphanStream(namespace).toList();
    }

    /**
     * The namespace that owns the texts of {@code element}: the element
     * itself if it is a {@link Namespace}, otherwise the nearest container
     * namespace ({@link Namespaces#nearest}). A description lives owned in
     * this namespace and points back via {@code modelElement}. Fails if the
     * element hangs in no containment tree.
     */
    public static Namespace owner(ModelElement element) {
        Objects.requireNonNull(element, "element");
        return Namespaces.nearest(element)
                .orElseThrow(() -> new IllegalStateException("no namespace found for "
                        + element.eClass().getName() + " '" + element.getName()
                        + "' — attach the element to its container before describing it"));
    }

    /**
     * Canonicalizes {@code language} to a BCP-47 tag; empty for {@code null},
     * blank and unparseable input (an undetermined language). Accepts
     * Java-locale underscores ({@code de_DE}). Writers that must satisfy the
     * mandatory CWM {@code language} attribute fall back to
     * {@code und}.
     */
    public static Optional<String> languageTag(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        String tag = Locale.forLanguageTag(language.trim().replace('_', '-')).toLanguageTag();
        return "und".equals(tag) ? Optional.empty() : Optional.of(tag);
    }

    /**
     * Upserts the Description ({@code type}, {@code language}) of
     * {@code element} with {@code text} and returns it. The Description lives
     * in {@link #owner(ModelElement)}; language is canonicalized, the
     * mandatory name is derived. One Description per (element, type,
     * language) holds by construction.
     */
    public static Description describe(ModelElement element, String type, String language, String text) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        Namespace home = owner(element);
        // language is mandatory in CWM (exactly one), so undetermined input
        // is stored as the BCP-47 neutral tag
        String lang = languageTag(language).orElse("und");
        Optional<Description> existing = candidates(element, type)
                .filter(d -> lang.equals(d.getLanguage()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setBody(text);
            return existing.get();
        }
        Description d = BusinessinformationFactory.eINSTANCE.createDescription();
        d.setName(label(element) + "_" + type + "_" + lang);
        d.setType(type);
        d.setLanguage(lang);
        d.setBody(text);
        d.getModelElement().add(element);
        home.getOwnedElement().add(d);
        return d;
    }

    /**
     * The body of the {@code type} Description of {@code element} for
     * {@code locale}, resolved via the RFC 4647 lookup chain — exact tag,
     * progressive shortening ({@code de-DE} → {@code de}),
     * {@code und}, then the lexicographically smallest tag of
     * the type as the deterministic last grab. A {@code null} locale asks for
     * the language-neutral text. The type vocabulary is the caller's — CWM
     * leaves {@code Description.type} open.
     */
    public static Optional<String> localizedBody(ModelElement element, String type, Locale locale) {
        List<Description> found = candidates(element, type)
                .filter(Descriptions::hasBody)
                .toList();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        for (String tag : chain(locale)) {
            for (Description d : found) {
                if (tag.equals(d.getLanguage())) {
                    return Optional.of(d.getBody());
                }
            }
        }
        // deterministic last grab: the lexicographically smallest tag of the type
        return found.stream()
                .min(Comparator.comparing(Description::getLanguage, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Description::getBody);
    }

    /**
     * Descriptions of {@code element} with the cheapest exact source: the
     * inverse index when one is installed, otherwise a scan of the owning
     * namespace — adapter-free and exact as long as instances follow the
     * ownership rule of {@link #owner(ModelElement)}. Reads on detached
     * elements answer empty; only writes fail loudly.
     */
    private static Stream<Description> candidates(ModelElement element, String type) {
        if (InverseReferences.isIndexed(element)) {
            return stream(element, type);
        }
        return Namespaces.nearest(element).stream()
                .flatMap(ns -> Namespaces.ownedElementStream(ns, Description.class))
                .filter(d -> type.equals(d.getType()))
                .filter(d -> d.getModelElement().contains(element));
    }

    private static List<String> chain(Locale locale) {
        List<String> tags = new ArrayList<>();
        if (locale != null) {
            String t = locale.toLanguageTag();
            while (t != null && !t.isEmpty() && !"und".equals(t)) {
                tags.add(t);
                int cut = t.lastIndexOf('-');
                t = cut > 0 ? t.substring(0, cut) : null;
            }
        }
        tags.add("und");
        return tags;
    }

    private static String label(ModelElement element) {
        return element.getName() != null && !element.getName().isBlank()
                ? element.getName()
                : element.eClass().getName();
    }
}
