/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.emf.ecore.CorpusModels.File;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Every corpus case: the notes are the expected ones; without errors the written files are the
 * expected ones, character for character, and they load again in a fresh resource set without
 * unresolved references, are valid by the {@code Diagnostician}, with symmetric opposites and
 * single-valued containers. With {@code -Dcwm.ecore.corpus.update=true} the corpus files and the
 * expected output are written anew first, to be reviewed in the diff.
 */
class CorpusTest {

    @BeforeAll
    static void update() throws IOException {
        if (CorpusCase.UPDATE) {
            for (File f : File.values()) {
                CorpusModels.write(f, CorpusCase.dir());
            }
            for (CorpusCase c : CorpusCase.values()) {
                writeExpected(c);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CorpusCase.class)
    void convertsToTheExpectedFiles(CorpusCase c, @TempDir Path out) throws IOException {
        Result r = c.convert();

        assertThat(notes(r)).isEqualTo(Files.readString(c.expected().resolve("notes.txt")));
        if (r.hasErrors()) {
            assertThat(fileNames(c.expected())).containsExactly("notes.txt");
            return;
        }
        List<Path> written = EcoreWriter.save(r.ePackages(), out, c.genModel, c.fileNames);
        List<String> expected = new ArrayList<>(fileNames(c.expected()));
        expected.remove("notes.txt");
        assertThat(written).extracting(p -> p.getFileName().toString()).containsExactlyInAnyOrderElementsOf(expected);
        for (Path f : written) {
            assertThat(Files.readString(f)).as(f.getFileName().toString())
                    .isEqualTo(Files.readString(c.expected().resolve(f.getFileName().toString())));
        }

        ResourceSet fresh = CwmPackages.newResourceSet();
        List<EPackage> reloaded = new ArrayList<>();
        for (Path f : written) {
            if (f.toString().endsWith(".ecore")) {
                reloaded.add((EPackage) CwmPackages.load(fresh, f).getContents().get(0));
            }
        }
        EcoreUtil.resolveAll(fresh);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(fresh)).as("unresolved proxies").isEmpty();
        for (EPackage p : reloaded) {
            Diagnostic d = Diagnostician.INSTANCE.validate(p);
            assertThat(d.getSeverity()).as(() -> "Diagnostician on " + p.getName() + ": " + d).isLessThan(Diagnostic.ERROR);
            for (TreeIterator<EObject> it = p.eAllContents(); it.hasNext();) {
                if (it.next() instanceof EReference ref) {
                    if (ref.getEOpposite() != null) {
                        assertThat(ref.getEOpposite().getEOpposite()).as("opposite of " + ref.getName()).isSameAs(ref);
                    }
                    if (ref.isContainer()) {
                        assertThat(ref.getUpperBound()).as("container " + ref.getName()).isEqualTo(1);
                    }
                }
            }
        }
    }

    /** The notes of a result, one line each. */
    static String notes(Result r) {
        return r.notes().stream().map(n -> n + "\n").collect(Collectors.joining());
    }

    static List<String> fileNames(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static void writeExpected(CorpusCase c) throws IOException {
        Path dir = c.expected();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.toList()) {
                    Files.delete(f);
                }
            }
        }
        Files.createDirectories(dir);
        Result r = c.convert();
        Files.writeString(dir.resolve("notes.txt"), notes(r));
        if (!r.hasErrors()) {
            EcoreWriter.save(r.ePackages(), dir, c.genModel, c.fileNames);
        }
    }
}
