/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.examples;

import static org.eclipse.daanse.cwm.model.daanse.units.util.Units.factor;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.util.Dimensions;
import org.eclipse.daanse.cwm.model.daanse.units.util.Prefixes;
import org.eclipse.daanse.cwm.model.daanse.units.util.SystemsOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.Units;

/**
 * A system of units of a project, next to the SI: the units of the sensor examples, with the
 * symbols their source files use — {@code F} for degree Fahrenheit, {@code kg/m2} and
 * {@code kJ/m2} in plain ASCII, {@code dBm} for a power level relative to one milliwatt.
 *
 * <p>The system is self-contained: its own base units, prefixes and dimensions, so a model that
 * uses it needs nothing else. The symbol of a unit is what a converter writes, so the symbols are
 * those of the sources, character for character.</p>
 */
public final class ExampleUnits {

    /** The name of the system. */
    public static final String NAME = "fennec-units";

    private ExampleUnits() {
    }

    /** Builds the system into {@code owner} and returns it. */
    public static SystemOfUnits create(Namespace owner) {
        SystemOfUnits units = SystemsOfUnits.create(owner, NAME);
        Prefix milli = Prefixes.create(units, "milli", "m", 10, -3);
        Prefix kilo = Prefixes.create(units, "kilo", "k", 10, 3);

        Unit metre = Units.ucum(base(units, "metre", "m", Dimensions.LENGTH), "m");
        Unit kilogram = Units.ucum(base(units, "kilogram", "kg", Dimensions.MASS), "kg");
        Unit second = Units.ucum(base(units, "second", "s", Dimensions.TIME), "s");
        Unit ampere = Units.ucum(base(units, "ampere", "A", Dimensions.ELECTRIC_CURRENT), "A");
        Unit kelvin = Units.ucum(base(units, "kelvin", "K", Dimensions.TEMPERATURE), "K");
        Unit one = Units.ucum(Units.one(units), "1");

        Units.ucum(Units.scaled(units, "percent", "%", one, "0.01"), "%");
        Unit radian = Units.ucum(Units.alternate(units, "radian", "rad", one), "rad");
        Units.ucum(Units.scaled(units, "degree", "°", radian, "0.0174532925199433"), "deg");
        Units.ucum(Units.scaled(units, "degreeCelsius", "°C", kelvin, "1", "273.15"), "Cel");
        Units.ucum(Units.scaled(units, "degreeFahrenheit", "F", kelvin, "0.5555555555555556", "459.67"), "[degF]");

        Unit pascal = Units.ucum(Units.derived(units, "pascal", "Pa", factor(kilogram, 1), factor(metre, -1),
                factor(second, -2)), "Pa");
        Unit joule = Units.ucum(Units.derived(units, "joule", "J", factor(kilogram, 1), factor(metre, 2),
                factor(second, -2)), "J");
        Unit kilojoule = Units.ucum(Units.prefixed(units, kilo, joule), "kJ");
        Units.ucum(Units.derived(units, "kilojoulePerSquareMetre", "kJ/m2", factor(kilojoule, 1), factor(metre, -2)),
                "kJ/m2");
        Unit watt = Units.ucum(Units.derived(units, "watt", "W", factor(joule, 1), factor(second, -1)), "W");
        Unit milliwatt = Units.ucum(Units.prefixed(units, milli, watt), "mW");
        Units.logarithmic(units, "decibelMilliwatt", "dBm", milliwatt, "10", "10");
        Units.ucum(Units.derived(units, "volt", "V", factor(watt, 1), factor(ampere, -1)), "V");
        Units.ucum(Units.derived(units, "metrePerSecond", "m/s", factor(metre, 1), factor(second, -1)), "m/s");
        Unit millimetre = Units.ucum(Units.prefixed(units, milli, metre), "mm");
        Unit hour = Units.ucum(Units.scaled(units, "hour", "h", second, "3600"), "h");
        Units.ucum(Units.derived(units, "millimetrePerHour", "mm/h", factor(millimetre, 1), factor(hour, -1)), "mm/h");
        Units.ucum(Units.derived(units, "kilogramPerSquareMetre", "kg/m2", factor(kilogram, 1), factor(metre, -2)),
                "kg/m2");
        return units;
    }

    /** The unit with {@code symbol} of a system built by {@link #create(Namespace)}. */
    public static Unit unit(SystemOfUnits units, String symbol) {
        return Units.bySymbol(units, symbol)
                .orElseThrow(() -> new IllegalArgumentException("no unit " + symbol + " in " + units.getName()));
    }

    private static Unit base(SystemOfUnits units, String name, String symbol, int position) {
        return Units.base(units, name, symbol, Dimensions.ownedIn(units, Dimensions.base(position, 1)));
    }
}
