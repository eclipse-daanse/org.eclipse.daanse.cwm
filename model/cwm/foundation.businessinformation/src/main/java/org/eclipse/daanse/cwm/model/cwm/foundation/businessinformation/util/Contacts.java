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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Contact;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Email;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Location;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResourceLocator;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Telephone;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;

public final class Contacts {

    private Contacts() {
    }

    /** Emails in priority order. */
    public static List<Email> emails(Contact contact) {
        return emailStream(contact).toList();
    }

    /** Stream twin of {@link #emails}. */
    public static Stream<Email> emailStream(Contact contact) {
        return contact == null ? Stream.empty() : contact.getEmail().stream();
    }

    /** Telephones in priority order. */
    public static List<Telephone> telephones(Contact contact) {
        return telephoneStream(contact).toList();
    }

    /** Stream twin of {@link #telephones}. */
    public static Stream<Telephone> telephoneStream(Contact contact) {
        return contact == null ? Stream.empty() : contact.getTelephone().stream();
    }

    /** Locations in priority order. */
    public static List<Location> locations(Contact contact) {
        return locationStream(contact).toList();
    }

    /** Stream twin of {@link #locations}. */
    public static Stream<Location> locationStream(Contact contact) {
        return contact == null ? Stream.empty() : contact.getLocation().stream();
    }

    /** Resource locators in priority order — CWM calls this reference {@code url}. */
    public static List<ResourceLocator> resourceLocators(Contact contact) {
        return resourceLocatorStream(contact).toList();
    }

    /** Stream twin of {@link #resourceLocators}. */
    public static Stream<ResourceLocator> resourceLocatorStream(Contact contact) {
        return contact == null ? Stream.empty() : contact.getUrl().stream();
    }

    /** Highest-priority Email. */
    public static Optional<Email> firstEmail(Contact contact) {
        return emailStream(contact).findFirst();
    }

    /** Highest-priority Telephone. */
    public static Optional<Telephone> firstTelephone(Contact contact) {
        return telephoneStream(contact).findFirst();
    }

    /** Highest-priority Location. */
    public static Optional<Location> firstLocation(Contact contact) {
        return locationStream(contact).findFirst();
    }

    /** Highest-priority ResourceLocator. */
    public static Optional<ResourceLocator> firstResourceLocator(Contact contact) {
        return resourceLocatorStream(contact).findFirst();
    }

    /** Highest-priority Email of the given {@code emailType}. */
    public static Optional<Email> findEmail(Contact contact, String emailType) {
        return emailType == null ? Optional.empty()
                : emailStream(contact).filter(e -> emailType.equals(e.getEmailType())).findFirst();
    }

    /** Highest-priority Telephone of the given {@code phoneType}. */
    public static Optional<Telephone> findTelephone(Contact contact, String phoneType) {
        return phoneType == null ? Optional.empty()
                : telephoneStream(contact).filter(t -> phoneType.equals(t.getPhoneType())).findFirst();
    }

    /** Highest-priority Location of the given {@code locationType}. */
    public static Optional<Location> findLocation(Contact contact, String locationType) {
        return locationType == null ? Optional.empty()
                : locationStream(contact).filter(l -> locationType.equals(l.getLocationType())).findFirst();
    }

    /** Contacts directly owned by {@code namespace}. */
    public static List<Contact> owned(Namespace namespace) {
        return ownedStream(namespace).toList();
    }

    /** Stream twin of {@link #owned}. */
    public static Stream<Contact> ownedStream(Namespace namespace) {
        return Namespaces.ownedElementStream(namespace, Contact.class);
    }

    /** Parties this Contact belongs to; a Contact may serve several. */
    public static List<ResponsibleParty> parties(Contact contact) {
        return partyStream(contact).toList();
    }

    /** Stream twin of {@link #parties}. */
    public static Stream<ResponsibleParty> partyStream(Contact contact) {
        return contact == null ? Stream.empty() : contact.getResponsibleParty().stream();
    }

    /** Whether the Contact holds no detail of any kind. */
    public static boolean isEmpty(Contact contact) {
        return contact == null || (contact.getEmail().isEmpty() && contact.getTelephone().isEmpty()
                && contact.getLocation().isEmpty() && contact.getUrl().isEmpty());
    }
}
