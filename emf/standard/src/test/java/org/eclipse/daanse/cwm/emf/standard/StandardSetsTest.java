/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.standard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A set of Ecore data types with UUIDs stated in the code: written the same way every time, kept equal to its file,
 * loaded under its URI and referred to by URI and UUID; and the UUIDs of the standard sets of the repository.
 */
class StandardSetsTest {

    private static final String URI_OF_KINDS = "https://types.example.org/kinds.xmi";
    private static final String URI_OF_OTHER = "https://types.example.org/other.xmi";
    private static final EcoreFactory F = EcoreFactory.eINSTANCE;
    private static final String TEXT = "_LdvhwOUIEfGl4IEUgfF4xA";
    private static final String WHOLE = "_LdvhweUIEfGl4IEUgfF4xA";

    @TempDir
    Path dir;

    /** Two kinds of value in a package, each with the UUID stated beside it. */
    private static XMIResource kinds() {
        XMIResource set = StandardSets.create(URI_OF_KINDS);
        EPackage kinds = pkg("kinds");
        set.getContents().add(kinds);
        StandardSets.id(kinds, "_LdvhwuUIEfGl4IEUgfF4xA");
        StandardSets.id(type(kinds, "Text"), TEXT);
        StandardSets.id(type(kinds, "WholeNumber"), WHOLE);
        return set;
    }

    @Test
    void theTextIsTheSameEveryTimeAndCarriesTheStatedUuids() {
        String first = StandardSets.text(kinds());
        assertThat(StandardSets.text(kinds())).as("a second build writes the same text").isEqualTo(first);
        assertThat(first).as("the licence header after the declaration")
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n")
                .contains("SPDX-License-Identifier: EPL-2.0")
                .as("line feeds only").doesNotContain("\r")
                .as("the stated UUIDs").contains("xmi:id=\"" + TEXT + "\" name=\"Text\"", "xmi:id=\"" + WHOLE + "\"");
    }

    @Test
    void anIdIsAShortUuidAndNamesOneElementOfItsResource() {
        XMIResource set = kinds();
        EPackage kinds = (EPackage) set.getContents().get(0);
        assertThatThrownBy(() -> StandardSets.id(type(kinds, "Flag"), "kind.flag")).as("an id that is no UUID")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kind.flag");
        EDataType again = StandardSets.id(type(kinds, "Again"), TEXT);
        assertThat(set.getID(again)).as("a UUID another element of the resource has").isNull();
        assertThat(set.getEObject(TEXT)).as("it stays with the first").isSameAs(kinds.getEClassifier("Text"));
        EDataType outside = StandardSets.id(type(pkg("loose"), "Text"), TEXT);
        assertThat(outside.eResource()).as("an element in no resource is returned as it is").isNull();
    }

    @Test
    void theFileIsKeptEqualToTheCode() throws Exception {
        Path file = dir.resolve("kinds.xmi");
        assertThat(StandardSets.upToDate(kinds(), file)).as("no file yet").isFalse();
        System.setProperty(StandardSets.WRITE, "true");
        try {
            assertThat(StandardSets.upToDate(kinds(), file)).as("written").isTrue();
        } finally {
            System.clearProperty(StandardSets.WRITE);
        }
        assertThat(StandardSets.upToDate(kinds(), file)).as("equal to the code").isTrue();
        XMIResource renamed = kinds();
        ((EDataType) renamed.getEObject(TEXT)).setName("String");
        assertThat(StandardSets.upToDate(renamed, file)).as("a renamed element").isFalse();
        assertThat(Files.readString(file)).as("the file stays until it is written again").contains("name=\"Text\"");
    }

    /**
     * A model file refers to two elements of the set as {@code href="<uri>#<uuid>"} and to one of a set whose URI is
     * not mapped. Read in a fresh resource set where the set is mapped, not loaded, the references to it are read from
     * its file; the reference to the other set stays unresolved, and no URI of the network is opened.
     */
    @Test
    void aModelFileRefersToTheSetByUriAndUuidAndNothingIsFetchedFromTheNetwork() throws Exception {
        Path setFile = dir.resolve("kinds.xmi");
        Files.writeString(setFile, StandardSets.text(kinds()), StandardCharsets.UTF_8);
        URL source = setFile.toUri().toURL();

        ResourceSet writing = new ResourceSetImpl();
        StandardSets.map(writing, URI_OF_KINDS, source, EcorePackage.eINSTANCE);
        EPackage kinds = StandardSets.load(writing, URI_OF_KINDS, EPackage.class);
        Resource other = new XMIResourceImpl(URI.createURI(URI_OF_OTHER));
        writing.getResources().add(other);
        EPackage others = pkg("others");
        other.getContents().add(others);
        String flag = EcoreUtil.generateUUID();
        StandardSets.id(type(others, "Flag"), flag);
        Path modelFile = dir.resolve("readings.xmi");
        Resource model = new XMIResourceImpl(URI.createFileURI(modelFile.toString()));
        writing.getResources().add(model);
        EPackage readings = pkg("readings");
        EClass reading = F.createEClass();
        reading.setName("Reading");
        readings.getEClassifiers().add(reading);
        reading.getEStructuralFeatures().add(attribute("station", (EDataType) kinds.getEClassifier("Text")));
        reading.getEStructuralFeatures().add(attribute("count", (EDataType) kinds.getEClassifier("WholeNumber")));
        reading.getEStructuralFeatures().add(attribute("checked", (EDataType) others.getEClassifier("Flag")));
        model.getContents().add(readings);
        model.save(Map.of());
        assertThat(Files.readString(modelFile)).as("the references by the URI of the set and the UUIDs").contains(
                "href=\"" + URI_OF_KINDS + "#" + TEXT + "\"", "href=\"" + URI_OF_KINDS + "#" + WHOLE + "\"",
                "href=\"" + URI_OF_OTHER + "#" + flag + "\"");

        ResourceSet again = new ResourceSetImpl();
        List<URI> asked = new ArrayList<>();
        again.getURIConverter().getURIHandlers().add(0, new URIHandlerImpl() {

            @Override
            public boolean canHandle(URI uri) {
                return "http".equals(uri.scheme()) || "https".equals(uri.scheme());
            }

            @Override
            public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
                asked.add(uri);
                throw new IOException("no network in this test: " + uri);
            }
        });
        // a factory for every XMI file, so that EMF tries to read a set it does not know
        again.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        StandardSets.map(again, URI_OF_KINDS, source, EcorePackage.eINSTANCE);
        Resource read = again.getResource(URI.createFileURI(modelFile.toString()), true);
        EClass readAgain = (EClass) ((EPackage) read.getContents().get(0)).getEClassifiers().get(0);
        EClassifier station = readAgain.getEStructuralFeatures().get(0).getEType();
        assertThat(station.eIsProxy()).as("station is resolved").isFalse();
        assertThat(station.eResource().getURI().toString()).as("from the set under its URI").isEqualTo(URI_OF_KINDS);
        assertThat(station.getName()).as("station is of the set's text kind").isEqualTo("Text");
        assertThat(readAgain.getEStructuralFeatures().get(1).getEType().getName()).as("count is of the whole kind")
                .isEqualTo("WholeNumber");
        assertThat(readAgain.getEStructuralFeatures().get(2).getEType().eIsProxy())
                .as("the reference to a set that is not mapped stays unresolved").isTrue();
        assertThat(asked).as("only the URI that is not mapped was asked for, and not opened")
                .containsExactly(URI.createURI(URI_OF_OTHER));
        assertThat(StandardSets.load(again, URI_OF_KINDS, EPackage.class).eResource())
                .as("the set read on demand is the set").isSameAs(station.eResource());
        assertThatThrownBy(() -> StandardSets.map(new ResourceSetImpl(), URI_OF_KINDS, null))
                .as("a set not on the class path").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(URI_OF_KINDS).hasMessageContaining("not on the class path");
        assertThatThrownBy(() -> StandardSets.load(new ResourceSetImpl(), URI_OF_KINDS, EPackage.class))
                .as("a set not mapped").isInstanceOf(IllegalStateException.class).hasMessageContaining("not mapped");
    }

    /**
     * The UUIDs of every standard set of the repository, the XMI files of the model modules: each of the short form,
     * none in two places, within a set or across the sets.
     */
    @Test
    void theUuidsOfTheStandardSetsOfTheRepositoryAreAllDifferent() throws Exception {
        Path models = Path.of(System.getProperty("user.dir"), "..", "..", "model").normalize();
        List<Path> sets;
        try (Stream<Path> files = Files.walk(models)) {
            sets = files.filter(f -> f.toString().endsWith(".xmi") && f.toString().contains("/src/main/resources/"))
                    .sorted().toList();
        }
        assertThat(sets).as("the standard sets under " + models).isNotEmpty();
        Pattern xmiId = Pattern.compile("xmi:id=\"([^\"]*)\"");
        Map<String, Path> seen = new HashMap<>();
        for (Path set : sets) {
            Matcher m = xmiId.matcher(Files.readString(set));
            while (m.find()) {
                String id = m.group(1);
                assertThat(id).as("an id of " + set).matches(StandardSets.SHORT_UUID);
                assertThat(seen.put(id, set)).as("the id " + id + " of " + set + " is in no other place").isNull();
            }
        }
    }

    private static EPackage pkg(String name) {
        EPackage p = F.createEPackage();
        p.setName(name);
        return p;
    }

    private static EDataType type(EPackage owner, String name) {
        EDataType t = F.createEDataType();
        t.setName(name);
        owner.getEClassifiers().add(t);
        return t;
    }

    private static EAttribute attribute(String name, EDataType type) {
        EAttribute a = F.createEAttribute();
        a.setName(name);
        a.setEType(type);
        return a;
    }
}
