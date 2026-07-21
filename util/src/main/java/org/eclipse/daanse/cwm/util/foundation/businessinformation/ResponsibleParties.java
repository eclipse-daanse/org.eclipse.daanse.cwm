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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Contact;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.util.objectmodel.core.InverseReferences;
import org.eclipse.daanse.cwm.util.objectmodel.core.ModelElements;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;

public final class ResponsibleParties {

    private ResponsibleParties() {
    }

    /** Stream of every party responsible for {@code element}. */
    public static Stream<ResponsibleParty> stream(ModelElement element) {
        return InverseReferences.referencing(element, BusinessinformationPackage.Literals.RESPONSIBLE_PARTY__MODEL_ELEMENT, ResponsibleParty.class);
    }

    /** Every party responsible for {@code element}. */
    public static List<ResponsibleParty> all(ModelElement element) {
        return stream(element).toList();
    }

    /** Stream of parties holding the given {@code responsibility} for {@code element}. */
    public static Stream<ResponsibleParty> stream(ModelElement element, String responsibility) {
        return responsibility == null ? Stream.empty()
                : stream(element).filter(p -> responsibility.equals(p.getResponsibility()));
    }

    /** Parties holding the given {@code responsibility} for {@code element}. */
    public static List<ResponsibleParty> all(ModelElement element, String responsibility) {
        return stream(element, responsibility).toList();
    }

    /** First party holding the given non-blank {@code responsibility} for {@code element}. */
    public static Optional<ResponsibleParty> find(ModelElement element, String responsibility) {
        return stream(element, responsibility).filter(ResponsibleParties::hasResponsibility).findFirst();
    }

    /** Like {@link #find(ModelElement, String)}, but also consults the immediately enclosing namespace. */
    public static Optional<ResponsibleParty> findInherited(ModelElement element, String responsibility) {
        return responsibility == null ? Optional.empty()
                : ModelElements.findInherited(element, ResponsibleParties::stream,
                        p -> responsibility.equals(p.getResponsibility()) && hasResponsibility(p));
    }

    /** Elements this party is responsible for. */
    public static List<ModelElement> elements(ResponsibleParty party) {
        return ModelElements.references(party, ResponsibleParty::getModelElement);
    }

    /** Whether the party carries a non-blank {@code responsibility}. */
    public static boolean hasResponsibility(ResponsibleParty party) {
        return party != null && party.getResponsibility() != null && !party.getResponsibility().isBlank();
    }

    /** Whether the party is responsible for itself, which C-3-3 forbids. */
    public static boolean isSelfReferencing(ResponsibleParty party) {
        return ModelElements.isSelfReferencing(party, ResponsibleParty::getModelElement);
    }

    /** Contacts of this party, in priority order. */
    public static List<Contact> contacts(ResponsibleParty party) {
        return contactStream(party).toList();
    }

    /** Stream twin of {@link #contacts}. */
    public static Stream<Contact> contactStream(ResponsibleParty party) {
        return party == null ? Stream.empty() : party.getContact().stream();
    }

    /** Stream of parties directly owned by {@code namespace}. */
    public static Stream<ResponsibleParty> ownedStream(Namespace namespace) {
        return Namespaces.ownedElementStream(namespace, ResponsibleParty.class);
    }

    /** Parties directly owned by {@code namespace}. */
    public static List<ResponsibleParty> owned(Namespace namespace) {
        return ownedStream(namespace).toList();
    }

    /** Stream of parties owned by {@code namespace} that are responsible for no element. */
    public static Stream<ResponsibleParty> orphanStream(Namespace namespace) {
        return ModelElements.unreferencedStream(namespace, ResponsibleParty.class, ResponsibleParty::getModelElement);
    }

    /** Parties directly owned by {@code namespace} that are responsible for nothing. */
    public static List<ResponsibleParty> orphans(Namespace namespace) {
        return orphanStream(namespace).toList();
    }

}
