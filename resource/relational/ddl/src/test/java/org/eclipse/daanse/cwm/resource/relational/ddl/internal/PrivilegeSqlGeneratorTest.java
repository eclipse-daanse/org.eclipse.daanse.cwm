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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
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

        List<String> grants = generator.grantStatements(List.of(select, execute, membership));
        assertThat(grants).containsExactly(
                "GRANT EXECUTE ON FUNCTION \"hr\".\"fn_answer\" TO \"reader\"",
                "GRANT \"parent\" TO \"reader\" WITH ADMIN OPTION",
                "GRANT SELECT ON \"hr\".\"employee\" TO \"reader\"");
        assertThat(grants).allSatisfy(g -> assertThat(g).doesNotContain("admin"));

        List<String> revokes = generator.revokeStatements(List.of(select, execute, membership));
        assertThat(revokes).containsExactly(
                "REVOKE SELECT ON \"hr\".\"employee\" FROM \"reader\"",
                "REVOKE \"parent\" FROM \"reader\"",
                "REVOKE EXECUTE ON FUNCTION \"hr\".\"fn_answer\" FROM \"reader\"");

        assertThat(generator.createRoleStatements(List.of(parent, reader))).containsExactly(
                "CREATE ROLE \"parent\"", "CREATE ROLE \"reader\"");
        assertThat(generator.dropRoleStatements(List.of(parent, reader))).containsExactly(
                "DROP ROLE \"reader\"", "DROP ROLE \"parent\"");
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
