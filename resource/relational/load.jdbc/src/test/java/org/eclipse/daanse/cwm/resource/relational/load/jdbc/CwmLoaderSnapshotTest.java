/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.BusinessInformationRules;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** The loader on snapshots built in code: what a database reports, and what the catalog makes of it. */
class CwmLoaderSnapshotTest {

    // --- what the database reports ----------------------------------------------------------

    @Test
    void aTableOfADatabaseWithoutSchemasLandsInASchemaWithoutName() {
        Snapshot snapshot = new Snapshot().table(null, "item", "id", "label").primaryKey(null, "item", "pk_item",
                "id");

        Catalog catalog = load(snapshot);

        assertThat(Catalogs.schemas(catalog)).singleElement().satisfies(schema -> {
            assertThat(schema.getName()).isNull();
            assertThat(Schemas.tables(schema)).extracting(Table::getName).containsExactly("item");
        });
        Table item = Schemas.tables(Catalogs.schemas(catalog).get(0)).get(0);
        assertThat(Tables.findPrimaryKey(item)).get().extracting(PrimaryKey::getName).isEqualTo("pk_item");
        assertThat(QualifiedNames.of(item)).isEqualTo("TestDB.item");
    }

    @Test
    void aUniqueConstraintOnPartOfThePrimaryKeyIsKept() {
        Snapshot snapshot = new Snapshot().table("s", "line", "order_id", "pos").primaryKey("s", "line", "pk_line",
                "order_id", "pos").unique("s", "line", "uq_line_order", "order_id");

        Table line = onlyTable(load(snapshot));

        assertThat(Tables.uniqueConstraints(line)).extracting(ModelElement::getName)
                .containsExactlyInAnyOrder("pk_line", "uq_line_order");
    }

    @Test
    void aUniqueConstraintOverExactlyThePrimaryKeyIsTheKeyItself() {
        Snapshot snapshot = new Snapshot().table("s", "line", "order_id", "pos").primaryKey("s", "line", "pk_line",
                "order_id", "pos").unique("s", "line", "uq_line", "order_id", "pos")
                .unique("s", "line", "uq_line_turned", "pos", "order_id");

        Table line = onlyTable(load(snapshot));

        assertThat(Tables.uniqueConstraints(line)).extracting(ModelElement::getName)
                .containsExactly("pk_line", "uq_line_turned");
    }

    @Test
    void anUnnamedForeignKeyOverTwoColumnsIsOneForeignKey() {
        Snapshot snapshot = new Snapshot().table("s", "orders", "id", "pos").primaryKey("s", "orders", "pk_orders",
                "id", "pos").table("s", "line", "order_id", "order_pos")
                .foreignKey(null, "s", "line", List.of("order_id", "order_pos"), "s", "orders", List.of("id", "pos"),
                        "pk_orders");

        Catalog catalog = load(snapshot);

        Table line = table(catalog, "line");
        assertThat(Tables.foreignKeys(line)).singleElement().satisfies(fk -> {
            assertThat(fk.getName()).isEqualTo("fk_line");
            assertThat(columnNames(fk)).containsExactly("order_id", "order_pos");
            assertThat(fk.getUniqueKey()).isSameAs(Tables.findPrimaryKey(table(catalog, "orders")).orElseThrow());
        });
    }

    @Test
    void twoUnnamedForeignKeysToTheSameKeyStayApartAndGetNamesOfTheirOwn() {
        // getImportedKeys orders by the referenced table and the key sequence: the rows of two
        // foreign keys to one key interleave
        Snapshot snapshot = new Snapshot().table("s", "orders", "id", "pos").primaryKey("s", "orders", "pk_orders",
                "id", "pos").table("s", "move", "from_id", "from_pos", "to_id", "to_pos")
                .importedKey(row("from_id", "id", 1)).importedKey(row("to_id", "id", 1))
                .importedKey(row("from_pos", "pos", 2)).importedKey(row("to_pos", "pos", 2));

        Table move = table(load(snapshot), "move");

        assertThat(Tables.foreignKeys(move)).extracting(CwmLoaderSnapshotTest::columnNames)
                .containsExactly(List.of("from_id", "from_pos"), List.of("to_id", "to_pos"));
        assertThat(Tables.foreignKeys(move)).extracting(ModelElement::getName).containsExactly("fk_move", "fk_move_2");
    }

    @Test
    void aForeignKeyToColumnsWithoutAConstraintDoesNotBindToThePrimaryKey() {
        // PostgreSQL lets a foreign key reference the columns of a unique index that is no constraint
        Snapshot snapshot = new Snapshot().table("s", "customer", "id", "code")
                .primaryKey("s", "customer", "pk_customer", "id").index("s", "customer", "ix_customer_code", true,
                        "code")
                .table("s", "orders", "customer_code").foreignKey("fk_orders_customer", "s", "orders",
                        List.of("customer_code"), "s", "customer", List.of("code"), "ix_customer_code");

        Catalog catalog = load(snapshot);

        ForeignKey fk = Tables.foreignKeys(table(catalog, "orders")).get(0);
        UniqueKey target = fk.getUniqueKey();
        assertThat(target).isNotInstanceOf(PrimaryKey.class);
        assertThat(target.getFeature()).extracting(ModelElement::getName).containsExactly("code");
        assertThat(target.eContainer()).isSameAs(table(catalog, "customer"));
        assertThat(catalog.eAllContents()).toIterable().as("the unique index is the key's, not one more")
                .noneMatch(o -> o instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex);
    }

    @Test
    void unnamedKeysAndChecksOfOneTableGetNamesOfTheirOwn() {
        Snapshot snapshot = new Snapshot().table("s", "orders", "id", "code", "note").primaryKey("s", "orders", null,
                "id").unique("s", "orders", null, "code").unique("s", "orders", null, "note")
                .check("s", "orders", null, "code > 0").check("s", "orders", null, "note > 0");

        Table orders = onlyTable(load(snapshot));

        List<String> names = orders.getOwnedElement().stream().map(ModelElement::getName).toList();
        assertThat(names).hasSize(5).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void anInsteadOfTriggerIsNotReadAsABeforeTrigger() {
        Snapshot snapshot = new Snapshot().table("s", "orders", "id")
                .trigger("s", "orders", "trg_orders_instead",
                        org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.INSTEAD_OF,
                        org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent.INSERT)
                .trigger("s", "orders", "trg_orders_before",
                        org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.BEFORE,
                        org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent.INSERT);

        Table orders = onlyTable(load(snapshot));

        assertThat(orders.getTrigger()).extracting(ModelElement::getName).containsExactly("trg_orders_before");
    }

    /**
     * MySQL and MariaDB report the action of a trigger whose body the DDL generator put into a
     * procedure of its own as the call of that procedure. The procedure is the trigger's, not a
     * routine of the catalog, so its body is the trigger's body: written again, the trigger gets the
     * same procedure. A trigger that does more than call it keeps its own action.
     */
    @Test
    void aTriggerThatOnlyCallsItsProcedureHasTheBodyOfThatProcedure() {
        org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming before =
                org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.BEFORE;
        org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent insert =
                org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent.INSERT;
        Snapshot snapshot = new Snapshot().table("s", "orders", "id")
                .trigger("s", "orders", "trg_audit", before, insert, "CALL `s`.`trg_audit_fn`()")
                .procedure("s", "trg_audit_fn", "BEGIN SET @n = 1; END",
                        "CREATE PROCEDURE `s`.`trg_audit_fn`() BEGIN SET @n = 1; END")
                .trigger("s", "orders", "trg_more", before, insert, "BEGIN CALL `s`.`trg_more_fn`(); SET @m = 2; END")
                .procedure("s", "trg_more_fn", "BEGIN SET @k = 3; END",
                        "CREATE PROCEDURE `s`.`trg_more_fn`() BEGIN SET @k = 3; END");

        Catalog catalog = load(snapshot);

        assertThat(onlyTable(catalog).getTrigger())
                .extracting(t -> t.getName() + ": " + t.getActionStatement().getBody())
                .containsExactly("trg_audit: BEGIN SET @n = 1; END",
                        "trg_more: BEGIN CALL `s`.`trg_more_fn`(); SET @m = 2; END");
        assertThat(paths(catalog)).as("the procedure of a trigger is no routine of the catalog")
                .noneMatch(p -> p.startsWith("Procedure "));
    }

    /**
     * The index a database made itself for a foreign key, and says so, is no index of the model:
     * written as DDL, the database would hold it twice. An index declared on the same column stays.
     */
    @Test
    void anIndexTheDatabaseMadeForAForeignKeyIsNotRead() {
        Snapshot snapshot = new Snapshot().table("s", "customer", "id").primaryKey("s", "customer", "pk_customer", "id")
                .table("s", "orders", "id", "customer_id", "product_id")
                .foreignKey("fk_orders_customer", "s", "orders", List.of("customer_id"), "s", "customer",
                        List.of("id"), "pk_customer")
                .indexForConstraint("s", "orders", "fk_orders_customer", "customer_id")
                .index("s", "orders", "ix_orders_product", false, "product_id");

        Catalog catalog = load(snapshot);

        assertThat(SQLIndexes.indexes(Catalogs.findSchema(catalog, "s").orElseThrow()))
                .extracting(ModelElement::getName).containsExactly("ix_orders_product");
    }

    /**
     * A routine keeps the specific name the database gives it where it differs from its name, as a
     * tagged value: it tells overloads apart, and a privilege names the overload it is on by it.
     */
    @Test
    void aRoutineKeepsItsSpecificNameWhereItDiffersFromItsName() {
        Snapshot snapshot = new Snapshot().schema("s").procedure("s", "f", "f_16384").procedure("s", "f", "f_16385")
                .procedure("s", "g", "g");

        List<Procedure> routines = Catalogs.findSchema(load(snapshot), "s").orElseThrow().getOwnedElement().stream()
                .filter(Procedure.class::isInstance).map(Procedure.class::cast).toList();

        assertThat(routines).extracting(p -> p.getName() + " "
                + TaggedValues.value(p, RelationalNames.SPECIFIC_NAME_TAG).orElse("(none)"))
                .containsExactlyInAnyOrder("f f_16384", "f f_16385", "g (none)");
        assertThat(routines).extracting(RelationalNames::specificName)
                .containsExactlyInAnyOrder("f_16384", "f_16385", "g");
    }

    @Test
    void aForeignKeyBindsToTheKeyOverItsColumnsInWhateverOrder_pairedByPosition() {
        Snapshot snapshot = new Snapshot().table("s", "customer", "id", "region", "number")
                .primaryKey("s", "customer", "pk_customer", "id")
                .unique("s", "customer", "uq_customer", "region", "number")
                .table("s", "orders", "customer_number", "customer_region")
                .foreignKey("fk_orders_customer", "s", "orders", List.of("customer_number", "customer_region"), "s",
                        "customer", List.of("number", "region"), "uq_customer");

        Catalog catalog = load(snapshot);

        ForeignKey fk = Tables.foreignKeys(table(catalog, "orders")).get(0);
        assertThat(fk.getUniqueKey().getName()).isEqualTo("uq_customer");
        assertThat(columnNames(fk)).as("in the order of the key's columns")
                .containsExactly("customer_region", "customer_number");
    }

    @Test
    void aMaterializedViewIsAViewAndLeftOutWithTheViews() {
        Snapshot snapshot = new Snapshot().table("s", "customer", "id").view("s", "v_customer", "id")
                .materializedView("s", "mv_customer");

        Catalog catalog = load(snapshot, "!is:view");

        Schema s = Catalogs.schemas(catalog).get(0);
        assertThat(s.getOwnedElement()).noneMatch(View.class::isInstance);
        assertThat(Schemas.tables(s)).extracting(Table::getName).containsExactly("customer");
    }

    @Test
    void aMaterializedViewKeptInATableIsAViewToo() {
        // Oracle reports the container of a materialized view as a table as well
        Snapshot snapshot = new Snapshot().table("s", "customer", "id").table("s", "mv_customer", "id")
                .materializedView("s", "mv_customer");

        Schema all = Catalogs.schemas(load(snapshot)).get(0);
        Schema withoutViews = Catalogs.schemas(load(snapshot, "!is:view")).get(0);

        View mv = Schemas.findView(all, "mv_customer").orElseThrow();
        assertThat(mv.isIsReadOnly()).isTrue();
        assertThat(TaggedValues.value(mv, RelationalRules.MATERIALIZED_TAG)).contains("true");
        assertThat(mv.getFeature()).extracting(ModelElement::getName).containsExactly("id");
        assertThat(Schemas.tables(all)).extracting(Table::getName).containsExactly("customer");
        assertThat(withoutViews.getOwnedElement()).extracting(ModelElement::getName).containsExactly("customer");
    }

    // --- what the selection reads ---------------------------------------------------------------

    @Test
    void anIncludeRuleOnASchemaReadsThatSchemaOnly() {
        Catalog catalog = load(shop(), "schema:sales");

        assertThat(Catalogs.schemas(catalog)).extracting(Schema::getName).containsExactly("sales");
        assertThat(paths(catalog)).noneMatch(p -> p.contains(".stock"));
    }

    @Test
    void anIncludeRuleThatMatchesNothingIsAnError() {
        assertThatThrownBy(() -> load(shop(), "schema:sales", "schema:salse", "!table:nowhere"))
                .isInstanceOfSatisfying(UnmatchedRulesException.class, e -> assertThat(e.rules())
                        .extracting(ElementRule::describe).containsExactly("schema:salse"))
                .hasMessage("This rule matches nothing in TestDB: schema:salse");
    }

    @Test
    void aKindTheSelectionExcludesIsNotEvenRead() {
        Set<String> asked = new HashSet<>();

        Catalog catalog = new CwmLoaderImpl().load(shop().recording(asked), JdbcToCwmConfig.defaults()
                .withSelection(selection("!is:foreignKey", "!is:check", "!is:index", "!is:trigger", "!is:procedure",
                        "!is:view", "!is:comment")));

        assertThat(asked).contains("tables", "columns", "primaryKeys", "uniqueConstraints")
                .doesNotContain("importedKeys", "checkConstraints", "indexInfos", "triggers", "procedures",
                        "functions", "viewDefinitions");
        assertThat(paths(catalog)).noneMatch(p -> p.startsWith("ForeignKey") || p.startsWith("View")
                || p.startsWith("Description"));
    }

    @Test
    void anExcludedSchemaLeavesNothingOfIt() {
        Catalog catalog = load(new Snapshot().table("tmp", "scratch", "id").primaryKey("tmp", "scratch", "pk", "id"),
                "!schema:tmp");

        assertThat(catalog.getOwnedElement()).as("not even the type of its column").isEmpty();
    }

    @Test
    void systemAndTemporaryTablesSayWhatTheyAre_andCanBeLeftOut() {
        Snapshot snapshot = new Snapshot().table("s", "orders", "id")
                .tableOfType("s", "sys_stats", TableReference.TYPE_SYSTEM_TABLE, "id")
                .tableOfType("s", "tmp_rows", TableReference.TYPE_GLOBAL_TEMPORARY, "id");

        Schema all = Catalogs.schemas(load(snapshot)).get(0);
        Schema plain = Catalogs.schemas(load(snapshot, "!is:system", "!is:temporary")).get(0);

        assertThat(Schemas.findTable(all, "sys_stats")).get().extracting(Table::isIsSystem).isEqualTo(true);
        assertThat(Schemas.findTable(all, "tmp_rows")).get().satisfies(t -> {
            assertThat(t.isIsTemporary()).isTrue();
            assertThat(t.getTemporaryScope()).isEqualTo("GLOBAL");
        });
        assertThat(Schemas.tables(plain)).extracting(Table::getName).containsExactly("orders");
    }

    @Test
    void commentsAreDescriptionsOfTheConfiguredType() {
        Snapshot snapshot = new Snapshot().table("s", "orders", "id").remarks("s", "orders", "What was ordered");

        Table orders = onlyTable(load(snapshot));
        Table custom = onlyTable(new CwmLoaderImpl().load(snapshot.build(),
                JdbcToCwmConfig.defaults().withCommentType("COMMENT")));
        Table without = onlyTable(load(snapshot, "!is:comment"));

        assertThat(Descriptions.find(orders, Descriptions.JDBC_REMARKS)).get().extracting(Description::getBody)
                .isEqualTo("What was ordered");
        assertThat(Descriptions.find(custom, "COMMENT")).isPresent();
        assertThat(Descriptions.all(without)).isEmpty();
        assertThatThrownBy(() -> JdbcToCwmConfig.defaults().withCommentType(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("!is:comment");
    }

    /**
     * What the loader reads is what a copy of the whole catalog, cut to the same selection, holds:
     * the shells it leaves out early are those the cut would leave out too.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "schema:sales", "table:sales.orders", "table:stock.*", "view:sales.v_*",
            "!is:constraint", "!is:primaryKey", "!is:unique", "!name:customer", "!path:**.orders.customer_id",
            "name:id", "schema:sales\n!is:index\n!is:comment", "is:procedure", "!schema:stock" })
    void readingASelectionEqualsCuttingTheWholeCatalog(String rules) {
        Selection selection = selection(rules);
        List<ConsequenceRule> consequences = ConsequenceRule.combine(RelationalRules.CONSEQUENCES,
                BusinessInformationRules.CONSEQUENCES);

        Catalog read = new CwmLoaderImpl().load(shop().build(), JdbcToCwmConfig.defaults().withSelection(selection));
        Catalog cut = Selections.copy(load(shop()), selection, consequences).model();

        assertThat(paths(read)).isEqualTo(paths(cut));
    }

    // --- helpers -----------------------------------------------------------------------------------

    /**
     * Two schemas: {@code stock.product}; {@code sales.customer} with a unique code, a comment and a
     * check, {@code sales.orders} with a foreign key to each, an index, a view and a procedure.
     */
    private static Snapshot shop() {
        return new Snapshot().schema("stock").schema("sales")
                .table("stock", "product", "id", "name").primaryKey("stock", "product", "pk_product", "id")
                .table("sales", "customer", "id", "code", "name").primaryKey("sales", "customer", "pk_customer", "id")
                .unique("sales", "customer", "uq_customer_code", "code").index("sales", "customer",
                        "uq_customer_code", true, "code")
                .check("sales", "customer", "ck_customer_name", "name IS NOT NULL")
                .remarks("sales", "customer", "Who orders")
                .table("sales", "orders", "id", "customer_id", "customer_code", "product_id", "qty")
                .primaryKey("sales", "orders", "pk_orders", "id")
                .index("sales", "orders", "pk_orders", true, "id")
                .index("sales", "orders", "ix_orders_customer", false, "customer_id")
                .foreignKey("fk_orders_customer", "sales", "orders", List.of("customer_id"), "sales", "customer",
                        List.of("id"), "pk_customer")
                .foreignKey("fk_orders_code", "sales", "orders", List.of("customer_code"), "sales", "customer",
                        List.of("code"), "uq_customer_code")
                .foreignKey("fk_orders_product", "sales", "orders", List.of("product_id"), "stock", "product",
                        List.of("id"), "pk_product")
                .view("sales", "v_orders", "id", "qty")
                .procedure("sales", "p_archive", "p_archive_1");
    }

    private static Catalog load(Snapshot snapshot, String... rules) {
        return new CwmLoaderImpl().load(snapshot.build(), JdbcToCwmConfig.defaults().withSelection(selection(rules)));
    }

    /** The rules in the words of the relational model and of comments. */
    private static Selection selection(String... rules) {
        return Selection.parse(RelationalRules.VOCABULARY.with(BusinessInformationRules.VOCABULARY), rules);
    }

    /** Every element of the catalog with its kind, in containment order. */
    private static List<String> paths(Catalog catalog) {
        return Selections.stream(catalog, Selection.all(), ModelElement.class)
                .map(e -> e.eClass().getName() + " " + QualifiedNames.of(e)).toList();
    }

    private static ImportedKey row(String column, String targetColumn, int sequence) {
        return new ImportedKeyRecord(
                new ColumnReference(Optional.of(Snapshot.ref("s", "orders")), targetColumn),
                new ColumnReference(Optional.of(Snapshot.ref("s", "move")), column), null, sequence,
                ImportedKey.ReferentialAction.NO_ACTION, ImportedKey.ReferentialAction.NO_ACTION,
                Optional.of("pk_orders"), ImportedKey.Deferrability.NOT_DEFERRABLE);
    }

    private static Table onlyTable(Catalog catalog) {
        return Schemas.tables(Catalogs.schemas(catalog).get(0)).get(0);
    }

    private static Table table(Catalog catalog, String name) {
        return Catalogs.schemas(catalog).stream().flatMap(s -> Schemas.tables(s).stream())
                .filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
    }

    private static List<String> columnNames(ForeignKey fk) {
        return fk.getFeature().stream().map(ModelElement::getName).toList();
    }
}
