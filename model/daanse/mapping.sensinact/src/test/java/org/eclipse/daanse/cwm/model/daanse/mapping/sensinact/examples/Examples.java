/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples;

import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.attr;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.attrs;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.cls;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.enumeration;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.many;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.pkg;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels.type;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources.at;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources.literal;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources.orElse;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources.path;
import static org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources.where;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.AdminMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.AdminMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ProviderMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ResourceMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ServiceMappings;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Providers;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Resources;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactStructure;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Services;

/**
 * The mapping examples of Eclipse Fennec event.atlas, written in CWM: each case is one model with
 * the class model of the source (the packages and names of Fennec's test Ecore models), the
 * provider structure and the mappings.
 *
 * <ul>
 * <li>{@link #battery()} — two LoRaWAN devices (Dragino LSE01, EM310-UDL) onto one provider
 * {@code battery-sensor}; inheritance across packages, a timestamp from a collection.</li>
 * <li>{@link #ecowitt()} — a weather station: id and timestamp from attributes, a fixed location,
 * three resources (the lifecycle rules are added where the lifecycle model is known).</li>
 * <li>{@link #weather()} — DWD weather reports: two services read reports 0 and 1 of a
 * collection; every attribute of the report subclass becomes a resource (the explicit form of
 * Fennec's reference mapping), two of a nested object; admin values through references.</li>
 * <li>{@link #iotraffic()} — a line-counting camera: one service per counting line, each picking
 * its line from a map by key.</li>
 * <li>{@link #temperature()} — two vendors' temperature sensors onto one provider with optional
 * services and resources; CWM data type names (Float, Integer) that the converter maps.</li>
 * </ul>
 */
public final class Examples {

    /** One example: its model, providers and mappings. */
    public record Case(String name, Model model, List<Provider> providers, List<ProviderMapping> mappings) {

        /** The service {@code service} of the provider {@code provider}. */
        public Service service(String provider, String service) {
            return SensinactStructure.services(provider(provider)).stream()
                    .filter(s -> s.getName().equals(service)).findFirst().orElseThrow();
        }

        /** The resource {@code resource} of a service. */
        public Resource resource(String provider, String service, String resource) {
            return SensinactStructure.resources(service(provider, service)).stream()
                    .filter(r -> r.getName().equals(resource)).findFirst().orElseThrow();
        }

        /** The provider named {@code name}. */
        public Provider provider(String name) {
            return providers.stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
        }

        /** The mapping named {@code name}. */
        public ProviderMapping mapping(String name) {
            return mappings.stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        }
    }

    private Examples() {
    }

    /** Every example. */
    public static List<Case> all() {
        return List.of(battery(), ecowitt(), weather(), iotraffic(), temperature());
    }

    /** Data types named like the Ecore types they stand for, in a package {@code ecore}. */
    private static final class EcoreTypes {
        private final Package ecore;
        private final Map<String, DataType> types = new HashMap<>();

        EcoreTypes(Model model) {
            this.ecore = pkg(model, "ecore");
        }

        DataType get(String name) {
            return types.computeIfAbsent(name, n -> type(ecore, n));
        }
    }

    /** Two LoRaWAN devices onto one provider. */
    public static Case battery() {
        Model model = ClassModels.model("battery");
        EcoreTypes t = new EcoreTypes(model);
        Package lorawan = pkg(model, "lorawan");
        Class uplink = cls(lorawan, "UplinkMessage");
        Class deviceInfo = cls(lorawan, "DeviceInfo");
        Class rxInfoClass = cls(lorawan, "RxInfo");
        Attribute deduplicationId = attr(uplink, "deduplicationId", t.get("EString"));
        Attribute time = attr(uplink, "time", t.get("EString"));
        attr(uplink, "fCnt", t.get("EInt"));
        Attribute uplinkDeviceInfo = attr(uplink, "deviceInfo", deviceInfo);
        Attribute rxInfo = many(uplink, "rxInfo", rxInfoClass);
        Attribute rxTime = attr(rxInfoClass, "time", t.get("EString"));
        attr(rxInfoClass, "rssi", t.get("EInt"));
        Attribute deviceProfileName = attr(deviceInfo, "deviceProfileName", t.get("EString"));
        attr(deviceInfo, "deviceName", t.get("EString"));

        Package dragino = pkg(model, "dragino");
        Class draginoObject = cls(dragino, "DecodedObject");
        Attribute batV = attr(draginoObject, "batV", t.get("EDouble"));
        attr(draginoObject, "temp_SOIL", t.get("EDouble"));
        Class draginoUplink = cls(dragino, "DraginoLSE01Uplink", uplink);
        Attribute draginoObjectRef = attr(draginoUplink, "object", draginoObject);

        Package em310 = pkg(model, "em310udl");
        Class em310Object = cls(em310, "DecodedObject");
        Attribute battery = attr(em310Object, "battery", t.get("EDouble"));
        attr(em310Object, "distance", t.get("EDouble"));
        Class em310Uplink = cls(em310, "EM310UDLUplink", uplink);
        Attribute em310ObjectRef = attr(em310Uplink, "object", em310Object);

        Package providers = pkg(model, "providers");
        Provider sensor = Providers.create(providers, "battery-sensor");
        sensor.setFriendlyNameRequired(true);
        Service batteryService = Services.create(sensor, "battery");
        batteryService.setDisplayName("Battery");
        Resource level = Resources.create(batteryService, "level", t.get("EDouble"));
        level.setDisplayName("Battery Level");

        Package mappings = pkg(model, "mappings");
        ProviderMapping draginoMapping = ProviderMappings.create(mappings, "dragino-battery-sensor", sensor,
                orElse(path(deduplicationId), "Dragino LSE01 Battery Sensor"), draginoUplink);
        ProviderMappings.timestamp(draginoMapping, at(path(rxInfo, rxTime), 0));
        ResourceMappings.create(ServiceMappings.create(draginoMapping, batteryService), level,
                path(draginoObjectRef, batV));
        AdminMappings.friendlyName(AdminMappings.create(draginoMapping), path(uplinkDeviceInfo, deviceProfileName));

        ProviderMapping em310Mapping = ProviderMappings.create(mappings, "em310udl-battery-sensor", sensor,
                orElse(path(deduplicationId), "EM310UDL Battery Sensor"), em310Uplink);
        ProviderMappings.timestamp(em310Mapping, path(time));
        ResourceMappings.create(ServiceMappings.create(em310Mapping, batteryService), level,
                path(em310ObjectRef, battery));
        AdminMappings.friendlyName(AdminMappings.create(em310Mapping), path(uplinkDeviceInfo, deviceProfileName));

        return new Case("battery", model, List.of(sensor), List.of(draginoMapping, em310Mapping));
    }

    /** A weather station with a fixed location. */
    public static Case ecowitt() {
        Model model = ClassModels.model("ecowitt");
        EcoreTypes t = new EcoreTypes(model);
        Package ecowitt = pkg(model, "ecowitt");
        Class weather = cls(ecowitt, "EcoWittWeather");
        Attribute passkey = attr(weather, "passkey", t.get("EString"));
        Attribute stationType = attr(weather, "stationType", t.get("EString"));
        Attribute dateUTC = attr(weather, "dateUTC", t.get("EDate"));
        Attribute temperaturOutdoor = attr(weather, "temperaturOutdoor", t.get("EDouble"));
        Attribute humidityOutdoor = attr(weather, "humidityOutdoor", t.get("EInt"));
        Attribute rainRate = attr(weather, "rainRate", t.get("EDouble"));
        attrs(weather, t.get("EDouble"), "temperaturIndoor", "barometerRel", "windSpeedMph");

        Package providers = pkg(model, "providers");
        Provider station = Providers.create(providers, "ecowitt-weather");
        Service service = Services.create(station, "weather");
        service.setDisplayName("weather");
        Resource temperature = Resources.create(service, "temperature", t.get("EDouble"));
        temperature.setDisplayName("Temperature");
        Resource humidity = Resources.create(service, "humidity", t.get("EInt"));
        humidity.setDisplayName("Humidity");
        Resource rain = Resources.create(service, "rainRate", t.get("EDouble"));
        rain.setDisplayName("Rain rate");

        Package mappings = pkg(model, "mappings");
        ProviderMapping mapping = ProviderMappings.create(mappings, "ecowitt-persistence", station, path(passkey),
                weather);
        ProviderMappings.timestamp(mapping, path(dateUTC));
        ServiceMapping sm = ServiceMappings.create(mapping, service);
        ResourceMappings.create(sm, temperature, path(temperaturOutdoor));
        ResourceMappings.create(sm, humidity, path(humidityOutdoor));
        ResourceMappings.create(sm, rain, path(rainRate));
        AdminMapping admin = AdminMappings.create(mapping);
        AdminMappings.friendlyName(admin, path(stationType));
        AdminMappings.location(admin, literal("8.531689"), literal("51.952503"));
        return new Case("ecowitt", model, List.of(station), List.of(mapping));
    }

    /** The measured values of a MOSMIX report, in the order of the source model. */
    private static final List<String> MOSMIX_VALUES = List.of("windDirection", "windSpeed", "windGustLastHour",
            "windGustLastThreeHours", "windGustMaxLast12Hours", "windGustProb25", "windGustProb40", "windGustProb55",
            "cloudCoverTotal", "cloudCoverBelow500", "cloudCoverEffective", "cloudCoverHigh", "cloudCoverMid",
            "cloudCoverLow", "precipitationLarger02Last6", "precipitationLarger50Last6",
            "precipitationLarger02LastDay", "precipitationLarger50LastDay", "precipitationLarger00Last12",
            "precipitationLarger02Last12", "precipitationLarger10Last12", "precipitationLarger50Last12",
            "fogPropLast1", "fogPropLast6", "fogPropLast12", "irRadianceGlobal", "surfacePressure",
            "precipitationSignificantWeatherTotal", "precipitationSignificantWeatherLast3", "snowRainEqLast1",
            "snowRainEqLast3", "sunshineDurationLast1", "tempAboveSurface5", "tempAboveSurface200",
            "tempDewpointAboveSurface200", "tempMinLast12", "tempMaxLast12", "visibility");

    /** DWD weather reports: the current report and the forecast, every attribute a resource. */
    public static Case weather() {
        Model model = ClassModels.model("weather");
        EcoreTypes t = new EcoreTypes(model);
        Package weather = pkg(model, "weather");
        Class reports = cls(weather, "WeatherReports");
        Class report = cls(weather, "WeatherReport");
        Class station = cls(weather, "Station");
        Class weatherStation = cls(weather, "WeatherStation", station);
        Class position = cls(weather, "GeoPosition");
        Class mosmix = cls(weather, "MOSMIXSWeatherReport", report);
        Class w1w2 = cls(weather, "W1W2");
        Enumeration code = enumeration(weather, "WMOWeatherCodeType", "W_00", "W_01", "W_02", "W_03");

        attr(reports, "id", t.get("EString"));
        Attribute reportList = many(reports, "reports", report);
        Attribute reportId = attr(report, "id", t.get("EString"));
        attr(report, "issueTime", t.get("EDate"));
        Attribute timestamp = attr(report, "timestamp", t.get("EDate"));
        Attribute reportStation = attr(report, "station", station);
        Attribute reportWeatherStation = attr(report, "weatherStation", weatherStation);
        Attribute stationName = attr(station, "name", t.get("EString"));
        Attribute location = attr(station, "location", position);
        attr(station, "country", t.get("EString"));
        Attribute weatherStationId = attr(weatherStation, "id", t.get("EString"));
        attr(weatherStation, "icaoCode", t.get("EString"));
        Attribute latitude = attr(position, "latitude", t.get("EDouble"));
        Attribute longitude = attr(position, "longitude", t.get("EDouble"));
        attr(position, "elevation", t.get("EInt"));
        for (String n : MOSMIX_VALUES) {
            attr(mosmix, n, t.get("EFloatObject"));
        }
        Attribute significant6 = attr(mosmix, "significantWeather6Hours", w1w2);
        attr(mosmix, "significantWeather3Hours", code);
        Attribute w1 = attr(w1w2, "w1", code);
        Attribute w2 = attr(w1w2, "w2", code);

        Package providers = pkg(model, "providers");
        Provider dwd = Providers.create(providers, "dwd-weather");
        Package mappings = pkg(model, "mappings");
        ProviderMapping mapping = ProviderMappings.create(mappings, "dwd-weather-reports", dwd,
                at(path(reportList, reportWeatherStation, weatherStationId), 0), reports);
        AdminMapping admin = AdminMappings.create(mapping);
        AdminMappings.friendlyName(admin, at(path(reportList, reportStation, stationName), 0));
        AdminMappings.location(admin, at(path(reportList, reportStation, location, latitude), 0),
                at(path(reportList, reportStation, location, longitude), 0));

        String[][] services = { { "currentWeather", "Current Weather", "0" }, { "forecast3H", "3H Forecast", "1" } };
        for (String[] s : services) {
            Service service = Services.create(dwd, s[0]);
            service.setDisplayName(s[1]);
            Resources.fromAttributes(service, mosmix, reportId, timestamp);
            Resource r1 = Resources.create(service, "significantWeather6HoursW1", code);
            r1.setDisplayName("w1");
            Resource r2 = Resources.create(service, "significantWeather6HoursW2", code);
            r2.setDisplayName("w2");
            ServiceMapping sm = ServiceMappings.create(mapping, service, at(path(reportList), Long.parseLong(s[2])));
            ServiceMappings.timestamp(sm, path(timestamp));
            ResourceMappings.mapAttributes(sm, mosmix);
            ResourceMappings.create(sm, r1, path(significant6, w1));
            ResourceMappings.create(sm, r2, path(significant6, w2));
        }
        return new Case("weather", model, List.of(dwd), List.of(mapping));
    }

    /** A line-counting camera: one service per counting line, picked from a map by key. */
    public static Case iotraffic() {
        Model model = ClassModels.model("iotraffic");
        EcoreTypes t = new EcoreTypes(model);
        Package iotraffic = pkg(model, "iotraffic");
        Class uplink = cls(iotraffic, "IotrafficUplink");
        Class counterUplink = cls(iotraffic, "CounterUplink", uplink);
        Class counterData = cls(iotraffic, "CounterData");
        Class lineEntry = cls(iotraffic, "LineEntry");
        Class trafficLine = cls(iotraffic, "TrafficLine");
        Class deviceInfo = cls(iotraffic, "DeviceInfo");
        Class rxInfoClass = cls(iotraffic, "RxInfo");
        Class locationClass = cls(iotraffic, "Location");

        Attribute time = attr(uplink, "time", t.get("EString"));
        attr(uplink, "deduplicationId", t.get("EString"));
        Attribute uplinkDeviceInfo = attr(uplink, "deviceInfo", deviceInfo);
        Attribute rxInfo = many(uplink, "rxInfo", rxInfoClass);
        Attribute object = attr(counterUplink, "object", counterData);
        Attribute fromText = attr(counterData, "from_time_text", t.get("EString"));
        Attribute toText = attr(counterData, "to_time_text", t.get("EString"));
        attr(counterData, "line_count", t.get("EInt"));
        Attribute lines = many(counterData, "lines", lineEntry);
        attr(lineEntry, "key", t.get("EString"));
        Attribute lineValue = attr(lineEntry, "value", trafficLine);
        attrs(trafficLine, t.get("EInt"), "line_number", "person", "bicycle", "car", "bus", "truck", "motorbike", "train",
                "total");
        Attribute devEui = attr(deviceInfo, "devEui", t.get("EString"));
        Attribute deviceName = attr(deviceInfo, "deviceName", t.get("EString"));
        Attribute rxLocation = attr(rxInfoClass, "location", locationClass);
        Attribute latitude = attr(locationClass, "latitude", t.get("EDouble"));
        Attribute longitude = attr(locationClass, "longitude", t.get("EDouble"));

        Package providers = pkg(model, "providers");
        Provider camera = Providers.create(providers, "iotraffic-camera");
        camera.setLocationRequired(true);
        camera.setFriendlyNameRequired(true);
        Service window = Services.create(camera, "window");
        window.setDisplayName("Reporting window");
        Resource from = Resources.create(window, "from", t.get("EString"));
        from.setDisplayName("From");
        Resource to = Resources.create(window, "to", t.get("EString"));
        to.setDisplayName("To");

        Package mappings = pkg(model, "mappings");
        ProviderMapping mapping = ProviderMappings.create(mappings, "iotraffic-counter", camera,
                path(uplinkDeviceInfo, devEui), counterUplink);
        ProviderMappings.timestamp(mapping, path(time));
        ServiceMapping windowMapping = ServiceMappings.create(mapping, window);
        ResourceMappings.create(windowMapping, from, path(object, fromText));
        ResourceMappings.create(windowMapping, to, path(object, toText));
        for (int line = 1; line <= 9; line++) {
            Service service = Services.create(camera, "line" + line);
            service.setDisplayName("Line " + line);
            Resources.fromAttributes(service, trafficLine);
            ServiceMapping sm = ServiceMappings.create(mapping, service,
                    where(path(object, lines, lineValue), "key=line_" + line));
            ResourceMappings.mapAttributes(sm, trafficLine);
        }
        AdminMapping admin = AdminMappings.create(mapping);
        AdminMappings.friendlyName(admin, path(uplinkDeviceInfo, deviceName));
        AdminMappings.location(admin, at(path(rxInfo, rxLocation, latitude), 0),
                at(path(rxInfo, rxLocation, longitude), 0));
        return new Case("iotraffic", model, List.of(camera), List.of(mapping));
    }

    /** Two vendors' temperature sensors onto one provider with optional parts. */
    public static Case temperature() {
        Model model = ClassModels.model("temperature");
        Package types = pkg(model, "types");
        DataType string = type(types, "String");
        DataType floating = type(types, "Float");
        DataType integer = type(types, "Integer");
        DataType decimal = type(types, "Double");
        DataType timestamp = type(types, "Timestamp");

        Package vendorA = pkg(model, "vendora");
        Class sensorA = cls(vendorA, "VendorATemperatureSensor");
        Attribute deviceName = attr(sensorA, "deviceName", string);
        Attribute lat = attr(sensorA, "lat", decimal);
        Attribute lon = attr(sensorA, "lon", decimal);
        Attribute currentTemp = attr(sensorA, "currentTemp", floating);
        Attribute minTemp = attr(sensorA, "minTemp", floating);
        Attribute maxTemp = attr(sensorA, "maxTemp", floating);
        Attribute batteryLevel = attr(sensorA, "batteryLevel", integer);

        Package vendorB = pkg(model, "vendorb");
        Class sensorB = cls(vendorB, "VendorBSensor");
        Class infoB = cls(vendorB, "DeviceInfo");
        Class gps = cls(vendorB, "GPSCoordinate");
        Class readings = cls(vendorB, "SensorReadings");
        Class status = cls(vendorB, "DeviceStatus");
        Attribute timestampB = attr(sensorB, "timestamp", timestamp);
        Attribute info = attr(sensorB, "deviceInfo", infoB);
        Attribute gpsRef = attr(sensorB, "gps", gps);
        Attribute readingsRef = attr(sensorB, "readings", readings);
        Attribute statusRef = attr(sensorB, "status", status);
        Attribute infoName = attr(infoB, "name", string);
        Attribute latitude = attr(gps, "latitude", decimal);
        Attribute longitude = attr(gps, "longitude", decimal);
        Attribute temperature = attr(readings, "temperature", floating);
        Attribute minTemperature = attr(readings, "minTemperature", floating);
        Attribute maxTemperature = attr(readings, "maxTemperature", floating);
        Attribute batteryPercent = attr(status, "batteryPercent", integer);
        Attribute signalStrength = attr(status, "signalStrength", integer);

        Package providers = pkg(model, "providers");
        Provider sensor = Providers.create(providers, "temperature-sensor");
        sensor.setLocationRequired(true);
        sensor.setFriendlyNameRequired(true);
        Service temperatureService = Services.create(sensor, "temperature");
        temperatureService.setDisplayName("Temperature");
        Resource current = Resources.create(temperatureService, "current", floating);
        current.setDisplayName("Current Temperature");
        Resource minimum = Resources.optional(temperatureService, "minimum", floating);
        minimum.setDisplayName("Minimum Temperature");
        Resource maximum = Resources.optional(temperatureService, "maximum", floating);
        maximum.setDisplayName("Maximum Temperature");
        Service statusService = Services.optional(sensor, "status");
        statusService.setDisplayName("Device Status");
        Resource battery = Resources.optional(statusService, "battery", integer);
        battery.setDisplayName("Battery Level");
        Resource signal = Resources.optional(statusService, "signal", integer);
        signal.setDisplayName("Signal Strength");

        Package mappings = pkg(model, "mappings");
        ProviderMapping a = ProviderMappings.create(mappings, "vendor-a-temperature-sensor", sensor,
                literal("Vendor A Temperature Sensor"), sensorA);
        ServiceMapping aTemperature = ServiceMappings.create(a, temperatureService);
        ResourceMappings.create(aTemperature, current, path(currentTemp));
        ResourceMappings.create(aTemperature, minimum, path(minTemp));
        ResourceMappings.create(aTemperature, maximum, path(maxTemp));
        ResourceMappings.create(ServiceMappings.create(a, statusService), battery, path(batteryLevel));
        AdminMapping adminA = AdminMappings.create(a);
        AdminMappings.friendlyName(adminA, path(deviceName));
        AdminMappings.location(adminA, path(lat), path(lon));

        ProviderMapping b = ProviderMappings.create(mappings, "vendor-b-temperature-sensor", sensor,
                literal("Vendor B Temperature Sensor"), sensorB);
        ProviderMappings.timestamp(b, path(timestampB));
        ServiceMapping bTemperature = ServiceMappings.create(b, temperatureService);
        ResourceMappings.create(bTemperature, current, path(readingsRef, temperature));
        ResourceMappings.create(bTemperature, minimum, path(readingsRef, minTemperature));
        ResourceMappings.create(bTemperature, maximum, path(readingsRef, maxTemperature));
        ServiceMapping bStatus = ServiceMappings.create(b, statusService);
        ResourceMappings.create(bStatus, battery, path(statusRef, batteryPercent));
        ResourceMappings.create(bStatus, signal, path(statusRef, signalStrength));
        AdminMapping adminB = AdminMappings.create(b);
        AdminMappings.friendlyName(adminB, path(info, infoName));
        AdminMappings.location(adminB, path(gpsRef, latitude), path(gpsRef, longitude));
        return new Case("temperature", model, List.of(sensor), List.of(a, b));
    }

    /** The namespace the mappings of a case live in. */
    public static Namespace mappings(Case c) {
        return c.mappings().get(0).getNamespace();
    }
}
