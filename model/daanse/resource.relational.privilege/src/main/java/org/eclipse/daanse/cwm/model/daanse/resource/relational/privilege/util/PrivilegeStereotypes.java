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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Stereotypes;

/**
 * The {@code core::Stereotype} markers of database principals.
 *
 * <p>
 * In CWM {@code responsibility} is usage-defined: a {@code ResponsibleParty}
 * can stand in a model for other reasons (steward, document owner, team). The
 * stereotype is CWM's <em>typed</em> marker: one instance per kind
 * ({@link #DATABASE_ROLE}, {@link #DATABASE_USER}, {@code baseClass =
 * "ResponsibleParty"}) that names the parties it brands in
 * {@code extendedElement}. PUBLIC deliberately carries <b>no</b> stereotype
 * (a pseudo grantee), so it is never created as a role.
 * </p>
 *
 * <p>
 * The questions here go through {@link Stereotypes}. In the Ecore mapping the
 * party does not refer to its stereotype (that association end is hidden), so
 * the answer is found by a lookup of what refers to the party: the party and
 * its stereotype must be part of one model (one containment tree, resource or
 * resource set) when asked, and an installed inverse index makes the lookup
 * fast.
 * </p>
 */
public final class PrivilegeStereotypes {

    /** Stereotype name for roles (can be created with CREATE ROLE). */
    public static final String DATABASE_ROLE = "databaseRole";

    /** Stereotype name for users that log in (not provisioned: there are no credentials). */
    public static final String DATABASE_USER = "databaseUser";

    /** {@code Stereotype.baseClass} of both markers. */
    public static final String BASE_CLASS = "ResponsibleParty";

    private PrivilegeStereotypes() {
    }

    /** A new, unowned marker ({@link #DATABASE_ROLE} or {@link #DATABASE_USER}). */
    public static Stereotype create(String name) {
        Stereotype stereotype = CoreFactory.eINSTANCE.createStereotype();
        stereotype.setName(name);
        stereotype.setBaseClass(BASE_CLASS);
        return stereotype;
    }

    /** Whether {@code party} carries the {@link #DATABASE_ROLE} marker. */
    public static boolean isDatabaseRole(ResponsibleParty party) {
        return Stereotypes.has(party, DATABASE_ROLE);
    }

    /** Whether {@code party} carries the {@link #DATABASE_USER} marker. */
    public static boolean isDatabaseUser(ResponsibleParty party) {
        return Stereotypes.has(party, DATABASE_USER);
    }

    /** The parties in {@code root}'s containment tree that carry the {@link #DATABASE_ROLE} marker. */
    public static List<ResponsibleParty> databaseRoles(ModelElement root) {
        return Selections.stream(root, Selection.all(), ResponsibleParty.class)
                .filter(PrivilegeStereotypes::isDatabaseRole).toList();
    }

    /** The parties in {@code root}'s containment tree that carry the {@link #DATABASE_USER} marker. */
    public static List<ResponsibleParty> databaseUsers(ModelElement root) {
        return Selections.stream(root, Selection.all(), ResponsibleParty.class)
                .filter(PrivilegeStereotypes::isDatabaseUser).toList();
    }
}
