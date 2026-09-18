/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.daanse.sql.select.Join;
import org.eclipse.daanse.cwm.model.daanse.sql.select.SelectFactory;
import org.eclipse.daanse.cwm.model.daanse.sql.select.UnresolvedTableReference;
import org.eclipse.daanse.cwm.model.daanse.sql.util.SqlModelValidator;
import org.junit.jupiter.api.Test;

class SqlModelValidatorTest {

    private final SqlFactory sql = SqlFactory.eINSTANCE;
    private final SelectFactory sel = SelectFactory.eINSTANCE;

    @Test
    void crossJoinWithConditionIsRejected() {
        Join j = sel.createJoin();
        j.setJoinType(JoinTypeKind.CROSS);
        j.setLeft(unresolved("a"));
        j.setRight(unresolved("b"));
        BooleanLiteral cond = sql.createBooleanLiteral();
        cond.setValue(TruthValueKind.TRUE);
        j.setCondition(cond);

        assertThat(SqlModelValidator.validate(j))
                .anyMatch(s -> s.contains("CROSS"));
    }

    @Test
    void cleanInnerJoinHasNoIssues() {
        Join j = sel.createJoin();
        j.setJoinType(JoinTypeKind.INNER);
        j.setLeft(unresolved("a"));
        j.setRight(unresolved("b"));
        BooleanLiteral cond = sql.createBooleanLiteral();
        cond.setValue(TruthValueKind.TRUE);
        j.setCondition(cond);

        assertThat(SqlModelValidator.validate(j)).isEmpty();
    }

    @Test
    void columnReferenceWithBothBranchesIsRejected() {
        ColumnReference c = sql.createColumnReference();
        // weder column noch qualifiedName ⇒ Verstoß (genau eines erforderlich)
        assertThat(SqlModelValidator.validate(c)).anyMatch(s -> s.contains("ColumnReference"));
    }

    private UnresolvedTableReference unresolved(String name) {
        UnresolvedTableReference t = sel.createUnresolvedTableReference();
        QualifiedName q = sql.createQualifiedName();
        q.getParts().add(name);
        t.setQualifiedName(q);
        return t;
    }
}
