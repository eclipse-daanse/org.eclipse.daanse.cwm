/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.atlas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.examples.ExampleUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The CWM files of the test projects are the examples of {@code model/daanse/mapping.sensinact}: written
 * here and compared with the files under {@code src/test/resources/it}, rewritten with
 * {@code -Dfennec-atlas.it.update=true}.
 *
 * <ul>
 * <li>{@code ecowitt}: the weather station with recording and retention rules and the units of its
 * resources, in two files — the source class model ({@code classes.xmi}, model
 * {@code weatherstation}) and providers, mapping, rules and units ({@code mapping.xmi}, model
 * {@code ecowitt}), which refers to the classes by {@code href}.</li>
 * <li>{@code battery}: two LoRaWAN devices onto one provider, with the unit of its resource, in one
 * file.</li>
 * </ul>
 */
class ItProjectsUpToDateTest {

    private static final Path IT = Path.of(System.getProperty("user.dir"), "src/test/resources/it");

    @TempDir
    Path out;

    @Test
    void ecowitt() throws Exception {
        Examples.Case c = Examples.ecowitt();
        Model mapping = c.model();
        Package rules = ClassModels.pkg(mapping, "rules");
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
        SystemOfUnits units = ExampleUnits.create(ClassModels.pkg(mapping, "units"));
        Quantities.create(units.getNamespace(), "temperature", ExampleUnits.unit(units, "F"), temperature);
        Quantities.create(units.getNamespace(), "humidity", ExampleUnits.unit(units, "%"), humidity);
        Quantities.create(units.getNamespace(), "rainRate", ExampleUnits.unit(units, "mm/h"), rain);

        Model classes = CoreFactory.eINSTANCE.createModel();
        classes.setName("weatherstation");
        classes.getOwnedElement().add(child(mapping, "ecore"));
        classes.getOwnedElement().add(child(mapping, "ecowitt"));

        Path dir = out.resolve("ecowitt/src/main/cwm");
        ResourceSet rs = CwmPackages.newResourceSet();
        Resource classesFile = create(rs, dir.resolve("classes.xmi"), classes);
        Resource mappingFile = create(rs, dir.resolve("mapping.xmi"), mapping);
        classesFile.save(options());
        mappingFile.save(options());
        compare("ecowitt/src/main/cwm/classes.xmi");
        compare("ecowitt/src/main/cwm/mapping.xmi");
        assertThat(Files.readString(dir.resolve("mapping.xmi")))
                .contains("href=\"classes.xmi#_EcoWittWeather\"");
    }

    @Test
    void battery() throws Exception {
        Examples.Case c = Examples.battery();
        SystemOfUnits units = ExampleUnits.create(ClassModels.pkg(c.model(), "units"));
        Quantities.create(units.getNamespace(), "level", ExampleUnits.unit(units, "V"),
                c.resource("battery-sensor", "battery", "level"));
        Path dir = out.resolve("battery/src/main/cwm");
        create(CwmPackages.newResourceSet(), dir.resolve("battery.xmi"), c.model()).save(options());
        compare("battery/src/main/cwm/battery.xmi");
    }

    private static Package child(Model model, String name) {
        return (Package) model.getOwnedElement().stream().filter(e -> name.equals(e.getName())).findFirst()
                .orElseThrow();
    }

    private static Resource create(ResourceSet rs, Path file, EObject root) {
        Resource r = rs.createResource(URI.createFileURI(file.toString()));
        r.getContents().add(root);
        ShortIds.assign(r);
        return r;
    }

    private void compare(String file) throws Exception {
        String written = Files.readString(out.resolve(file), StandardCharsets.UTF_8);
        Path expected = IT.resolve(file);
        if (Boolean.getBoolean("fennec-atlas.it.update")) {
            Files.createDirectories(expected.getParent());
            Files.writeString(expected, written, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(expected, StandardCharsets.UTF_8)).as(file).isEqualTo(written);
    }

    private static Map<Object, Object> options() {
        Map<Object, Object> options = new HashMap<>();
        options.put(XMLResource.OPTION_ENCODING, "UTF-8");
        options.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(120));
        return options;
    }
}
