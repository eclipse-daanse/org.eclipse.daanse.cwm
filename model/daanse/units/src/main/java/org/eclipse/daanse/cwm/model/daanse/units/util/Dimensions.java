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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;

/**
 * Creates, finds and compares dimensions. A dimension is the vector of the exponents of the seven
 * base dimensions, in the order of the features of {@link Dimension}: length, mass, time,
 * electric current, thermodynamic temperature, amount of substance, luminous intensity.
 *
 * <p>Its code is the QUDT dimension vector: the letters A (amount of substance), E (electric
 * current), L (length), I (luminous intensity), M (mass), H (thermodynamic temperature) and T
 * (time), each followed by its exponent, then D1 for a dimensionless quantity and D0 otherwise —
 * speed is {@code A0E0L1I0M0H0T-1D0}. A dimension that {@link #ownedIn} creates is named by its
 * code.</p>
 */
public final class Dimensions {

    /** Position of the length exponent in an exponent vector. */
    public static final int LENGTH = 0;
    /** Position of the mass exponent. */
    public static final int MASS = 1;
    /** Position of the time exponent. */
    public static final int TIME = 2;
    /** Position of the electric current exponent. */
    public static final int ELECTRIC_CURRENT = 3;
    /** Position of the thermodynamic temperature exponent. */
    public static final int TEMPERATURE = 4;
    /** Position of the amount of substance exponent. */
    public static final int AMOUNT_OF_SUBSTANCE = 5;
    /** Position of the luminous intensity exponent. */
    public static final int LUMINOUS_INTENSITY = 6;

    private Dimensions() {
    }

    /** A dimension owned by {@code owner}. */
    public static Dimension create(Namespace owner, String name, long length, long mass, long time,
            long electricCurrent, long temperature, long amountOfSubstance, long luminousIntensity) {
        Objects.requireNonNull(owner, "owner");
        Dimension d = UnitsFactory.eINSTANCE.createDimension();
        d.setName(Objects.requireNonNull(name, "name"));
        d.setLength(length);
        d.setMass(mass);
        d.setTime(time);
        d.setElectricCurrent(electricCurrent);
        d.setThermodynamicTemperature(temperature);
        d.setAmountOfSubstance(amountOfSubstance);
        d.setLuminousIntensity(luminousIntensity);
        owner.getOwnedElement().add(d);
        return d;
    }

    /**
     * The dimension with these exponents that {@code owner} owns, else a new one owned by it and
     * named by its code.
     */
    public static Dimension ownedIn(Namespace owner, long... exponents) {
        Objects.requireNonNull(owner, "owner");
        long[] e = checked(exponents);
        return find(owner, e).orElseGet(() -> create(owner, code(e), e[LENGTH], e[MASS], e[TIME],
                e[ELECTRIC_CURRENT], e[TEMPERATURE], e[AMOUNT_OF_SUBSTANCE], e[LUMINOUS_INTENSITY]));
    }

    /** The dimension with these exponents that {@code owner} owns. */
    public static Optional<Dimension> find(Namespace owner, long... exponents) {
        long[] e = checked(exponents);
        return owner.getOwnedElement().stream().filter(Dimension.class::isInstance).map(Dimension.class::cast)
                .filter(d -> Arrays.equals(exponents(d), e)).findFirst();
    }

    /** A vector with one exponent at {@code position}, e.g. {@code base(LENGTH, 1)}. */
    public static long[] base(int position, long exponent) {
        long[] e = new long[7];
        e[position] = exponent;
        return e;
    }

    /** The vector of a dimensionless quantity: all exponents zero. */
    public static long[] none() {
        return new long[7];
    }

    /** The exponents of a dimension, in the order of its features. */
    public static long[] exponents(Dimension d) {
        return new long[] { d.getLength(), d.getMass(), d.getTime(), d.getElectricCurrent(),
                d.getThermodynamicTemperature(), d.getAmountOfSubstance(), d.getLuminousIntensity() };
    }

    /** {@code a + power × b}: the vector of a product of units. */
    public static long[] plus(long[] a, long[] b, long power) {
        long[] r = checked(a).clone();
        long[] c = checked(b);
        for (int i = 0; i < 7; i++) {
            r[i] += power * c[i];
        }
        return r;
    }

    /** Whether two dimensions have the same exponents. */
    public static boolean same(Dimension a, Dimension b) {
        return a != null && b != null && Arrays.equals(exponents(a), exponents(b));
    }

    /** Whether all exponents are zero. */
    public static boolean isDimensionless(Dimension d) {
        return Arrays.equals(exponents(d), none());
    }

    /** The QUDT dimension vector of a dimension. */
    public static String code(Dimension d) {
        return code(exponents(d));
    }

    /** The QUDT dimension vector of exponents, e.g. {@code A0E0L1I0M0H0T-1D0} for speed. */
    public static String code(long... exponents) {
        long[] e = checked(exponents);
        return "A" + e[AMOUNT_OF_SUBSTANCE] + "E" + e[ELECTRIC_CURRENT] + "L" + e[LENGTH] + "I"
                + e[LUMINOUS_INTENSITY] + "M" + e[MASS] + "H" + e[TEMPERATURE] + "T" + e[TIME] + "D"
                + (Arrays.equals(e, none()) ? 1 : 0);
    }

    private static long[] checked(long[] exponents) {
        Objects.requireNonNull(exponents, "exponents");
        if (exponents.length != 7) {
            throw new IllegalArgumentException("a dimension has 7 exponents, not " + exponents.length);
        }
        return exponents;
    }
}
