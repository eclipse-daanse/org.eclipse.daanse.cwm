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
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Email;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Location;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResourceLocator;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Telephone;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Subsystem;
import org.junit.jupiter.api.Test;

/**
 * Contact holds its details through four {@code 0..*} containment references, so these
 * lookups are plain forward navigation and need no cross-reference index.
 *
 * <p>
 * {@link Contacts#parties} is the exception: it reads {@code Contact.responsibleParty},
 * the end of {@code ResponsiblePartyContact} that MOF declares on Contact. Both ends of
 * that association carry a Reference, so no reverse lookup is involved either.
 */
class ContactsTest {

    private static final BusinessinformationFactory BI = BusinessinformationFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    private Contact contact(String name) {
        Contact c = BI.createContact();
        c.setName(name);
        return c;
    }

    private Email email(String type, String address) {
        Email e = BI.createEmail();
        e.setEmailType(type);
        e.setEmailAddress(address);
        return e;
    }

    private Telephone phone(String type, String number) {
        Telephone t = BI.createTelephone();
        t.setPhoneType(type);
        t.setPhoneNumber(number);
        return t;
    }

    private Location location(String type, String city) {
        Location l = BI.createLocation();
        l.setLocationType(type);
        l.setCity(city);
        return l;
    }

    @Test
    void detailsAreReadInDeclarationOrder() {
        Contact c = contact("primary");
        Email work = email("work", "ops@example.invalid");
        Email home = email("home", "private@example.invalid");
        c.getEmail().add(work);
        c.getEmail().add(home);

        assertThat(Contacts.emails(c)).containsExactly(work, home);
        assertThat(Contacts.emailStream(c)).containsExactly(work, home);
        assertThat(Contacts.firstEmail(c)).contains(work);
    }

    @Test
    void findsDetailByType() {
        Contact c = contact("primary");
        c.getEmail().add(email("work", "ops@example.invalid"));
        c.getEmail().add(email("home", "private@example.invalid"));
        c.getTelephone().add(phone("mobile", "+49 000"));
        c.getLocation().add(location("office", "Jena"));

        assertThat(Contacts.findEmail(c, "home")).isPresent();
        assertThat(Contacts.findEmail(c, "missing")).isEmpty();
        assertThat(Contacts.findEmail(c, null)).isEmpty();
        assertThat(Contacts.findTelephone(c, "mobile")).isPresent();
        assertThat(Contacts.findLocation(c, "office")).isPresent();
        assertThat(Contacts.findLocation(c, "home")).isEmpty();
    }

    @Test
    void resourceLocatorsAreExposedLikeTheOtherDetails() {
        Contact c = contact("primary");
        ResourceLocator url = BI.createResourceLocator();
        url.setUrl("https://example.invalid");
        c.getUrl().add(url);

        assertThat(Contacts.resourceLocators(c)).containsExactly(url);
        assertThat(Contacts.resourceLocatorStream(c)).containsExactly(url);
        assertThat(Contacts.firstResourceLocator(c)).contains(url);
    }

    @Test
    void partiesComeFromTheDeclaredReference() {
        Contact c = contact("shared");
        ResponsibleParty owner = BI.createResponsibleParty();
        owner.setName("data-owner");
        ResponsibleParty steward = BI.createResponsibleParty();
        steward.setName("data-steward");
        // the association is 0..* on both ends
        owner.getContact().add(c);
        steward.getContact().add(c);

        assertThat(Contacts.parties(c)).containsExactlyInAnyOrder(owner, steward);
        assertThat(Contacts.partyStream(c)).hasSize(2);
    }

    @Test
    void ownedFindsContactsInANamespace() {
        Subsystem schema = CF.createSubsystem();
        schema.setName("S");
        Contact a = contact("a");
        Contact b = contact("b");
        schema.getOwnedElement().add(a);
        schema.getOwnedElement().add(b);

        assertThat(Contacts.owned(schema)).containsExactly(a, b);
        assertThat(Contacts.ownedStream(schema)).containsExactly(a, b);
    }

    @Test
    void isEmptyOnlyWhenNoDetailOfAnyKind() {
        Contact c = contact("empty");
        assertThat(Contacts.isEmpty(c)).isTrue();
        assertThat(Contacts.isEmpty(null)).isTrue();

        c.getTelephone().add(phone("mobile", "+49 000"));
        assertThat(Contacts.isEmpty(c)).isFalse();
    }

    @Test
    void nullInputsAreTolerated() {
        assertThat(Contacts.emails(null)).isEmpty();
        assertThat(Contacts.telephones(null)).isEmpty();
        assertThat(Contacts.locations(null)).isEmpty();
        assertThat(Contacts.resourceLocators(null)).isEmpty();
        assertThat(Contacts.firstEmail(null)).isEmpty();
        assertThat(Contacts.findEmail(null, "work")).isEmpty();
        assertThat(Contacts.parties(null)).isEmpty();
        assertThat(Contacts.owned(null)).isEmpty();
    }
}
