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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticTypeFactory;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.emf.ecore.EObject;

/** Creates libraries of semantic types and finds them by their URI. */
public final class TypeLibraries {

    private TypeLibraries() {
    }

    /** A library owned by {@code owner}, identified by {@code uri}, an absolute URI (rule ST1). */
    public static TypeLibrary create(Namespace owner, String name, String uri) {
        Objects.requireNonNull(owner, "owner");
        TypeLibrary library = SemanticTypeFactory.eINSTANCE.createTypeLibrary();
        library.setName(Objects.requireNonNull(name, "name"));
        library.setUri(Objects.requireNonNull(uri, "uri"));
        owner.getOwnedElement().add(library);
        return library;
    }

    /** States the system the types of {@code library} come from, and its version where {@code version} is given. */
    public static TypeLibrary source(TypeLibrary library, String system, String version) {
        library.setSourceSystem(Objects.requireNonNull(system, "system"));
        library.setSourceVersion(version);
        return library;
    }

    /** The libraries in {@code scope} and below, in the order of the model. */
    public static List<TypeLibrary> all(EObject scope) {
        List<TypeLibrary> libraries = new ArrayList<>();
        if (scope instanceof TypeLibrary library) {
            libraries.add(library);
        }
        scope.eAllContents().forEachRemaining(o -> {
            if (o instanceof TypeLibrary library) {
                libraries.add(library);
            }
        });
        return libraries;
    }

    /** The library of {@code scope} and below whose URI is {@code uri}. */
    public static Optional<TypeLibrary> byUri(EObject scope, String uri) {
        Objects.requireNonNull(uri, "uri");
        return all(scope).stream().filter(library -> uri.equals(library.getUri())).findFirst();
    }
}
