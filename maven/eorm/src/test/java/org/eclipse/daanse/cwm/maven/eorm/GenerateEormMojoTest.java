/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.eorm;

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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.orm.fennec.EormWriter;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.persistence.eorm.EntityMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The goal on the test projects under {@code src/test/resources/it}, the way a build runs it: the
 * Mojo is instantiated, its parameters set as a POM sets them, and executed; the written files are
 * checked. The {@code .ecore} files of the class models are given, without any CWM annotation:
 * classes and features are found by names.
 */
class GenerateEormMojoTest {

    @Test
    void eormNextToTheEcoreOfTheClassModel(@TempDir Path project) throws Exception {
        copyProject("library", project);
        Path cwm = project.resolve("src/main/cwm");
        Path ecoreDir = project.resolve("target/generated-ecore");
        Path eormDir = project.resolve("target/generated-resources/eorm");

        Files.createDirectories(ecoreDir);
        Files.copy(project.resolve("src/main/ecore/library.ecore"), ecoreDir.resolve("library.ecore"));
        GenerateEormMojo eorm = new GenerateEormMojo();
        set(eorm, "modelFile", cwm.resolve("mapping.cwmorm").toFile());
        set(eorm, "ecoreFile", ecoreDir.resolve("library.ecore").toFile());
        set(eorm, "outputDirectory", eormDir.toFile());
        set(eorm, "eormName", "library.eorm");
        set(eorm, "failOnValidation", true);
        eorm.execute();

        assertThat(ecoreDir.resolve("library.ecore")).isRegularFile();
        assertThat(eormDir.resolve("library.eorm")).isRegularFile();
        assertThat(eormDir.resolve("library.ecore")).as("copied next to the eorm").isRegularFile();
        String xml = Files.readString(eormDir.resolve("library.eorm"));
        assertThat(xml).contains("<class xsi:type=\"ecore:EClass\" href=\"library.ecore#//Book\"/>")
                .contains("<xmlMappingMetadataComplete/>");

        ResourceSet rs = EormWriter.newResourceSet();
        EntityMappings em = EormWriter.load(rs, eormDir.resolve("library.eorm"));
        EcoreUtil.resolveAll(rs);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(rs)).isEmpty();
        assertThat(em.getEntity()).extracting(e -> ((EClass) e.getClass_()).getName())
                .containsExactly("Publisher", "Book");
    }

    @Test
    void theChosenMappingSetBecomesOneEormOverTheNamedEcoreFiles(@TempDir Path project) throws Exception {
        copyProject("library", project);
        Path cwm = project.resolve("src/main/cwm");
        Path ecoreDir = project.resolve("target/generated-ecore");
        Path eormDir = project.resolve("target/generated-resources/eorm");
        // the class model's Ecore under another file name
        Files.createDirectories(ecoreDir);
        Files.copy(project.resolve("src/main/ecore/library.ecore"), ecoreDir.resolve("bibliothek.ecore"));
        // a model with two mapping sets, built in the temporary copy: the library set and a copy of it
        CwmPackages.registerKnown(getClass().getClassLoader());
        ResourceSet rs = CwmPackages.newResourceSet();
        EObject first = CwmPackages.load(rs, cwm.resolve("mapping.cwmorm")).getContents().get(0);
        EObject second = EcoreUtil.copy(first);
        ((ModelElement) second).setName("zweite");
        Resource two = rs.createResource(URI.createFileURI(cwm.resolve("two.cwmorm").toString()));
        two.getContents().addAll(List.of(first, second));
        two.save(Map.of());

        GenerateEormMojo eorm = new GenerateEormMojo();
        set(eorm, "modelFile", cwm.resolve("two.cwmorm").toFile());
        set(eorm, "ecoreFiles", List.of(ecoreDir.resolve("bibliothek.ecore").toFile()));
        set(eorm, "outputDirectory", eormDir.toFile());
        set(eorm, "failOnValidation", true);
        assertThatThrownBy(eorm::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("2 mapping set(s)");

        set(eorm, "mapping", "zweite");
        eorm.execute();

        try (Stream<Path> files = Files.list(eormDir)) {
            assertThat(files.map(p -> p.getFileName().toString()).sorted())
                    .as("one eorm for the one chosen set, next to the named ecore")
                    .containsExactly("bibliothek.ecore", "two.eorm");
        }
        assertThat(Files.readString(eormDir.resolve("two.eorm"))).contains("href=\"bibliothek.ecore#//Book\"");
        ResourceSet check = EormWriter.newResourceSet();
        EntityMappings em = EormWriter.load(check, eormDir.resolve("two.eorm"));
        EcoreUtil.resolveAll(check);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(check)).isEmpty();
        assertThat(em.getName()).isEqualTo("zweite");

        set(eorm, "ecoreFiles", List.of(ecoreDir.resolve("fehlt.ecore").toFile()));
        assertThatThrownBy(eorm::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("fehlt.ecore");
    }

    @Test
    void anEPackageNamedDifferentlyIsFoundByItsClassesOrThroughThePackageMap(@TempDir Path project) throws Exception {
        copyProject("library", project);
        Path out = project.resolve("target/generated-resources/eorm");
        // the class model's Ecore with its package renamed: library → bib
        ResourceSet rs = CwmPackages.newResourceSet();
        Resource ecore = rs.getResource(URI.createFileURI(
                project.resolve("src/main/ecore/library.ecore").toString()), true);
        EPackage p = (EPackage) ecore.getContents().get(0);
        p.setName("bib");
        p.setNsURI("http://example.org/bib");
        ecore.setURI(URI.createFileURI(project.resolve("src/main/ecore/bib.ecore").toString()));
        ecore.save(Map.of());
        GenerateEormMojo eorm = new GenerateEormMojo();
        set(eorm, "modelFile", project.resolve("src/main/cwm/mapping.cwmorm").toFile());
        set(eorm, "ecoreFile", project.resolve("src/main/ecore/bib.ecore").toFile());
        set(eorm, "outputDirectory", out.toFile());
        set(eorm, "failOnValidation", true);

        eorm.execute();
        assertThat(Files.readString(out.resolve("mapping.eorm"))).as("Book is the only class of its name")
                .contains("href=\"bib.ecore#//Book\"");

        set(eorm, "packageMap", Map.of("library", "http://example.org/bib"));
        eorm.execute();
        assertThat(Files.readString(out.resolve("mapping.eorm"))).contains("href=\"bib.ecore#//Book\"");
    }

    @Test
    void withoutEcoreTheGoalFails(@TempDir Path project) throws Exception {
        copyProject("library", project);
        GenerateEormMojo eorm = new GenerateEormMojo();
        set(eorm, "modelFile", project.resolve("src/main/cwm/mapping.cwmorm").toFile());
        set(eorm, "outputDirectory", project.resolve("out").toFile());
        set(eorm, "failOnValidation", true);

        assertThatThrownBy(eorm::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("ecoreFile or ecoreFiles");

        set(eorm, "ecoreFile", project.resolve("src/main/ecore/missing.ecore").toFile());
        assertThatThrownBy(eorm::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("missing.ecore");
    }

    @Test
    void aFurtherEPackageIsRegisteredBeforeLoadingAndAMissingOneFails(@TempDir Path project) throws Exception {
        copyProject("library", project);
        GenerateEormMojo eorm = new GenerateEormMojo();
        set(eorm, "modelFile", project.resolve("src/main/cwm/mapping.cwmorm").toFile());
        set(eorm, "ecoreFile", project.resolve("src/main/ecore/library.ecore").toFile());
        set(eorm, "outputDirectory", project.resolve("out").toFile());
        set(eorm, "failOnValidation", true);

        set(eorm, "extraPackages", List.of("org.eclipse.emf.ecore.EcorePackage"));
        eorm.execute();
        assertThat(project.resolve("out/mapping.eorm")).isRegularFile();

        set(eorm, "extraPackages", List.of("org.example.NoSuchPackage"));
        assertThatThrownBy(eorm::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("org.example.NoSuchPackage");
    }

    @Test
    void mappingThatBreaksTheExplicitProfileFailsUnlessReportOnly(@TempDir Path project) throws Exception {
        copyProject("library", project);
        Path mapping = project.resolve("src/main/cwm/mapping.cwmorm");
        String xml = Files.readString(mapping);
        // drop the column of Book.title: the explicit profile requires it
        Files.writeString(mapping, xml.replaceFirst(
                "(?s)<column xmi:id=\"Column_library\\.Book\\.title\\.TITLE\".*?</column>\\s*", ""));
        Path out = project.resolve("target/generated-resources/eorm");
        GenerateEormMojo eorm = new GenerateEormMojo();
        set(eorm, "modelFile", mapping.toFile());
        set(eorm, "ecoreFile", project.resolve("src/main/ecore/library.ecore").toFile());
        set(eorm, "outputDirectory", out.toFile());
        set(eorm, "failOnValidation", true);

        assertThatThrownBy(eorm::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("explicit");
        assertThat(out).doesNotExist();

        set(eorm, "failOnValidation", false);
        eorm.execute();
        assertThat(out.resolve("mapping.eorm")).isRegularFile();
    }

    @Test
    void runsInGenerateResourcesAfterTheGenerateSourcesOfTheEcoreGoal() throws IOException {
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
        assertThat(phases).containsEntry("fennec-eorm", "generate-resources");
    }

    static void copyProject(String name, Path target) throws IOException, URISyntaxException {
        Path source = Path.of(GenerateEormMojoTest.class.getResource("/it/" + name).toURI());
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
