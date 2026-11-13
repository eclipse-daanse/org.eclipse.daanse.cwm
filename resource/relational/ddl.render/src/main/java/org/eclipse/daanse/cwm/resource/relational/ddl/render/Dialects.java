/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;

/**
 * Resolves dialect ids to offline {@link Dialect} instances (no JDBC connection needed), and finds
 * the id of the dialect of a database a connection leads to.
 */
public final class Dialects {

    /** A display name paired with the dialect that emits its SQL. */
    public record NamedDialect(String name, Dialect dialect) {
    }

    private Dialects() {
    }

    /** The ids {@link #require(String)} accepts, one per dialect; aliases are listed there. */
    public static final List<String> IDS = List.of("ansi", "postgresql", "mysql", "mariadb", "oracle", "sqlserver",
            "h2", "duckdb");

    /** Further names of a dialect, each mapped to its id. */
    private static final Map<String, String> ALIASES = Map.of("sql99", "ansi", "postgres", "postgresql", "pg",
            "postgresql", "mssql", "sqlserver", "mssqlserver", "sqlserver");

    /** The default set: ANSI, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server. */
    public static List<NamedDialect> defaults() {
        return of(List.of("ansi", "postgresql", "mysql", "mariadb", "oracle", "sqlserver"));
    }

    /** Resolve the given dialect ids; unknown ids are skipped. */
    public static List<NamedDialect> of(List<String> ids) {
        List<NamedDialect> out = new ArrayList<>();
        for (String id : ids) {
            NamedDialect nd = named(id);
            if (nd != null) {
                out.add(nd);
            }
        }
        return out;
    }

    /**
     * The dialect of {@code id} (case-insensitive; aliases {@code sql99}, {@code postgres},
     * {@code pg}, {@code mssql}, {@code mssqlserver}).
     *
     * @throws IllegalArgumentException for an unknown id, naming the known ones
     */
    public static NamedDialect require(String id) {
        return named(id(id));
    }

    /**
     * The id of the dialect {@code id} names: one of {@link #IDS}, for an alias the id of its
     * dialect ({@code PG} → {@code postgresql}).
     *
     * @throws IllegalArgumentException for an unknown id, naming the known ones
     */
    public static String id(String id) {
        String lower = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        String canonical = ALIASES.getOrDefault(lower, lower);
        if (!IDS.contains(canonical)) {
            throw new IllegalArgumentException("unknown dialect '" + id + "'; known: " + String.join(", ", IDS));
        }
        return canonical;
    }

    /**
     * The id of the dialect of the database behind {@code connection}, by the product name and URL
     * its metadata report, as {@link #forProduct} reads them.
     *
     * @throws IllegalArgumentException for a database no dialect is known for, naming its product,
     *                                  its URL and the known ids
     * @throws SQLException             when the metadata cannot be read
     */
    public static String forConnection(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String product = metaData.getDatabaseProductName();
        String url = metaData.getURL();
        return forProduct(product, url).orElseThrow(() -> new IllegalArgumentException("no dialect for the database "
                + product + " (" + url + "); known: " + String.join(", ", IDS)));
    }

    /**
     * The id of the dialect of a database by the product name its JDBC metadata report: the first id
     * of {@link #IDS} the name holds, case and blanks aside ({@code PostgreSQL}, {@code MySQL},
     * {@code MariaDB}, {@code Oracle}, {@code Microsoft SQL Server}, {@code H2}, {@code DuckDB}).
     * Where the name holds none, by the subprotocol of the JDBC URL, {@code jdbc:<id or alias>:…}.
     * {@code ansi} is the dialect of no database and is never found.
     *
     * @param productName may be {@code null}
     * @param url         may be {@code null}
     * @return empty for a database no dialect is known for
     */
    public static Optional<String> forProduct(String productName, String url) {
        if (productName != null) {
            String product = productName.toLowerCase(Locale.ROOT).replace(" ", "");
            for (String id : IDS) {
                if (!"ansi".equals(id) && product.contains(id)) {
                    return Optional.of(id);
                }
            }
        }
        if (url != null && url.regionMatches(true, 0, "jdbc:", 0, 5)) {
            int end = url.indexOf(':', 5);
            if (end > 5) {
                String protocol = url.substring(5, end).toLowerCase(Locale.ROOT);
                String id = ALIASES.getOrDefault(protocol, protocol);
                if (!"ansi".equals(id) && IDS.contains(id)) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    static NamedDialect named(String id) {
        // construct offline (no JDBC connection); MySQL/MariaDB use backtick identifier quoting,
        // the rest the ANSI double-quote (a single symmetric quote string — SQL Server's asymmetric
        // [ ] brackets can't be expressed that way, so it stays ANSI offline). Dialects still differ
        // in type mapping and capabilities.
        String canonical;
        try {
            canonical = id(id);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        return switch (canonical) {
            case "ansi" -> new NamedDialect("ANSI", new AnsiDialect());
            case "postgresql" -> new NamedDialect("PostgreSQL", new PostgreSqlDialect());
            case "mysql" -> new NamedDialect("MySQL", new MySqlDialect(quote("`")));
            case "mariadb" -> new NamedDialect("MariaDB", new MariaDBDialect(quote("`")));
            case "oracle" -> new NamedDialect("Oracle", new OracleDialect());
            case "sqlserver" -> new NamedDialect("SQL Server", new MicrosoftSqlServerDialect());
            case "h2" -> new NamedDialect("H2", new H2Dialect());
            default -> new NamedDialect("DuckDB", new DuckDbDialect());
        };
    }

    private static DialectInitData quote(String q) {
        return DialectInitData.ansiDefaults().withQuoteIdentifierString(q);
    }
}
