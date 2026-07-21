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
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Contact;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
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
 * {@code ResponsibleParty.modelElement} is unidirectional, so "who is responsible for
 * this element" runs over the cross-reference index — the same shape as
 * {@link DescriptionsTest}.
 *
 * <p>
 * {@code ResponsiblePartyContact} is different: MOF declares a Reference for
 * <em>both</em> ends, so contacts are read straight off {@code getContact()}. The tests
 * below link contacts without also owning them, which is what a spec-conforming model
 * looks like and what a containment-based lookup would miss.
 */
class ResponsiblePartiesTest {

    private static final BusinessinformationFactory BI = BusinessinformationFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    private Resource resource;

    @BeforeEach
    void newResource() {
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        resource = new ResourceImpl(URI.createURI("test:/parties"));
        set.getResources().add(resource);
    }

    private <T extends EObject> T managed(T object) {
        resource.getContents().add(object);
        return object;
    }

    private ResponsibleParty party(String name, String responsibility) {
        ResponsibleParty p = BI.createResponsibleParty();
        p.setName(name);
        p.setResponsibility(responsibility);
        return managed(p);
    }

    private DataType table(String name) {
        DataType t = CF.createDataType();
        t.setName(name);
        return managed(t);
    }

    // ---- the association: both ends are MOF-declared -----------------------------

    @Test
    void contactsAreReadFromTheDeclaredReference() {
        ResponsibleParty owner = party("data-owner", "ownership");
        Contact c = BI.createContact();
        c.setName("primary");
        managed(c);
        // linked through the association, deliberately NOT owned by the party
        owner.getContact().add(c);

        // pins down what this test is for: a containment-based lookup would find nothing
        // here, which is exactly how the earlier implementation failed
        assertThat(owner.getOwnedElement()).isEmpty();

        assertThat(ResponsibleParties.contacts(owner)).containsExactly(c);
        assertThat(ResponsibleParties.contactStream(owner)).containsExactly(c);
    }

    @Test
    void aContactMayServeSeveralParties() {
        // the association is 0..* on both ends, so sharing is allowed
        ResponsibleParty owner = party("data-owner", "ownership");
        ResponsibleParty steward = party("data-steward", "stewardship");
        Contact shared = BI.createContact();
        shared.setName("shared");
        managed(shared);
        owner.getContact().add(shared);
        steward.getContact().add(shared);

        assertThat(ResponsibleParties.contacts(owner)).containsExactly(shared);
        assertThat(ResponsibleParties.contacts(steward)).containsExactly(shared);
        assertThat(Contacts.parties(shared)).containsExactlyInAnyOrder(owner, steward);
    }

    @Test
    void contactOrderIsPreserved() {
        ResponsibleParty p = party("escalation", "on-call");
        Contact first = BI.createContact();
        first.setName("first");
        Contact second = BI.createContact();
        second.setName("second");
        managed(first);
        managed(second);
        p.getContact().add(first);
        p.getContact().add(second);

        assertThat(ResponsibleParties.contacts(p)).containsExactly(first, second);
    }

    @Test
    void contactLookupToleratesNull() {
        assertThat(ResponsibleParties.contacts(null)).isEmpty();
        assertThat(ResponsibleParties.contactStream(null)).isEmpty();
        assertThat(Contacts.parties(null)).isEmpty();
    }

    // ---- the anchor side: reverse navigation over the index ----------------------

    @Test
    void findsPartiesResponsibleForAnElement() {
        DataType t = table("CUSTOMER");
        ResponsibleParty owner = party("data-owner", "ownership");
        owner.getModelElement().add(t);

        assertThat(ResponsibleParties.all(t)).containsExactly(owner);
        assertThat(ResponsibleParties.find(t, "ownership")).contains(owner);
        assertThat(ResponsibleParties.find(t, "stewardship")).isEmpty();
    }

    @Test
    void elementsReturnsTheAnchoredElements() {
        DataType a = table("A");
        DataType b = table("B");
        ResponsibleParty p = party("owner", "ownership");
        p.getModelElement().add(a);
        p.getModelElement().add(b);

        assertThat(ResponsibleParties.elements(p)).containsExactly(a, b);
    }

    @Test
    void orphansAreOwnedButAnchorNothing() {
        Subsystem schema = CF.createSubsystem();
        schema.setName("S");
        managed(schema);

        ResponsibleParty anchored = BI.createResponsibleParty();
        anchored.setName("anchored");
        anchored.setResponsibility("ownership");
        ResponsibleParty orphan = BI.createResponsibleParty();
        orphan.setName("orphan");
        orphan.setResponsibility("ownership");
        schema.getOwnedElement().add(anchored);
        schema.getOwnedElement().add(orphan);

        DataType t = table("T");
        anchored.getModelElement().add(t);

        assertThat(ResponsibleParties.owned(schema)).containsExactly(anchored, orphan);
        assertThat(ResponsibleParties.orphans(schema)).containsExactly(orphan);
    }

    @Test
    void selfReferenceIsDetected() {
        ResponsibleParty p = party("self", "ownership");
        p.getModelElement().add(p);

        assertThat(ResponsibleParties.isSelfReferencing(p)).isTrue();
        assertThat(ResponsibleParties.isSelfReferencing(party("other", "ownership"))).isFalse();
    }

    @Test
    void hasResponsibilityIgnoresBlank() {
        ResponsibleParty p = BI.createResponsibleParty();
        assertThat(ResponsibleParties.hasResponsibility(p)).isFalse();
        p.setResponsibility("  ");
        assertThat(ResponsibleParties.hasResponsibility(p)).isFalse();
        p.setResponsibility("ownership");
        assertThat(ResponsibleParties.hasResponsibility(p)).isTrue();
        assertThat(ResponsibleParties.hasResponsibility(null)).isFalse();
    }
}
