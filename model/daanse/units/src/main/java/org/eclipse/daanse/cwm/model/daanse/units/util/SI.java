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

import static org.eclipse.daanse.cwm.model.daanse.units.util.Units.factor;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitFactor;

/**
 * The International System of Units as a catalogue: {@link #create(Namespace)} builds it into a
 * namespace, and the constants name its elements.
 *
 * <p>It holds the 33 quantity kinds of JSR 385, the 24 decimal prefixes, the seven base units and
 * the unit one, the 22 coherent derived units with special names, the units accepted for use with
 * the SI (minute, hour, day, degree, litre, tonne, percent, hectare, gram) and products and
 * prefixed forms common in measurement data (m/s, km/h, mm/h, hPa, kWh, W/m² and others). Symbols,
 * multipliers and UCUM codes follow the SI brochure, QUDT and UCUM; the degree is pi / 180 radian
 * as a decimal approximation, as in QUDT. The kilometre per hour and the kilowatt hour are products
 * of units, so they convert exactly.</p>
 */
public final class SI {

    /** The name of the system. */
    public static final String NAME = "SI";

    /** Base unit of length. */
    public static final String METRE = "metre";
    /** Base unit of mass. */
    public static final String KILOGRAM = "kilogram";
    /** Base unit of time. */
    public static final String SECOND = "second";
    /** Base unit of electric current. */
    public static final String AMPERE = "ampere";
    /** Base unit of thermodynamic temperature. */
    public static final String KELVIN = "kelvin";
    /** Base unit of amount of substance. */
    public static final String MOLE = "mole";
    /** Base unit of luminous intensity. */
    public static final String CANDELA = "candela";
    /** The dimensionless unit, symbol 1. */
    public static final String ONE = "one";
    /** Plane angle. */
    public static final String RADIAN = "radian";
    /** Solid angle. */
    public static final String STERADIAN = "steradian";
    /** Frequency. */
    public static final String HERTZ = "hertz";
    /** Force. */
    public static final String NEWTON = "newton";
    /** Pressure. */
    public static final String PASCAL = "pascal";
    /** Energy. */
    public static final String JOULE = "joule";
    /** Power. */
    public static final String WATT = "watt";
    /** Electric charge. */
    public static final String COULOMB = "coulomb";
    /** Electric potential. */
    public static final String VOLT = "volt";
    /** Capacitance. */
    public static final String FARAD = "farad";
    /** Electric resistance. */
    public static final String OHM = "ohm";
    /** Electric conductance. */
    public static final String SIEMENS = "siemens";
    /** Magnetic flux. */
    public static final String WEBER = "weber";
    /** Magnetic flux density. */
    public static final String TESLA = "tesla";
    /** Inductance. */
    public static final String HENRY = "henry";
    /** Celsius temperature. */
    public static final String DEGREE_CELSIUS = "degreeCelsius";
    /** Luminous flux. */
    public static final String LUMEN = "lumen";
    /** Illuminance. */
    public static final String LUX = "lux";
    /** Activity of a radionuclide. */
    public static final String BECQUEREL = "becquerel";
    /** Absorbed dose. */
    public static final String GRAY = "gray";
    /** Dose equivalent. */
    public static final String SIEVERT = "sievert";
    /** Catalytic activity. */
    public static final String KATAL = "katal";
    /** 60 s. */
    public static final String MINUTE = "minute";
    /** 3600 s. */
    public static final String HOUR = "hour";
    /** 86400 s. */
    public static final String DAY = "day";
    /** Pi / 180 rad. */
    public static final String DEGREE = "degree";
    /** 0.001 m³. */
    public static final String LITRE = "litre";
    /** 1000 kg. */
    public static final String TONNE = "tonne";
    /** 0.01. */
    public static final String PERCENT = "percent";
    /** 10000 m². */
    public static final String HECTARE = "hectare";
    /** 0.001 kg. */
    public static final String GRAM = "gram";
    /** m². */
    public static final String SQUARE_METRE = "squareMetre";
    /** m³. */
    public static final String CUBIC_METRE = "cubicMetre";
    /** m/s. */
    public static final String METRE_PER_SECOND = "metrePerSecond";
    /** m/s². */
    public static final String METRE_PER_SECOND_SQUARED = "metrePerSecondSquared";
    /** km. */
    public static final String KILOMETRE = "kilometre";
    /** cm. */
    public static final String CENTIMETRE = "centimetre";
    /** mm. */
    public static final String MILLIMETRE = "millimetre";
    /** km/h. */
    public static final String KILOMETRE_PER_HOUR = "kilometrePerHour";
    /** mm/h. */
    public static final String MILLIMETRE_PER_HOUR = "millimetrePerHour";
    /** hPa. */
    public static final String HECTOPASCAL = "hectopascal";
    /** kPa. */
    public static final String KILOPASCAL = "kilopascal";
    /** kJ. */
    public static final String KILOJOULE = "kilojoule";
    /** kW. */
    public static final String KILOWATT = "kilowatt";
    /** kWh. */
    public static final String KILOWATT_HOUR = "kilowattHour";
    /** W/m². */
    public static final String WATT_PER_SQUARE_METRE = "wattPerSquareMetre";
    /** mA. */
    public static final String MILLIAMPERE = "milliampere";
    /** mV. */
    public static final String MILLIVOLT = "millivolt";
    /** ms. */
    public static final String MILLISECOND = "millisecond";
    /** mg. */
    public static final String MILLIGRAM = "milligram";
    /** kg/m². */
    public static final String KILOGRAM_PER_SQUARE_METRE = "kilogramPerSquareMetre";

    private SI() {
    }

    /** Builds the SI into {@code owner} and returns it. */
    public static SystemOfUnits create(Namespace owner) {
        SystemOfUnits si = SystemsOfUnits.abbreviation(SystemsOfUnits.create(owner, NAME), "SI");

        QuantityKind length = kind(si, "Length", 1, 0, 0, 0, 0, 0, 0);
        QuantityKind mass = kind(si, "Mass", 0, 1, 0, 0, 0, 0, 0);
        QuantityKind time = kind(si, "Time", 0, 0, 1, 0, 0, 0, 0);
        QuantityKind current = kind(si, "ElectricCurrent", 0, 0, 0, 1, 0, 0, 0);
        QuantityKind temperature = kind(si, "Temperature", 0, 0, 0, 0, 1, 0, 0);
        QuantityKind amount = kind(si, "AmountOfSubstance", 0, 0, 0, 0, 0, 1, 0);
        QuantityKind luminousIntensity = kind(si, "LuminousIntensity", 0, 0, 0, 0, 0, 0, 1);
        QuantityKind dimensionless = kind(si, "Dimensionless", 0, 0, 0, 0, 0, 0, 0);
        QuantityKind angle = kind(si, "Angle", 0, 0, 0, 0, 0, 0, 0);
        QuantityKind solidAngle = kind(si, "SolidAngle", 0, 0, 0, 0, 0, 0, 0);
        QuantityKind area = kind(si, "Area", 2, 0, 0, 0, 0, 0, 0);
        QuantityKind volume = kind(si, "Volume", 3, 0, 0, 0, 0, 0, 0);
        QuantityKind speed = kind(si, "Speed", 1, 0, -1, 0, 0, 0, 0);
        QuantityKind acceleration = kind(si, "Acceleration", 1, 0, -2, 0, 0, 0, 0);
        QuantityKind frequency = kind(si, "Frequency", 0, 0, -1, 0, 0, 0, 0);
        QuantityKind force = kind(si, "Force", 1, 1, -2, 0, 0, 0, 0);
        QuantityKind pressure = kind(si, "Pressure", -1, 1, -2, 0, 0, 0, 0);
        QuantityKind energy = kind(si, "Energy", 2, 1, -2, 0, 0, 0, 0);
        QuantityKind power = kind(si, "Power", 2, 1, -3, 0, 0, 0, 0);
        QuantityKind charge = kind(si, "ElectricCharge", 0, 0, 1, 1, 0, 0, 0);
        QuantityKind potential = kind(si, "ElectricPotential", 2, 1, -3, -1, 0, 0, 0);
        QuantityKind capacitance = kind(si, "ElectricCapacitance", -2, -1, 4, 2, 0, 0, 0);
        QuantityKind resistance = kind(si, "ElectricResistance", 2, 1, -3, -2, 0, 0, 0);
        QuantityKind conductance = kind(si, "ElectricConductance", -2, -1, 3, 2, 0, 0, 0);
        QuantityKind magneticFlux = kind(si, "MagneticFlux", 2, 1, -2, -1, 0, 0, 0);
        QuantityKind fluxDensity = kind(si, "MagneticFluxDensity", 0, 1, -2, -1, 0, 0, 0);
        QuantityKind inductance = kind(si, "ElectricInductance", 2, 1, -2, -2, 0, 0, 0);
        QuantityKind luminousFlux = kind(si, "LuminousFlux", 0, 0, 0, 0, 0, 0, 1);
        QuantityKind illuminance = kind(si, "Illuminance", -2, 0, 0, 0, 0, 0, 1);
        QuantityKind radioactivity = kind(si, "Radioactivity", 0, 0, -1, 0, 0, 0, 0);
        QuantityKind absorbedDose = kind(si, "RadiationDoseAbsorbed", 2, 0, -2, 0, 0, 0, 0);
        QuantityKind effectiveDose = kind(si, "RadiationDoseEffective", 2, 0, -2, 0, 0, 0, 0);
        QuantityKind catalyticActivity = kind(si, "CatalyticActivity", 0, 0, -1, 0, 0, 1, 0);

        String[][] prefixes = { { "quetta", "Q", "30" }, { "ronna", "R", "27" }, { "yotta", "Y", "24" },
                { "zetta", "Z", "21" }, { "exa", "E", "18" }, { "peta", "P", "15" }, { "tera", "T", "12" },
                { "giga", "G", "9" }, { "mega", "M", "6" }, { "kilo", "k", "3" }, { "hecto", "h", "2" },
                { "deca", "da", "1" }, { "deci", "d", "-1" }, { "centi", "c", "-2" }, { "milli", "m", "-3" },
                { "micro", "µ", "-6" }, { "nano", "n", "-9" }, { "pico", "p", "-12" }, { "femto", "f", "-15" },
                { "atto", "a", "-18" }, { "zepto", "z", "-21" }, { "yocto", "y", "-24" }, { "ronto", "r", "-27" },
                { "quecto", "q", "-30" } };
        for (String[] p : prefixes) {
            Prefixes.create(si, p[0], p[1], 10, Long.parseLong(p[2]));
        }
        Prefix kilo = prefix(si, "kilo");
        Prefix hecto = prefix(si, "hecto");
        Prefix centi = prefix(si, "centi");
        Prefix milli = prefix(si, "milli");

        Unit metre = base(si, METRE, "m", Dimensions.LENGTH, length);
        Unit kilogram = base(si, KILOGRAM, "kg", Dimensions.MASS, mass);
        Unit second = base(si, SECOND, "s", Dimensions.TIME, time);
        Unit ampere = base(si, AMPERE, "A", Dimensions.ELECTRIC_CURRENT, current);
        Unit kelvin = base(si, KELVIN, "K", Dimensions.TEMPERATURE, temperature);
        Unit mole = base(si, MOLE, "mol", Dimensions.AMOUNT_OF_SUBSTANCE, amount);
        Unit candela = base(si, CANDELA, "cd", Dimensions.LUMINOUS_INTENSITY, luminousIntensity);
        Unit one = kinds(Units.ucum(Units.one(si), "1"), dimensionless);

        Unit radian = kinds(Units.ucum(Units.alternate(si, RADIAN, "rad", one), "rad"), angle);
        Unit steradian = kinds(Units.ucum(Units.alternate(si, STERADIAN, "sr", one), "sr"), solidAngle);
        Unit hertz = derived(si, HERTZ, "Hz", "Hz", frequency, factor(second, -1));
        Unit newton = derived(si, NEWTON, "N", "N", force, factor(kilogram, 1), factor(metre, 1), factor(second, -2));
        Unit pascal = derived(si, PASCAL, "Pa", "Pa", pressure, factor(newton, 1), factor(metre, -2));
        Unit joule = derived(si, JOULE, "J", "J", energy, factor(newton, 1), factor(metre, 1));
        Unit watt = derived(si, WATT, "W", "W", power, factor(joule, 1), factor(second, -1));
        Unit coulomb = derived(si, COULOMB, "C", "C", charge, factor(ampere, 1), factor(second, 1));
        Unit volt = derived(si, VOLT, "V", "V", potential, factor(watt, 1), factor(ampere, -1));
        derived(si, FARAD, "F", "F", capacitance, factor(coulomb, 1), factor(volt, -1));
        derived(si, OHM, "Ω", "Ohm", resistance, factor(volt, 1), factor(ampere, -1));
        derived(si, SIEMENS, "S", "S", conductance, factor(ampere, 1), factor(volt, -1));
        Unit weber = derived(si, WEBER, "Wb", "Wb", magneticFlux, factor(volt, 1), factor(second, 1));
        derived(si, TESLA, "T", "T", fluxDensity, factor(weber, 1), factor(metre, -2));
        derived(si, HENRY, "H", "H", inductance, factor(weber, 1), factor(ampere, -1));
        kinds(Units.ucum(Units.scaled(si, DEGREE_CELSIUS, "°C", kelvin, "1", "273.15"), "Cel"), temperature);
        Unit lumen = derived(si, LUMEN, "lm", "lm", luminousFlux, factor(candela, 1), factor(steradian, 1));
        derived(si, LUX, "lx", "lx", illuminance, factor(lumen, 1), factor(metre, -2));
        kinds(Units.ucum(Units.alternate(si, BECQUEREL, "Bq", hertz), "Bq"), radioactivity);
        Unit gray = derived(si, GRAY, "Gy", "Gy", absorbedDose, factor(joule, 1), factor(kilogram, -1));
        kinds(Units.ucum(Units.alternate(si, SIEVERT, "Sv", gray), "Sv"), effectiveDose);
        derived(si, KATAL, "kat", "kat", catalyticActivity, factor(mole, 1), factor(second, -1));

        Unit squareMetre = derived(si, SQUARE_METRE, "m²", "m2", area, factor(metre, 2));
        Unit cubicMetre = derived(si, CUBIC_METRE, "m³", "m3", volume, factor(metre, 3));
        scaled(si, MINUTE, "min", "min", time, second, "60");
        Unit hour = scaled(si, HOUR, "h", "h", time, second, "3600");
        scaled(si, DAY, "d", "d", time, second, "86400");
        scaled(si, DEGREE, "°", "deg", angle, radian, "0.0174532925199433");
        scaled(si, LITRE, "L", "L", volume, cubicMetre, "0.001");
        scaled(si, TONNE, "t", "t", mass, kilogram, "1000");
        scaled(si, PERCENT, "%", "%", dimensionless, one, "0.01");
        scaled(si, HECTARE, "ha", "har", area, squareMetre, "10000");
        Unit gram = scaled(si, GRAM, "g", "g", mass, kilogram, "0.001");

        derived(si, METRE_PER_SECOND, "m/s", "m/s", speed, factor(metre, 1), factor(second, -1));
        derived(si, METRE_PER_SECOND_SQUARED, "m/s²", "m/s2", acceleration, factor(metre, 1), factor(second, -2));
        Unit kilometre = prefixed(si, kilo, metre, "km", length);
        prefixed(si, centi, metre, "cm", length);
        Unit millimetre = prefixed(si, milli, metre, "mm", length);
        derived(si, KILOMETRE_PER_HOUR, "km/h", "km/h", speed, factor(kilometre, 1), factor(hour, -1));
        derived(si, MILLIMETRE_PER_HOUR, "mm/h", "mm/h", speed, factor(millimetre, 1), factor(hour, -1));
        prefixed(si, hecto, pascal, "hPa", pressure);
        prefixed(si, kilo, pascal, "kPa", pressure);
        prefixed(si, kilo, joule, "kJ", energy);
        Unit kilowatt = prefixed(si, kilo, watt, "kW", power);
        derived(si, KILOWATT_HOUR, "kWh", "kW.h", energy, factor(kilowatt, 1), factor(hour, 1));
        derived(si, WATT_PER_SQUARE_METRE, "W/m²", "W/m2", null, factor(watt, 1), factor(metre, -2));
        prefixed(si, milli, ampere, "mA", current);
        prefixed(si, milli, volt, "mV", potential);
        prefixed(si, milli, second, "ms", time);
        prefixed(si, milli, gram, "mg", mass);
        derived(si, KILOGRAM_PER_SQUARE_METRE, "kg/m²", "kg/m2", null, factor(kilogram, 1), factor(metre, -2));
        return si;
    }

    /** The unit named {@code name} of a system built by {@link #create(Namespace)}. */
    public static Unit unit(SystemOfUnits si, String name) {
        return Units.byName(si, name).orElseThrow(() -> new IllegalArgumentException("no unit " + name + " in "
                + si.getName()));
    }

    /** The quantity kind named {@code name} of a system built by {@link #create(Namespace)}. */
    public static QuantityKind kind(SystemOfUnits si, String name) {
        return si.getOwnedElement().stream().filter(QuantityKind.class::isInstance).map(QuantityKind.class::cast)
                .filter(k -> Objects.equals(name, k.getName())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no quantity kind " + name + " in " + si.getName()));
    }

    /** The prefix named {@code name} of a system built by {@link #create(Namespace)}. */
    public static Prefix prefix(SystemOfUnits si, String name) {
        return si.getOwnedElement().stream().filter(Prefix.class::isInstance).map(Prefix.class::cast)
                .filter(p -> Objects.equals(name, p.getName())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no prefix " + name + " in " + si.getName()));
    }

    private static QuantityKind kind(SystemOfUnits si, String name, long length, long mass, long time,
            long current, long temperature, long amount, long luminousIntensity) {
        Dimension d = Dimensions.ownedIn(si,
                new long[] { length, mass, time, current, temperature, amount, luminousIntensity });
        return QuantityKinds.create(si, name, d);
    }

    private static Unit base(SystemOfUnits si, String name, String symbol, int position, QuantityKind kind) {
        Unit u = Units.base(si, name, symbol, Dimensions.ownedIn(si, Dimensions.base(position, 1)));
        return kinds(Units.ucum(u, symbol), kind);
    }

    private static Unit derived(SystemOfUnits si, String name, String symbol, String ucum, QuantityKind kind,
            UnitFactor... factors) {
        return kinds(Units.ucum(Units.derived(si, name, symbol, factors), ucum), kind);
    }

    private static Unit scaled(SystemOfUnits si, String name, String symbol, String ucum, QuantityKind kind,
            Unit reference, String multiplier) {
        return kinds(Units.ucum(Units.scaled(si, name, symbol, reference, multiplier), ucum), kind);
    }

    private static Unit prefixed(SystemOfUnits si, Prefix prefix, Unit reference, String ucum, QuantityKind kind) {
        return kinds(Units.ucum(Units.prefixed(si, prefix, reference), ucum), kind);
    }

    private static Unit kinds(Unit unit, QuantityKind kind) {
        return kind == null ? unit : Units.applicableTo(unit, kind);
    }
}
