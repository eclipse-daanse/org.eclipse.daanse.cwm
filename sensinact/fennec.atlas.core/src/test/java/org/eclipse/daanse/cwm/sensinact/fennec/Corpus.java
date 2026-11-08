/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.SensinactBindings;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactStructure;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.examples.ExampleUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;

/**
 * The corpus: the CWM examples of {@code model/daanse/mapping.sensinact}, with the units and the lifecycle
 * rules Fennec's originals carry, the Ecore files of their source models, the options, and Fennec's
 * files to compare with ({@code src/test/resources}). The units are those of the example system of
 * {@code model/daanse/units}, which writes them as the originals do; the quantities live in a
 * package {@code units} next to the rules.
 */
enum Corpus {

    /** Dragino LSE01 and EM310-UDL onto one UNIFIED provider. */
    BATTERY("battery", List.of("lorawan-uplink", "dragino-message", "em310udl-message"), Options.defaults(),
            List.of("expected/dragino-battery-mapping.xmi", "expected/em310udl-battery-mapping.xmi")),
    /** The Dragino mapping alone, without profile. */
    MINIMAL("battery", List.of("lorawan-uplink", "dragino-message", "em310udl-message"),
            Options.defaults().withProfiles(false), List.of("expected/dragino-battery-mapping_no-profile.xmi")),
    /** A weather station with recording and retention rules and a fixed location. */
    ECOWITT("ecowitt", List.of("ecowitt"), Options.defaults().withProfiles(false),
            List.of("expected/EcoWittPersistenceMapping.xmi")),
    /** Two services on two reports of a collection, every attribute a resource, rules per resource and service. */
    WEATHER("weather", List.of("dwd-weather"), Options.defaults().withProfiles(false),
            List.of("expected/WeatherReportsProviderMapping.xmi")),
    /** One service per counting line, picked by key. */
    IOTRAFFIC("iotraffic", List.of("iotraffic"), Options.defaults(), List.of("expected/IotrafficCounterMapping.xmi")),
    /** Two vendors onto one provider with optional parts; CWM type names mapped by the options. */
    TEMPERATURE("temperature", List.of("vendora", "vendorb"),
            Options.defaults().withDataTypes(Map.of("Float", "EFloat", "Integer", "EInt")),
            List.of("expected/VendorATemperatureSensor.xmi", "expected/VendorBTemperatureSensor.xmi"));

    final String example;
    final List<String> ecore;
    final Options options;
    final List<String> expected;

    Corpus(String example, List<String> ecore, Options options, List<String> expected) {
        this.example = example;
        this.ecore = ecore;
        this.options = options;
        this.expected = expected;
    }

    /** The test resources. */
    static Path resources() {
        return Path.of(System.getProperty("user.dir"), "src/test/resources");
    }

    /** The Ecore files of the source models. */
    List<Path> ecoreFiles() {
        return ecore.stream().map(n -> resources().resolve("ecore/" + n + ".ecore")).toList();
    }

    /** A fresh example with its rules, and the mappings of this case. */
    Examples.Case build() {
        Examples.Case c = switch (example) {
            case "battery" -> battery();
            case "ecowitt" -> ecowitt();
            case "weather" -> weather();
            case "iotraffic" -> Examples.iotraffic();
            default -> temperature();
        };
        return c;
    }

    /** The mappings to convert. */
    List<ProviderMapping> mappings(Examples.Case c) {
        return this == MINIMAL ? List.of(c.mapping("dragino-battery-sensor")) : c.mappings();
    }

    /** The package {@code units} of a case, with the example system of units. */
    static SystemOfUnits units(Examples.Case c) {
        return ExampleUnits.create(ClassModels.pkg(c.model(), "units"));
    }

    /** A quantity in the unit of {@code symbol}, named like the first resource, next to the system. */
    static void measure(SystemOfUnits units, String symbol, Resource... resources) {
        Quantities.create(units.getNamespace(), resources[0].getName(), ExampleUnits.unit(units, symbol), resources);
    }

    private static Examples.Case battery() {
        Examples.Case c = Examples.battery();
        measure(units(c), "V", c.resource("battery-sensor", "battery", "level"));
        return c;
    }

    private static Examples.Case ecowitt() {
        Examples.Case c = Examples.ecowitt();
        Package rules = ClassModels.pkg(c.model(), "rules");
        var temperature = c.resource("ecowitt-weather", "weather", "temperature");
        var humidity = c.resource("ecowitt-weather", "weather", "humidity");
        var rain = c.resource("ecowitt-weather", "weather", "rainRate");
        RecordingRules.relative(rules, "pct-5", 5.0, temperature);
        RecordingRules.absolute(rules, "abs-2", 2.0, humidity);
        RecordingRules.throttle(rules, "throttle-10m", Duration.ofMinutes(10), rain);
        RetentionSchedules.cleanupEvery(RetentionSchedules.values(rules, "keep-1y", "P365D", temperature),
                Duration.ofDays(7));
        RetentionSchedules.cleanupEvery(RetentionSchedules.values(rules, "keep-90d", "P90D", humidity, rain),
                Duration.ofDays(1));
        SystemOfUnits units = units(c);
        measure(units, "F", temperature);
        measure(units, "%", humidity);
        measure(units, "mm/h", rain);
        return c;
    }

    private static Examples.Case weather() {
        Examples.Case c = Examples.weather();
        Package rules = ClassModels.pkg(c.model(), "rules");
        var current = c.service("dwd-weather", "currentWeather");
        RecordingRules.relative(rules, "pct-10", 10.0, c.resource("dwd-weather", "currentWeather", "windSpeed"));
        RecordingRules.throttle(rules, "throttle-5m", Duration.ofMinutes(5),
                c.resource("dwd-weather", "currentWeather", "tempAboveSurface5"));
        RecordingRules.sampling(rules, "every-3", 3, c.resource("dwd-weather", "currentWeather", "cloudCoverTotal"));
        RecordingRules.throttle(rules, "throttle-1m", Duration.ofMinutes(1), current);
        RetentionSchedules.cleanupEvery(RetentionSchedules.values(rules, "keep-90d", "P90D", current),
                Duration.ofDays(1));
        SystemOfUnits units = units(c);
        for (Resource r : SensinactStructure.resources(current)) {
            String symbol = MOSMIX_UNITS.get(r.getName());
            if (symbol != null) {
                measure(units, symbol, r, c.resource("dwd-weather", "forecast3H", r.getName()));
            }
        }
        return c;
    }

    /** The unit of every value of a MOSMIX report, as the source model of Fennec's original gives it. */
    private static final Map<String, String> MOSMIX_UNITS = new HashMap<>();

    static {
        mosmix("°", "windDirection");
        mosmix("m/s", "windSpeed", "windGustLastHour", "windGustLastThreeHours", "windGustMaxLast12Hours");
        mosmix("%", "windGustProb25", "windGustProb40", "windGustProb55", "cloudCoverTotal", "cloudCoverBelow500",
                "cloudCoverEffective", "cloudCoverHigh", "cloudCoverMid", "cloudCoverLow", "precipitationLarger02Last6",
                "precipitationLarger50Last6", "precipitationLarger02LastDay", "precipitationLarger50LastDay",
                "precipitationLarger00Last12", "precipitationLarger02Last12", "precipitationLarger10Last12",
                "precipitationLarger50Last12", "fogPropLast1", "fogPropLast6", "fogPropLast12");
        mosmix("kJ/m2", "irRadianceGlobal");
        mosmix("Pa", "surfacePressure");
        mosmix("kg/m2", "precipitationSignificantWeatherTotal", "precipitationSignificantWeatherLast3",
                "snowRainEqLast1", "snowRainEqLast3");
        mosmix("s", "sunshineDurationLast1");
        mosmix("K", "tempAboveSurface5", "tempAboveSurface200", "tempDewpointAboveSurface200", "tempMinLast12",
                "tempMaxLast12");
        mosmix("m", "visibility");
    }

    private static void mosmix(String symbol, String... values) {
        for (String v : values) {
            MOSMIX_UNITS.put(v, symbol);
        }
    }

    private static Examples.Case temperature() {
        Examples.Case c = Examples.temperature();
        SystemOfUnits units = units(c);
        for (String r : List.of("current", "minimum", "maximum")) {
            measure(units, "°C", c.resource("temperature-sensor", "temperature", r));
        }
        measure(units, "%", c.resource("temperature-sensor", "status", "battery"));
        measure(units, "dBm", c.resource("temperature-sensor", "status", "signal"));
        return c;
    }
}
