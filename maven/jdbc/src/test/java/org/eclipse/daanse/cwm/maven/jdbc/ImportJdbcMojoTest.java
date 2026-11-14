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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The goal against an in-memory database, the way a build runs it: the Mojo is instantiated, its
 * parameters set as a POM or the command line sets them, and executed; the written XMI is loaded
 * again.
 */
class ImportJdbcMojoTest {

    private static final String URL = "jdbc:h2:mem:import;DB_CLOSE_DELAY=-1";

    private static Connection keepAlive;

    @BeforeAll
    static void database() throws Exception {
        keepAlive = DriverManager.getConnection(URL, "sa", "");
        try (Statement s = keepAlive.createStatement()) {
            s.execute("CREATE SCHEMA SHOP");
            s.execute("CREATE TABLE SHOP.CUSTOMER (ID BIGINT PRIMARY KEY, NAME VARCHAR(100) NOT NULL)");
            s.execute("CREATE TABLE SHOP.ORDERS (ID BIGINT PRIMARY KEY, CUSTOMER_ID BIGINT)");
            s.execute("CREATE TABLE SHOP.ORDERS_OLD (ID BIGINT)");
            s.execute("CREATE SCHEMA OTHER");
            s.execute("CREATE TABLE OTHER.T (ID BIGINT)");
        }
    }

    @Test
    void writesTheChosenSchemaAndTablesAsCatalogXmiWithReadableIds(@TempDir Path out) throws Exception {
        ImportJdbcMojo mojo = mojo(out);
        set(mojo, "user", "sa");
        set(mojo, "password", "");
        set(mojo, "catalogName", "shop");
        set(mojo, "selection", List.of("schema:SHOP", " !table:*_OLD "));

        mojo.execute();

        Path file = out.resolve("shop.xmi");
        assertThat(Files.readString(file)).contains("xmi:id=\"Table_shop.SHOP.CUSTOMER\"")
                .contains("xmi:id=\"Column_shop.SHOP.CUSTOMER.NAME\"").doesNotContain("ORDERS_OLD")
                .doesNotContain("OTHER");
        CwmPackages.registerKnown();
        Resource loaded = CwmPackages.load(CwmPackages.newResourceSet(), file);
        Catalog catalog = (Catalog) loaded.getContents().get(0);
        assertThat(catalog.getName()).isEqualTo("shop");
        assertThat(loaded.getEObject("Table_shop.SHOP.ORDERS")).isInstanceOf(Table.class);
    }

    @Test
    void ruleTextsSelectWhatIsRead_kindsInclusive(@TempDir Path out) throws Exception {
        ImportJdbcMojo mojo = mojo(out);
        set(mojo, "user", "sa");
        set(mojo, "password", "");
        set(mojo, "catalogName", "shop");
        set(mojo, "selection", List.of("table:SHOP.ORDERS", "!is:primaryKey"));

        mojo.execute();

        assertThat(Files.readString(out.resolve("shop.xmi"))).contains("xmi:id=\"Table_shop.SHOP.ORDERS\"")
                .doesNotContain("Table_shop.SHOP.CUSTOMER\"").doesNotContain("ORDERS_OLD")
                .doesNotContain("PrimaryKey");
    }

    @Test
    void aRuleThatMatchesNothingOrIsNoRuleFailsTheGoal(@TempDir Path out) throws Exception {
        ImportJdbcMojo mojo = mojo(out);
        set(mojo, "user", "sa");
        set(mojo, "password", "");
        set(mojo, "selection", List.of("schema:SHOP", "schema:shop"));

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("matches nothing").hasMessageContaining("schema:shop")
                .hasMessageNotContaining("schema:SHOP,");
        assertThat(out).isEmptyDirectory();

        set(mojo, "selection", List.of("schemas:SHOP"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Unknown selector 'schemas'").hasMessageContaining("schema, stereotype");
    }

    @Test
    void credentialsFromASettingsServerAndAChosenFileName(@TempDir Path out) throws Exception {
        Server server = new Server();
        server.setId("dev-db");
        server.setUsername("sa");
        server.setPassword("");
        Settings settings = new Settings();
        settings.addServer(server);
        ImportJdbcMojo mojo = mojo(out);
        set(mojo, "settings", settings);
        set(mojo, "serverId", "dev-db");
        set(mojo, "selection", List.of("schema:OTHER"));
        set(mojo, "fileName", "other.xmi");

        mojo.execute();

        assertThat(Files.readString(out.resolve("other.xmi"))).contains("name=\"T\"").doesNotContain("CUSTOMER");

        set(mojo, "serverId", "missing");
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("'missing'");
    }

    @Test
    void anUnreachableDatabaseFailsAndSkipDoesNothing(@TempDir Path out) throws Exception {
        ImportJdbcMojo mojo = mojo(out);
        set(mojo, "url", "jdbc:h2:file:" + out.resolve("absent/db") + ";IFEXISTS=TRUE");

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class).hasMessageContaining("absent");

        set(mojo, "skip", true);
        mojo.execute();
        assertThat(out).isEmptyDirectory();

    }

    @Test
    void driversGivenAsCoordinatesAreResolvedByMaven(@TempDir Path out) throws Exception {
        List<String> requested = new ArrayList<>();
        Path h2 = Path.of(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ImportJdbcMojo mojo = mojo(out);
        set(mojo, "user", "sa");
        set(mojo, "password", "");
        set(mojo, "selection", List.of("schema:OTHER"));
        set(mojo, "fileName", "other.xmi");
        set(mojo, "drivers", List.of(" com.h2database:h2:2.2.224 "));
        set(mojo, "repositorySession", proxy(RepositorySystemSession.class, (m, a) -> null));
        set(mojo, "repositorySystem", proxy(RepositorySystem.class, (m, args) -> {
            if (!m.getName().equals("resolveDependencies")) {
                return null;
            }
            DependencyRequest request = (DependencyRequest) args[1];
            request.getCollectRequest().getDependencies().forEach(d -> requested.add(d.getArtifact().toString()));
            ArtifactResult jar = new ArtifactResult(new ArtifactRequest());
            jar.setArtifact(new DefaultArtifact("com.h2database:h2:2.2.224").setFile(h2.toFile()));
            DependencyResult result = new DependencyResult(request);
            result.setArtifactResults(List.of(jar));
            return result;
        }));

        mojo.execute();

        assertThat(requested).containsExactly("com.h2database:h2:jar:2.2.224");
        assertThat(out.resolve("other.xmi")).exists();

        set(mojo, "drivers", List.of("h2"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("'h2'");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (p, m, args) -> answer.apply(m, args));
    }

    private static ImportJdbcMojo mojo(Path out) throws ReflectiveOperationException {
        ImportJdbcMojo mojo = new ImportJdbcMojo();
        set(mojo, "url", URL);
        set(mojo, "outputDirectory", out.toFile());
        return mojo;
    }

    /** Sets a parameter of the goal, declared by its class or by the class the goals share. */
    static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException notHere) {
                // declared further up
            }
        }
        throw new NoSuchFieldException(field);
    }
}
