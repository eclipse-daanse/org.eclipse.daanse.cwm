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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

}
