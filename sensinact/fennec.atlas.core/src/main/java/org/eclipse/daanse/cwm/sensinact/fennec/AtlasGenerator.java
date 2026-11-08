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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standalone.EcoreIndex;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.event.atlas.model.mapping.MappingProfile;

/**
 * The whole way to files: loads the {@code .ecore} files of the class model, converts the
 * mappings and writes {@code profiles/<provider>-profile.xmi} first, then
 * {@code mappings/<mapping>.xmi} — the two directories of a Fennec runtime, profiles before the
 * mappings that refer to them. The {@code .ecore} files are not copied: a Fennec runtime has their
 * EPackages registered, and the mappings refer to them by namespace URI.
 */
public final class AtlasGenerator {

    /**
     * What was written.
     *
     * @param mappings the mapping files
     * @param profiles the profile files
     * @param result   the conversion
     */
    public record Output(List<Path> mappings, List<Path> profiles, CwmToAtlas.Result result) {
    }

    private AtlasGenerator() {
    }

    /** Converts and writes into {@code dir}, the Ecore model given by its files. */
    public static Output withEcore(Collection<ProviderMapping> mappings, List<Path> ecoreFiles, Path dir,
            Options options) {
        return withEcore(mappings, ecoreFiles, dir, options, Map.of(), false);
    }

    /**
     * Converts and writes into {@code dir}. {@code packageMap} maps CWM packages to EPackages where
     * the names differ ({@link EcoreIndex#of(List, Map)}). With an error — of the validation unless
     * {@code lenient}, or of the conversion — nothing is written.
     */
    public static Output withEcore(Collection<ProviderMapping> mappings, List<Path> ecoreFiles, Path dir,
            Options options, Map<String, String> packageMap, boolean lenient) {
        List<EPackage> packages = loadEcore(ecoreFiles);
        CwmToAtlas.Result result = CwmToAtlas.convert(mappings, EcoreIndex.of(packages, packageMap), options, lenient);
        if (result.notes().stream().anyMatch(n -> n.severity() == Note.Severity.ERROR
                && !(lenient && AtlasNotes.VALIDATION.equals(n.rule())))) {
            return new Output(List.of(), List.of(), result);
        }
        ResourceSet out = AtlasWriter.newResourceSet();
        List<Path> profiles = new ArrayList<>();
        for (MappingProfile profile : result.profiles()) {
            profiles.add(AtlasWriter.save(out, profile,
                    dir.resolve(AtlasWriter.PROFILES).resolve(profile.getProfileId() + "-profile.xmi")));
        }
        List<Path> files = new ArrayList<>();
        for (var mapping : result.mappings()) {
            files.add(AtlasWriter.save(out, mapping, dir.resolve(AtlasWriter.MAPPINGS).resolve(mapping.getMid() + ".xmi")));
        }
        return new Output(files, profiles, result);
    }

    /**
     * The EPackages of {@code .ecore} files and of the files they refer to, each registered under
     * its namespace URI in the resource set that loaded them.
     */
    public static List<EPackage> loadEcore(List<Path> ecoreFiles) {
        ResourceSet rs = AtlasWriter.newResourceSet();
        for (Path file : ecoreFiles) {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("no .ecore file: " + file);
            }
            rs.getResource(URI.createFileURI(file.toAbsolutePath().normalize().toString()), true);
        }
        EcoreUtil.resolveAll(rs);
        List<EPackage> packages = new ArrayList<>();
        for (Resource r : List.copyOf(rs.getResources())) {
            for (EObject root : r.getContents()) {
                if (root instanceof EPackage p && !packages.contains(p) && !p.getNsURI().startsWith("http://www.eclipse.org/emf/")) {
                    packages.add(p);
                    rs.getPackageRegistry().put(p.getNsURI(), p);
                }
            }
        }
        return packages;
    }
}
