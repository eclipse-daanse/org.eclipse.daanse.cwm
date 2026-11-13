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
import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.line;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;

/**
 * How two catalogs differ, in both directions: the schemas only one of them holds, what differs in
 * the schemas both hold, and the same for the parties that hold privileges (roles, users,
 * {@code PUBLIC}). What {@link CatalogDiffer} produces.
 *
 * @param left              the first catalog, such as the model that was written
 * @param right             the second catalog, such as the model read back
 * @param schemasOnlyLeft   schemas only the first catalog holds
 * @param schemasOnlyRight  schemas only the second catalog holds
 * @param schemas           the diffs of the schemas both hold that differ, left as old, right as new
 * @param partiesOnlyLeft   parties only the first catalog holds
 * @param partiesOnlyRight  parties only the second catalog holds; empty under
 *                          {@link Tolerance#IGNORE_EXTRA_PARTIES}
 * @param parties           the parties both hold that differ
 */
public record CatalogDiff(Catalog left, Catalog right, List<Schema> schemasOnlyLeft, List<Schema> schemasOnlyRight,
        List<SchemaDiff> schemas, List<ResponsibleParty> partiesOnlyLeft, List<ResponsibleParty> partiesOnlyRight,
        List<PartyDiff> parties) {

    public CatalogDiff {
        schemasOnlyLeft = List.copyOf(schemasOnlyLeft);
        schemasOnlyRight = List.copyOf(schemasOnlyRight);
        schemas = List.copyOf(schemas);
        partiesOnlyLeft = List.copyOf(partiesOnlyLeft);
        partiesOnlyRight = List.copyOf(partiesOnlyRight);
        parties = List.copyOf(parties);
    }

    /** {@code true} iff the catalogs hold the same, as far as the settings of the comparison count. */
    public boolean isEmpty() {
        return schemasOnlyLeft.isEmpty() && schemasOnlyRight.isEmpty() && schemas.isEmpty()
                && partiesOnlyLeft.isEmpty() && partiesOnlyRight.isEmpty() && parties.isEmpty();
    }

    /**
     * One readable line per difference, sorted, so the same models give the same lines:
     * {@code <kind> <qualified name>: <difference>}, the difference {@code only left},
     * {@code only right} or {@code <aspect> <left value> left, <right value> right}, as in
     * {@code column shop.sales.customer.name: size (100) left, (50) right}. Empty iff
     * {@link #isEmpty()}.
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        schemasOnlyLeft.forEach(s -> lines.add(line(s, ONLY_LEFT)));
        schemasOnlyRight.forEach(s -> lines.add(line(s, ONLY_RIGHT)));
        schemas.forEach(s -> lines.addAll(s.lines()));
        partiesOnlyLeft.forEach(p -> lines.add(Lines.kind(p) + " " + p.getName() + ": " + ONLY_LEFT));
        partiesOnlyRight.forEach(p -> lines.add(Lines.kind(p) + " " + p.getName() + ": " + ONLY_RIGHT));
        parties.forEach(p -> lines.addAll(p.lines()));
        return lines.stream().sorted().toList();
    }
}
