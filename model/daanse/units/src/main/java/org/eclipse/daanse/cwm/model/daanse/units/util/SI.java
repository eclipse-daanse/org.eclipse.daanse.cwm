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

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitFactor;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMIResource;

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
 *
 * <p>The SI is also a standard set that other model files import, the file {@value #SET_FILE} beside this class
 * under the URI {@value #SET_URI}: {@link #standardSet()} builds it, {@link #load(ResourceSet)} reads it from the
 * jar, and a model file refers to a unit as {@code href="<SET_URI>#<id>"}. The id of every element is the UUID stated
 * beside it here, of a dimension in a table by the code of its exponents. {@link #create(Namespace)} gives the
 * elements the same ids where the owner lies in an XMI resource.</p>
 */
public final class SI {

    /** The name of the system. */
    public static final String NAME = "SI";

    /** The URI of the standard set of the SI, under which a model file refers to its units. */
    public static final String SET_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.units/si.xmi";

    /** The file of the standard set, beside this class in the jar. */
    public static final String SET_FILE = "si.xmi";

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

    /** The id of each dimension of the SI, by the code of its exponents: a dimension is made where a kind needs it. */
    private static final Map<String, String> DIMENSIONS = Map.ofEntries(
            Map.entry("A0E0L1I0M0H0T0D0", "_LdylEOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I0M1H0T0D0", "_LdylEeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I0M0H0T1D0", "_LdylEuUIEfGl4IEUgfF4xA"),
            Map.entry("A0E1L0I0M0H0T0D0", "_LdylE-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I0M0H1T0D0", "_LdylFOUIEfGl4IEUgfF4xA"),
            Map.entry("A1E0L0I0M0H0T0D0", "_LdylFeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I1M0H0T0D0", "_LdylFuUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I0M0H0T0D1", "_LdylF-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L2I0M0H0T0D0", "_LdylGOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L3I0M0H0T0D0", "_LdylGeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L1I0M0H0T-1D0", "_LdylGuUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L1I0M0H0T-2D0", "_LdylG-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I0M0H0T-1D0", "_LdylHOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L1I0M1H0T-2D0", "_LdylHeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L-1I0M1H0T-2D0", "_LdylHuUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L2I0M1H0T-2D0", "_LdylH-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L2I0M1H0T-3D0", "_LdylIOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E1L0I0M0H0T1D0", "_LdylIeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E-1L2I0M1H0T-3D0", "_LdylIuUIEfGl4IEUgfF4xA"),
            Map.entry("A0E2L-2I0M-1H0T4D0", "_LdylI-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E-2L2I0M1H0T-3D0", "_LdylJOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E2L-2I0M-1H0T3D0", "_LdylJeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E-1L2I0M1H0T-2D0", "_LdylJuUIEfGl4IEUgfF4xA"),
            Map.entry("A0E-1L0I0M1H0T-2D0", "_LdylJ-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E-2L2I0M1H0T-2D0", "_LdylKOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L-2I1M0H0T0D0", "_LdylKeUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L2I0M0H0T-2D0", "_LdylKuUIEfGl4IEUgfF4xA"),
            Map.entry("A1E0L0I0M0H0T-1D0", "_LdylK-UIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L0I0M1H0T-3D0", "_LdylLOUIEfGl4IEUgfF4xA"),
            Map.entry("A0E0L-2I0M1H0T0D0", "_LdylLeUIEfGl4IEUgfF4xA"));

    private SI() {
    }

    /** Builds the SI into {@code owner} and returns it. */
    public static SystemOfUnits create(Namespace owner) {
        return build(SystemsOfUnits.create(owner, NAME));
    }

    /** The standard set of the SI, built in memory with its ids, the system of units its root. */
    public static XMIResource standardSet() {
        XMIResource set = StandardSets.create(SET_URI);
        SystemOfUnits si = UnitsFactory.eINSTANCE.createSystemOfUnits();
        si.setName(NAME);
        set.getContents().add(si);
        build(si);
        return set;
    }

    /**
     * Maps the standard set in {@code resourceSet} to its file in the jar: a model file there that refers to
     * its units reads them from the jar ({@link StandardSets#map}).
     */
    public static void map(ResourceSet resourceSet) {
        StandardSets.map(resourceSet, SET_URI, SI.class.getResource(SET_FILE), CorePackage.eINSTANCE,
                UnitsPackage.eINSTANCE);
    }

    /**
     * The SI of the standard set in {@code resourceSet}, mapped there and read from the jar where it is not read
     * yet.
     */
    public static SystemOfUnits load(ResourceSet resourceSet) {
        map(resourceSet);
        return StandardSets.load(resourceSet, SET_URI, SystemOfUnits.class);
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

    /** Builds the SI into {@code si}, every element with the id stated beside it. */
    private static SystemOfUnits build(SystemOfUnits si) {
        StandardSets.id(SystemsOfUnits.abbreviation(si, "SI"), "_LdylLuUIEfGl4IEUgfF4xA");

        QuantityKind length = kind(si, "_LdylL-UIEfGl4IEUgfF4xA", "Length", 1, 0, 0, 0, 0, 0, 0);
        QuantityKind mass = kind(si, "_LdylMOUIEfGl4IEUgfF4xA", "Mass", 0, 1, 0, 0, 0, 0, 0);
        QuantityKind time = kind(si, "_LdylMeUIEfGl4IEUgfF4xA", "Time", 0, 0, 1, 0, 0, 0, 0);
        QuantityKind current = kind(si, "_LdylMuUIEfGl4IEUgfF4xA", "ElectricCurrent", 0, 0, 0, 1, 0, 0, 0);
        QuantityKind temperature = kind(si, "_LdylM-UIEfGl4IEUgfF4xA", "Temperature", 0, 0, 0, 0, 1, 0, 0);
        QuantityKind amount = kind(si, "_LdylNOUIEfGl4IEUgfF4xA", "AmountOfSubstance", 0, 0, 0, 0, 0, 1, 0);
        QuantityKind luminousIntensity = kind(si, "_LdylNeUIEfGl4IEUgfF4xA", "LuminousIntensity", 0, 0, 0, 0, 0, 0, 1);
        QuantityKind dimensionless = kind(si, "_LdylNuUIEfGl4IEUgfF4xA", "Dimensionless", 0, 0, 0, 0, 0, 0, 0);
        QuantityKind angle = kind(si, "_LdylN-UIEfGl4IEUgfF4xA", "Angle", 0, 0, 0, 0, 0, 0, 0);
        QuantityKind solidAngle = kind(si, "_LdylOOUIEfGl4IEUgfF4xA", "SolidAngle", 0, 0, 0, 0, 0, 0, 0);
        QuantityKind area = kind(si, "_LdzMIOUIEfGl4IEUgfF4xA", "Area", 2, 0, 0, 0, 0, 0, 0);
        QuantityKind volume = kind(si, "_LdzMIeUIEfGl4IEUgfF4xA", "Volume", 3, 0, 0, 0, 0, 0, 0);
        QuantityKind speed = kind(si, "_LdzMIuUIEfGl4IEUgfF4xA", "Speed", 1, 0, -1, 0, 0, 0, 0);
        QuantityKind acceleration = kind(si, "_LdzMI-UIEfGl4IEUgfF4xA", "Acceleration", 1, 0, -2, 0, 0, 0, 0);
        QuantityKind frequency = kind(si, "_LdzMJOUIEfGl4IEUgfF4xA", "Frequency", 0, 0, -1, 0, 0, 0, 0);
        QuantityKind force = kind(si, "_LdzMJeUIEfGl4IEUgfF4xA", "Force", 1, 1, -2, 0, 0, 0, 0);
        QuantityKind pressure = kind(si, "_LdzMJuUIEfGl4IEUgfF4xA", "Pressure", -1, 1, -2, 0, 0, 0, 0);
        QuantityKind energy = kind(si, "_LdzMJ-UIEfGl4IEUgfF4xA", "Energy", 2, 1, -2, 0, 0, 0, 0);
        QuantityKind power = kind(si, "_LdzMKOUIEfGl4IEUgfF4xA", "Power", 2, 1, -3, 0, 0, 0, 0);
        QuantityKind charge = kind(si, "_LdzMKeUIEfGl4IEUgfF4xA", "ElectricCharge", 0, 0, 1, 1, 0, 0, 0);
        QuantityKind potential = kind(si, "_LdzMKuUIEfGl4IEUgfF4xA", "ElectricPotential", 2, 1, -3, -1, 0, 0, 0);
        QuantityKind capacitance = kind(si, "_LdzMK-UIEfGl4IEUgfF4xA", "ElectricCapacitance", -2, -1, 4, 2, 0, 0, 0);
        QuantityKind resistance = kind(si, "_LdzMLOUIEfGl4IEUgfF4xA", "ElectricResistance", 2, 1, -3, -2, 0, 0, 0);
        QuantityKind conductance = kind(si, "_LdzMLeUIEfGl4IEUgfF4xA", "ElectricConductance", -2, -1, 3, 2, 0, 0, 0);
        QuantityKind magneticFlux = kind(si, "_LdzMLuUIEfGl4IEUgfF4xA", "MagneticFlux", 2, 1, -2, -1, 0, 0, 0);
        QuantityKind fluxDensity = kind(si, "_LdzML-UIEfGl4IEUgfF4xA", "MagneticFluxDensity", 0, 1, -2, -1, 0, 0, 0);
        QuantityKind inductance = kind(si, "_LdzMMOUIEfGl4IEUgfF4xA", "ElectricInductance", 2, 1, -2, -2, 0, 0, 0);
        QuantityKind luminousFlux = kind(si, "_LdzMMeUIEfGl4IEUgfF4xA", "LuminousFlux", 0, 0, 0, 0, 0, 0, 1);
        QuantityKind illuminance = kind(si, "_LdzMMuUIEfGl4IEUgfF4xA", "Illuminance", -2, 0, 0, 0, 0, 0, 1);
        QuantityKind radioactivity = kind(si, "_LdzMM-UIEfGl4IEUgfF4xA", "Radioactivity", 0, 0, -1, 0, 0, 0, 0);
        QuantityKind absorbedDose = kind(si, "_LdzMNOUIEfGl4IEUgfF4xA", "RadiationDoseAbsorbed", 2, 0, -2, 0, 0, 0, 0);
        QuantityKind effectiveDose = kind(si, "_LdzMNeUIEfGl4IEUgfF4xA", "RadiationDoseEffective", 2, 0, -2, 0, 0, 0,
                0);
        QuantityKind catalyticActivity = kind(si, "_LdzMNuUIEfGl4IEUgfF4xA", "CatalyticActivity", 0, 0, -1, 0, 0, 1, 0);

        // id, name, symbol, exponent of 10
        String[][] prefixes = { { "_LdzMN-UIEfGl4IEUgfF4xA", "quetta", "Q", "30" },
                { "_LdzMOOUIEfGl4IEUgfF4xA", "ronna", "R", "27" }, { "_LdzMOeUIEfGl4IEUgfF4xA", "yotta", "Y", "24" },
                { "_LdzMOuUIEfGl4IEUgfF4xA", "zetta", "Z", "21" }, { "_LdzMO-UIEfGl4IEUgfF4xA", "exa", "E", "18" },
                { "_LdzMPOUIEfGl4IEUgfF4xA", "peta", "P", "15" }, { "_LdzMPeUIEfGl4IEUgfF4xA", "tera", "T", "12" },
                { "_LdzMPuUIEfGl4IEUgfF4xA", "giga", "G", "9" }, { "_LdzMP-UIEfGl4IEUgfF4xA", "mega", "M", "6" },
                { "_LdzMQOUIEfGl4IEUgfF4xA", "kilo", "k", "3" }, { "_LdzMQeUIEfGl4IEUgfF4xA", "hecto", "h", "2" },
                { "_LdzMQuUIEfGl4IEUgfF4xA", "deca", "da", "1" }, { "_LdzMQ-UIEfGl4IEUgfF4xA", "deci", "d", "-1" },
                { "_LdzMROUIEfGl4IEUgfF4xA", "centi", "c", "-2" }, { "_LdzMReUIEfGl4IEUgfF4xA", "milli", "m", "-3" },
                { "_LdzMRuUIEfGl4IEUgfF4xA", "micro", "µ", "-6" }, { "_LdzMR-UIEfGl4IEUgfF4xA", "nano", "n", "-9" },
                { "_LdzMSOUIEfGl4IEUgfF4xA", "pico", "p", "-12" }, { "_LdzMSeUIEfGl4IEUgfF4xA", "femto", "f", "-15" },
                { "_LdzMSuUIEfGl4IEUgfF4xA", "atto", "a", "-18" }, { "_LdzMS-UIEfGl4IEUgfF4xA", "zepto", "z", "-21" },
                { "_LdzMTOUIEfGl4IEUgfF4xA", "yocto", "y", "-24" }, { "_LdzMTeUIEfGl4IEUgfF4xA", "ronto", "r", "-27" },
                { "_LdzMTuUIEfGl4IEUgfF4xA", "quecto", "q", "-30" } };
        for (String[] p : prefixes) {
            StandardSets.id(Prefixes.create(si, p[1], p[2], 10, Long.parseLong(p[3])), p[0]);
        }
        Prefix kilo = prefix(si, "kilo");
        Prefix hecto = prefix(si, "hecto");
        Prefix centi = prefix(si, "centi");
        Prefix milli = prefix(si, "milli");

        Unit metre = base(si, "_LdzMT-UIEfGl4IEUgfF4xA", METRE, "m", Dimensions.LENGTH, length);
        Unit kilogram = base(si, "_LdzMUOUIEfGl4IEUgfF4xA", KILOGRAM, "kg", Dimensions.MASS, mass);
        Unit second = base(si, "_LdzMUeUIEfGl4IEUgfF4xA", SECOND, "s", Dimensions.TIME, time);
        Unit ampere = base(si, "_LdzMUuUIEfGl4IEUgfF4xA", AMPERE, "A", Dimensions.ELECTRIC_CURRENT, current);
        Unit kelvin = base(si, "_LdzMU-UIEfGl4IEUgfF4xA", KELVIN, "K", Dimensions.TEMPERATURE, temperature);
        Unit mole = base(si, "_LdzMVOUIEfGl4IEUgfF4xA", MOLE, "mol", Dimensions.AMOUNT_OF_SUBSTANCE, amount);
        Unit candela = base(si, "_LdzMVeUIEfGl4IEUgfF4xA", CANDELA, "cd", Dimensions.LUMINOUS_INTENSITY,
                luminousIntensity);
        Unit one = kinds("_LdzMVuUIEfGl4IEUgfF4xA", Units.ucum(Units.one(si), "1"), dimensionless);

        Unit radian = kinds("_LdzMV-UIEfGl4IEUgfF4xA", Units.ucum(Units.alternate(si, RADIAN, "rad", one), "rad"),
                angle);
        Unit steradian = kinds("_LdzMWOUIEfGl4IEUgfF4xA", Units.ucum(Units.alternate(si, STERADIAN, "sr", one), "sr"),
                solidAngle);
        Unit hertz = derived(si, "_LdzMWeUIEfGl4IEUgfF4xA", HERTZ, "Hz", "Hz", frequency,
                factor("_LdzMWuUIEfGl4IEUgfF4xA", second, -1));
        Unit newton = derived(si, "_LdzMW-UIEfGl4IEUgfF4xA", NEWTON, "N", "N", force,
                factor("_LdzMXOUIEfGl4IEUgfF4xA", kilogram, 1), factor("_LdzMXeUIEfGl4IEUgfF4xA", metre, 1),
                factor("_LdzMXuUIEfGl4IEUgfF4xA", second, -2));
        Unit pascal = derived(si, "_LdzMX-UIEfGl4IEUgfF4xA", PASCAL, "Pa", "Pa", pressure,
                factor("_LdzMYOUIEfGl4IEUgfF4xA", newton, 1), factor("_LdzMYeUIEfGl4IEUgfF4xA", metre, -2));
        Unit joule = derived(si, "_LdzMYuUIEfGl4IEUgfF4xA", JOULE, "J", "J", energy,
                factor("_LdzMY-UIEfGl4IEUgfF4xA", newton, 1), factor("_LdzMZOUIEfGl4IEUgfF4xA", metre, 1));
        Unit watt = derived(si, "_LdzMZeUIEfGl4IEUgfF4xA", WATT, "W", "W", power,
                factor("_LdzMZuUIEfGl4IEUgfF4xA", joule, 1), factor("_LdzMZ-UIEfGl4IEUgfF4xA", second, -1));
        Unit coulomb = derived(si, "_LdzMaOUIEfGl4IEUgfF4xA", COULOMB, "C", "C", charge,
                factor("_LdzMaeUIEfGl4IEUgfF4xA", ampere, 1), factor("_LdzMauUIEfGl4IEUgfF4xA", second, 1));
        Unit volt = derived(si, "_LdzMa-UIEfGl4IEUgfF4xA", VOLT, "V", "V", potential,
                factor("_LdzMbOUIEfGl4IEUgfF4xA", watt, 1), factor("_LdzMbeUIEfGl4IEUgfF4xA", ampere, -1));
        derived(si, "_LdzMbuUIEfGl4IEUgfF4xA", FARAD, "F", "F", capacitance,
                factor("_LdzMb-UIEfGl4IEUgfF4xA", coulomb, 1), factor("_LdzMcOUIEfGl4IEUgfF4xA", volt, -1));
        derived(si, "_LdzMceUIEfGl4IEUgfF4xA", OHM, "Ω", "Ohm", resistance, factor("_LdzMcuUIEfGl4IEUgfF4xA", volt, 1),
                factor("_LdzzMOUIEfGl4IEUgfF4xA", ampere, -1));
        derived(si, "_LdzzMeUIEfGl4IEUgfF4xA", SIEMENS, "S", "S", conductance,
                factor("_LdzzMuUIEfGl4IEUgfF4xA", ampere, 1), factor("_LdzzM-UIEfGl4IEUgfF4xA", volt, -1));
        Unit weber = derived(si, "_LdzzNOUIEfGl4IEUgfF4xA", WEBER, "Wb", "Wb", magneticFlux,
                factor("_LdzzNeUIEfGl4IEUgfF4xA", volt, 1), factor("_LdzzNuUIEfGl4IEUgfF4xA", second, 1));
        derived(si, "_LdzzN-UIEfGl4IEUgfF4xA", TESLA, "T", "T", fluxDensity,
                factor("_LdzzOOUIEfGl4IEUgfF4xA", weber, 1), factor("_LdzzOeUIEfGl4IEUgfF4xA", metre, -2));
        derived(si, "_LdzzOuUIEfGl4IEUgfF4xA", HENRY, "H", "H", inductance, factor("_LdzzO-UIEfGl4IEUgfF4xA", weber, 1),
                factor("_LdzzPOUIEfGl4IEUgfF4xA", ampere, -1));
        kinds("_LdzzPeUIEfGl4IEUgfF4xA",
                Units.ucum(Units.scaled(si, DEGREE_CELSIUS, "°C", kelvin, "1", "273.15"), "Cel"), temperature);
        Unit lumen = derived(si, "_LdzzPuUIEfGl4IEUgfF4xA", LUMEN, "lm", "lm", luminousFlux,
                factor("_LdzzP-UIEfGl4IEUgfF4xA", candela, 1), factor("_LdzzQOUIEfGl4IEUgfF4xA", steradian, 1));
        derived(si, "_LdzzQeUIEfGl4IEUgfF4xA", LUX, "lx", "lx", illuminance,
                factor("_LdzzQuUIEfGl4IEUgfF4xA", lumen, 1), factor("_LdzzQ-UIEfGl4IEUgfF4xA", metre, -2));
        kinds("_LdzzROUIEfGl4IEUgfF4xA", Units.ucum(Units.alternate(si, BECQUEREL, "Bq", hertz), "Bq"), radioactivity);
        Unit gray = derived(si, "_LdzzReUIEfGl4IEUgfF4xA", GRAY, "Gy", "Gy", absorbedDose,
                factor("_LdzzRuUIEfGl4IEUgfF4xA", joule, 1), factor("_LdzzR-UIEfGl4IEUgfF4xA", kilogram, -1));
        kinds("_LdzzSOUIEfGl4IEUgfF4xA", Units.ucum(Units.alternate(si, SIEVERT, "Sv", gray), "Sv"), effectiveDose);
        derived(si, "_LdzzSeUIEfGl4IEUgfF4xA", KATAL, "kat", "kat", catalyticActivity,
                factor("_LdzzSuUIEfGl4IEUgfF4xA", mole, 1), factor("_LdzzS-UIEfGl4IEUgfF4xA", second, -1));

        Unit squareMetre = derived(si, "_LdzzTOUIEfGl4IEUgfF4xA", SQUARE_METRE, "m²", "m2", area,
                factor("_LdzzTeUIEfGl4IEUgfF4xA", metre, 2));
        Unit cubicMetre = derived(si, "_Ld0aQOUIEfGl4IEUgfF4xA", CUBIC_METRE, "m³", "m3", volume,
                factor("_Ld0aQeUIEfGl4IEUgfF4xA", metre, 3));
        scaled(si, "_Ld0aQuUIEfGl4IEUgfF4xA", MINUTE, "min", "min", time, second, "60");
        Unit hour = scaled(si, "_Ld0aQ-UIEfGl4IEUgfF4xA", HOUR, "h", "h", time, second, "3600");
        scaled(si, "_Ld0aROUIEfGl4IEUgfF4xA", DAY, "d", "d", time, second, "86400");
        scaled(si, "_Ld0aReUIEfGl4IEUgfF4xA", DEGREE, "°", "deg", angle, radian, "0.0174532925199433");
        scaled(si, "_Ld0aRuUIEfGl4IEUgfF4xA", LITRE, "L", "L", volume, cubicMetre, "0.001");
        scaled(si, "_Ld0aR-UIEfGl4IEUgfF4xA", TONNE, "t", "t", mass, kilogram, "1000");
        scaled(si, "_Ld0aSOUIEfGl4IEUgfF4xA", PERCENT, "%", "%", dimensionless, one, "0.01");
        scaled(si, "_Ld0aSeUIEfGl4IEUgfF4xA", HECTARE, "ha", "har", area, squareMetre, "10000");
        Unit gram = scaled(si, "_Ld0aSuUIEfGl4IEUgfF4xA", GRAM, "g", "g", mass, kilogram, "0.001");

        derived(si, "_Ld0aS-UIEfGl4IEUgfF4xA", METRE_PER_SECOND, "m/s", "m/s", speed,
                factor("_Ld0aTOUIEfGl4IEUgfF4xA", metre, 1), factor("_Ld0aTeUIEfGl4IEUgfF4xA", second, -1));
        derived(si, "_Ld0aTuUIEfGl4IEUgfF4xA", METRE_PER_SECOND_SQUARED, "m/s²", "m/s2", acceleration,
                factor("_Ld0aT-UIEfGl4IEUgfF4xA", metre, 1), factor("_Ld0aUOUIEfGl4IEUgfF4xA", second, -2));
        Unit kilometre = prefixed(si, "_Ld0aUeUIEfGl4IEUgfF4xA", kilo, metre, "km", length);
        prefixed(si, "_Ld0aUuUIEfGl4IEUgfF4xA", centi, metre, "cm", length);
        Unit millimetre = prefixed(si, "_Ld0aU-UIEfGl4IEUgfF4xA", milli, metre, "mm", length);
        derived(si, "_Ld0aVOUIEfGl4IEUgfF4xA", KILOMETRE_PER_HOUR, "km/h", "km/h", speed,
                factor("_Ld0aVeUIEfGl4IEUgfF4xA", kilometre, 1), factor("_Ld0aVuUIEfGl4IEUgfF4xA", hour, -1));
        derived(si, "_Ld0aV-UIEfGl4IEUgfF4xA", MILLIMETRE_PER_HOUR, "mm/h", "mm/h", speed,
                factor("_Ld0aWOUIEfGl4IEUgfF4xA", millimetre, 1), factor("_Ld0aWeUIEfGl4IEUgfF4xA", hour, -1));
        prefixed(si, "_Ld0aWuUIEfGl4IEUgfF4xA", hecto, pascal, "hPa", pressure);
        prefixed(si, "_Ld0aW-UIEfGl4IEUgfF4xA", kilo, pascal, "kPa", pressure);
        prefixed(si, "_Ld0aXOUIEfGl4IEUgfF4xA", kilo, joule, "kJ", energy);
        Unit kilowatt = prefixed(si, "_Ld0aXeUIEfGl4IEUgfF4xA", kilo, watt, "kW", power);
        derived(si, "_Ld0aXuUIEfGl4IEUgfF4xA", KILOWATT_HOUR, "kWh", "kW.h", energy,
                factor("_Ld0aX-UIEfGl4IEUgfF4xA", kilowatt, 1), factor("_Ld0aYOUIEfGl4IEUgfF4xA", hour, 1));
        derived(si, "_Ld0aYeUIEfGl4IEUgfF4xA", WATT_PER_SQUARE_METRE, "W/m²", "W/m2", null,
                factor("_Ld0aYuUIEfGl4IEUgfF4xA", watt, 1), factor("_Ld0aY-UIEfGl4IEUgfF4xA", metre, -2));
        prefixed(si, "_Ld0aZOUIEfGl4IEUgfF4xA", milli, ampere, "mA", current);
        prefixed(si, "_Ld0aZeUIEfGl4IEUgfF4xA", milli, volt, "mV", potential);
        prefixed(si, "_Ld0aZuUIEfGl4IEUgfF4xA", milli, second, "ms", time);
        prefixed(si, "_Ld0aZ-UIEfGl4IEUgfF4xA", milli, gram, "mg", mass);
        derived(si, "_Ld0aaOUIEfGl4IEUgfF4xA", KILOGRAM_PER_SQUARE_METRE, "kg/m²", "kg/m2", null,
                factor("_Ld0aaeUIEfGl4IEUgfF4xA", kilogram, 1), factor("_Ld0aauUIEfGl4IEUgfF4xA", metre, -2));

        for (ModelElement element : si.getOwnedElement()) {
            if (element instanceof Dimension d) {
                String code = Dimensions.code(d);
                StandardSets.id(d, Objects.requireNonNull(DIMENSIONS.get(code),
                        () -> "no id is stated for the dimension " + code));
            }
        }
        return si;
    }

    /** A factor of a product of units, with its id. */
    private record Factor(String id, Unit unit, long exponent) {
    }

    private static Factor factor(String id, Unit unit, long exponent) {
        return new Factor(id, unit, exponent);
    }

    private static QuantityKind kind(SystemOfUnits si, String id, String name, long length, long mass, long time,
            long current, long temperature, long amount, long luminousIntensity) {
        Dimension d = Dimensions.ownedIn(si,
                new long[] { length, mass, time, current, temperature, amount, luminousIntensity });
        return StandardSets.id(QuantityKinds.create(si, name, d), id);
    }

    private static Unit base(SystemOfUnits si, String id, String name, String symbol, int position,
            QuantityKind kind) {
        Unit u = Units.base(si, name, symbol, Dimensions.ownedIn(si, Dimensions.base(position, 1)));
        return kinds(id, Units.ucum(u, symbol), kind);
    }

    private static Unit derived(SystemOfUnits si, String id, String name, String symbol, String ucum,
            QuantityKind kind, Factor... factors) {
        DerivedUnit u = Units.derived(si, name, symbol,
                Stream.of(factors).map(f -> Units.factor(f.unit(), f.exponent())).toArray(UnitFactor[]::new));
        for (int i = 0; i < factors.length; i++) {
            StandardSets.id(u.getFactor().get(i), factors[i].id());
        }
        return kinds(id, Units.ucum(u, ucum), kind);
    }

    private static Unit scaled(SystemOfUnits si, String id, String name, String symbol, String ucum,
            QuantityKind kind, Unit reference, String multiplier) {
        return kinds(id, Units.ucum(Units.scaled(si, name, symbol, reference, multiplier), ucum), kind);
    }

    private static Unit prefixed(SystemOfUnits si, String id, Prefix prefix, Unit reference, String ucum,
            QuantityKind kind) {
        return kinds(id, Units.ucum(Units.prefixed(si, prefix, reference), ucum), kind);
    }

    /** {@code unit} with the id {@code id}, applicable to {@code kind} where it is not {@code null}. */
    private static Unit kinds(String id, Unit unit, QuantityKind kind) {
        return StandardSets.id(kind == null ? unit : Units.applicableTo(unit, kind), id);
    }
}
