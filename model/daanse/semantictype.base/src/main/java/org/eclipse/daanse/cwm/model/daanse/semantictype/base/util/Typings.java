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
import java.util.function.Predicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticTypeFactory;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticTypePackage;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.eclipse.emf.ecore.EObject;

/**
 * Types elements, finds the typings of an element and filters the typings of a model.
 *
 * <p>A typing is owned by the namespace it is created in and names its elements; it is not owned by the elements,
 * because a data type may own nothing (CWM 1.1, C-3-2). The typings of an element are found backwards through
 * {@link InverseReferences}: with an {@code ECrossReferenceAdapter} installed on the resource set in constant time,
 * without one by a scan of the model.</p>
 */
public final class Typings {

    private Typings() {
    }

    /** A typing owned by {@code owner}: {@code elements} are of {@code type}. */
    public static Typing create(Namespace owner, String name, SemanticType type, ModelElement... elements) {
        Objects.requireNonNull(owner, "owner");
        if (elements.length == 0) {
            throw new IllegalArgumentException("a typing names at least one element");
        }
        Typing typing = SemanticTypeFactory.eINSTANCE.createTyping();
        typing.setName(Objects.requireNonNull(name, "name"));
        typing.setType(Objects.requireNonNull(type, "type"));
        for (ModelElement element : elements) {
            typing.getModelElement().add(Objects.requireNonNull(element, "element"));
        }
        owner.getOwnedElement().add(typing);
        return typing;
    }

    /** The typings that name {@code element}. */
    public static List<Typing> own(ModelElement element) {
        return InverseReferences.referencingList(element, SemanticTypePackage.Literals.TYPING__MODEL_ELEMENT,
                Typing.class);
    }

    /** The types the typings of {@code element} give it: one, or one in each vocabulary it is typed in. */
    public static List<SemanticType> typesOf(ModelElement element) {
        return own(element).stream().map(Typing::getType).filter(type -> type != null).distinct().toList();
    }

    /** The type of {@code element}, where a typing gives it one; the first of {@link #typesOf} where several do. */
    public static Optional<SemanticType> typeOf(ModelElement element) {
        return typesOf(element).stream().findFirst();
    }

    /** The typings in {@code scope} and below, in the order of the model. */
    public static List<Typing> all(EObject scope) {
        return filter(scope, typing -> true);
    }

    /** The typings in {@code scope} and below whose type is {@code type} or narrower than it. */
    public static List<Typing> ofType(EObject scope, SemanticType type) {
        Objects.requireNonNull(type, "type");
        return filter(scope, typing -> typing.getType() != null && SemanticTypes.isA(typing.getType(), type));
    }

    /** The typings in {@code scope} and below whose type lies in {@code library}. */
    public static List<Typing> inLibrary(EObject scope, TypeLibrary library) {
        Objects.requireNonNull(library, "library");
        return filter(scope, typing -> typing.getType() != null && typing.getType().getNamespace() == library);
    }

    /** The typings in {@code scope} and below whose type lies in a library whose URI is {@code libraryUri}. */
    public static List<Typing> inLibrary(EObject scope, String libraryUri) {
        Objects.requireNonNull(libraryUri, "libraryUri");
        return filter(scope, typing -> typing.getType() != null && SemanticTypes.libraryOf(typing.getType())
                .filter(library -> libraryUri.equals(library.getUri())).isPresent());
    }

    private static List<Typing> filter(EObject scope, Predicate<Typing> keep) {
        Objects.requireNonNull(scope, "scope");
        List<Typing> typings = new ArrayList<>();
        if (scope instanceof Typing typing && keep.test(typing)) {
            typings.add(typing);
        }
        scope.eAllContents().forEachRemaining(o -> {
            if (o instanceof Typing typing && keep.test(typing)) {
                typings.add(typing);
            }
        });
        return typings;
    }
}
