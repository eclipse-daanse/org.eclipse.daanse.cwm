/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.jdbc;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport.Exported;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.ExportSettings;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.StatementFailedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Writes a CWM relational catalog into a live database over JDBC: the part of the model the rules
 * of {@link #selection} choose, as the DDL of the database's dialect, statement for statement what
 * {@code cwm-ddl:generate} writes for the same model, rules and settings. With {@link #dropFirst}
 * the drop script of the same model runs first, each statement only where the database holds what
 * it drops, so the goal runs again and again. The export itself is {@link CwmJdbcExport}; this
 * goal loads the model, parses the rules, maps the settings, takes the credentials from
 * {@code settings.xml}, provides the driver and reports what was done.
 *
 * <p>Runs without a project as well ({@code mvn …:export -Dcwm-jdbc.modelFile=… -Dcwm-jdbc.url=…});
 * the JDBC driver comes as a dependency of the plugin declaration or, on the command line, as Maven
 * coordinates in {@code drivers}.</p>
 */
@Mojo(name = "export", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false,
        threadSafe = true)
public class ExportJdbcMojo extends AbstractJdbcMojo {

    /**
     * The CWM XMI holding the relational catalog; files it references by relative {@code href} are
     * loaded with it.
     */
    @Parameter(property = "cwm-jdbc.modelFile", required = true)
    private File modelFile;

    /**
     * The catalog of {@link #modelFile} to write, by name; unset: the one catalog the file holds. A
     * file with several catalogs needs it.
     */
    @Parameter(property = "cwm-jdbc.catalogName")
    private String catalogName;

    /**
     * The rules that say what is written, one per entry, {@code <selector>:<pattern>}; a leading
     * {@code !} leaves out what the rule matches. Without rules everything is written, roles and
     * grants included. The words are those of {@code cwm-jdbc:import} and {@code cwm-ddl:generate}:
     * {@code schema:sales}, {@code table:sales.order*}, {@code view:v_*}, {@code name:tmp_*},
     * {@code is:constraint}, {@code is:index}, {@code is:role}, {@code !is:privilege},
     * {@code !is:comment}, ... An include rule that matches nothing fails the goal.
     */
    @Parameter(property = "cwm-jdbc.selection")
    private List<String> selection;

    /**
     * Qualify tables with their schema ({@code schema.table}); off to write one schema into the
     * schema the connection selects.
     */
    @Parameter(property = "cwm-jdbc.includeSchema", defaultValue = "true")
    private boolean includeSchema;

    /** Write {@code CREATE SCHEMA} (and drop it with {@link #dropFirst}); off where the schemas exist. */
    @Parameter(property = "cwm-jdbc.createSchema", defaultValue = "true")
    private boolean createSchema;

    /**
     * {@code CREATE ... IF NOT EXISTS} for schemas, tables and indexes where the dialect has it. The
     * drop script of {@link #dropFirst} is written with {@code IF EXISTS} either way.
     */
    @Parameter(property = "cwm-jdbc.ifNotExists", defaultValue = "true")
    private boolean ifNotExists;

    /** {@code CASCADE} on the tables and schemas {@link #dropFirst} drops, where the dialect has it. */
    @Parameter(property = "cwm-jdbc.cascade", defaultValue = "false")
    private boolean cascade;

    /**
     * Fail before anything runs where the dialect cannot write what the model holds as the model has
     * it, such as a materialized view where the database has none, naming every case. Off: it is
     * written plainer or left out, and the build log names each case as a warning.
     */
    @Parameter(property = "cwm-jdbc.strict", defaultValue = "false")
    private boolean strict;

    /**
     * The type of the descriptions written as table and column comments ({@code Description.type});
     * the import reads comments as this type. Comments are left out with the rule {@code !is:comment}.
     */
    @Parameter(property = "cwm-jdbc.commentType", defaultValue = Descriptions.JDBC_REMARKS)
    private String commentType;

    /**
     * Drop first what the model creates, so the goal runs again and again: the drop script of the
     * same model runs before the create script, each statement only where the database holds what
     * it drops; the others are skipped.
     */
    @Parameter(property = "cwm-jdbc.dropFirst", defaultValue = "false")
    private boolean dropFirst;

    /**
     * The dialect the statements are written in: {@code ansi}, {@code postgresql}, {@code mysql},
     * {@code mariadb}, {@code oracle}, {@code sqlserver}, {@code h2}, {@code duckdb} or an alias;
     * unset: the dialect of the database, by the product name it reports.
     */
    @Parameter(property = "cwm-jdbc.dialect")
    private String dialect;

    /**
     * Interfaces of further generated EPackages the model uses, e.g.
     * {@code org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage}; their jars come as
     * dependencies of the plugin declaration.
     */
    @Parameter
    private List<String> extraPackages;

    @Override
    protected void run() throws MojoExecutionException, MojoFailureException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new MojoFailureException("cwm-jdbc: modelFile does not exist: " + modelFile);
        }
        ExportSettings settings;
        try {
            settings = ExportSettings.defaults()
                    .withDdl(DdlSettings.defaults().withIncludeSchema(includeSchema).withCreateSchema(createSchema)
                            .withIfNotExists(ifNotExists).withCascade(cascade).withStrict(strict)
                            .withCommentType(commentType == null ? Descriptions.JDBC_REMARKS : commentType.trim()))
                    .withDropFirst(dropFirst).withDialect(dialect);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("cwm-jdbc: " + e.getMessage(), e);
        }
        Selection rules = rules(selection);
        Catalog catalog = catalog(load());
        Login login = login();
        Exported done;
        try (URLClassLoader driverLoader = driverLoader()) {
            done = CwmJdbcExport.write(url, login.user(), login.password(), driverClass, driverLoader, catalog, rules,
                    settings);
        } catch (StatementFailedException e) {
            throw new MojoFailureException("cwm-jdbc: writing " + modelFile + " into " + url + ": " + e.getMessage(),
                    e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // an include rule that matches nothing (UnmatchedRulesException), no dialect for the
            // database, tables that refer to each other where the dialect declares foreign keys inline,
            // strict and what the dialect cannot write (UnwritableException)
            throw new MojoFailureException("cwm-jdbc: writing " + modelFile + ": " + e.getMessage(), e);
        } catch (SQLException e) {
            throw new MojoExecutionException("cwm-jdbc: cannot write into " + url + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("cwm-jdbc: " + e.getMessage(), e);
        }
        report(done);
    }

    /** What the selection left out and retained, and the statements that ran and were skipped. */
    private void report(Exported done) {
        Selected<Catalog> selected = done.selected();
        if (!selected.leftOut().isEmpty() || !selected.retained().isEmpty()) {
            getLog().info("cwm-jdbc: the selection leaves out " + selected.leftOut().size() + " and retains "
                    + selected.retained().size() + " elements");
            selected.leftOut().forEach(l -> getLog().info("cwm-jdbc:   left out " + l));
            selected.retained().forEach(r -> getLog().info("cwm-jdbc:   retained " + r));
        }
        for (DdlStatement skipped : done.skipped()) {
            getLog().debug("cwm-jdbc: skipped, as the database does not hold it: " + skipped.sql());
        }
        getLog().info("cwm-jdbc: wrote catalog " + selected.model().getName() + " of " + modelFile + " into " + url
                + " in the dialect " + done.dialect() + ": " + done.executed().size() + " statements ran"
                + (dropFirst ? ", " + done.skipped().size() + " of the drop script skipped" : ""));
    }

    /** The model file, loaded with what it refers to. */
    private Resource load() throws MojoExecutionException {
        try {
            return CwmPackages.loadModel(modelFile.toPath(), getClass().getClassLoader(), extraPackages);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cwm-jdbc: cannot load " + modelFile + ": " + e.getMessage(), e);
        }
    }

    /** The catalog of {@code model} to write: the one named {@link #catalogName}, or the only one. */
    private Catalog catalog(Resource model) throws MojoFailureException {
        List<Catalog> catalogs = new ArrayList<>();
        for (EObject root : model.getContents()) {
            if (root instanceof Catalog catalog) {
                catalogs.add(catalog);
            }
            root.eAllContents().forEachRemaining(o -> {
                if (o instanceof Catalog catalog) {
                    catalogs.add(catalog);
                }
            });
        }
        List<String> names = catalogs.stream().map(c -> String.valueOf(c.getName())).toList();
        if (catalogName != null && !catalogName.isBlank()) {
            return catalogs.stream().filter(c -> Objects.equals(catalogName.trim(), c.getName())).findFirst()
                    .orElseThrow(() -> new MojoFailureException("cwm-jdbc: " + modelFile + " holds no catalog "
                            + catalogName.trim() + (names.isEmpty() ? "" : ", only " + String.join(", ", names))));
        }
        if (catalogs.size() != 1) {
            throw new MojoFailureException("cwm-jdbc: " + modelFile + (catalogs.isEmpty()
                    ? " holds no relational catalog"
                    : " holds the catalogs " + String.join(", ", names) + "; name one in catalogName"));
        }
        return catalogs.get(0);
    }
}
