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
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcImport;

/**
 * Reads the structure of a live database over JDBC and writes it as a CWM relational catalog
 * XMI: schemas, tables, columns with their SQL types, primary and unique keys, check constraints,
 * foreign keys, indexes, views, triggers and procedures, column and table comments as
 * descriptions, the roles, users and their privileges; what of it, the rules of
 * {@link #selection} say. Every element gets a readable {@code xmi:id} built from its name path,
 * so mapping files can refer to it. The import itself is {@link CwmJdbcImport}; this goal parses
 * the rules, takes the credentials from {@code settings.xml} and provides the driver.
 *
 * <p>Runs without a project as well ({@code mvn …:import -Dcwm-jdbc.url=…}); the JDBC driver comes
 * as a dependency of the plugin declaration or, on the command line, as Maven coordinates in
 * {@code drivers}.</p>
 */
@Mojo(name = "import", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresProject = false, threadSafe = true)
public class ImportJdbcMojo extends AbstractJdbcMojo {

    /** The name of the CWM catalog; unset: the name the database reports. */
    @Parameter(property = "cwm-jdbc.catalogName")
    private String catalogName;

    /**
     * The rules that say what is read, one per entry, {@code <selector>:<pattern>}; a leading
     * {@code !} leaves out what the rule matches. Without rules everything is read, roles, users
     * and privileges included. The words are those of {@link CwmJdbcImport#vocabulary()}:
     * {@code schema:sales}, {@code table:sales.order*}, {@code view:v_*}, {@code name:tmp_*},
     * {@code is:constraint}, {@code is:index}, {@code is:role}, {@code !is:privilege}, ... An
     * include rule that matches nothing fails the goal.
     */
    @Parameter(property = "cwm-jdbc.selection")
    private List<String> selection;

    /** Where the XMI is written. */
    @Parameter(property = "cwm-jdbc.outputDirectory", defaultValue = "${project.build.directory}/generated-cwm")
    private File outputDirectory;

    /** File name of the XMI; default: the catalog name with the extension {@code .xmi}. */
    @Parameter(property = "cwm-jdbc.fileName")
    private String fileName;

    /** Add {@link #outputDirectory} as a resource directory, so the XMI lands in the jar. */
    @Parameter(property = "cwm-jdbc.attachResource", defaultValue = "true")
    private boolean attachResource;

    @Override
    protected void run() throws MojoExecutionException, MojoFailureException {
        Login login = login();
        JdbcToCwmConfig config = JdbcToCwmConfig.defaults().withCatalogName(catalogName)
                .withSelection(rules(selection));
        Catalog catalog;
        try (URLClassLoader driverLoader = driverLoader()) {
            catalog = CwmJdbcImport.load(url, login.user(), login.password(), driverClass, driverLoader, config);
        } catch (UnmatchedRulesException e) {
            throw new MojoFailureException("cwm-jdbc: reading " + url + ": " + e.getMessage(), e);
        } catch (SQLException e) {
            throw new MojoExecutionException("cwm-jdbc: cannot read " + url + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("cwm-jdbc: " + e.getMessage(), e);
        }
        Path file = outputDirectory.toPath()
                .resolve(fileName != null && !fileName.isBlank() ? fileName.trim() : CwmJdbcImport.fileName(catalog));
        try {
            CwmPackages.save(file, List.of(catalog));
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cwm-jdbc: cannot write " + file + ": " + e.getMessage(), e);
        }
        getLog().info("cwm-jdbc: wrote catalog " + catalog.getName() + " of " + url + " to " + file);
        if (attachResource && project != null) {
            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getAbsolutePath());
            project.addResource(resource);
        }
    }
}
