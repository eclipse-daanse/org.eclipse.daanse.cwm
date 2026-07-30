/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;

/**
 * Die {@code core::Stereotype}-Markierung der Datenbank-Principals.
 *
 * <p>{@code responsibility} ist laut Spec usage-defined — eine
 * {@code ResponsibleParty} kann aus anderen Gründen im Modell stehen (Steward,
 * Dokumentverantwortliche, Team). Der Stereotype ist CWMs <em>typisierter</em>
 * Marker: je Art eine Instanz ({@link #DATABASE_ROLE}, {@link #DATABASE_USER},
 * {@code baseClass = "ResponsibleParty"}), die über {@code extendedElement} auf
 * die Parties zeigt. Die Party-Seite ist das versteckte Assoziationsende
 * ({@code oppositeRoleName = "stereotype"}) — Auswahl läuft deshalb über die
 * Stereotype-Instanzen (bzw. {@code InverseReferences}), nie über den
 * responsibility-Text. PUBLIC trägt bewusst <b>keinen</b> Stereotype
 * (Pseudo-Grantee) und fällt so bei der Rollenanlage automatisch heraus.</p>
 */
public final class PrivilegeStereotypes {

    /** Stereotype-Name für Rollen (anlegbar per CREATE ROLE). */
    public static final String DATABASE_ROLE = "databaseRole";

    /** Stereotype-Name für anmeldefähige User (nicht provisionierbar — keine Credentials). */
    public static final String DATABASE_USER = "databaseUser";

    /** {@code Stereotype.baseClass} beider Marker. */
    public static final String BASE_CLASS = "ResponsibleParty";

    private PrivilegeStereotypes() {
    }

    /** Erzeugt eine Marker-Instanz ({@link #DATABASE_ROLE} oder {@link #DATABASE_USER}). */
    public static Stereotype create(String name) {
        Stereotype stereotype = CoreFactory.eINSTANCE.createStereotype();
        stereotype.setName(name);
        stereotype.setBaseClass(BASE_CLASS);
        return stereotype;
    }

    /** Die Marker-Instanz mit {@code name}, falls vorhanden. */
    public static Optional<Stereotype> byName(Collection<? extends Stereotype> stereotypes, String name) {
        return stereotypes.stream().map(Stereotype.class::cast)
                .filter(s -> name.equals(s.getName())).findFirst();
    }

    /** Alle als Rolle markierten Parties (leere Liste, wenn der Marker fehlt). */
    public static List<ResponsibleParty> databaseRoles(Collection<? extends Stereotype> stereotypes) {
        return extendedParties(stereotypes, DATABASE_ROLE);
    }

    /** Alle als User markierten Parties (leere Liste, wenn der Marker fehlt). */
    public static List<ResponsibleParty> databaseUsers(Collection<? extends Stereotype> stereotypes) {
        return extendedParties(stereotypes, DATABASE_USER);
    }

    /** Ob {@code party} den {@link #DATABASE_ROLE}-Marker trägt. */
    public static boolean isDatabaseRole(ResponsibleParty party, Collection<? extends Stereotype> stereotypes) {
        return databaseRoles(stereotypes).contains(party);
    }

    private static List<ResponsibleParty> extendedParties(Collection<? extends Stereotype> stereotypes,
            String name) {
        return byName(stereotypes, name)
                .map(s -> s.getExtendedElement().stream()
                        .filter(ResponsibleParty.class::isInstance).map(ResponsibleParty.class::cast).toList())
                .orElse(List.of());
    }
}
