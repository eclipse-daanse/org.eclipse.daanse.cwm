/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Document;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Subsystem;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code Document.modelElement} is unidirectional, so lookups run over the
 * cross-reference index — same shape as {@link DescriptionsTest}, with
 * {@code reference} in place of {@code body}.
 */
class DocumentsTest {

    private static final BusinessinformationFactory BI = BusinessinformationFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final String TYPE = "handbook";

    private Resource resource;

    @BeforeEach
    void newResource() {
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        resource = new ResourceImpl(URI.createURI("test:/documents"));
        set.getResources().add(resource);
    }

    private <T extends EObject> T managed(T object) {
        resource.getContents().add(object);
        return object;
    }

    private DataType table(String name) {
        DataType t = CF.createDataType();
        t.setName(name);
        return managed(t);
    }

    private Document document(String name, String type, String reference) {
        Document d = BI.createDocument();
        d.setName(name);
        d.setType(type);
        d.setReference(reference);
        return managed(d);
    }

    @Test
    void findsDocumentsAttachedToAnElement() {
        DataType t = table("CUSTOMER");
        Document d = document("guide", TYPE, "https://example.invalid/guide");
        d.getModelElement().add(t);

        assertThat(Documents.all(t)).containsExactly(d);
        assertThat(Documents.stream(t)).containsExactly(d);
    }

    @Test
    void filtersByType() {
        DataType t = table("CUSTOMER");
        Document handbook = document("guide", TYPE, "https://example.invalid/guide");
        Document contract = document("terms", "contract", "https://example.invalid/terms");
        handbook.getModelElement().add(t);
        contract.getModelElement().add(t);

        assertThat(Documents.all(t, TYPE)).containsExactly(handbook);
        assertThat(Documents.find(t, TYPE)).contains(handbook);
        assertThat(Documents.find(t, "missing")).isEmpty();
    }

    @Test
    void findSkipsDocumentsWithoutUsableReference() {
        DataType t = table("CUSTOMER");
        Document blank = document("empty", TYPE, "   ");
        Document usable = document("real", TYPE, "https://example.invalid/real");
        blank.getModelElement().add(t);
        usable.getModelElement().add(t);

        // both are attached, but only one carries a reference
        assertThat(Documents.all(t, TYPE)).containsExactly(blank, usable);
        assertThat(Documents.find(t, TYPE)).contains(usable);
    }

    @Test
    void findInheritedConsultsOnlyTheImmediateNamespace() {
        Subsystem schema = CF.createSubsystem();
        schema.setName("S");
        managed(schema);
        DataType t = CF.createDataType();
        t.setName("CUSTOMER");
        schema.getOwnedElement().add(t);

        Document onSchema = document("schema-guide", TYPE, "https://example.invalid/schema");
        onSchema.getModelElement().add(schema);

        assertThat(Documents.find(t, TYPE)).isEmpty();
        assertThat(Documents.findInherited(t, TYPE)).contains(onSchema);
        assertThat(Documents.findInherited(t, null)).isEmpty();
    }

    @Test
    void elementsReturnsWhatTheDocumentDescribes() {
        DataType a = table("A");
        DataType b = table("B");
        Document d = document("guide", TYPE, "https://example.invalid/guide");
        d.getModelElement().add(a);
        d.getModelElement().add(b);

        assertThat(Documents.elements(d)).containsExactly(a, b);
    }

    @Test
    void hasReferenceIgnoresBlank() {
        assertThat(Documents.hasReference(null)).isFalse();
        assertThat(Documents.hasReference(BI.createDocument())).isFalse();
        assertThat(Documents.hasReference(document("d", TYPE, "  "))).isFalse();
        assertThat(Documents.hasReference(document("d", TYPE, "x"))).isTrue();
    }

    @Test
    void selfReferenceIsDetected() {
        Document d = document("self", TYPE, "x");
        d.getModelElement().add(d);

        assertThat(Documents.isSelfReferencing(d)).isTrue();
        assertThat(Documents.isSelfReferencing(document("other", TYPE, "x"))).isFalse();
    }

    @Test
    void orphansAreOwnedButDescribeNothing() {
        Subsystem schema = CF.createSubsystem();
        schema.setName("S");
        managed(schema);

        Document attached = BI.createDocument();
        attached.setName("attached");
        attached.setType(TYPE);
        attached.setReference("x");
        Document orphan = BI.createDocument();
        orphan.setName("orphan");
        orphan.setType(TYPE);
        orphan.setReference("y");
        schema.getOwnedElement().add(attached);
        schema.getOwnedElement().add(orphan);

        attached.getModelElement().add(table("T"));

        assertThat(Documents.owned(schema)).containsExactly(attached, orphan);
        assertThat(Documents.orphans(schema)).containsExactly(orphan);
    }

    @Test
    void nullInputsAreTolerated() {
        assertThat(Documents.all(null)).isEmpty();
        assertThat(Documents.stream(null)).isEmpty();
        assertThat(Documents.find(null, TYPE)).isEmpty();
        assertThat(Documents.elements(null)).isEmpty();
        assertThat(Documents.owned(null)).isEmpty();
        assertThat(Documents.orphans(null)).isEmpty();
    }
}
