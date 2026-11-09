/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.ecore;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The goal run on the test projects under {@code src/test/resources/it} — in place of an
 * invoker IT: the Mojo is instantiated, its parameters set as a POM would set them, and the
 * written files checked.
 */
class GenerateEcoreMojoTest {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";

    @Test
    void libraryProjectWritesLibraryEcore(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        GenerateEcoreMojo mojo = new GenerateEcoreMojo();
        set(mojo, "modelFile", project.resolve("src/main/cwm/objects.xmi").toFile());
        set(mojo, "outputDirectory", project.resolve("target/generated-ecore").toFile());

        mojo.execute();

        Path ecore = project.resolve("target/generated-ecore/library.ecore");
        assertThat(ecore).isRegularFile();
        EPackage library = load(ecore);
        EClass book = (EClass) library.getEClassifier("Book");
        EClass publisher = (EClass) library.getEClassifier("Publisher");
        assertThat(book).isNotNull();
        EReference toPublisher = (EReference) book.getEStructuralFeature("publisher");
        assertThat(toPublisher.getEType()).isSameAs(publisher);
        assertThat(toPublisher.getEOpposite()).isSameAs(publisher.getEStructuralFeature("books"));
    }

    @Test
    void anUnknownDataTypeFailsUntilMappedAndTheGenModelPropertiesAreWritten(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path model = project.resolve("src/main/cwm/objects.xmi");
        // a data type outside the default mapping: String renamed to Text
        Files.writeString(model, Files.readString(model).replace("name=\"String\"/>", "name=\"Text\"/>"));
        Path out = project.resolve("target/generated-ecore");
        GenerateEcoreMojo mojo = new GenerateEcoreMojo();
        set(mojo, "modelFile", model.toFile());
        set(mojo, "outputDirectory", out.toFile());
        set(mojo, "documentation", true);
        set(mojo, "constraints", true);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("TYPE");

        set(mojo, "dataTypes", Map.of("Text", "java.lang.String"));
        set(mojo, "genModel", true);
        set(mojo, "basePackage", "org.example");
        set(mojo, "genModelAnnotations", Map.of("oSGiCompatible", "false", "copyrightText", "Example"));
        mojo.execute();

        try (Stream<Path> files = Files.list(out)) {
            assertThat(files.map(p -> p.getFileName().toString()).sorted())
                    .containsExactly("library.ecore", "library.genmodel");
        }
        EPackage library = load(out.resolve("library.ecore"));
        assertThat(library.getEClassifier("Text").getInstanceClassName()).isEqualTo("java.lang.String");
        assertThat(library.getEAnnotation(GENMODEL).getDetails().map()).containsEntry("basePackage", "org.example")
                .containsEntry("prefix", "Library").containsEntry("oSGiCompatible", "false")
                .containsEntry("copyrightText", "Example");
        assertThat(Files.readString(out.resolve("library.genmodel"))).contains("basePackage=\"org.example\"");
    }

    @Test
    void documentationAndConstraintsCanBeLeftOut(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path model = project.resolve("src/main/cwm/objects.xmi");
        // Book gets a description and an OCL constraint
        String book = "<ownedElement xsi:type=\"core:Class\" xmi:id=\"Class_library.Book\" name=\"Book\">";
        Files.writeString(model, Files.readString(model).replace(book, book + "\n"
                + "<ownedElement xsi:type=\"businessinformation:Description\" xmi:id=\"BookDoc\" name=\"BookDoc\""
                + " body=\"A book of the library.\" type=\"documentation\" modelElement=\"Class_library.Book\"/>\n"
                + "<ownedElement xsi:type=\"core:Constraint\" xmi:id=\"TitleGiven\" name=\"TitleGiven\""
                + " constrainedElement=\"Class_library.Book\">"
                + "<body xmi:id=\"TitleGivenBody\" body=\"not self.title.oclIsUndefined()\" language=\"OCL\"/>"
                + "</ownedElement>"));
        Path out = project.resolve("target/generated-ecore");
        GenerateEcoreMojo mojo = new GenerateEcoreMojo();
        set(mojo, "modelFile", model.toFile());
        set(mojo, "outputDirectory", out.toFile());
        set(mojo, "documentation", true);
        set(mojo, "constraints", true);
        mojo.execute();
        EPackage with = load(out.resolve("library.ecore"));

        set(mojo, "documentation", false);
        set(mojo, "constraints", false);
        mojo.execute();
        EPackage without = load(out.resolve("library.ecore"));

        assertThat(EcoreUtil.getDocumentation(with.getEClassifier("Book"))).isEqualTo("A book of the library.");
        assertThat(EcoreUtil.getConstraints(with.getEClassifier("Book"))).containsExactly("TitleGiven");
        assertThat(documented(without)).isEmpty();
        assertThat(EcoreUtil.getConstraints(without.getEClassifier("Book"))).isEmpty();
        assertThat(without.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore")).as("no OCL delegates").isNull();
    }

    @Test
    void fileNamesNameTheWrittenFilesAndAnUnknownPackageFails(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path out = project.resolve("target/generated-ecore");
        GenerateEcoreMojo mojo = new GenerateEcoreMojo();
        set(mojo, "modelFile", project.resolve("src/main/cwm/objects.xmi").toFile());
        set(mojo, "outputDirectory", out.toFile());
        set(mojo, "splitPackages", true);
        set(mojo, "genModel", true);
        set(mojo, "fileNames", Map.of("library", "library-classes"));
        mojo.execute();

        assertThat(out.resolve("library-classes.ecore")).isRegularFile();
        assertThat(out.resolve("library-classes.genmodel")).isRegularFile();
        assertThat(out.resolve("library.ecore")).doesNotExist();

        set(mojo, "fileNames", Map.of("books", "x"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("'books' names 0");
    }

    @Test
    void missingModelFileFails(@TempDir Path tmp) throws Exception {
        GenerateEcoreMojo mojo = new GenerateEcoreMojo();
        set(mojo, "modelFile", tmp.resolve("nope.xmi").toFile());
        set(mojo, "outputDirectory", tmp.toFile());

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("nope.xmi");
    }

    @Test
    void runsInGenerateSourcesBeforeTheGenerateResourcesOfTheEormGoal() throws IOException {
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
        assertThat(phases).containsEntry("cwm-ecore", "generate-sources");
    }

    /** The elements of a package that carry GenModel documentation. */
    static List<EObject> documented(EPackage p) {
        List<EObject> out = new ArrayList<>();
        p.eAllContents().forEachRemaining(o -> {
            if (o instanceof EModelElement e && EcoreUtil.getDocumentation(e) != null) {
                out.add(o);
            }
        });
        return out;
    }

    static EPackage load(Path ecore) {
        return (EPackage) CwmPackages.load(CwmPackages.newResourceSet(), ecore).getContents().get(0);
    }

    static Path copyProject(String name, Path target) throws IOException, URISyntaxException {
        Path source = Path.of(GenerateEcoreMojoTest.class.getResource("/it/" + name).toURI());
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
        return target;
    }

    static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
