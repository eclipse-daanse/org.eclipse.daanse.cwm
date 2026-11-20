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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.ExternalType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Checks the libraries, types and typings of a model beyond what the metamodel enforces, by rules that hold for any
 * vocabulary. What a type of a given vocabulary may type is checked by that vocabulary's rules (the types of an
 * Analysis Services server: {@code semantictype.xmla}).
 *
 * <ul>
 * <li>{@code ST1} the URI of a library, the stated URI of a type and the library URI of an external type are
 * absolute and well-formed.</li>
 * <li>{@code ST2} no two libraries of a model share a URI, nor two types, stated or derived.</li>
 * <li>{@code ST3} a type lies in a library (OCL {@code ST3}).</li>
 * <li>{@code ST4} within a library, the names, the names there and the numbers there of the types are unique.</li>
 * <li>{@code ST5} no type is narrower than itself, directly or through other types (OCL {@code ST5} for the direct
 * case).</li>
 * <li>{@code ST7} the same types of a type lie in other libraries (OCL {@code ST7}); an external type names a
 * library that is neither the type's own nor one of the model, and its type there by name or number.</li>
 * <li>{@code ST8} the type of a typing, and the broader and same types of a type, resolve and lie in a library of
 * the same model or of a delivered library, recognised by its URI.</li>
 * </ul>
 */
public final class SemanticTypeModelValidator {

    /** The module the rules of this validator belong to. */
    public static final String MODULE = "semantictype";

    public static final Rule ST1 = Rule.error(MODULE, "ST1",
            "The URIs of libraries, of types and of external libraries are absolute and well-formed.");

    public static final Rule ST2 = Rule.error(MODULE, "ST2",
            "No two libraries and no two types of a model share a URI.");

    public static final Rule ST3 = Rule.error(MODULE, "ST3", "A type lies in a library.");

    public static final Rule ST4 = Rule.error(MODULE, "ST4",
            "Within a library the names, names there and numbers there of the types are unique.");

    public static final Rule ST5 = Rule.error(MODULE, "ST5", "No type is narrower than itself.");

    public static final Rule ST7 = Rule.error(MODULE, "ST7",
            "The same types of a type lie in other libraries; an external type names another library outside the "
                    + "model and its type there.");

    public static final Rule ST8 = Rule.error(MODULE, "ST8",
            "A typing and a type refer to types of a library of the same model or of a delivered library.");

    /** Every rule of this validator, in the order of their codes. */
    public static final List<Rule> RULES = List.of(ST1, ST2, ST3, ST4, ST5, ST7, ST8);

    private SemanticTypeModelValidator() {
    }

    /** All findings for the libraries, types and typings in {@code root} and below, with no library delivered. */
    public static List<Finding> check(EObject root) {
        return check(root, Set.of());
    }

    /**
     * All findings for the libraries, types and typings in {@code root} and below. A type may lie outside the model
     * of {@code root} where its library's URI is one of {@code deliveredLibraries}: a library a program delivers
     * beside the model.
     */
    public static List<Finding> check(EObject root, Collection<String> deliveredLibraries) {
        Objects.requireNonNull(deliveredLibraries, "deliveredLibraries");
        Check check = new Check(root, deliveredLibraries);
        check.run();
        return check.findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return Findings.errorLines(check(root));
    }

    /** The errors of {@link #check(EObject, Collection)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root, Collection<String> deliveredLibraries) {
        return Findings.errorLines(check(root, deliveredLibraries));
    }

    /** One run over a model. */
    private static final class Check {

        private final EObject root;
        private final EObject model;
        private final Collection<String> delivered;
        private final List<Finding> findings = new ArrayList<>();
        private final Map<String, List<EObject>> byUri = new LinkedHashMap<>();
        private final List<TypeLibrary> libraries = new ArrayList<>();

        Check(EObject root, Collection<String> delivered) {
            this.root = Objects.requireNonNull(root, "root");
            this.model = EcoreUtil.getRootContainer(root);
            this.delivered = delivered;
        }

        void run() {
            List<EObject> all = new ArrayList<>(List.of(root));
            root.eAllContents().forEachRemaining(all::add);
            for (EObject o : all) {
                if (o instanceof TypeLibrary library) {
                    libraries.add(library);
                }
            }
            for (EObject o : all) {
                if (o instanceof TypeLibrary library) {
                    library(library);
                } else if (o instanceof SemanticType type) {
                    type(type);
                } else if (o instanceof Typing typing) {
                    typing(typing);
                }
            }
            byUri.forEach((uri, owners) -> {
                if (owners.size() > 1 && !sameNameInOneLibrary(owners)) {
                    for (EObject owner : owners) {
                        List<String> others = owners.stream().filter(o -> o != owner)
                                .map(SemanticTypeModelValidator::path).toList();
                        findings.add(ST2.at(owner, path(owner) + " has the URI " + uri + ", as " + String.join(", ",
                                others) + " has"));
                    }
                }
            });
        }

        /**
         * Whether the owners of one URI are types of one library whose URIs are all derived: then they share a name,
         * which ST4 reports.
         */
        private static boolean sameNameInOneLibrary(List<EObject> owners) {
            return owners.stream().allMatch(o -> o instanceof SemanticType t && (t.getUri() == null
                    || t.getUri().isEmpty()) && t.getNamespace() == ((SemanticType) owners.get(0)).getNamespace());
        }

        private void library(TypeLibrary library) {
            if (absolute(library, "the URI", library.getUri())) {
                byUri.computeIfAbsent(library.getUri(), k -> new ArrayList<>()).add(library);
            }
            List<SemanticType> types = SemanticTypes.of(library);
            unique(library, types, "name", SemanticType::getName);
            unique(library, types, "name there", SemanticType::getSourceName);
            unique(library, types.stream().filter(SemanticType::isSetSourceNumber).toList(), "number there",
                    type -> type.getSourceNumber());
        }

        private void unique(TypeLibrary library, List<SemanticType> types, String what,
                Function<SemanticType, Object> key) {
            Map<Object, List<SemanticType>> seen = new LinkedHashMap<>();
            for (SemanticType type : types) {
                Object value = key.apply(type);
                if (value != null) {
                    seen.computeIfAbsent(value, k -> new ArrayList<>()).add(type);
                }
            }
            seen.forEach((value, same) -> {
                if (same.size() > 1) {
                    findings.add(ST4.at(library, path(library) + " has " + same.size() + " types of the " + what + " "
                            + value + ": " + String.join(", ", same.stream().map(SemanticTypeModelValidator::path)
                                    .toList())));
                }
            });
        }

        private void type(SemanticType type) {
            if (!(type.getNamespace() instanceof TypeLibrary)) {
                findings.add(ST3.at(type, path(type) + " lies in no library"));
            }
            if (type.getUri() != null) {
                absolute(type, "the URI", type.getUri());
            }
            SemanticTypes.uriOf(type).filter(SemanticTypeModelValidator::isAbsolute)
                    .ifPresent(uri -> byUri.computeIfAbsent(uri, k -> new ArrayList<>()).add(type));
            for (SemanticType broader : type.getBroader()) {
                refers(type, broader, "the broader type");
            }
            for (SemanticType same : type.getSameAs()) {
                if (refers(type, same, "the same type") && same.getNamespace() == type.getNamespace()) {
                    findings.add(ST7.at(type, path(type) + " is the same as " + path(same)
                            + " of its own library; the same type lies in another library"));
                }
            }
            for (ExternalType external : type.getSameAsExternal()) {
                external(type, external);
            }
            cycle(type);
        }

        private void external(SemanticType type, ExternalType external) {
            String uri = external.getLibraryUri();
            if (!absolute(type, "the library URI of an external type", uri)) {
                return;
            }
            if (SemanticTypes.libraryOf(type).filter(own -> uri.equals(own.getUri())).isPresent()) {
                findings.add(ST7.at(type, path(type) + " names its own library " + uri + " as an external one"));
            } else if (libraries.stream().anyMatch(library -> uri.equals(library.getUri()))) {
                findings.add(ST7.at(type, path(type) + " names the library " + uri
                        + " as an external one; it lies in the model, sameAs names its type"));
            }
            if (external.getSourceName() == null && !external.isSetSourceNumber()) {
                findings.add(ST7.at(type, path(type) + " names a type of " + uri + " by neither name nor number"));
            }
        }

        /** Reports a type on a cycle of broader types, at the type the walk started from. */
        private void cycle(SemanticType type) {
            Deque<SemanticType> next = new ArrayDeque<>(type.getBroader());
            Set<SemanticType> seen = new HashSet<>();
            while (!next.isEmpty()) {
                SemanticType t = next.removeFirst();
                if (t == type) {
                    findings.add(ST5.at(type, path(type) + " is narrower than itself"));
                    return;
                }
                if (t != null && !t.eIsProxy() && seen.add(t)) {
                    next.addAll(t.getBroader());
                }
            }
        }

        private void typing(Typing typing) {
            SemanticType type = typing.getType();
            if (type == null) {
                findings.add(ST8.at(typing, path(typing) + " names no type"));
                return;
            }
            refers(typing, type, "the type");
        }

        /**
         * Whether {@code target}, referred to by {@code from}, resolves; reports under ST8 where it does not, or where
         * it lies neither in the model nor in a delivered library.
         */
        private boolean refers(EObject from, SemanticType target, String what) {
            if (target.eIsProxy()) {
                findings.add(ST8.at(from, path(from) + ": " + what + " " + EcoreUtil.getURI(target)
                        + " does not resolve"));
                return false;
            }
            TypeLibrary library = SemanticTypes.libraryOf(target).orElse(null);
            if (library == null) {
                if (EcoreUtil.getRootContainer(target) != model) {
                    findings.add(ST8.at(from, path(from) + ": " + what + " " + path(target)
                            + " lies in no library and outside the model"));
                }
                return true;
            }
            if (EcoreUtil.getRootContainer(library) != model && !delivered.contains(library.getUri())) {
                findings.add(ST8.at(from, path(from) + ": " + what + " " + path(target) + " lies in the library "
                        + library.getUri() + ", which is neither in the model nor delivered"));
            }
            return true;
        }

        /** Whether {@code uri} is absolute and well-formed; reports under ST1 where it is not. */
        private boolean absolute(EObject element, String what, String uri) {
            if (uri == null || uri.isEmpty()) {
                findings.add(ST1.at(element, path(element) + " has no " + what.replace("the ", "")));
                return false;
            }
            if (!isAbsolute(uri)) {
                findings.add(ST1.at(element, path(element) + ": " + what + " " + uri
                        + " is no absolute, well-formed URI"));
                return false;
            }
            return true;
        }
    }

    /** Whether {@code uri} is an absolute, well-formed URI. */
    static boolean isAbsolute(String uri) {
        try {
            return new URI(uri).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The names from the root down to {@code element}, joined by dots. */
    static String path(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
