/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.cwm.resource.relational.load.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.util.Synonyms;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * H2 synonyms through the whole chain: {@code H2MetadataProvider} →
 * {@code DatabaseServiceImpl} snapshot → {@code CwmLoader}.
 */
@TestInstance(Lifecycle.PER_CLASS)
class JdbcToCwmLoaderSynonymsH2Test {

    private static final String SCHEMA = "SYN_H2";
    private static final String OTHER = "SYN_H2_OTHER";

    private Connection connection;
    private Schema loaded;

    @BeforeAll
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:cwmSynonymsH2;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE SCHEMA " + SCHEMA);
            s.execute("CREATE SCHEMA " + OTHER);
            s.execute("CREATE TABLE " + SCHEMA + ".CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100))");
            s.execute("CREATE VIEW " + SCHEMA + ".V_CUSTOMERS AS SELECT ID, NAME FROM " + SCHEMA + ".CUSTOMERS");
            s.execute("CREATE TABLE " + OTHER + ".ORDERS (ID INT PRIMARY KEY)");
            s.execute("CREATE SYNONYM " + SCHEMA + ".SYN_CUSTOMERS FOR " + SCHEMA + ".CUSTOMERS");
            s.execute("CREATE SYNONYM " + SCHEMA + ".SYN_VIEW FOR " + SCHEMA + ".V_CUSTOMERS");
            s.execute("CREATE SYNONYM " + SCHEMA + ".SYN_ORDERS FOR " + OTHER + ".ORDERS");
        }
        // The dialect-only metadata (synonyms, sequences, ...) is read for the
        // connection's current schema.
        connection.setSchema(SCHEMA);
        MetaInfo info = new DatabaseServiceImpl().createMetaInfo(connection, new H2MetadataProvider());
        Catalog catalog = new CwmLoaderImpl().load(info,
                JdbcToCwmConfig.builder().schemas(SCHEMA).catalogName("SYN").build());
        loaded = Catalogs.schemas(catalog).stream().filter(sc -> SCHEMA.equals(sc.getName())).findFirst()
                .orElseThrow();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed())
            connection.close();
    }

    @Test
    void synonymsAreLoadedAsSynonymElements() {
        assertThat(Synonyms.synonyms(loaded)).extracting(Synonym::getName)
                .containsExactlyInAnyOrder("SYN_CUSTOMERS", "SYN_VIEW", "SYN_ORDERS");
    }

    @Test
    void driverSynonymRowsDoNotBecomeTables() {
        // H2's getTables() also lists the synonyms (TABLE_TYPE = SYNONYM) — with the
        // target's columns. They must not show up as tables.
        assertThat(Schemas.tables(loaded)).extracting(Table::getName).containsExactly("CUSTOMERS");
        assertThat(Schemas.views(loaded)).extracting(View::getName).containsExactly("V_CUSTOMERS");
    }

    @Test
    void synonymToTableResolvesToLoadedTable() {
        Table customers = Schemas.findTable(loaded, "CUSTOMERS").orElseThrow();
        Synonym syn = Synonyms.find(loaded, "SYN_CUSTOMERS").orElseThrow();

        assertThat(syn.getTarget()).isSameAs(customers);
        assertThat(syn.getTargetSchemaName()).isEqualTo(SCHEMA);
        assertThat(syn.getTargetObjectType()).isEqualTo("BASE TABLE");
        assertThat(ColumnSets.columns(customers)).hasSize(2);
    }

    @Test
    void synonymToViewResolvesToLoadedView() {
        assertThat(Synonyms.find(loaded, "SYN_VIEW").orElseThrow().getTarget())
                .isSameAs(Schemas.findView(loaded, "V_CUSTOMERS").orElseThrow());
    }

    @Test
    void synonymIntoFilteredSchemaKeepsRawTarget() {
        Synonym syn = Synonyms.find(loaded, "SYN_ORDERS").orElseThrow();

        assertThat(syn.getTarget()).isNull();
        assertThat(syn.getTargetSchemaName()).isEqualTo(OTHER);
        assertThat(syn.getTargetName()).isEqualTo("ORDERS");
    }
}
