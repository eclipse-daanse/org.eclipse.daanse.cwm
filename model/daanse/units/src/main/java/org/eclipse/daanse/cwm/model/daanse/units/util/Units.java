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
import java.math.MathContext;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.AlternateUnit;
import org.eclipse.daanse.cwm.model.daanse.units.BaseUnit;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.LogarithmicUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.PrefixedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.ReferringUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Scale;
import org.eclipse.daanse.cwm.model.daanse.units.ScaledUnit;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitFactor;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;

/**
 * Creates units, one kind each, and converts values between units of the same dimension.
 *
 * <p>Every helper sets the dimension of the unit it creates from the definition: a base unit has
 * the given one, a derived unit the product of the dimensions of its factors (the dimension owned
 * by the same namespace, created there when missing), a unit defined through another unit the
 * dimension of that unit.</p>
 *
 * <p>A unit converts to the coherent base units of its dimension (metre, kilogram, second and so
 * on, and their products) as {@code base = value × factor + term} ({@link Linear}); a logarithmic
 * unit converts to its reference first. Two units of the same dimension are compatible: a value
 * converts from one to the other. Decimal arithmetic uses {@link MathContext#DECIMAL128},
 * logarithms use {@code double}.</p>
 */
public final class Units {

    private static final UnitsFactory F = UnitsFactory.eINSTANCE;

    /** A linear conversion to the coherent base units: {@code base = value × factor + term}. */
    public record Linear(BigDecimal factor, BigDecimal term) {

        /** The conversion of a coherent unit. */
        public static final Linear IDENTITY = new Linear(BigDecimal.ONE, BigDecimal.ZERO);

        /** The value in the base units. */
        public BigDecimal apply(BigDecimal value) {
            return value.multiply(factor, MathContext.DECIMAL128).add(term, MathContext.DECIMAL128);
        }

        /** The value in this unit of a value in the base units. */
        public BigDecimal inverse(BigDecimal base) {
            return base.subtract(term, MathContext.DECIMAL128).divide(factor, MathContext.DECIMAL128);
        }

        /** This conversion, then {@code next}. */
        public Linear then(Linear next) {
            return new Linear(factor.multiply(next.factor, MathContext.DECIMAL128),
                    term.multiply(next.factor, MathContext.DECIMAL128).add(next.term, MathContext.DECIMAL128));
        }
    }

    private Units() {
    }

    /** A base unit of {@code dimension}: metre, kilogram, second. */
    public static BaseUnit base(Namespace owner, String name, String symbol, Dimension dimension) {
        BaseUnit u = named(owner, F.createBaseUnit(), name, symbol);
        u.setDimension(Objects.requireNonNull(dimension, "dimension"));
        return u;
    }

    /** One factor of a derived unit: {@code unit} to the power {@code exponent}. */
    public static UnitFactor factor(Unit unit, long exponent) {
        if (exponent == 0) {
            throw new IllegalArgumentException("the exponent of a factor is not zero");
        }
        UnitFactor f = F.createUnitFactor();
        f.setUnit(Objects.requireNonNull(unit, "unit"));
        f.setExponent(exponent);
        return f;
    }

    /**
     * A derived unit, the product of its factors in the order given: metre per second is
     * {@code derived(owner, "metrePerSecond", "m/s", factor(metre, 1), factor(second, -1))}.
     * Without factors it is the dimensionless unit one.
     */
    public static DerivedUnit derived(Namespace owner, String name, String symbol, UnitFactor... factors) {
        Objects.requireNonNull(factors, "factors");
        long[] dimension = Dimensions.none();
        for (UnitFactor f : factors) {
            Objects.requireNonNull(f, "factor");
            dimension = Dimensions.plus(dimension, Dimensions.exponents(f.getUnit().getDimension()), f.getExponent());
        }
        DerivedUnit u = named(owner, F.createDerivedUnit(), name, symbol);
        u.getFactor().addAll(List.of(factors));
        u.setDimension(Dimensions.ownedIn(owner, dimension));
        return u;
    }

    /** The dimensionless unit one, symbol {@code 1}. */
    public static DerivedUnit one(Namespace owner) {
        return derived(owner, "one", "1");
    }

    /**
     * A unit whose values convert to {@code reference} as {@code value × multiplier}: an hour is
     * second with multiplier 3600.
     */
    public static ScaledUnit scaled(Namespace owner, String name, String symbol, Unit reference, String multiplier) {
        decimal(multiplier, "multiplier");
        ScaledUnit u = referring(owner, F.createScaledUnit(), name, symbol, reference);
        u.setMultiplier(multiplier);
        return u;
    }

    /**
     * A unit whose values convert to {@code reference} as {@code (value + offset) × multiplier}: a
     * degree Celsius is kelvin with multiplier 1 and offset 273.15.
     */
    public static ScaledUnit scaled(Namespace owner, String name, String symbol, Unit reference, String multiplier,
            String offset) {
        decimal(offset, "offset");
        ScaledUnit u = scaled(owner, name, symbol, reference, multiplier);
        u.setOffset(offset);
        return u;
    }

    /** {@code reference} scaled by {@code prefix}, named and symbolized by both: kilo and metre make kilometre, km. */
    public static PrefixedUnit prefixed(Namespace owner, Prefix prefix, Unit reference) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(reference, "reference");
        PrefixedUnit u = referring(owner, F.createPrefixedUnit(), prefix.getName() + reference.getName(),
                prefix.getSymbol() + reference.getSymbol(), reference);
        u.setPrefix(prefix);
        return u;
    }

    /** {@code reference} under a symbol of its own, for another kind of quantity: becquerel is hertz. */
    public static AlternateUnit alternate(Namespace owner, String name, String symbol, Unit reference) {
        return referring(owner, F.createAlternateUnit(), name, symbol, reference);
    }

    /**
     * A logarithmic unit whose values convert to {@code reference} as
     * {@code base ^ (value / divisor)}: decibel-milliwatt is milliwatt with base 10 and divisor 10.
     */
    public static LogarithmicUnit logarithmic(Namespace owner, String name, String symbol, Unit reference,
            String base, String divisor) {
        decimal(base, "base");
        decimal(divisor, "divisor");
        LogarithmicUnit u = referring(owner, F.createLogarithmicUnit(), name, symbol, reference);
        u.setBase(base);
        u.setDivisor(divisor);
        return u;
    }

    /** Sets the UCUM code of {@code unit}. */
    public static <U extends Unit> U ucum(U unit, String code) {
        unit.setUcumCode(Objects.requireNonNull(code, "code"));
        return unit;
    }

    /** Names the kinds of quantity {@code unit} measures. */
    public static <U extends Unit> U applicableTo(U unit, QuantityKind... kinds) {
        for (QuantityKind k : kinds) {
            unit.getQuantityKind().add(Objects.requireNonNull(k, "kind"));
        }
        return unit;
    }

    /**
     * The exponents of the dimension that follows from the definition of {@code unit}: of a base
     * unit its stated dimension, of a derived unit the product of its factors, of a unit defined
     * through another unit that unit's.
     *
     * @throws IllegalArgumentException when a unit is defined through itself
     */
    public static long[] definedDimension(Unit unit) {
        return definedDimension(unit, new HashSet<>());
    }

    private static long[] definedDimension(Unit unit, Set<Unit> visiting) {
        if (!visiting.add(unit)) {
            throw new IllegalArgumentException(unit.getSymbol() + " is defined through itself");
        }
        try {
            if (unit instanceof DerivedUnit d) {
                long[] e = Dimensions.none();
                for (UnitFactor f : d.getFactor()) {
                    e = Dimensions.plus(e, definedDimension(f.getUnit(), visiting), f.getExponent());
                }
                return e;
            }
            if (unit instanceof ReferringUnit r) {
                return definedDimension(r.getReference(), visiting);
            }
            return Dimensions.exponents(unit.getDimension());
        } finally {
            visiting.remove(unit);
        }
    }

    /**
     * The unit {@code unit} is a scaled, prefixed or logarithmic form of: kilometre and mile give
     * metre, degree Celsius gives kelvin; a base, derived or alternate unit is its own.
     */
    public static Unit systemUnit(Unit unit) {
        Unit u = unit;
        for (int steps = 0; steps < 64 && (u instanceof ScaledUnit || u instanceof PrefixedUnit
                || u instanceof LogarithmicUnit); steps++) {
            u = ((ReferringUnit) u).getReference();
        }
        return u;
    }

    /**
     * The linear conversion of {@code unit} to the coherent base units of its dimension.
     *
     * @throws IllegalArgumentException for a logarithmic unit, a product with a factor that has an
     *                                  offset, or a unit defined through itself
     */
    public static Linear toBase(Unit unit) {
        return toBase(unit, new HashSet<>());
    }

    private static Linear toBase(Unit unit, Set<Unit> visiting) {
        if (!visiting.add(unit)) {
            throw new IllegalArgumentException(unit.getSymbol() + " is defined through itself");
        }
        try {
            if (unit instanceof DerivedUnit d) {
                BigDecimal factor = BigDecimal.ONE;
                for (UnitFactor f : d.getFactor()) {
                    Linear l = toBase(f.getUnit(), visiting);
                    if (l.term().signum() != 0) {
                        throw new IllegalArgumentException(f.getUnit().getSymbol()
                                + " has an offset and is no factor of a product");
                    }
                    factor = factor.multiply(power(l.factor(), f.getExponent()), MathContext.DECIMAL128);
                }
                return new Linear(factor, BigDecimal.ZERO);
            }
            if (unit instanceof ScaledUnit s) {
                BigDecimal multiplier = decimal(s.getMultiplier(), "multiplier");
                BigDecimal offset = s.getOffset() == null ? BigDecimal.ZERO : decimal(s.getOffset(), "offset");
                return new Linear(multiplier, offset.multiply(multiplier, MathContext.DECIMAL128))
                        .then(toBase(s.getReference(), visiting));
            }
            if (unit instanceof PrefixedUnit p) {
                return new Linear(Prefixes.factor(p.getPrefix()), BigDecimal.ZERO).then(toBase(p.getReference(), visiting));
            }
            if (unit instanceof AlternateUnit a) {
                return toBase(a.getReference(), visiting);
            }
            if (unit instanceof LogarithmicUnit) {
                throw new IllegalArgumentException(unit.getSymbol() + " is logarithmic, its conversion is not linear");
            }
            return Linear.IDENTITY;
        } finally {
            visiting.remove(unit);
        }
    }

    /** Whether values convert between the two units: they have the same dimension. */
    public static boolean compatible(Unit a, Unit b) {
        return Dimensions.same(a.getDimension(), b.getDimension());
    }

    /** {@code value} in {@code from} as a value in {@code to}; values measured from the zero of the units. */
    public static BigDecimal convert(BigDecimal value, Unit from, Unit to) {
        return convert(value, from, to, Scale.ABSOLUTE);
    }

    /**
     * {@code value} in {@code from} as a value in {@code to}. A {@link Scale#RELATIVE relative}
     * value is a difference of values and converts without the offsets of the units.
     *
     * @throws IllegalArgumentException when the units have different dimensions
     */
    public static BigDecimal convert(BigDecimal value, Unit from, Unit to, Scale scale) {
        Objects.requireNonNull(value, "value");
        if (!compatible(from, to)) {
            throw new IllegalArgumentException(from.getSymbol() + " and " + to.getSymbol()
                    + " have different dimensions: " + Dimensions.code(from.getDimension()) + " and "
                    + Dimensions.code(to.getDimension()));
        }
        return fromBase(toBase(value, from, scale), to, scale);
    }

    private static BigDecimal toBase(BigDecimal value, Unit unit, Scale scale) {
        if (unit instanceof LogarithmicUnit log) {
            relativeIsLinear(log, scale);
            double reference = Math.pow(decimal(log.getBase(), "base").doubleValue(),
                    value.doubleValue() / decimal(log.getDivisor(), "divisor").doubleValue());
            return toBase(BigDecimal.valueOf(reference), log.getReference(), scale);
        }
        Linear l = toBase(unit);
        return scale == Scale.RELATIVE ? value.multiply(l.factor(), MathContext.DECIMAL128) : l.apply(value);
    }

    private static BigDecimal fromBase(BigDecimal base, Unit unit, Scale scale) {
        if (unit instanceof LogarithmicUnit log) {
            relativeIsLinear(log, scale);
            double reference = fromBase(base, log.getReference(), scale).doubleValue();
            double value = decimal(log.getDivisor(), "divisor").doubleValue() * Math.log(reference)
                    / Math.log(decimal(log.getBase(), "base").doubleValue());
            return BigDecimal.valueOf(value);
        }
        Linear l = toBase(unit);
        return scale == Scale.RELATIVE ? base.divide(l.factor(), MathContext.DECIMAL128) : l.inverse(base);
    }

    private static void relativeIsLinear(LogarithmicUnit unit, Scale scale) {
        if (scale == Scale.RELATIVE) {
            throw new IllegalArgumentException(unit.getSymbol() + " is logarithmic: a difference of its values "
                    + "converts to no difference of the reference");
        }
    }

    /** The unit with {@code symbol} that {@code system} owns or allows. */
    public static Optional<Unit> bySymbol(SystemOfUnits system, String symbol) {
        return Stream.concat(of(system).stream(), system.getAllowedUnit().stream())
                .filter(u -> Objects.equals(symbol, u.getSymbol())).findFirst();
    }

    /** The unit named {@code name} that {@code system} owns. */
    public static Optional<Unit> byName(SystemOfUnits system, String name) {
        return of(system).stream().filter(u -> Objects.equals(name, u.getName())).findFirst();
    }

    /** The units {@code system} owns, in order. */
    public static List<Unit> of(SystemOfUnits system) {
        return system.getOwnedElement().stream().filter(Unit.class::isInstance).map(Unit.class::cast).toList();
    }

    /** A decimal literal as a number. */
    static BigDecimal decimal(String literal, String what) {
        if (literal == null) {
            throw new IllegalArgumentException("the " + what + " is missing");
        }
        try {
            return new BigDecimal(literal.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the " + what + " '" + literal + "' is no decimal number", e);
        }
    }

    private static BigDecimal power(BigDecimal factor, long exponent) {
        BigDecimal p = factor.pow((int) Math.abs(exponent), MathContext.DECIMAL128);
        return exponent >= 0 ? p : BigDecimal.ONE.divide(p, MathContext.DECIMAL128);
    }

    private static <U extends Unit> U named(Namespace owner, U unit, String name, String symbol) {
        Objects.requireNonNull(owner, "owner");
        unit.setName(Objects.requireNonNull(name, "name"));
        unit.setSymbol(Objects.requireNonNull(symbol, "symbol"));
        owner.getOwnedElement().add(unit);
        return unit;
    }

    private static <U extends ReferringUnit> U referring(Namespace owner, U unit, String name, String symbol,
            Unit reference) {
        Objects.requireNonNull(reference, "reference");
        U u = named(owner, unit, name, symbol);
        u.setReference(reference);
        u.setDimension(reference.getDimension());
        return u;
    }
}
