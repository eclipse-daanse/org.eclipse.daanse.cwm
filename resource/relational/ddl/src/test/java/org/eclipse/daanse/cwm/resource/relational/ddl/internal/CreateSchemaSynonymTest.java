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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.SynonymFactory;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** Synonyms in {@code createSchema} / {@code dropSchema}. */
class CreateSchemaSynonymTest {

    private final OracleDialect oracle = new OracleDialect();

    @Test
    void synonymsComeLastAndChainsAfterTheirTarget() {
        SqlGenFixture f = SqlGenFixture.build("HR", oracle);
        Synonym cust = synonym(f.schema, "CUST", f.customers);
        Synonym clients = synonym(f.schema, "CLIENTS", cust);
        // Declared before its target synonym.
        f.schema.getOwnedElement().move(0, clients);

        List<String> ddl = new DdlGeneratorImpl(oracle).createSchema(f.schema);

        int cust1 = indexOf(ddl, "CREATE SYNONYM \"HR\".\"CUST\" FOR \"HR\".\"CUSTOMERS\"");
        int clientsAt = indexOf(ddl, "CREATE SYNONYM \"HR\".\"CLIENTS\" FOR \"HR\".\"CUST\"");
        assertThat(cust1).isLessThan(clientsAt);
        assertThat(cust1).isGreaterThan(indexOf(ddl, "CREATE VIEW"));
        assertThat(clientsAt).isEqualTo(ddl.size() - 1);
    }

    @Test
    void resolvedTargetWinsOverRawName() {
        SqlGenFixture f = SqlGenFixture.build("HR", oracle);
        Synonym cust = synonym(f.schema, "CUST", f.customers);
        cust.setTargetName("OLD_NAME");

        assertThat(new DdlGeneratorImpl(oracle).createSchema(f.schema, EnumSet.of(Feature.SYNONYM)))
                .containsExactly("CREATE SYNONYM \"HR\".\"CUST\" FOR \"HR\".\"CUSTOMERS\"");
    }

    @Test
    void unresolvedTargetUsesRawFields() {
        SqlGenFixture f = SqlGenFixture.build("HR", oracle);
        Synonym remote = synonym(f.schema, "EMP_REMOTE", null);
        remote.setTargetSchemaName("HQ");
        remote.setTargetName("EMPLOYEES");
        remote.setDbLink("HQ_LINK");

        assertThat(new DdlGeneratorImpl(oracle).createSchema(f.schema, EnumSet.of(Feature.SYNONYM)))
                .containsExactly("CREATE SYNONYM \"HR\".\"EMP_REMOTE\" FOR \"HQ\".\"EMPLOYEES\"@HQ_LINK");
    }

    @Test
    void publicSynonymOnOracle() {
        SqlGenFixture f = SqlGenFixture.build("HR", oracle);
        Schema pub = schema(f, "PUBLIC");
        synonym(pub, "CUSTOMERS", f.customers).setIsPublic(true);

        DdlGeneratorImpl gen = new DdlGeneratorImpl(oracle);
        assertThat(gen.createSchema(pub, EnumSet.of(Feature.SYNONYM)))
                .containsExactly("CREATE PUBLIC SYNONYM \"CUSTOMERS\" FOR \"HR\".\"CUSTOMERS\"");
        assertThat(gen.dropSchema(pub, EnumSet.of(Feature.SYNONYM)))
                .containsExactly("DROP PUBLIC SYNONYM \"CUSTOMERS\"");
    }

    @Test
    void sqlServerTargetInOtherDatabase() {
        MicrosoftSqlServerDialect mssql = new MicrosoftSqlServerDialect();
        SqlGenFixture f = SqlGenFixture.build("dbo", mssql);
        Synonym orders = synonym(f.schema, "ord", null);
        orders.setTargetCatalogName("otherdb");
        orders.setTargetSchemaName("sales");
        orders.setTargetName("orders");

        assertThat(new DdlGeneratorImpl(mssql).createSchema(f.schema, EnumSet.of(Feature.SYNONYM)))
                .containsExactly("CREATE SYNONYM \"dbo\".\"ord\" FOR \"otherdb\".\"sales\".\"orders\"");
    }

    @Test
    void inexpressibleSynonymIsSkipped() {
        H2Dialect h2 = new H2Dialect();
        SqlGenFixture f = SqlGenFixture.build("HR", h2);
        synonym(f.schema, "CUST", f.customers);
        Synonym remote = synonym(f.schema, "EMP_REMOTE", null);
        remote.setTargetName("EMPLOYEES");
        remote.setDbLink("HQ_LINK");

        assertThat(new DdlGeneratorImpl(h2).createSchema(f.schema, EnumSet.of(Feature.SYNONYM)))
                .containsExactly("CREATE SYNONYM \"HR\".\"CUST\" FOR \"HR\".\"CUSTOMERS\"");
    }

    @Test
    void dialectWithoutSynonymsEmitsNothing() {
        PostgreSqlDialect pg = new PostgreSqlDialect();
        SqlGenFixture f = SqlGenFixture.build("sales", pg);
        synonym(f.schema, "cust", f.customers);

        DdlGeneratorImpl gen = new DdlGeneratorImpl(pg);
        assertThat(gen.createSchema(f.schema)).noneMatch(s -> s.contains("SYNONYM"));
        assertThat(gen.dropSchema(f.schema)).noneMatch(s -> s.contains("SYNONYM"));
    }

    @Test
    void synonymFeatureCanBeLeftOut() {
        SqlGenFixture f = SqlGenFixture.build("HR", oracle);
        synonym(f.schema, "CUST", f.customers);

        DdlGeneratorImpl gen = new DdlGeneratorImpl(oracle);
        EnumSet<Feature> withoutSynonyms = EnumSet.complementOf(EnumSet.of(Feature.SYNONYM));
        assertThat(gen.createSchema(f.schema, withoutSynonyms)).noneMatch(s -> s.contains("SYNONYM"));
        assertThat(gen.dropSchema(f.schema, withoutSynonyms)).noneMatch(s -> s.contains("SYNONYM"));
    }

    @Test
    void dropSchemaDropsSynonymsFirst() {
        H2Dialect h2 = new H2Dialect();
        SqlGenFixture f = SqlGenFixture.build("HR", h2);
        synonym(f.schema, "CUST", f.customers);

        List<String> ddl = new DdlGeneratorImpl(h2).dropSchema(f.schema);

        assertThat(ddl.get(0)).isEqualTo("DROP SYNONYM IF EXISTS \"HR\".\"CUST\"");
    }

    @Test
    void generatedSchemaRunsOnH2() throws Exception {
        H2Dialect h2 = new H2Dialect();
        SqlGenFixture f = SqlGenFixture.build("HR", h2);
        synonym(f.schema, "CUST", f.customers);
        DdlGeneratorImpl gen = new DdlGeneratorImpl(h2);

        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:cwmSynonymDdl", "sa", "");
                Statement s = c.createStatement()) {
            for (String sql : gen.createSchema(f.schema, EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)))) {
                s.execute(sql);
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM \"HR\".\"CUST\"")) {
                assertThat(rs.next()).isTrue();
            }
            for (String sql : gen.dropSchema(f.schema, EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)))) {
                s.execute(sql);
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SYNONYMS")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private static Schema schema(SqlGenFixture f, String name) {
        Schema s = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createSchema();
        s.setName(name);
        if (f.schema.getNamespace() != null) {
            f.schema.getNamespace().getOwnedElement().add(s);
        }
        return s;
    }

    private static Synonym synonym(Schema schema, String name, ModelElement target) {
        Synonym s = SynonymFactory.eINSTANCE.createSynonym();
        s.setName(name);
        s.setTarget(target);
        if (target != null) {
            s.setTargetSchemaName(((Schema) target.getNamespace()).getName());
            s.setTargetName(target.getName());
        }
        schema.getOwnedElement().add(s);
        return s;
    }

    private static int indexOf(List<String> ddl, String statement) {
        for (int i = 0; i < ddl.size(); i++) {
            if (ddl.get(i).startsWith(statement)) {
                return i;
            }
        }
        throw new AssertionError("no statement starts with " + statement + " in " + ddl);
    }
}
