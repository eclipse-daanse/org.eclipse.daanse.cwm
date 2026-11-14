/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.RuleVocabulary;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.MetadataProviders;

/**
 * Loads a live database into a CWM relational {@code Catalog}: connect, take one JDBC metadata
 * snapshot with the metadata provider of the database product (or the generic JDBC reading when
 * there is none), run the {@code CwmLoader} on it for the structure, with the
 * {@code PrivilegeLoader} as its part for roles, users and their privileges, and cut the catalog to
 * the selection of the configuration. These are the steps of the Maven goal
 * {@code cwm-jdbc:import}, for use from Java; {@code CwmPackages.save} of {@code emf/standalone}
 * writes the catalog, {@link CwmJdbcExport} takes the way back:
 *
 * <pre>{@code
 * JdbcToCwmConfig config = JdbcToCwmConfig.defaults().withCatalogName("shop")
 *         .withSelection(CwmJdbcImport.selection(List.of("schema:sales", "!table:tmp_*", "is:role")));
 * Catalog catalog = CwmJdbcImport.load("jdbc:postgresql://localhost:5432/shop", "reader", "secret", null,
 *         getClass().getClassLoader(), config);
 * CwmPackages.save(Path.of("target/generated-cwm", CwmJdbcImport.fileName(catalog)), List.of(catalog));
 * }</pre>
 *
 * <p>Inside OSGi the {@code CwmLoader} and {@code PrivilegeLoader} services are the entry; this
 * class wires by hand what the service registry wires there.</p>
 */
public final class CwmJdbcImport {

    private CwmJdbcImport() {
    }

    /**
     * The catalog of the database at {@code url}, as {@link #load(Connection, JdbcToCwmConfig)}
     * gives it: connects as {@link Connections#connect(String, String, String, String, ClassLoader)}
     * does, loads, and closes the connection.
     */
    public static Catalog load(String url, String user, String password, String driverClass, ClassLoader loader,
            JdbcToCwmConfig config) throws SQLException {
        try (Connection connection = Connections.connect(url, user, password, driverClass, loader)) {
            return load(connection, config);
        }
    }

    /**
     * The catalog of the database behind {@code dataSource}, as
     * {@link #load(Connection, JdbcToCwmConfig)} gives it: for a configured {@code DataSource}
     * (a pool, an OSGi service, a remote DuckDB that attaches its server on connect) where a URL is
     * not enough. The connection is closed afterwards.
     */
    public static Catalog load(DataSource dataSource, JdbcToCwmConfig config) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return load(connection, config);
        }
    }

    /** The file name of the catalog's XMI: its name with {@code .xmi}, {@code catalog.xmi} without name. */
    public static String fileName(Catalog catalog) {
        String name = catalog.getName();
        return (name == null || name.isBlank() ? "catalog" : name) + ".xmi";
    }

    /**
     * The catalog of the database behind {@code connection}, as {@code config} selects it
     * ({@code null}: everything). The catalog also owns the database's roles and users
     * ({@code businessinformation::ResponsibleParty}, marked by the stereotypes {@code databaseRole}
     * and {@code databaseUser}, which it owns too) with their table, column, routine and role
     * privileges on the loaded elements, as far as the selection holds them: an include rule on
     * schemas does not reach them, {@code is:role} does. How many privileges a database reports
     * depends on its metadata provider.
     *
     * @throws UnmatchedRulesException when an include rule matches nothing in the database
     */
    public static Catalog load(Connection connection, JdbcToCwmConfig config) throws SQLException {
        DatabaseServiceImpl service = new DatabaseServiceImpl();
        Optional<MetadataProvider> provider = MetadataProviders.forConnection(connection);
        MetaInfo info = provider.isPresent() ? service.createMetaInfo(connection, provider.get())
                : service.createMetaInfo(connection);
        return new CwmLoaderImpl(List.of(new PrivilegeLoaderImpl())).load(info, config);
    }

    /**
     * The words a selection of an imported catalog is written in: those of the relational model,
     * the business information and the privileges, {@link PrivilegeRules#VOCABULARY}.
     */
    public static RuleVocabulary vocabulary() {
        return PrivilegeRules.VOCABULARY;
    }

    /**
     * The selection the rule texts {@code rules} describe in {@link #vocabulary()}, one rule per
     * text or line: {@code schema:sales}, {@code !table:tmp_*}, {@code !is:privilege}.
     *
     * @throws IllegalArgumentException for a rule the vocabulary does not know
     */
    public static Selection selection(List<String> rules) {
        return Selection.parse(vocabulary(), rules == null ? List.of() : rules);
    }
}
