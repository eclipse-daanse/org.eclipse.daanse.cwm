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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.ONLY_LEFT;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.ONLY_RIGHT;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.differs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;

/**
 * A role, user or other party with privileges that both models hold, by name, and how it differs:
 * the stereotypes that mark it, the privileges it holds and the roles it is a member of. A membership
 * is a {@link RolePrivilege}.
 *
 * @param left                 the party of the first model
 * @param right                the party of the second model
 * @param stereotypesOnlyLeft  names of stereotypes only the first model's party carries
 * @param stereotypesOnlyRight names of stereotypes only the second model's party carries
 * @param privilegesOnlyLeft   privileges and memberships only the first model's party holds
 * @param privilegesOnlyRight  privileges and memberships only the second model's party holds
 * @param privilegesChanged    the same privilege or membership on both sides, granted differently
 */
public record PartyDiff(ResponsibleParty left, ResponsibleParty right, List<String> stereotypesOnlyLeft,
        List<String> stereotypesOnlyRight, List<Privilege> privilegesOnlyLeft, List<Privilege> privilegesOnlyRight,
        List<PrivilegeChange> privilegesChanged) {

    public PartyDiff {
        stereotypesOnlyLeft = List.copyOf(stereotypesOnlyLeft);
        stereotypesOnlyRight = List.copyOf(stereotypesOnlyRight);
        privilegesOnlyLeft = List.copyOf(privilegesOnlyLeft);
        privilegesOnlyRight = List.copyOf(privilegesOnlyRight);
        privilegesChanged = List.copyOf(privilegesChanged);
    }

    /** {@code true} iff the party is the same on both sides. */
    public boolean isEmpty() {
        return stereotypesOnlyLeft.isEmpty() && stereotypesOnlyRight.isEmpty() && privilegesOnlyLeft.isEmpty()
                && privilegesOnlyRight.isEmpty() && privilegesChanged.isEmpty();
    }

    /**
     * One readable line per difference: {@code role shop_reader: stereotype databaseRole only left},
     * {@code privilege of shop_reader: SELECT on shop.sales.orders only right}, {@code membership of
     * shop_writer: member of shop_reader grantable yes left, no right}.
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        String party = Lines.kind(left) + " " + left.getName();
        stereotypesOnlyLeft.forEach(s -> lines.add(party + ": stereotype " + s + " " + ONLY_LEFT));
        stereotypesOnlyRight.forEach(s -> lines.add(party + ": stereotype " + s + " " + ONLY_RIGHT));
        privilegesOnlyLeft.forEach(p -> lines.add(of(p) + Lines.privilege(p) + " " + ONLY_LEFT));
        privilegesOnlyRight.forEach(p -> lines.add(of(p) + Lines.privilege(p) + " " + ONLY_RIGHT));
        for (PrivilegeChange change : privilegesChanged) {
            for (PrivilegeChange.Aspect aspect : change.aspects()) {
                lines.add(of(change.left()) + Lines.privilege(change.left()) + " " + switch (aspect) {
                case GRANTABLE -> differs("grantable", Boolean.TRUE.equals(change.left().getGrantable()),
                        Boolean.TRUE.equals(change.right().getGrantable()));
                case GRANTOR -> differs("grantor", grantor(change.left()), grantor(change.right()));
                });
            }
        }
        return lines;
    }

    private String of(Privilege privilege) {
        return (privilege instanceof RolePrivilege ? "membership of " : "privilege of ") + left.getName() + ": ";
    }

    private static String grantor(Privilege privilege) {
        return privilege.getGrantor() == null ? null : privilege.getGrantor().getName();
    }
}
