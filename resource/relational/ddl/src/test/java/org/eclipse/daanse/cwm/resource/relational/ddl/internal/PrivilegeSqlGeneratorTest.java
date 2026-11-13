/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * Offline rendering of the privilege model: the same static model produces
 * symmetric GRANT and REVOKE statements; dialect deviations (MSSQL memberships
 * as ALTER ROLE, Oracle EXECUTE without FUNCTION keyword) come from the
 * dialect's grant rendering. The grantor is never rendered.
 */
class PrivilegeSqlGeneratorTest {

    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;
    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;

    private final ResponsibleParty reader = party("reader");
    private final ResponsibleParty parent = party("parent");
    private final ResponsibleParty admin = party("admin");
    private final Table employee;
    private final Procedure fnAnswer;
    private final TablePrivilege select;
    private final ProcedurePrivilege execute;
    private final RolePrivilege membership;

    PrivilegeSqlGeneratorTest() {
        Schema schema = RF.createSchema();
        schema.setName("hr");
        employee = RF.createTable();
        employee.setName("employee");
        schema.getOwnedElement().add(employee);
        fnAnswer = RF.createProcedure();
        fnAnswer.setName("fn_answer");
        fnAnswer.setType(ProcedureType.FUNCTION);
        schema.getOwnedElement().add(fnAnswer);

        select = PF.createTablePrivilege();
        select.setAction(TablePrivilegeAction.SELECT);
        select.setTable(employee);
        select.setGrantor(admin); // darf im SQL nie auftauchen
        reader.getOwnedElement().add(select);

        execute = PF.createProcedurePrivilege();
        execute.setProcedure(fnAnswer);
        reader.getOwnedElement().add(execute);

        membership = PF.createRolePrivilege();
        membership.setRole(parent);
        membership.setGrantable(Boolean.TRUE);
        reader.getOwnedElement().add(membership);
    }

    @Test
    void postgresRendersGrantAndRevokeSymmetrically() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect());

        List<String> grants = DdlStatement.sql(generator.grantStatements(List.of(select, execute, membership)));
        assertThat(grants).containsExactly(
                "GRANT EXECUTE ON FUNCTION \"hr\".\"fn_answer\" TO \"reader\"",
                "GRANT \"parent\" TO \"reader\" WITH ADMIN OPTION",
                "GRANT SELECT ON \"hr\".\"employee\" TO \"reader\"");
        assertThat(grants).allSatisfy(g -> assertThat(g).doesNotContain("admin"));

        List<String> revokes = DdlStatement.sql(generator.revokeStatements(List.of(select, execute, membership)));
        assertThat(revokes).containsExactly(
                "REVOKE SELECT ON \"hr\".\"employee\" FROM \"reader\"",
                "REVOKE \"parent\" FROM \"reader\"",
                "REVOKE EXECUTE ON FUNCTION \"hr\".\"fn_answer\" FROM \"reader\"");

        assertThat(DdlStatement.sql(generator.createRoleStatements(List.of(parent, reader)))).containsExactly(
                "CREATE ROLE \"parent\"", "CREATE ROLE \"reader\"");
        assertThat(DdlStatement.sql(generator.dropRoleStatements(List.of(parent, reader)))).containsExactly(
                "DROP ROLE \"reader\"", "DROP ROLE \"parent\"");
    }

    @Test
    void withoutSchemaNamesTablesAndRoutinesAreNamedBare() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect(),
                DdlSettings.defaults().withIncludeSchema(false));

        assertThat(generator.grantStatement(select)).isEqualTo("GRANT SELECT ON \"employee\" TO \"reader\"");
        assertThat(generator.grantStatement(execute))
                .isEqualTo("GRANT EXECUTE ON FUNCTION \"fn_answer\" TO \"reader\"");
    }

    @Test
    void eachStatementNamesItsPrivilegeOrParty() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect());

        assertThat(generator.grantStatements(List.of(select, execute))).extracting(DdlStatement::element)
                .containsExactly(execute, select);
        assertThat(generator.createRoleStatements(List.of(reader))).extracting(DdlStatement::element)
                .containsExactly(reader);
    }

    @Test
    void columnRestrictionRendersAsColumnList() {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column email = RF.createColumn();
        email.setName("email");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column name = RF.createColumn();
        name.setName("name");
        employee.getFeature().add(email);
        employee.getFeature().add(name);
        TablePrivilege updateColumns = PF.createTablePrivilege();
        updateColumns.setAction(TablePrivilegeAction.UPDATE);
        updateColumns.setTable(employee);
        updateColumns.getColumn().add(name);
        updateColumns.getColumn().add(email);
        reader.getOwnedElement().add(updateColumns);

        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect());
        // Spalten sortiert; leer = ganze Tabelle.
        assertThat(generator.grantStatement(updateColumns))
                .isEqualTo("GRANT UPDATE (\"email\", \"name\") ON \"hr\".\"employee\" TO \"reader\"");
        assertThat(generator.revokeStatement(updateColumns))
                .isEqualTo("REVOKE UPDATE (\"email\", \"name\") ON \"hr\".\"employee\" FROM \"reader\"");
    }

    @Test
    void grantableRendersWithGrantOption() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect());
        select.setGrantable(Boolean.TRUE);
        assertThat(generator.grantStatement(select)).endsWith(" WITH GRANT OPTION");
        select.setGrantable(Boolean.FALSE);
        assertThat(generator.grantStatement(select)).doesNotContain("WITH GRANT OPTION");
    }

    @Test
    void mssqlRendersMembershipsAsAlterRole() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new MicrosoftSqlServerDialect());

        String grant = generator.grantStatement(membership);
        assertThat(grant).startsWith("ALTER ROLE ").contains(" ADD MEMBER ");
        // Kein ADMIN OPTION in SQL Server — das Flag wird ignoriert.
        assertThat(grant).doesNotContain("ADMIN OPTION");
        assertThat(generator.revokeStatement(membership)).startsWith("ALTER ROLE ").contains(" DROP MEMBER ");
        // EXECUTE ohne FUNCTION/PROCEDURE-Keyword.
        assertThat(generator.grantStatement(execute)).startsWith("GRANT EXECUTE ON ")
                .doesNotContain("FUNCTION").doesNotContain("PROCEDURE");
    }

    @Test
    void oracleRendersExecuteWithoutRoutineKeyword() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new OracleDialect());
        assertThat(generator.grantStatement(execute)).startsWith("GRANT EXECUTE ON ")
                .doesNotContain("FUNCTION").doesNotContain("PROCEDURE").endsWith(" TO \"reader\"");
        assertThat(generator.revokeStatement(execute)).startsWith("REVOKE EXECUTE ON ")
                .doesNotContain("FUNCTION");
    }

    /**
     * A user is created without credentials where the dialect has users, PostgreSQL's a role that may
     * log in, and dropped again; a dialect without users, DuckDB's, writes nothing for it.
     */
    @Test
    void usersAreCreatedWhereTheDialectHasUsers() {
        ResponsibleParty clerk = party("clerk");
        PrivilegeSqlGenerator postgres = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect(),
                DdlSettings.defaults().withIfNotExists(false));

        assertThat(DdlStatement.sql(postgres.createUserStatements(List.of(clerk))))
                .containsExactly("CREATE USER \"clerk\"");
        assertThat(DdlStatement.sql(postgres.dropUserStatements(List.of(clerk))))
                .containsExactly("DROP USER \"clerk\"");
        assertThat(postgres.createUserStatements(List.of(clerk))).extracting(DdlStatement::element)
                .containsExactly(clerk);
        PrivilegeSqlGenerator duckdb = new PrivilegeSqlGeneratorImpl(new DuckDbDialect());
        assertThat(duckdb.createUserStatements(List.of(clerk))).isEmpty();
        assertThat(duckdb.dropUserStatements(List.of(clerk))).isEmpty();
    }

    @Test
    void overloadsOfARoutineAreGrantedByTheirParameterTypes() {
        Procedure byNumber = overloadOfAnswer("integer");
        Procedure byText = overloadOfAnswer("text");
        // the specific name a database gave the routine is its own and no part of the statement
        TaggedValues.set(byText, RelationalNames.SPECIFIC_NAME_TAG, "fn_overload_16384");
        ProcedurePrivilege onNumber = PF.createProcedurePrivilege();
        onNumber.setProcedure(byNumber);
        reader.getOwnedElement().add(onNumber);
        ProcedurePrivilege onText = PF.createProcedurePrivilege();
        onText.setProcedure(byText);
        reader.getOwnedElement().add(onText);
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect());

        assertThat(generator.grantStatement(onNumber))
                .isEqualTo("GRANT EXECUTE ON FUNCTION \"hr\".\"fn_overload\"(integer) TO \"reader\"");
        assertThat(generator.grantStatement(onText))
                .isEqualTo("GRANT EXECUTE ON FUNCTION \"hr\".\"fn_overload\"(text) TO \"reader\"");
        assertThat(generator.revokeStatement(onText))
                .isEqualTo("REVOKE EXECUTE ON FUNCTION \"hr\".\"fn_overload\"(text) FROM \"reader\"");
        assertThat(generator.grantStatement(execute)).as("a routine without overloads keeps its bare name")
                .isEqualTo("GRANT EXECUTE ON FUNCTION \"hr\".\"fn_answer\" TO \"reader\"");
    }

    /** A function {@code hr.fn_overload} with one parameter of the type {@code typeName}, returning text. */
    private Procedure overloadOfAnswer(String typeName) {
        Procedure function = RF.createProcedure();
        function.setName("fn_overload");
        function.setType(ProcedureType.FUNCTION);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType type = RF.createSQLSimpleType();
        type.setName(typeName);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter in = RF.createSQLParameter();
        in.setName("value");
        in.setKind(org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind.PDK_IN);
        in.setType(type);
        function.getParameter().add(in);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType text = RF.createSQLSimpleType();
        text.setName("text");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter result = RF.createSQLParameter();
        result.setKind(org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind.PDK_RETURN);
        result.setType(text);
        function.getParameter().add(result);
        employee.getNamespace().getOwnedElement().add(function);
        return function;
    }

    @Test
    void privilegeWithoutGranteeIsRejected() {
        PrivilegeSqlGenerator generator = new PrivilegeSqlGeneratorImpl(new PostgreSqlDialect());
        Privilege stray = PF.createTablePrivilege();
        ((TablePrivilege) stray).setAction(TablePrivilegeAction.SELECT);
        ((TablePrivilege) stray).setTable(employee);
        assertThatThrownBy(() -> generator.grantStatement(stray))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grantee");
    }

    private static ResponsibleParty party(String name) {
        ResponsibleParty party = BF.createResponsibleParty();
        party.setName(name);
        party.setResponsibility("database role");
        return party;
    }
}
