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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The goal on the test project under {@code src/test/resources/it}, the way a build runs it: the
 * Mojo is instantiated, its parameters set as a POM sets them, and executed; the written scripts
 * are checked, the H2 one by running it.
 */
class GenerateDdlMojoTest {

    @Test
    void oneCreateAndOneDropScriptPerDialectAndTheH2ScriptRuns(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path out = project.resolve("target/generated-resources/ddl");
        GenerateDdlMojo mojo = mojo(project, out);
        set(mojo, "dialects", List.of("postgresql", "H2"));
        set(mojo, "drop", true);

        mojo.execute();

        try (Stream<Path> files = Files.list(out)) {
            assertThat(files.map(p -> p.getFileName().toString()).sorted()).containsExactly("catalog-h2-drop.sql",
                    "catalog-h2.sql", "catalog-postgresql-drop.sql", "catalog-postgresql.sql");
        }
        String pg = Files.readString(out.resolve("catalog-postgresql.sql"));
        assertThat(pg).startsWith("-- CREATE script for PostgreSQL: LIBRARYDB.LIB from catalog.xmi")
                .contains("CREATE TABLE").contains("\"LIB\".\"BOOK\"").contains("FOREIGN KEY")
                .contains("CREATE INDEX");
        assertThat(pg.indexOf("\"LIB\".\"PUBLISHER\"")).as("the referenced table first")
                .isLessThan(pg.indexOf("FOREIGN KEY"));
        assertThat(Files.readString(out.resolve("catalog-h2-drop.sql"))).contains("DROP TABLE");

        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:ddl;DB_CLOSE_DELAY=-1");
                Statement s = c.createStatement()) {
            for (String statement : statements(out.resolve("catalog-h2.sql"))) {
                s.execute(statement);
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_SCHEMA = 'LIB'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("PUBLISHER and BOOK").isEqualTo(2);
            }
            for (String statement : statements(out.resolve("catalog-h2-drop.sql"))) {
                s.execute(statement);
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_SCHEMA = 'LIB'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void theSelectionAndTheSettingsShapeTheStatements(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path out = project.resolve("ddl");
        GenerateDdlMojo mojo = mojo(project, out);
        set(mojo, "dialects", List.of("ansi"));
        set(mojo, "selection", List.of("schema:LIBRARYDB.LIB", " !is:foreignKey ", "!is:index # no index either"));
        set(mojo, "includeSchema", false);
        set(mojo, "ifNotExists", false);
        set(mojo, "scriptName", "tables");

        mojo.execute();

        String sql = Files.readString(out.resolve("tables-ansi.sql"));
        assertThat(sql).contains("-- selected by schema:LIBRARYDB.LIB, !is:foreignKey, !is:index")
                .contains("CREATE TABLE").contains("\"BOOK\"").doesNotContain("\"LIB\".")
                .doesNotContain("CREATE SCHEMA").doesNotContain("IF NOT EXISTS").doesNotContain("FOREIGN KEY")
                .doesNotContain("CREATE INDEX");
    }

    /**
     * DuckDB keeps every index column ascending: the descending column of the model is written
     * ascending, and the build log says so; strict, the goal fails, naming the case, and writes no
     * script.
     */
    @Test
    void strictTheGoalFailsWhereADialectCannotWriteWhatTheModelHolds(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path model = project.resolve("src/main/cwm/catalog.xmi");
        Files.writeString(model, Files.readString(model).replace("isAscending=\"true\"", "isAscending=\"false\""));
        Path out = project.resolve("ddl");
        GenerateDdlMojo mojo = mojo(project, out);
        set(mojo, "dialects", List.of("duckdb"));
        set(mojo, "strict", true);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("index LIBRARYDB.LIB.IX_BOOK_PUBLISHER: ")
                .hasMessageContaining("cannot write DESC on column PUBLISHER_ID");
        assertThat(out).doesNotExist();

        set(mojo, "strict", false);
        mojo.execute();

        assertThat(Files.readString(out.resolve("catalog-duckdb.sql"))).contains("CREATE INDEX")
                .doesNotContain("DESC");
    }

    @Test
    void withoutCreateSchemaTheTablesGoIntoASchemaThatExists(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        Path out = project.resolve("ddl");
        GenerateDdlMojo mojo = mojo(project, out);
        set(mojo, "createSchema", false);
        set(mojo, "drop", true);

        mojo.execute();

        assertThat(Files.readString(out.resolve("catalog-ansi.sql"))).doesNotContain("CREATE SCHEMA")
                .contains("\"LIB\".\"BOOK\"");
        assertThat(Files.readString(out.resolve("catalog-ansi-drop.sql"))).doesNotContain("DROP SCHEMA");
    }

    @Test
    void anUnknownDialectOrRuleOrARuleThatMatchesNothingFails(@TempDir Path tmp) throws Exception {
        Path project = copyProject("library", tmp);
        GenerateDdlMojo mojo = mojo(project, project.resolve("ddl"));

        set(mojo, "dialects", List.of("db2"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("db2")
                .hasMessageContaining("postgresql");

        set(mojo, "dialects", List.of("h2"));
        set(mojo, "selection", List.of("feature:TABLE"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("feature")
                .hasMessageContaining("schema");

        set(mojo, "selection", List.of("schema:DWH"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("schema:DWH").hasMessageContaining("LIBRARYDB");

        set(mojo, "modelFile", tmp.resolve("nope.xmi").toFile());
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("nope.xmi");
    }

    private static GenerateDdlMojo mojo(Path project, Path out) throws ReflectiveOperationException {
        GenerateDdlMojo mojo = new GenerateDdlMojo();
        set(mojo, "modelFile", project.resolve("src/main/cwm/catalog.xmi").toFile());
        set(mojo, "outputDirectory", out.toFile());
        set(mojo, "dialects", List.of("ansi"));
        set(mojo, "includeSchema", true);
        set(mojo, "createSchema", true);
        set(mojo, "ifNotExists", true);
        set(mojo, "delimiter", ";");
        return mojo;
    }

    /** The statements of a written script: separated by the delimiter and a blank line, comments dropped. */
    private static List<String> statements(Path script) throws IOException {
        return Stream.of(Files.readString(script).split(";\n\n")).map(String::strip)
                .map(s -> s.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + b + "\n").strip())
                .filter(s -> !s.isEmpty()).toList();
    }

    static Path copyProject(String name, Path target) throws IOException, URISyntaxException {
        Path source = Path.of(GenerateDdlMojoTest.class.getResource("/it/" + name).toURI());
        try (Stream<Path> all = Files.walk(source)) {
            for (Path p : all.toList()) {
                Path t = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t);
                }
            }
        }
        return target;
    }

    static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
