/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.atlas;

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
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.sensinact.fennec.AtlasGenerator;
import org.eclipse.daanse.cwm.sensinact.fennec.AtlasNotes;
import org.eclipse.daanse.cwm.sensinact.fennec.CwmToAtlas;
import org.eclipse.daanse.cwm.sensinact.fennec.InvalidMappingException;
import org.eclipse.daanse.cwm.sensinact.fennec.Note;
import org.eclipse.daanse.cwm.sensinact.fennec.Options;

/**
 * Converts the CWM sensiNact mappings of a CWM XMI, with the recording and retention rules and the
 * units of their resources,
 * into Fennec event.atlas provider mappings and profiles: {@code mappings/<mapping>.xmi} and, with
 * {@link #profiles}, {@code profiles/<provider>-profile.xmi} below {@link #outputDirectory}.
 *
 * <p>The provider mappings refer to the classes and features of the source model as Ecore
 * elements, so the Ecore model of the source classes is an input: {@link #ecoreFile} and
 * {@link #ecoreFiles} name the {@code .ecore} files, written by any tool. Classes and features are
 * found by their names ({@link #packageMap} for packages named differently). The written files
 * refer to them by namespace URI, so the {@code .ecore} files are not copied; a Fennec runtime has
 * the source packages registered anyway. This goal runs in {@code generate-resources}, after an
 * Ecore model generated in {@code generate-sources}.</p>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class GenerateAtlasMojo extends AbstractMojo {

    /** The CWM XMI holding the provider mappings; files it references by relative {@code href} are loaded with it. */
    @Parameter(property = "fennec-atlas.modelFile", required = true)
    private File modelFile;

    /** The names of the provider mappings to convert; default: every provider mapping of the model file. */
    @Parameter
    private List<String> mappings;

    /** The {@code .ecore} of the source class model; with {@link #ecoreFiles} at least one is required. */
    @Parameter(property = "fennec-atlas.ecoreFile")
    private File ecoreFile;

    /**
     * Further {@code .ecore} files of the source class model, e.g. one per package; files the given
     * ones reference are loaded anyway. Each must exist.
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

    /** Where {@code mappings/} and {@code profiles/} are written. */
    @Parameter(property = "fennec-atlas.outputDirectory",
            defaultValue = "${project.build.directory}/generated-resources/atlas")
    private File outputDirectory;

    /**
     * One {@code UNIFIED} profile per provider, so that all mappings onto a provider build one
     * sensiNact model named after it; {@code false} writes no profiles, each mapping then builds
     * its own model, named after the mapping.
     */
    @Parameter(property = "fennec-atlas.profiles", defaultValue = "true")
    private boolean profiles;

    /** CWM data type name → Ecore data type name ({@code Float → EFloat}), before every other rule. */
    @Parameter
    private Map<String, String> dataTypes;

    /** Reject mappings the validators reject; {@code false} converts them anyway and reports. */
    @Parameter(property = "fennec-atlas.failOnValidation", defaultValue = "true")
    private boolean failOnValidation;

    /** Add {@link #outputDirectory} as a resource directory, so the files land in the jar. */
    @Parameter(property = "fennec-atlas.attachResource", defaultValue = "true")
    private boolean attachResource;

    /** Skips the goal. */
    @Parameter(property = "fennec-atlas.skip", defaultValue = "false")
    private boolean skip;

    /** Interfaces of further generated EPackages to register before loading. */
    @Parameter
    private List<String> extraPackages;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("fennec-atlas: skipped");
            return;
        }
        if (modelFile == null || !modelFile.isFile()) {
            throw new MojoFailureException("modelFile does not exist: " + modelFile);
        }
        List<Path> given = ecoreFiles();
        List<ProviderMapping> chosen = chosenMappings();
        Options options = Options.defaults().withProfiles(profiles)
                .withDataTypes(dataTypes == null ? Map.of() : dataTypes);
        AtlasGenerator.Output out;
        try {
            out = AtlasGenerator.withEcore(chosen, given, outputDirectory.toPath(), options,
                    packageMap == null ? Map.of() : packageMap, !failOnValidation);
        } catch (InvalidMappingException e) {
            e.findings().forEach(n -> getLog().error("fennec-atlas: " + n));
            throw new MojoFailureException(e.getMessage(), e);
        }
        report(out.result().notes());
        List<Note> errors = out.result().errors().stream().filter(n -> !AtlasNotes.VALIDATION.equals(n.rule()))
                .toList();
        if (!errors.isEmpty()) {
            throw new MojoFailureException(errors.size() + " conversion error(s), first: " + errors.get(0));
        }
        getLog().info("fennec-atlas: wrote " + out.mappings().size() + " mapping(s) and " + out.profiles().size()
                + " profile(s) below " + outputDirectory);
        if (attachResource && project != null) {
            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getAbsolutePath());
            project.addResource(resource);
        }
    }

    private List<Path> ecoreFiles() throws MojoFailureException {
        List<Path> given = new ArrayList<>();
        if (ecoreFile != null) {
            given.add(ecoreFile.toPath());
        }
        if (ecoreFiles != null) {
            ecoreFiles.forEach(f -> given.add(f.toPath()));
        }
        if (given.isEmpty()) {
            throw new MojoFailureException("fennec-atlas: no .ecore of the source class model: set ecoreFile or ecoreFiles");
        }
        for (Path f : given) {
            if (!f.toFile().isFile()) {
                throw new MojoFailureException("fennec-atlas: .ecore file does not exist: " + f);
            }
        }
        return given;
    }

    private List<ProviderMapping> chosenMappings() throws MojoExecutionException, MojoFailureException {
        List<ProviderMapping> all;
        try {
            all = CwmToAtlas.mappingsIn(
                    CwmPackages.loadModel(modelFile.toPath(), getClass().getClassLoader(), extraPackages));
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cannot load " + modelFile + ": " + e.getMessage(), e);
        }
        if (all.isEmpty()) {
            throw new MojoFailureException(modelFile + " holds no provider mapping");
        }
        if (mappings == null || mappings.isEmpty()) {
            return all;
        }
        List<ProviderMapping> chosen = new ArrayList<>();
        for (String name : mappings) {
            List<ProviderMapping> named = all.stream().filter(m -> name.equals(m.getName())).toList();
            if (named.isEmpty()) {
                throw new MojoFailureException(modelFile + " holds no provider mapping named '" + name + "'; it holds "
                        + all.stream().map(ProviderMapping::getName).toList());
            }
            chosen.addAll(named);
        }
        return chosen;
    }

    private void report(List<Note> notes) {
        for (Note n : notes) {
            switch (n.severity()) {
            case ERROR -> getLog().error("fennec-atlas: " + n);
            case WARNING -> getLog().warn("fennec-atlas: " + n);
            default -> getLog().info("fennec-atlas: " + n);
            }
        }
    }
}
