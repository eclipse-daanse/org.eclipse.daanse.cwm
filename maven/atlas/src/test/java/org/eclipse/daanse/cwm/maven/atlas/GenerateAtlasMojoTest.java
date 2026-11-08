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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.daanse.cwm.sensinact.fennec.AtlasGenerator;
import org.eclipse.daanse.cwm.sensinact.fennec.AtlasWriter;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.event.atlas.model.mapping.AbsoluteChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.MappingPackage;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping;
import org.eclipse.fennec.event.atlas.model.mapping.util.MappingResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The goal on the test projects under {@code src/test/resources/it}, the way a build runs it: the
 * Mojo is instantiated, its parameters set as a POM sets them, and executed; the written files are
 * checked, also by loading them the way a Fennec runtime does.
 */
class GenerateAtlasMojoTest {

    @TempDir
    Path project;

    private Path out() {
        return project.resolve("target/generated-resources/atlas");
    }

    private GenerateAtlasMojo ecowitt() throws Exception {
        copyProject("ecowitt", project);
        GenerateAtlasMojo atlas = new GenerateAtlasMojo();
        set(atlas, "modelFile", project.resolve("src/main/cwm/mapping.xmi").toFile());
        set(atlas, "ecoreFile", project.resolve("src/main/ecore/ecowitt.ecore").toFile());
        set(atlas, "outputDirectory", out().toFile());
        set(atlas, "profiles", false);
        set(atlas, "failOnValidation", true);
        return atlas;
    }

    private GenerateAtlasMojo battery() throws Exception {
        copyProject("battery", project);
        Path ecore = project.resolve("src/main/ecore");
        GenerateAtlasMojo atlas = new GenerateAtlasMojo();
        set(atlas, "modelFile", project.resolve("src/main/cwm/battery.xmi").toFile());
        set(atlas, "ecoreFiles", List.of(ecore.resolve("lorawan-uplink.ecore").toFile(),
                ecore.resolve("dragino-message.ecore").toFile(), ecore.resolve("em310udl-message.ecore").toFile()));
        set(atlas, "outputDirectory", out().toFile());
        set(atlas, "profiles", true);
        set(atlas, "failOnValidation", true);
        return atlas;
    }

    @Test
    void aMappingFromTwoFilesWithTheRulesAndUnitsOfItsResources() throws Exception {
        ecowitt().execute();

        Path mapping = out().resolve("mappings/ecowitt-persistence.xmi");
        assertThat(mapping).isRegularFile();
        assertThat(out().resolve("profiles")).as("no profiles asked for").doesNotExist();
        assertThat(Files.readString(mapping))
                .contains("<providerClasses href=\"http://www.ecowitt.com/weatherstation#//EcoWittWeather\"/>")
                .contains("href=\"http://www.ecowitt.com/weatherstation#//EcoWittWeather/rainRate\"")
                .contains("<resources name=\"Temperature\" unit=\"F\"")
                .contains("<resources name=\"Rain rate\" unit=\"mm/h\"")
                .contains("<changeRule xsi:type=\"mapping:TimeThrottleChangeRule\" id=\"throttle-10m\" interval=\"10\" "
                        + "intervalUnit=\"MINUTES\"/>")
                .contains("<deletionRule id=\"keep-1y\" retention=\"365\" retentionUnit=\"DAYS\" cleanupInterval=\"7\" "
                        + "cleanupIntervalUnit=\"DAYS\"/>");

        ProviderMapping loaded = loadAsFennec(mapping, project.resolve("src/main/ecore/ecowitt.ecore"));
        assertThat(loaded.getServices().get(0).getResources()).extracting(r -> r.getChangeRule().getClass().getSimpleName())
                .containsExactly("PercentageChangeRuleImpl", "AbsoluteChangeRuleImpl", "TimeThrottleChangeRuleImpl");
        assertThat(loaded.getServices().get(0).getResources().get(1).getChangeRule())
                .isInstanceOfSatisfying(AbsoluteChangeRule.class, r -> assertThat(r.getDelta()).isEqualTo(2.0));
    }

    @Test
    void oneProfilePerProviderAndTheChosenMappings() throws Exception {
        GenerateAtlasMojo atlas = battery();
        atlas.execute();
        assertThat(files(out().resolve("mappings"))).containsExactly("dragino-battery-sensor.xmi",
                "em310udl-battery-sensor.xmi");
        assertThat(files(out().resolve("profiles"))).containsExactly("battery-sensor-profile.xmi");
        assertThat(Files.readString(out().resolve("mappings/dragino-battery-sensor.xmi")))
                .contains("href=\"../profiles/battery-sensor-profile.xmi#battery-sensor\"")
                .contains("<resources name=\"Battery Level\" unit=\"V\"");
        assertThat(Files.readString(out().resolve("profiles/battery-sensor-profile.xmi")))
                .contains("expectedUnit=\"V\"");

        Path chosen = project.resolve("target/chosen");
        set(atlas, "outputDirectory", chosen.toFile());
        set(atlas, "mappings", List.of("em310udl-battery-sensor"));
        atlas.execute();
        assertThat(files(chosen.resolve("mappings"))).containsExactly("em310udl-battery-sensor.xmi");
        assertThat(files(chosen.resolve("profiles"))).containsExactly("battery-sensor-profile.xmi");

        set(atlas, "mappings", List.of("no-such-mapping"));
        assertThatThrownBy(atlas::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("no provider mapping named 'no-such-mapping'")
                .hasMessageContaining("dragino-battery-sensor");
    }

    @Test
    void withoutEcoreTheGoalFails() throws Exception {
        GenerateAtlasMojo atlas = ecowitt();
        set(atlas, "ecoreFile", null);
        assertThatThrownBy(atlas::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("ecoreFile or ecoreFiles");

        set(atlas, "ecoreFile", project.resolve("src/main/ecore/missing.ecore").toFile());
        assertThatThrownBy(atlas::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("missing.ecore");
        assertThat(out()).doesNotExist();
    }

    @Test
    void aClassMissingInTheEcoreModelFailsAndNothingIsWritten() throws Exception {
        GenerateAtlasMojo atlas = battery();
        set(atlas, "ecoreFiles", List.of(project.resolve("src/main/ecore/lorawan-uplink.ecore").toFile()));
        assertThatThrownBy(atlas::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("conversion error").hasMessageContaining("UNRESOLVED");
        assertThat(out()).doesNotExist();
    }

    @Test
    void aMappingTheValidatorsRejectFailsUnlessReportOnly() throws Exception {
        GenerateAtlasMojo atlas = ecowitt();
        Path mapping = project.resolve("src/main/cwm/mapping.xmi");
        // the temperature resource loses its type: P2
        Files.writeString(mapping, Files.readString(mapping).replaceFirst(
                "<type xsi:type=\"core:DataType\" href=\"classes.xmi#_EDouble\"/>", ""));

        assertThatThrownBy(atlas::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("error(s) in the mappings").hasMessageContaining("P2 ").hasMessageContaining("has no type");
        assertThat(out()).doesNotExist();

        set(atlas, "failOnValidation", false);
        atlas.execute();
        assertThat(out().resolve("mappings/ecowitt-persistence.xmi")).isRegularFile();
    }

    @Test
    void dataTypesOverrideTheTypeOfAResource() throws Exception {
        GenerateAtlasMojo atlas = battery();
        set(atlas, "dataTypes", Map.of("EDouble", "EFloat"));
        atlas.execute();
        assertThat(Files.readString(out().resolve("mappings/dragino-battery-sensor.xmi")))
                .contains("href=\"http://www.eclipse.org/emf/2002/Ecore#//EFloat\"").doesNotContain("#//EDouble");
        assertThat(Files.readString(out().resolve("profiles/battery-sensor-profile.xmi")))
                .contains("href=\"http://www.eclipse.org/emf/2002/Ecore#//EFloat\"");
    }

    @Test
    void anEPackageNamedDifferentlyIsFoundByItsClassesOrThroughThePackageMap() throws Exception {
        GenerateAtlasMojo atlas = ecowitt();
        // the source model's Ecore with its package renamed: ecowitt → station
        Path renamed = project.resolve("src/main/ecore/station.ecore");
        ResourceSet rs = AtlasWriter.newResourceSet();
        Resource ecore = rs.getResource(URI.createFileURI(project.resolve("src/main/ecore/ecowitt.ecore").toString()), true);
        EPackage p = (EPackage) ecore.getContents().get(0);
        p.setName("station");
        p.setNsURI("http://example.org/station");
        ecore.setURI(URI.createFileURI(renamed.toString()));
        ecore.save(Map.of());
        set(atlas, "ecoreFile", renamed.toFile());

        atlas.execute();
        assertThat(Files.readString(out().resolve("mappings/ecowitt-persistence.xmi")))
                .as("EcoWittWeather is the only class of its name")
                .contains("href=\"http://example.org/station#//EcoWittWeather/rainRate\"");

        set(atlas, "packageMap", Map.of("ecowitt", "http://example.org/station"));
        atlas.execute();
        assertThat(Files.readString(out().resolve("mappings/ecowitt-persistence.xmi")))
                .contains("href=\"http://example.org/station#//EcoWittWeather\"");
    }

    @Test
    void aFurtherEPackageIsRegisteredBeforeLoadingAndAMissingOneFails() throws Exception {
        GenerateAtlasMojo atlas = ecowitt();
        set(atlas, "extraPackages", List.of("org.eclipse.emf.ecore.EcorePackage"));
        atlas.execute();
        assertThat(out().resolve("mappings/ecowitt-persistence.xmi")).isRegularFile();

        set(atlas, "extraPackages", List.of("org.example.NoSuchPackage"));
        assertThatThrownBy(atlas::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("org.example.NoSuchPackage");
    }

    @Test
    void runsInGenerateResources() throws IOException {
        Map<String, String> phases = new HashMap<>();
        for (URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/maven/plugin.xml"))) {
            try (InputStream in = url.openStream()) {
                String descriptor = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Matcher prefix = Pattern.compile("<goalPrefix>([^<]+)</goalPrefix>").matcher(descriptor);
                Matcher phase = Pattern.compile("<phase>([^<]+)</phase>").matcher(descriptor);
                if (prefix.find() && phase.find()) {
                    phases.put(prefix.group(1), phase.group(1));
                }
            }
        }
        assertThat(phases).containsEntry("fennec-atlas", "generate-resources");
    }

    /** A written mapping, loaded as a Fennec runtime loads it: source packages registered by namespace URI. */
    private static ProviderMapping loadAsFennec(Path mapping, Path ecoreFile) {
        ResourceSet rs = new ResourceSetImpl();
        rs.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        rs.getPackageRegistry().put(MappingPackage.eNS_URI, MappingPackage.eINSTANCE);
        AtlasGenerator.loadEcore(List.of(ecoreFile)).forEach(p -> rs.getPackageRegistry().put(p.getNsURI(), p));
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new MappingResourceFactoryImpl());
        ProviderMapping loaded = (ProviderMapping) rs.getResource(URI.createFileURI(mapping.toString()), true)
                .getContents().get(0);
        EcoreUtil.resolveAll(rs);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(rs)).isEmpty();
        return loaded;
    }

    private static List<String> files(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    static void copyProject(String name, Path target) throws IOException, URISyntaxException {
        Path source = Path.of(GenerateAtlasMojoTest.class.getResource("/it/" + name).toURI());
        try (Stream<Path> all = Files.walk(source)) {
            for (Path p : all.toList()) {
                Path t = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t);
                }
            }
        }
    }

    static void set(Mojo target, String field, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
