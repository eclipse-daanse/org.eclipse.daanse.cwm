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
package org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.util;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.BusinessnomenclatureFactory;
import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.Glossary;
import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.Term;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;

/**
 * Helpers around {@code businessnomenclature::Glossary} and {@code Term}:
 * the CWM home for business vocabulary — what an element is <em>called</em>
 * in the domain, with definition and synonyms, anchored to model elements
 * via the {@code modelElement} reference.
 *
 * <p>A {@link Glossary} is a {@code core::Package} and lives owned in the
 * package it describes; its {@link Term}s live owned in the glossary. Terms
 * are {@code ModelElement}s: language-neutral vocabulary is the term itself —
 * the glossary answers "what does it mean", not "how is it labelled per
 * language". Synonyms point at their preferred term via
 * {@code Term.preferredTerm}.
 */
public final class Glossaries {

    private Glossaries() {
    }

    /** Stream of the glossaries owned by {@code owner}. */
    public static Stream<Glossary> ownedStream(Package owner) {
        return Namespaces.ownedElementStream(owner, Glossary.class);
    }

    /** Glossaries owned by {@code owner}. */
    public static List<Glossary> owned(Package owner) {
        return ownedStream(owner).toList();
    }

    /** Finds or creates the glossary named {@code name} owned by {@code owner}. */
    public static Glossary glossary(Package owner, String name, String language) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Optional<Glossary> existing = Namespaces.findOwnedByName(owner, Glossary.class, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        Glossary glossary = BusinessnomenclatureFactory.eINSTANCE.createGlossary();
        glossary.setName(name);
        // language is mandatory in CWM (exactly one), so undetermined input
        // is stored as the BCP-47 neutral tag
        glossary.setLanguage(languageTag(language).orElse("und"));
        owner.getOwnedElement().add(glossary);
        return glossary;
    }

    /** Finds or creates the term {@code name} in {@code glossary} and sets its definition. */
    public static Term define(Glossary glossary, String name, String definition) {
        Objects.requireNonNull(name, "name");
        Term term = Namespaces.findOwnedByName(glossary, Term.class, name)
                .orElseGet(() -> {
                    Term t = BusinessnomenclatureFactory.eINSTANCE.createTerm();
                    t.setName(name);
                    glossary.getOwnedElement().add(t);
                    return t;
                });
        if (definition != null) {
            term.setDefinition(definition);
        }
        return term;
    }

    /** Stream of the terms owned by {@code glossary}. */
    public static Stream<Term> termStream(Glossary glossary) {
        return Namespaces.ownedElementStream(glossary, Term.class);
    }

    /** Terms owned by {@code glossary}. */
    public static List<Term> terms(Glossary glossary) {
        return termStream(glossary).toList();
    }

    /** The terms anchored at {@code element}, found across the glossaries owned by {@code owner}. */
    public static List<Term> termsFor(Package owner, ModelElement element) {
        return ownedStream(owner)
                .flatMap(Glossaries::termStream)
                .filter(t -> t.getModelElement().contains(element))
                .toList();
    }

    /**
     * Canonicalizes {@code language} to a BCP-47 tag; empty for {@code null},
     * blank and unparseable input (an undetermined language — {@code und} is
     * the IETF tag for it, not a CWM term). Accepts Java-locale underscores
     * ({@code de_DE}).
     */
    public static Optional<String> languageTag(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        String tag = Locale.forLanguageTag(language.trim().replace('_', '-')).toLanguageTag();
        return "und".equals(tag) ? Optional.empty() : Optional.of(tag);
    }
}
