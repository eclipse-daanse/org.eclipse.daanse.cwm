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
package org.eclipse.daanse.cwm.util.foundation.businessinformation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Document;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.util.objectmodel.core.InverseReferences;
import org.eclipse.daanse.cwm.util.objectmodel.core.ModelElements;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;

public final class Documents {

    private Documents() {
    }

    /** Stream of every Document attached to {@code element}. */
    public static Stream<Document> stream(ModelElement element) {
        return InverseReferences.referencing(element, BusinessinformationPackage.Literals.DOCUMENT__MODEL_ELEMENT, Document.class);
    }

    /** Every Document attached to {@code element}. */
    public static List<Document> all(ModelElement element) {
        return stream(element).toList();
    }

    /** Stream of Documents of the given {@code type} attached to {@code element}. */
    public static Stream<Document> stream(ModelElement element, String type) {
        return type == null ? Stream.empty() : stream(element).filter(d -> type.equals(d.getType()));
    }

    /** Documents of the given {@code type} attached to {@code element}. */
    public static List<Document> all(ModelElement element, String type) {
        return stream(element, type).toList();
    }

    /** First Document of the given {@code type} with a non-blank reference. */
    public static Optional<Document> find(ModelElement element, String type) {
        return stream(element, type).filter(Documents::hasReference).findFirst();
    }

    /** Like {@link #find(ModelElement, String)}, but also consults the immediately enclosing namespace. */
    public static Optional<Document> findInherited(ModelElement element, String type) {
        return type == null ? Optional.empty()
                : ModelElements.findInherited(element, Documents::stream, d -> type.equals(d.getType()) && hasReference(d));
    }

    /** Elements this Document applies to. */
    public static List<ModelElement> elements(Document document) {
        return ModelElements.references(document, Document::getModelElement);
    }

    /** Whether the Document carries a non-blank {@code reference}. */
    public static boolean hasReference(Document document) {
        return document != null && document.getReference() != null && !document.getReference().isBlank();
    }

    /** Whether the Document describes itself, which C-3-2 forbids. */
    public static boolean isSelfReferencing(Document document) {
        return ModelElements.isSelfReferencing(document, Document::getModelElement);
    }

    /** Stream of Documents directly owned by {@code namespace}. */
    public static Stream<Document> ownedStream(Namespace namespace) {
        return Namespaces.ownedElementStream(namespace, Document.class);
    }

    /** Documents directly owned by {@code namespace}. */
    public static List<Document> owned(Namespace namespace) {
        return ownedStream(namespace).toList();
    }

    /** Stream of Documents owned by {@code namespace} that describe no element. */
    public static Stream<Document> orphanStream(Namespace namespace) {
        return ModelElements.unreferencedStream(namespace, Document.class, Document::getModelElement);
    }

    /** Documents directly owned by {@code namespace} that describe nothing. */
    public static List<Document> orphans(Namespace namespace) {
        return orphanStream(namespace).toList();
    }

}
