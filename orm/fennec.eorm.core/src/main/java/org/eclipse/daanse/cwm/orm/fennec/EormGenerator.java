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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * The whole way from a CWM-ORM mapping set to files Fennec reads: the {@code .eorm} next to the
 * {@code .ecore} files of the mapped class model, in one directory, with relative {@code href}s
 * between them. The {@code .ecore} files are given — written by any tool, as long as the names
 * match the CWM class model ({@link EcoreIndex}); they are copied into the output directory when
 * they live elsewhere.
 */
public final class EormGenerator {

    /**
     * What was written.
     *
     * @param eorm       the {@code .eorm} file
     * @param ecoreFiles the {@code .ecore} files next to it
     * @param result     the EORM conversion result
     */
    public record Output(Path eorm, List<Path> ecoreFiles, CwmOrmToEorm.Result result) {
    }

    private EormGenerator() {
    }

    /**
     * Loads {@code ecoreFile} and the {@code .ecore} files it references, copies them into
     * {@code dir} if they are elsewhere, then converts and writes the mapping as
     * {@code dir/eormFileName}.
     *
     * @throws InvalidMappingException if the mapping breaks the explicit profile and not {@code lenient}
     */
    public static Output withEcore(EntityMappings cwm, Path ecoreFile, Path dir, String eormFileName,
            boolean lenient) {
        return withEcore(cwm, List.of(ecoreFile), dir, eormFileName, lenient);
    }

    /**
     * Like {@link #withEcore(EntityMappings, Path, Path, String, boolean)} for a class model
     * written into several {@code .ecore} files (e.g. one per package): loads all of them and those
     * they reference. The mapping set stays one {@code .eorm}.
     *
     * @throws InvalidMappingException if the mapping breaks the explicit profile and not {@code lenient}
     */
    public static Output withEcore(EntityMappings cwm, List<Path> ecoreFiles, Path dir, String eormFileName,
            boolean lenient) {
        return withEcore(cwm, ecoreFiles, Map.of(), dir, eormFileName, lenient);
    }

    /**
     * Like {@link #withEcore(EntityMappings, List, Path, String, boolean)} with CWM packages mapped
     * to EPackages explicitly, for an Ecore model whose packages are named differently
     * ({@link EcoreIndex#of(List, Map)}).
     *
     * @throws InvalidMappingException if the mapping breaks the explicit profile and not {@code lenient}
     */
    public static Output withEcore(EntityMappings cwm, List<Path> ecoreFiles, Map<String, String> packageMap,
            Path dir, String eormFileName, boolean lenient) {
        if (!lenient) {
            CwmOrmToEorm.requireExplicit(cwm);
        }
        ResourceSet rs = EormWriter.newResourceSet();
        for (Path ecoreFile : ecoreFiles) {
            rs.getResource(fileUri(ecoreFile), true);
        }
        EcoreUtil.resolveAll(rs);
        List<Resource> ecoreResources = new ArrayList<>();
        List<EPackage> packages = new ArrayList<>();
        for (Resource r : List.copyOf(rs.getResources())) {
            if (!r.getContents().isEmpty() && r.getContents().get(0) instanceof EPackage p && r.getURI().isFile()) {
                ecoreResources.add(r);
                packages.add(p);
            }
        }
        List<Path> written = new ArrayList<>();
        Path target = dir.toAbsolutePath().normalize();
        // all files move together, so the relative hrefs between them stay valid
        boolean copy = ecoreResources.stream().anyMatch(
                r -> !target.equals(Path.of(r.getURI().toFileString()).toAbsolutePath().normalize().getParent()));
        try {
            for (Resource r : ecoreResources) {
                Path file = target.resolve(r.getURI().lastSegment());
                if (copy) {
                    r.setURI(fileUri(file));
                }
                written.add(file);
            }
            if (copy) {
                java.nio.file.Files.createDirectories(target);
                Map<String, Object> options = new HashMap<>();
                options.put(XMLResource.OPTION_ENCODING, "UTF-8");
                options.put(XMLResource.OPTION_LINE_WIDTH, 120);
                for (Resource r : ecoreResources) {
                    r.save(options);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CwmOrmToEorm.Result result = CwmOrmToEorm.convert(cwm, EcoreIndex.of(packages, packageMap), lenient);
        Path eorm = EormWriter.save(result.eorm(), target.resolve(eormFileName));
        return new Output(eorm, written, result);
    }

    private static URI fileUri(Path file) {
        return URI.createFileURI(file.toAbsolutePath().normalize().toString());
    }
}
