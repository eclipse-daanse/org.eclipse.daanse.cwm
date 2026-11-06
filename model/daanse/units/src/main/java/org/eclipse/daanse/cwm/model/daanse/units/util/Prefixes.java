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

import java.math.BigDecimal;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;

/** Creates prefixes and computes their factor, base to the power of exponent. */
public final class Prefixes {

    private Prefixes() {
    }

    /** A prefix owned by {@code owner}: kilo is {@code create(owner, "kilo", "k", 10, 3)}. */
    public static Prefix create(Namespace owner, String name, String symbol, long base, long exponent) {
        Objects.requireNonNull(owner, "owner");
        Prefix p = UnitsFactory.eINSTANCE.createPrefix();
        p.setName(Objects.requireNonNull(name, "name"));
        p.setSymbol(Objects.requireNonNull(symbol, "symbol"));
        p.setBase(base);
        p.setExponent(exponent);
        owner.getOwnedElement().add(p);
        return p;
    }

    /** The exact factor of a prefix: 1000 for kilo, 0.001 for milli, 1024 for kibi. */
    public static BigDecimal factor(Prefix prefix) {
        BigDecimal base = BigDecimal.valueOf(prefix.getBase());
        long exponent = prefix.getExponent();
        return exponent >= 0 ? base.pow((int) exponent) : BigDecimal.ONE.divide(base.pow((int) -exponent));
    }
}
