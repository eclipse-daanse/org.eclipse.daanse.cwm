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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.CwmSchemaMapper;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritable;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.UnwritableException;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/**
 * Each statement names the element it is written for, the drop script undoes the create script
 * part by part, and what the dialect API cannot write is left out or written plainer, never wrong.
 */
class ScriptPartsTest {

    private final PostgreSqlDialect pg = new PostgreSqlDialect();
    private final DdlGeneratorImpl generator = new DdlGeneratorImpl(pg);

    @Test
    void eachStatementNamesItsElementAndTheDropScriptRunsThePartsBackwards() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        Trigger trigger = f.attachTrigger(f.orders, "trg_orders_audit", "BEGIN RETURN NEW; END;");
        f.schema.getOwnedElement().add(trigger);
        SqlGenFixture.comment(f.schema, f.customers, "all customers");

        List<DdlStatement> create = generator.create(List.of(f.schema));
        List<DdlStatement> drop = generator.drop(List.of(f.schema));

        assertThat(create).extracting(DdlStatement::element).containsExactly(f.schema, f.customers, f.orders,
                Descriptions.find(f.customers, Descriptions.JDBC_REMARKS).orElseThrow(), f.uniqueEmail,
                f.emailCheck, f.nameIndex, f.ordersFk, f.view, trigger, trigger);
        assertThat(drop).extracting(DdlStatement::element).containsExactly(trigger, trigger, f.view, f.ordersFk,
                f.nameIndex, f.emailCheck, f.uniqueEmail, f.orders, f.customers, f.schema);
        assertThat(drop.get(0).sql()).startsWith("DROP TRIGGER");
        assertThat(drop.get(1).sql()).startsWith("DROP FUNCTION");
        assertThat(create).filteredOn(s -> s.element() instanceof Description).singleElement()
                .satisfies(s -> assertThat(s.sql()).startsWith("COMMENT ON TABLE"));
    }

    /**
     * PostgreSQL has materialized views: one is created and dropped as one. The dialect writes no
     * comment on a materialized view, so its comment is left out, a case the dialect cannot write.
     */
    @Test
    void aMaterializedViewIsWrittenAsOneWhereTheDialectHasThem() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        TaggedValues.set(f.view, RelationalRules.MATERIALIZED_TAG, "true");
        SqlGenFixture.comment(f.schema, f.view, "customers and their orders");

        List<DdlStatement> create = generator.create(List.of(f.schema));

        assertThat(create).filteredOn(s -> s.element() == f.view).singleElement()
                .satisfies(s -> assertThat(s.sql()).startsWith(
                        "CREATE MATERIALIZED VIEW IF NOT EXISTS \"sales\".\"CUSTOMER_ORDERS\" AS SELECT"));
        assertThat(create).noneMatch(s -> s.sql().contains("customers and their orders"));
        assertThat(generator.drop(List.of(f.schema))).filteredOn(s -> s.element() == f.view).singleElement()
                .satisfies(s -> assertThat(s.sql())
                        .isEqualTo("DROP MATERIALIZED VIEW IF EXISTS \"sales\".\"CUSTOMER_ORDERS\""));
        assertThatThrownBy(() -> new DdlGeneratorImpl(pg, DdlSettings.defaults().withStrict(true))
                .create(List.of(f.schema))).isInstanceOfSatisfying(UnwritableException.class,
                        e -> assertThat(e.cases()).singleElement().extracting(Unwritable::what)
                                .isEqualTo("a comment on a materialized view"));
    }

    /**
     * PostgreSQL has partial indexes and keeps the order of an index column: an index over part of the
     * rows is written with its filter, and dropped as any index; a descending column is written
     * {@code DESC}.
     */
    @Test
    void anIndexOverPartOfTheRowsIsWrittenWithItsFilterAndADescendingColumnDesc() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        f.nameIndex.setFilterCondition("\"NAME\" IS NOT NULL");
        SQLIndex descending = SQLIndexes.index("IDX_CUSTOMERS_EMAIL", false, f.customers,
                (org.eclipse.daanse.cwm.model.cwm.resource.relational.Column) f.customers.getFeature().get(1));
        ((SQLIndexColumn) descending.getIndexedFeature().get(0)).setIsAscending(false);
        f.schema.getOwnedElement().add(descending);

        List<DdlStatement> create = generator.create(List.of(f.schema));

        assertThat(create).filteredOn(s -> s.element() == f.nameIndex).singleElement()
                .satisfies(s -> assertThat(s.sql()).isEqualTo("CREATE INDEX IF NOT EXISTS \"IDX_CUSTOMERS_NAME\""
                        + " ON \"sales\".\"CUSTOMERS\" (\"NAME\") WHERE \"NAME\" IS NOT NULL"));
        assertThat(generator.drop(List.of(f.schema))).filteredOn(s -> s.element() == f.nameIndex).hasSize(1);
        assertThat(create).filteredOn(s -> s.element() == descending).singleElement()
                .satisfies(s -> assertThat(s.sql()).isEqualTo("CREATE INDEX IF NOT EXISTS \"IDX_CUSTOMERS_EMAIL\""
                        + " ON \"sales\".\"CUSTOMERS\" (\"EMAIL\" DESC)"));
    }

    @Test
    void aDeferrableForeignKeyIsWrittenWithoutItsDeferrability() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        ForeignKey fk = f.ordersFk;
        fk.setDeferrability(DeferrabilityType.INITIALLY_IMMEDIATE);

        List<DdlStatement> create = generator.create(List.of(f.schema));

        assertThat(create).filteredOn(s -> s.element() == fk).singleElement()
                .satisfies(s -> assertThat(s.sql()).doesNotContain("DEFERR"));
    }

    /**
     * SQL Server's dialect names a view a view in the statement of its comment, so the comments on a
     * view and on its columns are written, after the view.
     */
    @Test
    void theCommentsOnAViewAndItsColumnsAreWrittenWhereTheDialectNamesAViewAView() {
        MicrosoftSqlServerDialect sqlServer = new MicrosoftSqlServerDialect();
        SqlGenFixture f = SqlGenFixture.build("sales", sqlServer);
        Column name = Columns.create(f.view, "NAME",
                ColumnSets.findColumn(f.customers, "NAME").orElseThrow().getType());
        SqlGenFixture.comment(f.schema, f.view, "customers and their orders");
        SqlGenFixture.comment(f.schema, name, "who orders");

        List<DdlStatement> create = new DdlGeneratorImpl(sqlServer).create(List.of(f.schema));

        List<DdlStatement> onView = create.stream().filter(s -> s.element() instanceof Description d
                && (d.getModelElement().contains(f.view) || d.getModelElement().contains(name))).toList();
        assertThat(onView).extracting(DdlStatement::sql).hasSize(2)
                .allMatch(sql -> sql.contains("@level1type = N'VIEW', @level1name = N'CUSTOMER_ORDERS'"))
                .anyMatch(sql -> sql.contains("@level2name = N'NAME'") && sql.contains("N'who orders'"));
        assertThat(create.indexOf(onView.get(0))).as("after the view")
                .isGreaterThan(create.indexOf(create.stream().filter(s -> s.element() == f.view).findFirst()
                        .orElseThrow()));
    }

    @Test
    void anUnnamedPrimaryKeyIsCreatedUnderItsDefaultName() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        Table customers = f.customers;
        Tables.findPrimaryKey(customers).orElseThrow().setName(null);

        assertThat(generator.create(List.of(f.schema))).filteredOn(s -> s.element() == customers).singleElement()
                .satisfies(s -> assertThat(s.sql()).contains("CONSTRAINT \"pk_CUSTOMERS\" PRIMARY KEY"));
    }

    /**
     * SQL Server writes no orientation, and its triggers fire once per statement: a row trigger is
     * written as a statement trigger, which the generator says, while PostgreSQL writes it as a row
     * trigger. The statements stay as the dialect writes them.
     */
    @Test
    void aRowTriggerIsWrittenPerStatementWhereTheDialectWritesNoOrientation() {
        MicrosoftSqlServerDialect sqlServer = new MicrosoftSqlServerDialect();
        SqlGenFixture f = SqlGenFixture.build("sales", sqlServer);
        Trigger trigger = f.attachTrigger(f.orders, "trg_orders_audit", "SET NOCOUNT ON;");
        f.schema.getOwnedElement().add(trigger);
        TableReference sqlServerOrders = new DdlGeneratorImpl(sqlServer).tableReference(f.orders);
        TableReference pgOrders = generator.tableReference(f.orders);

        assertThat(CwmSchemaMapper.rowTriggerWrittenPerStatement(sqlServer, sqlServerOrders, trigger, "trg"))
                .isTrue();
        assertThat(CwmSchemaMapper.rowTriggerWrittenPerStatement(pg, pgOrders, trigger, "trg")).isFalse();
        assertThat(new DdlGeneratorImpl(sqlServer).create(List.of(f.schema))).filteredOn(s -> s.element() == trigger)
                .isNotEmpty().noneMatch(s -> s.sql().contains("FOR EACH"));
        assertThat(generator.create(List.of(f.schema))).filteredOn(s -> s.element() == trigger)
                .anyMatch(s -> s.sql().contains("FOR EACH ROW"));

        trigger.setActionOrientation(ActionOrientationType.STATEMENT);
        assertThat(CwmSchemaMapper.rowTriggerWrittenPerStatement(sqlServer, sqlServerOrders, trigger, "trg"))
                .as("a statement trigger is written as one").isFalse();
    }

    @Test
    void anEmptyListGivesAnEmptyScript() {
        assertThat(generator.create(List.<Schema>of())).isEmpty();
        assertThat(generator.drop(List.<Schema>of())).isEmpty();
    }
}
