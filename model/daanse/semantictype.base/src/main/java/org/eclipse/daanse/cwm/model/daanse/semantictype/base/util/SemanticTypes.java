/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.base.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.daanse.semantictype.base.ExternalType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticTypeFactory;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Creates semantic types, says their URI, finds them and filters them.
 *
 * <p>A type is owned by its library. Its URI is the one stated, else the URI of its library and its name, after a
 * slash: the type {@code Town} of the library {@code https://types.example.org/harbour} is
 * {@code https://types.example.org/harbour/Town}. A library URI that ends in a slash or a hash takes no slash of its
 * own; a name is written with its characters beyond letters, digits, {@code -}, {@code .}, {@code _} and {@code ~}
 * percent-encoded in UTF-8. Names are unique within a library (rule ST4), so the derived URIs are too.</p>
 *
 * <p>A type is narrower than its broader types and, transitively, than theirs. Filters take the model they look in;
 * a type of another model is found when that model is given as a scope as well.</p>
 */
public final class SemanticTypes {

    private static final SemanticTypeFactory F = SemanticTypeFactory.eINSTANCE;

    private SemanticTypes() {
    }

    /** A type owned by {@code library}. */
    public static SemanticType create(TypeLibrary library, String name) {
        Objects.requireNonNull(library, "library");
        SemanticType type = F.createSemanticType();
        type.setName(Objects.requireNonNull(name, "name"));
        library.getOwnedElement().add(type);
        return type;
    }

    /** States what {@code type} is in the system of its library: its name and its number there. */
    public static <T extends SemanticType> T source(T type, String nameThere, long numberThere) {
        type.setSourceName(Objects.requireNonNull(nameThere, "nameThere"));
        type.setSourceNumber(numberThere);
        return type;
    }

    /** States the name of {@code type} in the system of its library. */
    public static <T extends SemanticType> T sourceName(T type, String nameThere) {
        type.setSourceName(Objects.requireNonNull(nameThere, "nameThere"));
        return type;
    }

    /** States the number of {@code type} in the system of its library. */
    public static <T extends SemanticType> T sourceNumber(T type, long numberThere) {
        type.setSourceNumber(numberThere);
        return type;
    }

    /** States the URI of {@code type} in place of the derived one. */
    public static <T extends SemanticType> T uri(T type, String uri) {
        type.setUri(Objects.requireNonNull(uri, "uri"));
        return type;
    }

    /** Says what {@code type} means, for a person. */
    public static <T extends SemanticType> T description(T type, String text) {
        type.setDescription(Objects.requireNonNull(text, "text"));
        return type;
    }

    /** Makes {@code type} a special case of each of {@code broader}. */
    public static <T extends SemanticType> T broader(T type, SemanticType... broader) {
        for (SemanticType b : broader) {
            type.getBroader().add(Objects.requireNonNull(b, "broader"));
        }
        return type;
    }

    /** Says that {@code type} is each of {@code same}, types of other libraries. */
    public static <T extends SemanticType> T sameAs(T type, SemanticType... same) {
        for (SemanticType s : same) {
            type.getSameAs().add(Objects.requireNonNull(s, "same"));
        }
        return type;
    }

    /**
     * Says that {@code type} is a type of a system that has no library in the model: the type named
     * {@code nameThere} or numbered {@code numberThere} (one of them at least) in the library {@code libraryUri}.
     */
    public static ExternalType sameAsExternal(SemanticType type, String libraryUri, String nameThere,
            Long numberThere) {
        Objects.requireNonNull(type, "type");
        if (nameThere == null && numberThere == null) {
            throw new IllegalArgumentException("an external type has a name or a number there");
        }
        ExternalType external = F.createExternalType();
        external.setLibraryUri(Objects.requireNonNull(libraryUri, "libraryUri"));
        external.setSourceName(nameThere);
        if (numberThere != null) {
            external.setSourceNumber(numberThere);
        }
        type.getSameAsExternal().add(external);
        return external;
    }

    /** The library that owns {@code type}, where one does. */
    public static Optional<TypeLibrary> libraryOf(SemanticType type) {
        return type.getNamespace() instanceof TypeLibrary library ? Optional.of(library) : Optional.empty();
    }

    /** The URI of {@code type}: the stated one, else the derived one; none for a type outside a library. */
    public static Optional<String> uriOf(SemanticType type) {
        if (type.getUri() != null && !type.getUri().isEmpty()) {
            return Optional.of(type.getUri());
        }
        return libraryOf(type).filter(library -> library.getUri() != null && type.getName() != null)
                .map(library -> derivedUri(library.getUri(), type.getName()));
    }

    /** The URI derived from a library URI and a name. */
    public static String derivedUri(String libraryUri, String name) {
        String base = libraryUri.endsWith("/") || libraryUri.endsWith("#") ? libraryUri : libraryUri + "/";
        return base + segment(name);
    }

    /** The types owned by {@code library}, in their order. */
    public static List<SemanticType> of(TypeLibrary library) {
        return library.getOwnedElement().stream().filter(SemanticType.class::isInstance).map(SemanticType.class::cast)
                .toList();
    }

    /** The types in {@code scope} and below, in the order of the model. */
    public static List<SemanticType> all(EObject scope) {
        List<SemanticType> types = new ArrayList<>();
        if (scope instanceof SemanticType type) {
            types.add(type);
        }
        scope.eAllContents().forEachRemaining(o -> {
            if (o instanceof SemanticType type) {
                types.add(type);
            }
        });
        return types;
    }

    /** The type of {@code scope} and below whose URI, stated or derived, is {@code uri}. */
    public static Optional<SemanticType> byUri(EObject scope, String uri) {
        Objects.requireNonNull(uri, "uri");
        return all(scope).stream().filter(type -> uriOf(type).filter(uri::equals).isPresent()).findFirst();
    }

    /** The type of {@code library} whose name (ours) is {@code name}. */
    public static Optional<SemanticType> named(TypeLibrary library, String name) {
        return of(library).stream().filter(type -> Objects.equals(name, type.getName())).findFirst();
    }

    /** The type of {@code library} whose name there is {@code nameThere}. */
    public static Optional<SemanticType> byNameThere(TypeLibrary library, String nameThere) {
        return of(library).stream().filter(type -> Objects.equals(nameThere, type.getSourceName())).findFirst();
    }

    /** The type of {@code library} whose number there is {@code number}. */
    public static Optional<SemanticType> byNumber(TypeLibrary library, long number) {
        return of(library).stream().filter(type -> type.isSetSourceNumber() && type.getSourceNumber() == number)
                .findFirst();
    }

    /**
     * Whether {@code type} is {@code other} or narrower than it: {@code other} is reached from {@code type} through
     * broader types. A cycle of broader types ends the walk.
     */
    public static boolean isA(SemanticType type, SemanticType other) {
        Objects.requireNonNull(other, "other");
        return broaderClosure(type).contains(other);
    }

    /** {@code type} and every type it is narrower than, each once, nearest first. */
    public static List<SemanticType> broaderClosure(SemanticType type) {
        Objects.requireNonNull(type, "type");
        Set<SemanticType> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<SemanticType> closure = new ArrayList<>();
        Deque<SemanticType> next = new ArrayDeque<>(List.of(type));
        while (!next.isEmpty()) {
            SemanticType t = next.removeFirst();
            if (t != null && seen.add(t)) {
                closure.add(t);
                next.addAll(t.getBroader());
            }
        }
        return closure;
    }

    /**
     * The types narrower than {@code type}, transitively, found in {@code scopes} and below; without a scope in the
     * model of {@code type}.
     */
    public static List<SemanticType> narrowerThan(SemanticType type, EObject... scopes) {
        Objects.requireNonNull(type, "type");
        List<EObject> roots = scopes.length == 0 ? List.of(EcoreUtil.getRootContainer(type)) : List.of(scopes);
        Set<SemanticType> seen = new HashSet<>();
        List<SemanticType> narrower = new ArrayList<>();
        for (EObject root : roots) {
            for (SemanticType candidate : all(root)) {
                if (candidate != type && seen.add(candidate) && isA(candidate, type)) {
                    narrower.add(candidate);
                }
            }
        }
        return narrower;
    }

    /** {@code name} as a segment of a URI: unreserved characters stay, all others are percent-encoded in UTF-8. */
    private static String segment(String name) {
        StringBuilder out = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (c < 0x80 && (Character.isLetterOrDigit(c) || c == '-' || c == '.' || c == '_' || c == '~')) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }
}
