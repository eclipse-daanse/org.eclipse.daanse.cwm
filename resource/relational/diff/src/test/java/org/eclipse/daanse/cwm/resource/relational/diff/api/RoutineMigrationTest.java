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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * A routine goes into a migration as the create script writes it: one that comes is created, one
 * that goes is dropped by its signature, one whose body changes is replaced where the dialect writes
 * {@code OR REPLACE} and dropped and created where not, and one whose parameter is renamed is
 * dropped and created, as {@code OR REPLACE} keeps the names.
 */
class RoutineMigrationTest {

    private static final Dialect PG = new PostgreSqlDialect();

    private final SchemaDiffer differ = new SchemaDifferImpl();
    private final MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());

    @Test
    void aRoutineThatComesIsCreatedAndOneThatGoesIsDropped() {
        Schema without = TestModels.sales();
        Schema with = TestModels.sales();
        function(with, "amount", "SELECT amount * 2");

        assertThat(emitter.emit(plan(without, with), PG)).containsExactly("CREATE FUNCTION \"sales\".\"fn_total\""
                + "(\"amount\" INTEGER) RETURNS INTEGER LANGUAGE sql AS $$SELECT amount * 2$$");
        assertThat(emitter.emit(plan(with, without), PG))
                .containsExactly("DROP FUNCTION IF EXISTS \"sales\".\"fn_total\"(INTEGER)");
    }

    @Test
    void aNewBodyIsReplacedWhereTheDialectWritesOrReplace() {
        Schema before = TestModels.sales();
        function(before, "amount", "SELECT amount * 2");
        Schema after = TestModels.sales();
        function(after, "amount", "SELECT amount * 3");

        List<ChangeOp> ops = plan(before, after);

        assertThat(ops).singleElement().isInstanceOf(ChangeOp.ReplaceRoutine.class);
        assertThat(emitter.emit(ops, PG)).singleElement().asString()
                .startsWith("CREATE OR REPLACE FUNCTION \"sales\".\"fn_total\"").contains("amount * 3");
        assertThat(emitter.emit(ops, new MySqlDialect())).satisfiesExactly(
                s -> assertThat(s).startsWith("DROP FUNCTION"), s -> assertThat(s).startsWith("CREATE FUNCTION"));
    }

    @Test
    void aRenamedParameterIsDroppedAndCreated() {
        Schema before = TestModels.sales();
        function(before, "amount", "SELECT amount * 2");
        Schema after = TestModels.sales();
        function(after, "qty", "SELECT qty * 2");

        assertThat(plan(before, after)).extracting(op -> op.getClass().getSimpleName())
                .containsExactly("DropRoutine", "CreateRoutine");
    }

    private List<ChangeOp> plan(Schema oldSchema, Schema newSchema) {
        return ChangePlanner.create().plan(differ.diff(oldSchema, newSchema));
    }

    /** {@code fn_total(<parameter> INTEGER) RETURNS INTEGER} in SQL with {@code body}. */
    private static Procedure function(Schema schema, String parameter, String body) {
        RelationalFactory r = RelationalFactory.eINSTANCE;
        Procedure function = r.createProcedure();
        function.setName("fn_total");
        function.setType(ProcedureType.FUNCTION);
        SQLParameter in = r.createSQLParameter();
        in.setName(parameter);
        in.setKind(ParameterDirectionKind.PDK_IN);
        in.setType(TestModels.type(schema, "INTEGER"));
        function.getParameter().add(in);
        SQLParameter result = r.createSQLParameter();
        result.setName("returnValue");
        result.setKind(ParameterDirectionKind.PDK_RETURN);
        result.setType(TestModels.type(schema, "INTEGER"));
        function.getParameter().add(result);
        ProcedureExpression expression = CoreFactory.eINSTANCE.createProcedureExpression();
        expression.setLanguage("SQL");
        expression.setBody(body);
        function.setBody(expression);
        schema.getOwnedElement().add(function);
        return function;
    }
}
