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

import java.io.File;
import java.nio.file.Path;
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
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.emf.ecore.CwmToEcore;
import org.eclipse.daanse.cwm.emf.ecore.EcoreWriter;
import org.eclipse.daanse.cwm.emf.ecore.Note;
import org.eclipse.daanse.cwm.emf.ecore.Options;
import org.eclipse.daanse.cwm.emf.ecore.Result;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Converts the class model of a CWM XMI into {@code .ecore} files, one per root EPackage, and
 * optionally a {@code .genmodel}. The EPackages carry the GenModel annotations EMF code generation
 * reads ({@code prefix}, {@code basePackage}, and what {@link #genModelAnnotations} adds), so a
 * generator that takes an {@code .ecore} alone finds them.
 *
 * <p>Runs in {@code generate-sources}, so a following goal in the same build (EMF code generation,
 * {@code fennec-eorm:generate} in {@code generate-resources}) finds the files. Error notes
 * (unknown data type, n-ary association, a type outside the converted packages, an invalid
 * result) fail the build; warnings only with {@link #failOnNotes}.</p>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateEcoreMojo extends AbstractMojo {

    /** The CWM XMI; files it references by relative {@code href} are loaded with it. */
    @Parameter(property = "cwm-ecore.modelFile", required = true)
    private File modelFile;

    /**
     * Qualified names (dot separated, or their trailing part) of the packages to convert. Empty:
     * every package that holds classes, data types or enumerations.
     */
    @Parameter
    private List<String> packages;

    /** Where the {@code .ecore} files (and the {@code .genmodel}) are written. */
    @Parameter(property = "cwm-ecore.outputDirectory", defaultValue = "${project.build.directory}/generated-ecore")
    private File outputDirectory;

    /** Prefix of the generated namespace URIs. */
    @Parameter(property = "cwm-ecore.nsUriBase", defaultValue = Options.DEFAULT_NS_URI_BASE)
    private String nsUriBase;

    /** Additional CWM data type names and their Java instance classes, e.g. {@code <Text>java.lang.String</Text>}. */
    @Parameter
    private Map<String, String> dataTypes;

    /** Map data types onto the {@code EcorePackage} data types instead of generating EDataTypes. */
    @Parameter(property = "cwm-ecore.reuseEcoreDataTypes", defaultValue = "false")
    private boolean reuseEcoreDataTypes;

    /**
     * Annotation source and delegate URI of the OCL invariants and OCL operation bodies; the
     * default is classic Eclipse OCL, {@code http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot}
     * selects Pivot OCL.
     */
    @Parameter(property = "cwm-ecore.oclDelegateUri", defaultValue = Options.DEFAULT_OCL_DELEGATE_URI)
    private String oclDelegateUri;

    /**
     * Make every converted package a root EPackage with a file of its own instead of a
     * sub-package in the file of the package around it; namespace URIs, names and the Java
     * packages of the genmodel stay the same.
     */
    @Parameter(property = "cwm-ecore.splitPackages", defaultValue = "false")
    private boolean splitPackages;

    /**
     * File names of the written packages, e.g. {@code <shop.enumerations>enums.ecore</shop.enumerations>}:
     * the key names a package by its qualified name or a trailing part of it (or the name of the
     * root EPackage), {@code .ecore} is appended when missing; packages without entry are named
     * after themselves.
     */
    @Parameter
    private Map<String, String> fileNames;

    /** Also write a {@code .genmodel}. */
    @Parameter(property = "cwm-ecore.genModel", defaultValue = "false")
    private boolean genModel;

    /**
     * Java base package of the generated code: written as GenModel {@code basePackage} onto every
     * root EPackage and into the {@code .genmodel}.
     */
    @Parameter(property = "cwm-ecore.basePackage")
    private String basePackage;

    /**
     * Further GenModel properties written onto every root EPackage, e.g.
     * {@code <oSGiCompatible>true</oSGiCompatible>} or {@code <copyrightText>…</copyrightText>};
     * {@code basePackage} and {@code prefix} come from their own settings.
     */
    @Parameter
    private Map<String, String> genModelAnnotations;

    /** Write the CWM descriptions as GenModel {@code documentation}, which ends up in the Javadoc. */
    @Parameter(property = "cwm-ecore.documentation", defaultValue = "true")
    private boolean documentation;

    /** Convert the OCL and Java constraints of the class model into invariants. */
    @Parameter(property = "cwm-ecore.constraints", defaultValue = "true")
    private boolean constraints;

    /** Fail the build on warning notes too (error notes always fail it). */
    @Parameter(property = "cwm-ecore.failOnNotes", defaultValue = "false")
    private boolean failOnNotes;

    /**
     * Interfaces of further generated EPackages to register before loading, e.g.
     * {@code org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage}; their jars come as
     * dependencies of the plugin declaration.
     */
    @Parameter
    private List<String> extraPackages;

    /** Add {@link #outputDirectory} as a resource directory, so the {@code .ecore} files land in the jar. */
    @Parameter(property = "cwm-ecore.attachResource", defaultValue = "true")
    private boolean attachResource;

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

        ResourceSet rs = CwmPackages.newResourceSet();
        org.eclipse.emf.ecore.resource.Resource model;
        try {
            model = CwmPackages.load(rs, modelFile.toPath());
            EcoreUtil.resolveAll(rs);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cannot load " + modelFile + ": " + e.getMessage(), e);
        }

        Options options = Options.defaults().withNsUriBase(nsUriBase).withDataTypes(dataTypes)
                .withReuseEcoreDataTypes(reuseEcoreDataTypes).withPackages(packages)
                .withOclDelegateUri(oclDelegateUri).withSplitPackages(splitPackages)
                .withDocumentation(documentation).withConstraints(constraints).withBasePackage(basePackage)
                .withGenModelAnnotations(genModelAnnotations);
        Result result = CwmToEcore.convert(CwmToEcore.packagesOf(model), options);
        report(result.notes());
        if (result.hasErrors()) {
            throw new MojoFailureException(result.errors().size() + " conversion error(s) in " + modelFile
                    + ", first: " + result.errors().get(0));
        }
        if (failOnNotes && result.notes().stream().anyMatch(n -> n.severity() == Note.Severity.WARNING)) {
            throw new MojoFailureException("conversion warnings in " + modelFile + " and failOnNotes=true");
        }
        if (result.ePackages().isEmpty()) {
            throw new MojoFailureException("no package with classes, data types or enumerations in " + modelFile);
        }

        String artifactId = project == null ? "model" : project.getArtifactId();
        List<Path> written;
        try {
            EcoreWriter.GenModelSettings settings = genModel
                    ? new EcoreWriter.GenModelSettings(basePackage, null, "/" + artifactId + "/src/main/java",
                            artifactId)
                    : null;
            written = EcoreWriter.save(result.ePackages(), outputDirectory.toPath(), settings, fileNames);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("cwm-ecore: " + e.getMessage(), e);
        }
        for (Path p : written) {
            getLog().info("cwm-ecore: wrote " + p);
        }
        if (attachResource && project != null) {
            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getAbsolutePath());
            project.addResource(resource);
        }
    }

    private void report(List<Note> notes) {
        for (Note n : notes) {
            switch (n.severity()) {
            case ERROR -> getLog().error("cwm-ecore: " + n);
            case WARNING -> getLog().warn("cwm-ecore: " + n);
            default -> getLog().info("cwm-ecore: " + n);
            }
        }
    }
}
