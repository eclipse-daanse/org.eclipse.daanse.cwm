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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcImport;

/**
 * What the goals of {@code cwm-jdbc} share: the connection to the database, its URL, the
 * credentials, from the parameters or a {@code settings.xml} server, and the JDBC driver, from the
 * plugin's class path or resolved from Maven coordinates; the rules of a selection; and
 * {@code skip}. A goal maps its own parameters to one call of
 * {@code resource/relational/jdbc.standalone}.
 */
abstract class AbstractJdbcMojo extends AbstractMojo {

    /** The JDBC URL, e.g. {@code jdbc:postgresql://localhost:5432/shop}. */
    @Parameter(property = "cwm-jdbc.url", required = true)
    protected String url;

    /** The database user; with {@link #serverId} taken from {@code settings.xml}. */
    @Parameter(property = "cwm-jdbc.user")
    private String user;

    /** The password; with {@link #serverId} taken from {@code settings.xml} (encrypted ones too). */
    @Parameter(property = "cwm-jdbc.password")
    private String password;

    /** The id of a {@code <server>} in {@code settings.xml} that holds user and password. */
    @Parameter(property = "cwm-jdbc.serverId")
    private String serverId;

    /** The driver class; unset: the driver that accepts the URL. */
    @Parameter(property = "cwm-jdbc.driverClass")
    protected String driverClass;

    /**
     * JDBC drivers as Maven coordinates, {@code groupId:artifactId:version} (or
     * {@code groupId:artifactId:extension[:classifier]:version}), resolved with their dependencies
     * from the repositories of the build. For the command line, where there is no plugin
     * declaration to give the driver as dependency; in a POM that is the usual way.
     */
    @Parameter(property = "cwm-jdbc.drivers")
    private List<String> drivers;

    /** Skips the goal, e.g. in builds where the database is not reachable. */
    @Parameter(property = "cwm-jdbc.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private SettingsDecrypter decrypter;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("cwm-jdbc: skipped");
            return;
        }
        if (url == null || url.isBlank()) {
            throw new MojoFailureException("cwm-jdbc: url is required");
        }
        run();
    }

    /** What the goal does, once {@code skip} is off and a URL is given. */
    protected abstract void run() throws MojoExecutionException, MojoFailureException;

    /** User and password. */
    protected record Login(String user, String password) {
    }

    /** User and password: the explicit parameters win over the {@code settings.xml} server. */
    protected Login login() throws MojoFailureException {
        if (serverId == null || serverId.isBlank()) {
            return new Login(user, password);
        }
        Server server = settings == null ? null : settings.getServer(serverId.trim());
        if (server == null) {
            throw new MojoFailureException("cwm-jdbc: no <server> with id '" + serverId + "' in settings.xml");
        }
        if (decrypter != null) {
            SettingsDecryptionResult result = decrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
            for (SettingsProblem problem : result.getProblems()) {
                getLog().warn("cwm-jdbc: " + problem);
            }
            server = result.getServer();
        }
        return new Login(user != null ? user : server.getUsername(), password != null ? password : server.getPassword());
    }

    /**
     * The selection the rule texts describe, in the words of {@link CwmJdbcImport#vocabulary()},
     * which reading and writing share.
     *
     * @throws MojoFailureException for a rule with an unknown selector or name
     */
    protected static Selection rules(List<String> texts) throws MojoFailureException {
        try {
            return CwmJdbcImport.selection(trimmed(texts));
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("cwm-jdbc: " + e.getMessage(), e);
        }
    }

    /** The plugin's class loader, extended by the jars of {@link #drivers} and their dependencies. */
    protected URLClassLoader driverLoader() throws MojoFailureException, MojoExecutionException {
        List<String> coordinates = trimmed(drivers);
        if (coordinates.isEmpty()) {
            return new URLClassLoader(new URL[0], getClass().getClassLoader());
        }
        CollectRequest collect = new CollectRequest();
        for (String c : coordinates) {
            try {
                collect.addDependency(new Dependency(new DefaultArtifact(c), "runtime"));
            } catch (IllegalArgumentException e) {
                throw new MojoFailureException("cwm-jdbc: driver '" + c
                        + "' is no Maven coordinate groupId:artifactId:version", e);
            }
        }
        collect.setRepositories(remoteRepositories);
        List<URL> urls = new ArrayList<>();
        try {
            for (ArtifactResult r : repositorySystem
                    .resolveDependencies(repositorySession, new DependencyRequest(collect, null)).getArtifactResults()) {
                urls.add(r.getArtifact().getFile().toURI().toURL());
                getLog().debug("cwm-jdbc: driver jar " + r.getArtifact().getFile());
            }
        } catch (DependencyResolutionException e) {
            throw new MojoFailureException("cwm-jdbc: cannot resolve the drivers " + coordinates + ": " + e.getMessage(),
                    e);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("cwm-jdbc: " + e.getMessage(), e);
        }
        return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
    }

    /** The non-blank values, trimmed; none for {@code null}. */
    protected static List<String> trimmed(List<String> values) {
        return values == null ? List.of()
                : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }
}
