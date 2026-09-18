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

import java.util.EnumSet;
import java.util.List;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** Table and column comments in {@code createSchema}, offline. */
class CreateSchemaCommentTest {

    private final PostgreSqlDialect pg = new PostgreSqlDialect();

    @Test
    void commentsFollowTheirTableBeforeAnyConstraint() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        SqlGenFixture.comment(f.schema, f.customers, "all customers");
        SqlGenFixture.comment(f.schema, f.customers.getFeature().stream().filter(x -> "NAME".equals(x.getName())).findFirst().orElseThrow(), "it's the name");

        List<String> ddl = new DdlGeneratorImpl(pg).createSchema(f.schema);

        int table = indexOf(ddl, "COMMENT ON TABLE \"sales\".\"CUSTOMERS\" IS 'all customers'");
        int column = indexOf(ddl, "COMMENT ON COLUMN \"sales\".\"CUSTOMERS\".\"NAME\" IS 'it''s the name'");
        assertThat(table).isGreaterThan(indexOf(ddl, "CREATE TABLE"));
        assertThat(column).isGreaterThan(table);
        assertThat(column).isLessThan(indexOf(ddl, "ALTER TABLE"));
    }

    @Test
    void commentsCanBeLeftOut() {
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        SqlGenFixture.comment(f.schema, f.customers, "all customers");

        assertThat(new DdlGeneratorImpl(pg).createSchema(f.schema, EnumSet.complementOf(EnumSet.of(Feature.COMMENT))))
                .noneMatch(s -> s.startsWith("COMMENT"));
        assertThat(new DdlGeneratorImpl(pg, DdlSettings.defaults().withCommentType(null)).createSchema(f.schema))
                .noneMatch(s -> s.startsWith("COMMENT"));
    }

    private static int indexOf(List<String> ddl, String prefix) {
        for (int i = 0; i < ddl.size(); i++) {
            if (ddl.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("no statement starts with " + prefix + " in " + ddl);
    }
}
