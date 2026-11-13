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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** Every setting holds for every statement, and the dialect decides what it can write. */
class SettingsHonouredTest {

    private static Schema sales(String schemaName) {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "x");
        sales.setName(schemaName);
        Table orders = Tables.create(sales, "orders");
        Column id = Columns.create(orders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column qty = Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKeys.create(orders, "pk_orders", id);
        sales.getOwnedElement().add(SQLIndexes.index("ix_orders_qty", false, orders, qty));
        return sales;
    }

    @Test
    void withoutIfNotExistsNoStatementSaysIfExists() {
        Schema sales = sales("sales");
        DdlGeneratorImpl generator = new DdlGeneratorImpl(new PostgreSqlDialect(),
                DdlSettings.defaults().withIfNotExists(false));

        List<String> all = new ArrayList<>(generator.createSchema(sales));
        all.addAll(generator.dropSchema(sales));

        assertThat(all).isNotEmpty().noneMatch(s -> s.contains("IF NOT EXISTS"))
                .noneMatch(s -> s.contains("IF EXISTS"));
    }

    @Test
    void aSingleCreateTableAsksTheDialectForIfNotExistsToo() {
        Schema sales = sales("sales");
        Table orders = Schemas.tables(sales).get(0);

        assertThat(new DdlGeneratorImpl(new OracleDialect()).createElement(orders)).singleElement().asString()
                .startsWith("CREATE TABLE \"sales\"");
        assertThat(new DdlGeneratorImpl(new H2Dialect(), DdlSettings.defaults().withIfNotExists(false))
                .createElement(orders)).singleElement().asString().startsWith("CREATE TABLE \"sales\"");
        assertThat(new DdlGeneratorImpl(new H2Dialect()).createElement(orders)).singleElement().asString()
                .startsWith("CREATE TABLE IF NOT EXISTS");
    }

    @Test
    void aDialectWithoutIndexesNeitherCreatesNorDropsOne() {
        Schema sales = sales("sales");
        DdlGeneratorImpl generator = new DdlGeneratorImpl(new H2Dialect() {
            @Override
            public boolean supportsIndexDdl() {
                return false;
            }
        });

        assertThat(generator.createSchema(sales)).noneMatch(s -> s.contains("INDEX"));
        assertThat(generator.dropSchema(sales)).noneMatch(s -> s.contains("INDEX"));
    }

    @Test
    void aSchemaWithoutNameGivesBareNamesAndNoSchemaStatement() {
        Schema unnamed = sales(null);
        DdlGeneratorImpl generator = new DdlGeneratorImpl(new PostgreSqlDialect());

        List<String> all = new ArrayList<>(generator.createSchema(unnamed));
        all.addAll(generator.dropSchema(unnamed));

        assertThat(all).isNotEmpty().noneMatch(s -> s.contains("SCHEMA")).noneMatch(s -> s.contains("\"null\""))
                .noneMatch(s -> s.contains("\".\"orders\""));
        assertThat(all).anyMatch(s -> s.startsWith("CREATE TABLE IF NOT EXISTS \"orders\""));
    }
}
