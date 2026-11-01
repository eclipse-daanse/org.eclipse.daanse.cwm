/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

/** The test corpus under {@code src/test/resources/corpus} (see its README). */
final class Corpus {

    private Corpus() {
    }

    static Path dir() {
        try {
            return Path.of(Corpus.class.getResource("/corpus").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The 23 files holding a mapping set, relative to {@link #dir()}. */
    static List<String> files() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir().resolve("orm-tutorials"))) {
            s.sorted().forEach(d -> out.add("orm-tutorials/" + d.getFileName() + "/mapping.cwmorm"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** The one mapping set of a corpus file, loaded with everything it references. */
    static EntityMappings mapping(String file) {
        CwmPackages.registerKnown();
        ResourceSet rs = CwmPackages.newResourceSet();
        Resource r = CwmPackages.load(rs, dir().resolve(file));
        EcoreUtil.resolveAll(rs);
        List<EntityMappings> all = CwmOrmToEorm.mappingsIn(r);
        if (all.size() != 1) {
            throw new IllegalStateException(file + " holds " + all.size() + " mapping sets");
        }
        return all.get(0);
    }

    /**
     * The {@code .ecore} files of the class model a corpus file maps, below {@code /ecore/corpus};
     * written by a CWM-to-Ecore conversion and stripped of its CWM annotations, so they bind by names
     * only.
     */
    static List<Path> ecoreFiles(String file) {
        try (Stream<Path> s = Files.list(Fixture.ecoreDir().resolve("corpus").resolve(baseName(file)))) {
            return s.filter(p -> p.toString().endsWith(".ecore")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The root EPackages of {@link #ecoreFiles}. */
    static List<EPackage> ecorePackages(String file) {
        ResourceSet rs = EormWriter.newResourceSet();
        List<EPackage> out = new ArrayList<>();
        for (Path p : ecoreFiles(file)) {
            out.add((EPackage) rs.getResource(URI.createFileURI(p.toString()), true).getContents().get(0));
        }
        EcoreUtil.resolveAll(rs);
        return out;
    }

    /** The name of the output of a corpus file without extension. */
    static String baseName(String file) {
        String name = eormName(file);
        return name.substring(0, name.length() - ".eorm".length());
    }

    /** A file name for the output of a corpus file. */
    static String eormName(String file) {
        String name = Path.of(file).getFileName().toString();
        if (name.equals("mapping.cwmorm")) {
            name = Path.of(file).getParent().getFileName().toString();
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ".eorm";
    }
}
