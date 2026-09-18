/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions.executeAll;

import java.sql.Connection;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.TriggerFixtures;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Setting, changing and removing table and column comments through the
 * migration, live on every {@link DialectProfile}: each migration must run.
 */
class DiffCommentParameterizedTest {

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void comments_set_change_and_remove_apply(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            Schema none = schema(profile, null, null);
            Schema first = schema(profile, "customers", "customer's name");
            Schema second = schema(profile, "all customers", null);
            MigrationEmitterImpl emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect).createSchema(none,
                        profile.nonTriggerFeatures()));

                List<ChangeOp> set = ChangePlanner.create().plan(new SchemaDifferImpl().diff(none, first));
                assertThat(set).hasSize(2).allMatch(ChangeOp.SetComment.class::isInstance);
                executeAll(c, emitter.emit(set, dialect));

                List<ChangeOp> change = ChangePlanner.create().plan(new SchemaDifferImpl().diff(first, second));
                assertThat(change).hasSize(2).allMatch(ChangeOp.SetComment.class::isInstance);
                executeAll(c, emitter.emit(change, dialect));
            } finally {
                profile.cleanup(c, none, dialect, profile.allFeatures());
            }
        }
    }

    private static Schema schema(DialectProfile profile, String tableComment, String columnComment) {
        Schema s = TriggerFixtures.build(profile.schemaName(), null, profile.triggerTiming(), profile.triggerEvent());
        Table customers = Schemas.tables(s).get(0);
        comment(s, customers, tableComment);
        comment(s, ColumnSets.columns(customers).get(1), columnComment);
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

    private static ActiveDatabase activateOrSkip(DialectProfile profile) {
        try {
            return profile.activate();
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "Database '" + profile + "' unavailable (no Docker?): " + e.getMessage());
            throw new AssertionError("unreachable");
        }
    }
}
