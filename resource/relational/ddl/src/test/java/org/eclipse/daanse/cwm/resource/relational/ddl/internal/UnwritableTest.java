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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritable;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.UnwritableException;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.junit.jupiter.api.Test;

/**
 * What a dialect cannot write as the model has it is written plainer or left out, and the log names
 * each case; strict, the generation fails, and its exception names every case of the run with its
 * element, what the dialect cannot write and the dialect.
 */
class UnwritableTest {

    private static final DdlSettings STRICT = DdlSettings.defaults().withStrict(true);

    private final DuckDbDialect duckdb = new DuckDbDialect();
    private final MicrosoftSqlServerDialect sqlServer = new MicrosoftSqlServerDialect();

    /**
     * Four things DuckDB cannot write: a materialized view, an index over part of the rows, a
     * descending index column and a deferrable foreign key.
     */
    private SqlGenFixture fourCasesForDuckDb() {
        SqlGenFixture f = SqlGenFixture.build("sales", duckdb);
        TaggedValues.set(f.view, RelationalRules.MATERIALIZED_TAG, "true");
        f.nameIndex.setFilterCondition("\"NAME\" IS NOT NULL");
        Column email = ColumnSets.findColumn(f.customers, "EMAIL").orElseThrow();
        SQLIndex descending = SQLIndexes.index("IDX_CUSTOMERS_EMAIL", false, f.customers, email);
        ((SQLIndexColumn) descending.getIndexedFeature().get(0)).setIsAscending(false);
        f.schema.getOwnedElement().add(descending);
        f.ordersFk.setDeferrability(DeferrabilityType.INITIALLY_IMMEDIATE);
        return f;
    }

    @Test
    void notStrictWhatTheDialectCannotWriteIsWrittenPlainerOrLeftOut() {
        SqlGenFixture f = fourCasesForDuckDb();

        List<DdlStatement> create = new DdlGeneratorImpl(duckdb).create(List.of(f.schema));

        assertThat(create).filteredOn(s -> s.element() == f.view).singleElement()
                .satisfies(s -> assertThat(s.sql()).startsWith("CREATE VIEW"));
        assertThat(create).noneMatch(s -> s.element() == f.nameIndex);
        assertThat(create).filteredOn(s -> s.sql().contains("IDX_CUSTOMERS_EMAIL")).singleElement()
                .satisfies(s -> assertThat(s.sql()).doesNotContain("DESC"));
        assertThat(create).filteredOn(s -> s.element() == f.orders).singleElement()
                .satisfies(s -> assertThat(s.sql()).contains("FOREIGN KEY").doesNotContain("DEFERR"));
    }

    @Test
    void strictTheCreateAndTheDropScriptFailNamingEveryCase() {
        SqlGenFixture f = fourCasesForDuckDb();
        DdlGeneratorImpl strict = new DdlGeneratorImpl(duckdb, STRICT);

        assertThatThrownBy(() -> strict.create(List.of(f.schema))).isInstanceOfSatisfying(UnwritableException.class,
                e -> {
                    assertThat(e.cases()).extracting(Unwritable::element).containsExactlyInAnyOrder(
                            "view sales.CUSTOMER_ORDERS", "index sales.IDX_CUSTOMERS_NAME",
                            "index sales.IDX_CUSTOMERS_EMAIL", "foreign key sales.ORDERS.FK_ORDERS_CUSTOMERS");
                    assertThat(e.cases()).extracting(Unwritable::dialect).containsOnly(duckdb.name());
                    assertThat(e.getMessage()).contains("4 cases", "view sales.CUSTOMER_ORDERS: " + duckdb.name()
                            + " cannot write MATERIALIZED (not strict: written as a plain view)");
                });
        assertThatThrownBy(() -> strict.drop(List.of(f.schema))).isInstanceOfSatisfying(UnwritableException.class,
                e -> assertThat(e.cases()).hasSize(4));
        assertThatThrownBy(() -> strict.createElement(f.view)).isInstanceOfSatisfying(UnwritableException.class,
                e -> assertThat(e.cases()).singleElement().extracting(Unwritable::what).isEqualTo("MATERIALIZED"));
    }

    /**
     * SQL Server's triggers fire after the statement and once per statement: a {@code BEFORE} row
     * trigger is written as an {@code AFTER} statement trigger, or, strict, not at all.
     */
    @Test
    void aBeforeRowTriggerIsWrittenAfterThePerStatementOnSqlServer() {
        SqlGenFixture f = SqlGenFixture.build("sales", sqlServer);
        Trigger trigger = f.attachTrigger(f.orders, "trg_orders_audit", "SET NOCOUNT ON;");
        f.schema.getOwnedElement().add(trigger);

        assertThat(new DdlGeneratorImpl(sqlServer).create(List.of(f.schema))).filteredOn(s -> s.element() == trigger)
                .anyMatch(s -> s.sql().contains("AFTER INSERT"));
        assertThatThrownBy(() -> new DdlGeneratorImpl(sqlServer, STRICT).create(List.of(f.schema)))
                .isInstanceOfSatisfying(UnwritableException.class, e -> assertThat(e.cases())
                        .extracting(Unwritable::element, Unwritable::what, Unwritable::instead)
                        .containsExactlyInAnyOrder(
                                org.assertj.core.groups.Tuple.tuple("trigger sales.trg_orders_audit", "BEFORE",
                                        "written AFTER"),
                                org.assertj.core.groups.Tuple.tuple("trigger sales.trg_orders_audit",
                                        "FOR EACH ROW", "written as a statement trigger")));
    }

    /** MySQL keeps no comment on a view: it is left out, or, strict, the generation fails. */
    @Test
    void aCommentOnAViewWhereTheDatabaseKeepsNone() {
        MySqlDialect mysql = new MySqlDialect();
        SqlGenFixture f = SqlGenFixture.build("sales", mysql);
        SqlGenFixture.comment(f.schema, f.view, "customers and their orders");

        assertThat(new DdlGeneratorImpl(mysql).create(List.of(f.schema)))
                .noneMatch(s -> s.sql().contains("customers and their orders"));
        assertThatThrownBy(() -> new DdlGeneratorImpl(mysql, STRICT).create(List.of(f.schema)))
                .isInstanceOfSatisfying(UnwritableException.class,
                        e -> assertThat(e.cases()).singleElement().satisfies(c -> {
                            assertThat(c.element()).isEqualTo("view sales.CUSTOMER_ORDERS");
                            assertThat(c.what()).isEqualTo("a comment on a view");
                            assertThat(c.instead()).isEqualTo("left out");
                        }));
    }

    /** DuckDB has no users: they are left out, or, strict, the generation fails naming each. */
    @Test
    void usersWhereTheDialectHasNone() {
        ResponsibleParty clerk = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        clerk.setName("clerk");
        ResponsibleParty auditor = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        auditor.setName("auditor");

        assertThat(new PrivilegeSqlGeneratorImpl(duckdb).createUserStatements(List.of(clerk, auditor))).isEmpty();
        assertThatThrownBy(() -> new PrivilegeSqlGeneratorImpl(duckdb, STRICT)
                .createUserStatements(List.of(clerk, auditor))).isInstanceOfSatisfying(UnwritableException.class,
                        e -> assertThat(e.cases()).extracting(Unwritable::element)
                                .containsExactly("user auditor", "user clerk"));
    }
}
