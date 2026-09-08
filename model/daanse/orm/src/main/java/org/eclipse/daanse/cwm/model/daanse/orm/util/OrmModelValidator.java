/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.OwnableRelationship;
import org.eclipse.daanse.cwm.model.daanse.orm.RelationshipMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.ValueMapping;
import org.eclipse.emf.ecore.EObject;

/**
 * Programmatische Prüfung der Modell-Invarianten für das {@code orm}-Metamodell.
 *
 * <p>Das Modell selbst ist bewusst OCL-frei (plain mapping model); die semantischen
 * Prüfungen der früheren Pivot-Constraints leben hier: Zielmultiplizität der
 * Beziehungs-Mappings, genau eine Owning-Side je bidirektional gemappter Association
 * und die Namenskonsistenz zwischen Mapping und CWM-Anker. Liefert eine Liste
 * menschenlesbarer Befunde (leer ⇒ gültig).</p>
 */
public final class OrmModelValidator {

    private OrmModelValidator() {
    }

    /** Prüft {@code root} und alle enthaltenen Elemente; gibt die Befunde zurück (leer ⇒ ok). */
    public static List<String> validate(EObject root) {
        List<String> issues = new ArrayList<>();
        Map<Classifier, List<RelationshipMapping>> byAssociation = new HashMap<>();
        collect(root, issues, byAssociation);
        for (Iterator<EObject> it = root.eAllContents(); it.hasNext();) {
            collect(it.next(), issues, byAssociation);
        }
        checkOwningSides(byAssociation, issues);
        return issues;
    }

    private static void collect(EObject o, List<String> issues,
            Map<Classifier, List<RelationshipMapping>> byAssociation) {
        if (o instanceof RelationshipMapping rm) {
            AssociationEnd end = rm.getCwmAssociationEnd();
            if (end != null) {
                checkMultiplicity(rm, end, issues);
                Classifier owner = end.getOwner();
                if (owner != null) {
                    byAssociation.computeIfAbsent(owner, k -> new ArrayList<>()).add(rm);
                }
                checkName(rm, end.getName(), issues);
            }
        } else if (o instanceof ValueMapping vm && vm.getCwmAttribute() != null) {
            checkName(vm, vm.getCwmAttribute().getName(), issues);
        }
    }

    /** Ehemals CwmSingleTargetMultiplicity / CwmManyTargetMultiplicity. */
    private static void checkMultiplicity(RelationshipMapping rm, AssociationEnd end, List<String> issues) {
        Multiplicity mult = end.getMultiplicity();
        if (mult == null || mult.getRange().isEmpty()) {
            return;
        }
        boolean toOne = rm instanceof ManyToOne || rm instanceof OneToOne;
        boolean toMany = rm instanceof OneToMany || rm instanceof ManyToMany;
        for (MultiplicityRange range : mult.getRange()) {
            long upper = range.getUpper();
            if (toOne && upper != 1) {
                issues.add(rm.eClass().getName() + " '" + label(rm)
                        + "': Zielende '" + end.getName() + "' hat obere Multiplizität " + upper
                        + ", ein To-One-Mapping verlangt 1.");
            }
            if (toMany && upper == 1) {
                issues.add(rm.eClass().getName() + " '" + label(rm)
                        + "': Zielende '" + end.getName() + "' hat obere Multiplizität 1, "
                        + "ein To-Many-Mapping verlangt mehr als 1 (oder unbegrenzt).");
            }
        }
    }

    /** Ehemals MappedByMatchesOtherEnd: genau eine Owning-Side je bidirektional gemappter Association. */
    private static void checkOwningSides(Map<Classifier, List<RelationshipMapping>> byAssociation,
            List<String> issues) {
        for (Map.Entry<Classifier, List<RelationshipMapping>> e : byAssociation.entrySet()) {
            List<RelationshipMapping> mappings = e.getValue();
            if (mappings.size() < 2) {
                continue;
            }
            long owners = mappings.stream().filter(OrmModelValidator::isOwningSide).count();
            if (owners != 1) {
                issues.add("Association '" + name(e.getKey()) + "': " + mappings.size()
                        + " Mappings, davon " + owners
                        + " Owning-Sides — genau eine Seite muss die Owning-Side sein.");
            }
        }
    }

    private static boolean isOwningSide(RelationshipMapping rm) {
        if (rm instanceof OwnableRelationship or) {
            return !or.isSetOwningSide() || or.isOwningSide();
        }
        // ManyToOne ist immer die Owning-Side.
        return true;
    }

    /** Namenskonsistenz-Warnung: der informative Mapping-Name folgt dem CWM-Anker. */
    private static void checkName(EObject mapping, String anchorName, List<String> issues) {
        String own = mapping instanceof org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement me
                ? me.getName()
                : null;
        if (own != null && anchorName != null && !own.equals(anchorName)) {
            issues.add(mapping.eClass().getName() + " '" + own
                    + "': Name weicht vom CWM-Anker '" + anchorName
                    + "' ab (Warnung — der Anker ist maßgeblich).");
        }
    }

    private static String label(RelationshipMapping rm) {
        String n = rm.getName();
        if (n != null) {
            return n;
        }
        AssociationEnd end = rm.getCwmAssociationEnd();
        return end != null && end.getName() != null ? end.getName() : "?";
    }

    private static String name(Classifier c) {
        return c.getName() != null ? c.getName() : "?";
    }
}
