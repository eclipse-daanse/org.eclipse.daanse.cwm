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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;

/** Resolves dialect ids to offline {@link Dialect} instances (no JDBC connection needed). */
public final class Dialects {

    /** A display name paired with the dialect that emits its SQL. */
    public record NamedDialect(String name, Dialect dialect) {
    }

    private Dialects() {
    }

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

    static NamedDialect named(String id) {
        // construct offline (no JDBC connection); MySQL/MariaDB use backtick identifier quoting,
        // the rest the ANSI double-quote (a single symmetric quote string — SQL Server's asymmetric
        // [ ] brackets can't be expressed that way, so it stays ANSI offline). Dialects still differ
        // in type mapping and capabilities.
        return switch (id == null ? "" : id.trim().toLowerCase()) {
            case "ansi", "sql99" -> new NamedDialect("ANSI", new AnsiDialect());
            case "postgresql", "postgres", "pg" -> new NamedDialect("PostgreSQL", new PostgreSqlDialect());
            case "mysql" -> new NamedDialect("MySQL", new MySqlDialect(quote("`")));
            case "mariadb" -> new NamedDialect("MariaDB", new MariaDBDialect(quote("`")));
            case "oracle" -> new NamedDialect("Oracle", new OracleDialect());
            case "sqlserver", "mssql", "mssqlserver" ->
                new NamedDialect("SQL Server", new MicrosoftSqlServerDialect());
            case "h2" -> new NamedDialect("H2", new H2Dialect());
            default -> null;
        };
    }

    private static DialectInitData quote(String q) {
        return DialectInitData.ansiDefaults().withQuoteIdentifierString(q);
    }
}
