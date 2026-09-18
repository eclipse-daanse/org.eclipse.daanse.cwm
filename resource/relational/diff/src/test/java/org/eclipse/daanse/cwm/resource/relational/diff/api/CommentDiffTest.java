/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** Table and column comments: detection, planning and the emitted SQL, offline. */
class CommentDiffTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void changedTableCommentIsSetLast() {
        SchemaDiff diff = differ.diff(schema("old", null), schema("new", null));
        assertThat(diff.commentsChanged()).singleElement()
                .satisfies(c -> assertThat(c.oldComment()).isEqualTo("old"))
                .satisfies(c -> assertThat(c.newComment()).isEqualTo("new"))
                .satisfies(c -> assertThat(c.element()).isInstanceOf(Table.class));
        assertThat(ChangePlanner.create().plan(diff)).singleElement().isInstanceOf(ChangeOp.SetComment.class);
    }

    @Test
    void removedColumnCommentIsSetToNull() {
        SchemaDiff diff = differ.diff(schema(null, "id of the customer"), schema(null, null));
        CommentChange c = diff.commentsChanged().get(0);
        assertThat(c.element()).isInstanceOf(Column.class);
        assertThat(c.newComment()).isNull();
    }

    @Test
    void addedTableBringsItsComments() {
        Schema empty = R.createSchema();
        empty.setName("sales");
        SchemaDiff diff = differ.diff(empty, schema("customers", "key"));
        assertThat(diff.commentsChanged()).extracting(CommentChange::newComment)
                .containsExactly("customers", "key");
        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        assertThat(ops.get(0)).isInstanceOf(ChangeOp.CreateTable.class);
        assertThat(ops.subList(1, ops.size())).allMatch(ChangeOp.SetComment.class::isInstance);
    }

    @Test
    void sameCommentIsNoDifference() {
        assertThat(differ.diff(schema("t", "c"), schema("t", "c")).isEmpty()).isTrue();
    }

    @Test
    void commentsCanBeLeftOut() {
        DiffSettings off = DiffSettings.defaults().withCommentType(null);
        assertThat(differ.diff(schema("old", null), schema("new", null), off).isEmpty()).isTrue();
    }

    @Test
    void emitsCommentOnStatements() {
        MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());
        List<String> sql = emitter.emit(
                ChangePlanner.create().plan(differ.diff(schema(null, null), schema("it's", "key"))),
                new PostgreSqlDialect());
        assertThat(sql).containsExactly(
                "COMMENT ON TABLE \"sales\".\"customers\" IS 'it''s'",
                "COMMENT ON COLUMN \"sales\".\"customers\".\"id\" IS 'key'");
    }

    private static Schema schema(String tableComment, String columnComment) {
        Schema s = R.createSchema();
        s.setName("sales");
        Table t = R.createTable();
        t.setName("customers");
        s.getOwnedElement().add(t);
        SQLSimpleType integer = R.createSQLSimpleType();
        integer.setName("INTEGER");
        integer.setTypeNumber(Types.INTEGER);
        s.getOwnedElement().add(integer);
        Column id = R.createColumn();
        id.setName("id");
        id.setType(integer);
        t.getFeature().add(id);
        comment(s, t, tableComment);
        comment(s, id, columnComment);
        return s;
    }

    private static void comment(Schema owner, ModelElement element, String body) {
        if (body == null) {
            return;
        }
        Description d = BusinessinformationFactory.eINSTANCE.createDescription();
        d.setType(DdlSettings.COMMENT_TYPE_JDBC_REMARKS);
        d.setBody(body);
        d.getModelElement().add(element);
        owner.getOwnedElement().add(d);
    }
}
