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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.emf.codegen.ecore.genmodel.GenJDKLevel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * Writes the EPackages of a conversion: one {@code .ecore} per root EPackage into one directory,
 * cross references between them as relative {@code href}s, optionally a {@code .genmodel} for EMF
 * code generation. A file is named after its package unless a file name is given for it; with
 * {@link Options#splitPackages()} every converted package is a root and so gets a file of its own.
 *
 * <p>After saving, every root EPackage is the content of a file resource; Ecore elements keep
 * their identity, so a following step (e.g. an EORM mapping) refers to them and serialises those
 * references relative to the written files.</p>
 */
public final class EcoreWriter {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";

    /**
     * Settings of the {@code .genmodel}.
     *
     * @param basePackage    Java base package of the generated code, e.g. {@code org.example}
     * @param prefix         class name prefix of the package factory/package classes; only
     *                       applied when there is a single root package, {@code null} keeps the
     *                       default (the capitalised package name)
     * @param modelDirectory source folder of the generated code, e.g. {@code /my.project/src/main/java}
     * @param modelPluginId  plugin (bundle) id written into the genmodel
     */
    public record GenModelSettings(String basePackage, String prefix, String modelDirectory, String modelPluginId) {

        /** Settings without a base package, writing to {@code /model/src/main/java}. */
        public static GenModelSettings defaults() {
            return new GenModelSettings(null, null, "/model/src/main/java", "model");
        }
    }

    private EcoreWriter() {
    }

    /**
     * Saves the root EPackages of a result into {@code dir}, each named after its package;
     * {@code genModel} {@code null} writes no {@code .genmodel}.
     */
    public static List<Path> save(Result result, Path dir, GenModelSettings genModel) {
        return save(result.ePackages(), dir, genModel, Map.of());
    }

    /**
     * Saves root EPackages into {@code dir} and returns the written files, the {@code .genmodel}
     * last; the {@code .genmodel} is named after the first {@code .ecore}.
     *
     * @param fileNames file name per package: the key names the package by its qualified CWM name
     *                  (dot separated) or a trailing part of it, like {@link Options#packages()},
     *                  else by the name of the root EPackage; {@code .ecore} is appended when the
     *                  value has no extension. A package without entry gets {@code <name>.ecore},
     *                  a clash of such names a counter.
     * @throws IllegalArgumentException if a key names no root package or several, or two packages
     *                                  would get the same named file
     */
    public static List<Path> save(List<EPackage> roots, Path dir, GenModelSettings genModel,
            Map<String, String> fileNames) {
        Map<EPackage, String> named = fileNames(roots, fileNames == null ? Map.of() : fileNames);
        try {
            Files.createDirectories(dir);
            ResourceSet rs = CwmPackages.newResourceSet();
            List<Path> files = new ArrayList<>();
            List<Resource> resources = new ArrayList<>();
            Set<String> taken = new HashSet<>();
            named.values().forEach(n -> taken.add(n.toLowerCase(Locale.ROOT)));
            for (EPackage p : roots) {
                String fileName = named.get(p);
                if (fileName == null) {
                    String base = p.getName();
                    fileName = base + ".ecore";
                    for (int i = 2; !taken.add(fileName.toLowerCase(Locale.ROOT)); i++) {
                        fileName = base + "_" + i + ".ecore";
                    }
                }
                Path file = dir.resolve(fileName);
                Resource r = rs.createResource(fileUri(file));
                r.getContents().add(p);
                resources.add(r);
                files.add(file);
            }
            if (genModel != null && !roots.isEmpty()) {
                String first = files.get(0).getFileName().toString();
                Path file = dir.resolve(first.substring(0, first.length() - ".ecore".length()) + ".genmodel");
                Resource r = rs.createResource(fileUri(file));
                r.getContents().add(genModel(roots, files, genModel));
                resources.add(r);
                files.add(file);
            }
            for (Resource r : resources) {
                r.save(options());
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GenModel genModel(List<EPackage> roots, List<Path> ecoreFiles, GenModelSettings s) {
        GenModel gm = GenModelFactory.eINSTANCE.createGenModel();
        gm.setModelDirectory(s.modelDirectory());
        gm.setModelPluginID(s.modelPluginId());
        gm.setModelName(capitalize(roots.get(0).getName()));
        gm.setImporterID("org.eclipse.emf.importer.ecore");
        gm.setComplianceLevel(GenJDKLevel.JDK170_LITERAL);
        gm.setRootExtendsClass("org.eclipse.emf.ecore.impl.MinimalEObjectImpl$Container");
        gm.setOperationReflection(true);
        for (Path f : ecoreFiles) {
            gm.getForeignModel().add(f.getFileName().toString());
        }
        gm.initialize(roots);
        for (GenPackage gp : gm.getGenPackages()) {
            // a package taken out of the package around it keeps the Java package it had inside it;
            // without a base package here, the one the conversion annotated holds already
            EPackage ep = gp.getEcorePackage();
            String around = EcoreUtil.getAnnotation(ep, CwmAnnotations.PACKAGE, CwmAnnotations.KEY_SUPER_PACKAGE);
            String base = s.basePackage() == null || s.basePackage().isBlank() ? null : s.basePackage();
            if (base != null) {
                base = around == null ? base : base + "." + around;
            } else {
                base = EcoreUtil.getAnnotation(ep, GENMODEL, "basePackage");
                if (base == null) {
                    base = around;
                }
            }
            if (base != null) {
                gp.setBasePackage(base);
            }
            if (s.prefix() != null && !s.prefix().isBlank() && roots.size() == 1) {
                gp.setPrefix(s.prefix());
            }
        }
        return gm;
    }

    /** The file name of each root package that {@code fileNames} names. */
    private static Map<EPackage, String> fileNames(List<EPackage> roots, Map<String, String> fileNames) {
        Map<EPackage, String> out = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, String> e : fileNames.entrySet()) {
            List<EPackage> hits = roots.stream().filter(p -> names(p, e.getKey())).toList();
            if (hits.size() != 1) {
                throw new IllegalArgumentException("file name entry '" + e.getKey() + "' names " + hits.size()
                        + " of the written packages " + roots.stream().map(EcoreWriter::label).toList()
                        + "; name exactly one, by its qualified name or a trailing part of it");
            }
            String name = e.getValue().endsWith(".ecore") ? e.getValue() : e.getValue() + ".ecore";
            if (!used.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("file name " + name + " is given to two packages");
            }
            if (out.put(hits.get(0), name) != null) {
                throw new IllegalArgumentException("package " + label(hits.get(0)) + " is given two file names");
            }
        }
        return out;
    }

    private static boolean names(EPackage p, String key) {
        String path = CwmAnnotations.path(p);
        return key.equals(p.getName()) || path != null && (path.equals(key) || path.endsWith("." + key));
    }

    private static String label(EPackage p) {
        String path = CwmAnnotations.path(p);
        return path != null ? path : p.getName();
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "Model" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static URI fileUri(Path file) {
        return URI.createFileURI(file.toAbsolutePath().normalize().toString());
    }

    private static Map<String, Object> options() {
        Map<String, Object> o = new HashMap<>();
        o.put(XMLResource.OPTION_ENCODING, "UTF-8");
        o.put(XMLResource.OPTION_LINE_WIDTH, 120);
        return o;
    }
}
