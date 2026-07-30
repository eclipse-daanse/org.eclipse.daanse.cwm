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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.emf.ecore.EObject;

/**
 * Programmatische Prüfung der Modell-Invarianten für das {@code privilege}-Metamodell.
 *
 * <p>Spiegelt die deklarativen Pivot-OCL-Constraints aus der {@code .ecore}: jedes
 * {@code Privilege} gehört einer {@code ResponsibleParty} (Grantee = namespace), die
 * Pflicht-Ziele sind gesetzt, keine Selbstmitgliedschaft. Zusätzlich, was OCL nicht gut
 * kann: die Zyklusfreiheit des Mitgliedschaftsgraphen der {@code RolePrivilege}-Kanten.
 * Liefert eine Liste menschenlesbarer Befunde (leer ⇒ gültig).</p>
 */
public final class PrivilegeModelValidator {

    private PrivilegeModelValidator() {
    }

    /** Prüft {@code root} und alle enthaltenen Elemente; gibt die Befunde zurück (leer ⇒ ok). */
    public static List<String> validate(EObject root) {
        return validateAll(List.of(root));
    }

    /** Prüft alle {@code roots} samt Inhalt (z. B. die Parties eines Loader-Ergebnisses). */
    public static List<String> validateAll(Iterable<? extends EObject> roots) {
        List<String> issues = new ArrayList<>();
        Map<Namespace, List<ResponsibleParty>> membershipEdges = new HashMap<>();
        for (EObject root : roots) {
            check(root, issues, membershipEdges);
            for (Iterator<EObject> it = root.eAllContents(); it.hasNext();) {
                check(it.next(), issues, membershipEdges);
            }
        }
        checkMembershipGraph(membershipEdges, issues);
        return issues;
    }

    private static void check(EObject o, List<String> issues, Map<Namespace, List<ResponsibleParty>> edges) {
        if (o instanceof Privilege privilege
                && !(privilege.getNamespace() instanceof ResponsibleParty)) {
            issues.add("Privilege '" + privilege.getName()
                    + "': Grantee ist die besitzende ResponsibleParty — namespace muss eine "
                    + "ResponsibleParty sein.");
        }
        if (o instanceof TablePrivilege tp) {
            if (tp.getTable() == null) {
                issues.add("TablePrivilege '" + tp.getName() + "': table ist Pflicht.");
            } else {
                for (var column : tp.getColumn()) {
                    if (!tp.getTable().getFeature().contains(column)) {
                        issues.add("TablePrivilege '" + tp.getName() + "': Spalte '" + column.getName()
                                + "' gehoert nicht zur Zieltabelle '" + tp.getTable().getName() + "'.");
                    }
                }
            }
        }
        if (o instanceof ProcedurePrivilege pp && pp.getProcedure() == null) {
            issues.add("ProcedurePrivilege '" + pp.getName() + "': procedure ist Pflicht.");
        }
        if (o instanceof RolePrivilege rp) {
            if (rp.getRole() == null) {
                issues.add("RolePrivilege '" + rp.getName() + "': role ist Pflicht.");
            } else if (rp.getRole() == rp.getNamespace()) {
                issues.add("RolePrivilege '" + rp.getName() + "': eine Rolle kann nicht sich selbst erben.");
            } else if (rp.getNamespace() != null) {
                edges.computeIfAbsent(rp.getNamespace(), n -> new ArrayList<>()).add(rp.getRole());
            }
        }
    }

    /** Zyklusfreiheit des Mitgliedschaftsgraphen (Kanten: Grantee → RolePrivilege.role). */
    private static void checkMembershipGraph(Map<Namespace, List<ResponsibleParty>> parents,
            List<String> issues) {
        Set<Namespace> done = new HashSet<>();
        Set<Namespace> inProgress = new HashSet<>();
        for (Namespace start : parents.keySet()) {
            if (hasCycle(start, parents, done, inProgress)) {
                issues.add("Mitgliedschaftsgraph enthaelt einen Zyklus (erreichbar von '"
                        + start.getName() + "').");
                return;
            }
        }
    }

    private static boolean hasCycle(Namespace node, Map<Namespace, List<ResponsibleParty>> parents,
            Set<Namespace> done, Set<Namespace> inProgress) {
        if (done.contains(node)) {
            return false;
        }
        if (!inProgress.add(node)) {
            return true;
        }
        for (ResponsibleParty parent : parents.getOrDefault(node, List.of())) {
            if (hasCycle(parent, parents, done, inProgress)) {
                return true;
            }
        }
        inProgress.remove(node);
        done.add(node);
        return false;
    }
}
