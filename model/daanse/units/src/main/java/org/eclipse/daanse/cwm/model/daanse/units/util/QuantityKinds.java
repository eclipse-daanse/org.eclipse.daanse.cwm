/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsPackage;

/** Creates quantity kinds and finds the units that measure them. */
public final class QuantityKinds {

    private QuantityKinds() {
    }

    /** A kind of quantity owned by {@code owner}, of {@code dimension}. */
    public static QuantityKind create(Namespace owner, String name, Dimension dimension) {
        Objects.requireNonNull(owner, "owner");
        QuantityKind k = UnitsFactory.eINSTANCE.createQuantityKind();
        k.setName(Objects.requireNonNull(name, "name"));
        k.setDimension(Objects.requireNonNull(dimension, "dimension"));
        owner.getOwnedElement().add(k);
        return k;
    }

    /** Makes {@code kind} a special case of {@code broader}, a kind of the same dimension. */
    public static QuantityKind broader(QuantityKind kind, QuantityKind broader) {
        kind.setBroader(Objects.requireNonNull(broader, "broader"));
        return kind;
    }

    /** Sets the symbol of {@code kind}, e.g. l for length. */
    public static QuantityKind symbol(QuantityKind kind, String symbol) {
        kind.setSymbol(Objects.requireNonNull(symbol, "symbol"));
        return kind;
    }

    /** The units that name {@code kind} as one of the kinds they measure. */
    public static List<Unit> units(QuantityKind kind) {
        return InverseReferences.referencingList(kind, UnitsPackage.Literals.UNIT__QUANTITY_KIND, Unit.class);
    }

    /** Whether {@code kind} is {@code other} or narrower than it. */
    public static boolean isA(QuantityKind kind, QuantityKind other) {
        int steps = 0;
        for (QuantityKind k = kind; k != null && steps++ < 64; k = k.getBroader()) {
            if (k == other) {
                return true;
            }
        }
        return false;
    }
}
