/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;

/**
 * What a model grants: the privileges its parties ({@code ResponsibleParty})
 * own — on tables, columns and routines, and their role memberships — the
 * parties marked {@code databaseRole}, the roles a script creates, and those
 * marked {@code databaseUser}, the users a script creates where the dialect has
 * users, without credentials. Which of them a script holds is the model's: cut
 * it to a selection first ({@code !is:privilege}, {@code !is:role},
 * {@code !is:user}), and the privilege model's consequences take along what
 * loses its target.
 */
record Grants(List<ResponsibleParty> roles, List<ResponsibleParty> users, List<Privilege> privileges) {

    /** The roles, users and privileges in {@code model}'s containment tree. */
    static Grants in(ModelElement model) {
        List<Privilege> privileges = Selections.stream(model, Selection.all(), Privilege.class)
                .filter(p -> p.getNamespace() instanceof ResponsibleParty).filter(Grants::hasTarget).toList();
        List<ResponsibleParty> parties = Selections.list(model, Selection.all(), ResponsibleParty.class);
        return new Grants(parties.stream().filter(PrivilegeStereotypes::isDatabaseRole).toList(),
                parties.stream().filter(PrivilegeStereotypes::isDatabaseUser).toList(), privileges);
    }

    private static boolean hasTarget(Privilege privilege) {
        return privilege instanceof TablePrivilege table && table.getTable() != null
                || privilege instanceof ProcedurePrivilege procedure && procedure.getProcedure() != null
                || privilege instanceof RolePrivilege membership && membership.getRole() != null;
    }
}
