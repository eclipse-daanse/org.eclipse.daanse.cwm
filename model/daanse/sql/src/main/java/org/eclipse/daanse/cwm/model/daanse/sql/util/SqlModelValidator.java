/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sql.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.sql.ColumnReference;
import org.eclipse.daanse.cwm.model.daanse.sql.InPredicate;
import org.eclipse.daanse.cwm.model.daanse.sql.MatchPredicate;
import org.eclipse.daanse.cwm.model.daanse.sql.JoinTypeKind;
import org.eclipse.daanse.cwm.model.daanse.sql.UniquePredicate;
import org.eclipse.daanse.cwm.model.daanse.sql.select.Join;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SetOperation;
import org.eclipse.emf.ecore.EObject;

/**
 * Programmatische Prüfung der Modell-Invarianten für das {@code sql}-Metamodell.
 *
 * <p>Spiegelt die deklarativen Pivot-OCL-Constraints aus der {@code .ecore}, ist aber ohne
 * OCL-Runtime auswertbar. Liefert eine Liste menschenlesbarer Befunde (leer ⇒ gültig).</p>
 */
public final class SqlModelValidator {

    private SqlModelValidator() {
    }

    /** Prüft {@code root} und alle enthaltenen Elemente; gibt die Befunde zurück (leer ⇒ ok). */
    public static List<String> validate(EObject root) {
        List<String> issues = new ArrayList<>();
        check(root, issues);
        for (Iterator<EObject> it = root.eAllContents(); it.hasNext();) {
            check(it.next(), issues);
        }
        return issues;
    }

    private static void check(EObject o, List<String> issues) {
        if (o instanceof Join j) {
            if (j.getJoinType() == JoinTypeKind.CROSS && j.getCondition() != null) {
                issues.add("Join: ein CROSS-Verbund darf keine condition tragen.");
            }
        } else if (o instanceof ColumnReference c) {
            boolean resolved = c.getColumn() != null;
            boolean named = c.getQualifiedName() != null;
            if (resolved == named) {
                issues.add("ColumnReference: genau eines von column (aufgelöst) oder qualifiedName "
                        + "(namensbasiert) muss gesetzt sein.");
            }
        } else if (o instanceof InPredicate in) {
            if (in.getSubquery() != null && !in.getList().isEmpty()) {
                issues.add("InPredicate: entweder list ODER subquery, nicht beides.");
            }
        } else if (o instanceof MatchPredicate m) {
            if (m.getSubquery() == null) {
                issues.add("MatchPredicate: subquery fehlt.");
            }
        } else if (o instanceof UniquePredicate u) {
            if (u.getSubquery() == null) {
                issues.add("UniquePredicate: subquery fehlt.");
            }
        } else if (o instanceof SetOperation s) {
            if (s.getLeft() == null || s.getRight() == null) {
                issues.add("SetOperation: left und right müssen gesetzt sein.");
            }
        }
    }
}
