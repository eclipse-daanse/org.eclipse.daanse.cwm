/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.ddl;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Written;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts;

/**
 * Writes the DDL of a CWM XMI, the part of it the rules of {@link #selection} choose: one
 * {@code CREATE} script per SQL dialect, {@code <scriptName>-<dialect>.sql}, and with {@link #drop}
 * the matching {@code <scriptName>-<dialect>-drop.sql}. The scripts are {@link CwmDdlScripts}'s; this
 * goal parses the rules, maps the settings and adds the output directory to the resources.
 *
 * <p>Statements come in dependency order over all schemas (schemas, tables, comments, keys and
 * constraints, indexes, foreign keys, views, triggers, then roles and grants); each ends with
 * {@link #delimiter} and a blank line, so the script runs as it is in a SQL console or a migration
 * tool.</p>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class GenerateDdlMojo extends AbstractMojo {

    /**
     * The CWM XMI holding the relational catalogs and schemas; files it references by relative
     * {@code href} are loaded with it.
     */
    @Parameter(property = "cwm-ddl.modelFile", required = true)
    private File modelFile;

    /**
     * The SQL dialects, one script each: {@code ansi}, {@code postgresql}, {@code mysql},
     * {@code mariadb}, {@code oracle}, {@code sqlserver}, {@code h2}, {@code duckdb}.
     */
    @Parameter(property = "cwm-ddl.dialects", defaultValue = "ansi")
    private List<String> dialects;

    /**
     * The rules that say what is written, one per entry, {@code <selector>:<pattern>}; a leading
     * {@code !} leaves out what the rule matches. Without rules everything is written, roles and
     * grants included. The words are those of {@link CwmDdlScripts#vocabulary()}, the same as for
     * {@code cwm-jdbc:import}: {@code schema:sales}, {@code table:sales.order*}, {@code view:v_*},
     * {@code name:tmp_*}, {@code is:constraint}, {@code is:index}, {@code is:role},
     * {@code !is:privilege}, {@code !is:comment}, ... An include rule that matches nothing fails the
     * goal.
     */
    @Parameter(property = "cwm-ddl.selection")
    private List<String> selection;

    /**
     * Qualify tables with their schema ({@code schema.table}); off for a database whose connection
     * selects the schema, one schema per script.
     */
    @Parameter(property = "cwm-ddl.includeSchema", defaultValue = "true")
    private boolean includeSchema;

    /** Write {@code CREATE SCHEMA} (and {@code DROP SCHEMA}); off where the schemas exist already. */
    @Parameter(property = "cwm-ddl.createSchema", defaultValue = "true")
    private boolean createSchema;

    /**
     * {@code CREATE ... IF NOT EXISTS} for schemas, tables and indexes and {@code DROP ... IF EXISTS}
     * where the dialect has it.
     */
    @Parameter(property = "cwm-ddl.ifNotExists", defaultValue = "true")
    private boolean ifNotExists;

    /** {@code CASCADE} on dropped tables and schemas where the dialect has it. */
    @Parameter(property = "cwm-ddl.cascade", defaultValue = "false")
    private boolean cascade;

    /**
     * Fail where a dialect cannot write what the model holds as the model has it, such as a
     * materialized view where the database has none, naming every case. Off: it is written plainer
     * or left out, and the build log names each case as a warning.
     */
    @Parameter(property = "cwm-ddl.strict", defaultValue = "false")
    private boolean strict;

    /** Also write the {@code DROP} script, in reverse order. */
    @Parameter(property = "cwm-ddl.drop", defaultValue = "false")
    private boolean drop;

    /** Where the scripts are written. */
    @Parameter(property = "cwm-ddl.outputDirectory",
            defaultValue = "${project.build.directory}/generated-resources/ddl")
    private File outputDirectory;

    /** First part of the script names; default: the model file's name without extension. */
    @Parameter(property = "cwm-ddl.scriptName")
    private String scriptName;

    /** Written after every statement. */
    @Parameter(property = "cwm-ddl.delimiter", defaultValue = ";")
    private String delimiter;

    /**
     * Interfaces of further generated EPackages the model uses, e.g.
     * {@code org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage}; their jars come as
     * dependencies of the plugin declaration.
     */
    @Parameter
    private List<String> extraPackages;

    /** Add {@link #outputDirectory} as a resource directory, so the scripts land in the jar. */
    @Parameter(property = "cwm-ddl.attachResource", defaultValue = "true")
    private boolean attachResource;

    /** Skips the goal. */
    @Parameter(property = "cwm-ddl.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("cwm-ddl: skipped");
            return;
        }
        if (modelFile == null || !modelFile.isFile()) {
            throw new MojoFailureException("modelFile does not exist: " + modelFile);
        }
        CwmDdlScripts scripts;
        Selection rules;
        try {
            scripts = new CwmDdlScripts(dialects, DdlSettings.defaults().withIncludeSchema(includeSchema)
                    .withCreateSchema(createSchema).withIfNotExists(ifNotExists).withCascade(cascade)
                    .withStrict(strict), delimiter);
            rules = CwmDdlScripts.selection(trimmed(selection));
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("cwm-ddl: " + e.getMessage(), e);
        }
        org.eclipse.emf.ecore.resource.Resource model = load();
        Written<ModelElement> written;
        try {
            written = scripts.write(model, rules, outputDirectory.toPath(), scriptName, drop);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new MojoFailureException("cwm-ddl: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("cwm-ddl: cannot write below " + outputDirectory + ": " + e.getMessage(),
                    e);
        }
        List<LeftOut> leftOut = written.selected().leftOut();
        if (!leftOut.isEmpty()) {
            getLog().info("cwm-ddl: the selection leaves out " + leftOut.size() + " elements");
            leftOut.forEach(l -> getLog().debug("cwm-ddl: left out " + l));
        }
        for (CwmDdlScripts.Script s : written.scripts()) {
            getLog().info("cwm-ddl: wrote " + s.file() + " (" + s.statements().size() + " statements)");
        }
        if (attachResource && project != null) {
            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getAbsolutePath());
            project.addResource(resource);
        }
    }

    private org.eclipse.emf.ecore.resource.Resource load() throws MojoExecutionException {
        try {
            return CwmPackages.loadModel(modelFile.toPath(), getClass().getClassLoader(), extraPackages);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cannot load " + modelFile + ": " + e.getMessage(), e);
        }
    }

    /** The non-blank values, trimmed; none for {@code null}. */
    private static List<String> trimmed(List<String> values) {
        return values == null ? List.of()
                : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }
}
