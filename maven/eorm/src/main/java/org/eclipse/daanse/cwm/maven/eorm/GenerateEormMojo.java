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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator;
import org.eclipse.daanse.cwm.orm.fennec.CwmOrmToEorm;
import org.eclipse.daanse.cwm.orm.fennec.EormGenerator;
import org.eclipse.daanse.cwm.orm.fennec.EormNotes;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.orm.fennec.InvalidMappingException;
import org.eclipse.daanse.cwm.orm.fennec.Note;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Converts the CWM-ORM mapping set of a CWM XMI into a Fennec {@code .eorm} file, written next to
 * the {@code .ecore} files of the mapped class model.
 *
 * <p>The EORM model refers to Ecore classes and features, so the Ecore model of the class model is
 * an input: {@link #ecoreFile} and {@link #ecoreFiles} name the {@code .ecore} files, written by any
 * tool; an Ecore model generated in the same build is written in {@code generate-sources}. Classes
 * and features are found by their names ({@link #packageMap} for packages named differently); the files are
 * copied into {@link #outputDirectory} when they live elsewhere. This goal runs in
 * {@code generate-resources}, after {@code generate-sources}; bound to the same phase, goals run in
 * the order of the POM.</p>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class GenerateEormMojo extends AbstractMojo {

    /** The CWM XMI holding the mapping set; files it references by relative {@code href} are loaded with it. */
    @Parameter(property = "fennec-eorm.modelFile", required = true)
    private File modelFile;

    /** The name of the mapping set, when the model holds more than one. */
    @Parameter(property = "fennec-eorm.mapping")
    private String mapping;

    /** The {@code .ecore} of the mapped class model; with {@link #ecoreFiles} at least one is required. */
    @Parameter(property = "fennec-eorm.ecoreFile")
    private File ecoreFile;

    /**
     * Further {@code .ecore} files of the class model, e.g. one per package; files the given ones
     * reference are loaded anyway. Each must exist.
     */
    @Parameter
    private List<File> ecoreFiles;

    /**
     * CWM packages whose EPackage has another name: the key names the CWM package by its qualified
     * name or a trailing part of it, the value the EPackage by namespace URI, qualified name or
     * name. Packages without entry are matched by name.
     */
    @Parameter
    private Map<String, String> packageMap;

    /** Where the {@code .eorm} and the {@code .ecore} files next to it are written. */
    @Parameter(property = "fennec-eorm.outputDirectory",
            defaultValue = "${project.build.directory}/generated-resources/eorm")
    private File outputDirectory;

    /** File name of the {@code .eorm}; default: the model file's name with the extension {@code .eorm}. */
    @Parameter(property = "fennec-eorm.eormName")
    private String eormName;

    /** Reject a mapping set that breaks the explicit profile; {@code false} converts it anyway and reports. */
    @Parameter(property = "fennec-eorm.failOnValidation", defaultValue = "true")
    private boolean failOnValidation;

    /** Add {@link #outputDirectory} as a resource directory, so the files land in the jar. */
    @Parameter(property = "fennec-eorm.attachResource", defaultValue = "true")
    private boolean attachResource;

    /** Interfaces of further generated EPackages to register before loading. */
    @Parameter
    private List<String> extraPackages;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new MojoFailureException("modelFile does not exist: " + modelFile);
        }
        ClassLoader loader = getClass().getClassLoader();
        CwmPackages.registerKnown(loader);
        if (extraPackages != null) {
            for (String name : extraPackages) {
                try {
                    CwmPackages.register(name, loader);
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
        EntityMappings cwm = mappingSet();
        String name = eormName != null && !eormName.isBlank() ? eormName : baseName(modelFile) + ".eorm";
        Path dir = outputDirectory.toPath();
        boolean lenient = !failOnValidation;

        List<Path> given = new ArrayList<>();
        if (ecoreFile != null) {
            given.add(ecoreFile.toPath());
        }
        if (ecoreFiles != null) {
            ecoreFiles.forEach(f -> given.add(f.toPath()));
        }
        if (given.isEmpty()) {
            throw new MojoFailureException("fennec-eorm: no .ecore of the class model: set ecoreFile or ecoreFiles");
        }
        for (Path f : given) {
            if (!f.toFile().isFile()) {
                throw new MojoFailureException("fennec-eorm: .ecore file does not exist: " + f);
            }
        }
        EormGenerator.Output out;
        try {
            out = EormGenerator.withEcore(cwm, given, packageMap == null ? Map.of() : packageMap, dir, name, lenient);
        } catch (InvalidMappingException e) {
            for (OrmModelValidator.Finding f : e.findings()) {
                getLog().error("fennec-eorm: " + f);
            }
            throw new MojoFailureException(e.getMessage(), e);
        }
        report(out.result().notes());
        List<Note> errors = out.result().errors().stream().filter(n -> !EormNotes.VALIDATION.equals(n.rule()))
                .toList();
        if (!errors.isEmpty()) {
            throw new MojoFailureException(errors.size() + " conversion error(s), first: " + errors.get(0));
        }
        getLog().info("fennec-eorm: wrote " + out.eorm() + " next to " + out.ecoreFiles());
        if (attachResource && project != null) {
            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getAbsolutePath());
            project.addResource(resource);
        }
    }

    private EntityMappings mappingSet() throws MojoExecutionException, MojoFailureException {
        ResourceSet rs = CwmPackages.newResourceSet();
        List<EntityMappings> all;
        try {
            all = CwmOrmToEorm.mappingsIn(CwmPackages.load(rs, modelFile.toPath()));
            EcoreUtil.resolveAll(rs);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cannot load " + modelFile + ": " + e.getMessage(), e);
        }
        List<EntityMappings> named = mapping == null ? all
                : all.stream().filter(m -> mapping.equals(m.getName())).toList();
        if (named.size() != 1) {
            throw new MojoFailureException(modelFile + " holds " + named.size() + " mapping set(s)"
                    + (mapping == null ? "; name one with <mapping>" : " named '" + mapping + "'"));
        }
        return named.get(0);
    }

    private void report(List<Note> notes) {
        for (Note n : notes) {
            switch (n.severity()) {
            case ERROR -> getLog().error("fennec-eorm: " + n);
            case WARNING -> getLog().warn("fennec-eorm: " + n);
            default -> getLog().info("fennec-eorm: " + n);
            }
        }
    }

    private static String baseName(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
