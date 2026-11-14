/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiffer;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.Tolerance;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport.Exported;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.mysql.MySqlDatabaseProvider;
import org.junit.jupiter.api.Test;

/**
 * MySQL in a container: a schema is a database, every DDL statement commits as it runs, and neither
 * {@code DROP CONSTRAINT} nor {@code DROP INDEX} has {@code IF EXISTS}, so a statement of the drop
 * script that names what the database does not hold fails where it runs. The roles are left out:
 * MySQL reports privileges in part.
 */
class CwmJdbcExportMySqlTest {

    private static final ActiveDatabase DATABASE = new MySqlDatabaseProvider().activate("cwm_export");

    /**
     * The model gained a check without a name, a unique constraint, an index and a foreign key since
     * it was last exported. With {@code dropFirst} it goes in again: the
     * statements that would drop them are skipped instead of failing, and what comes back is the
     * model; once more, and the database holds everything to drop.
     */
    @Test
    void whatTheModelGainedSinceTheLastExportIsNotDropped() throws Exception {
        try (Connection c = DATABASE.dataSource().getConnection()) {
            Selection rules = CwmJdbcExport.selection(List.of("!kind:ResponsibleParty"));
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);
            Catalog model = ShopModels.shop("my_", false, "`");
            Exported first = CwmJdbcExport.write(c, model, rules, settings);
            assertThat(first.dialect()).isEqualTo("mysql");

            List<String> gained = ShopModels.gain(model, "my_", false, "`");
            Exported again = CwmJdbcExport.write(c, model, rules, settings);

            assertThat(again.skipped()).extracting(s -> RelationalNames.of(s.element()))
                    .containsExactlyInAnyOrderElementsOf(gained);
            assertThat(CwmJdbcExportTest.sql(again.skipped())).as("MySQL has no IF EXISTS for them")
                    .noneMatch(s -> s.contains("IF EXISTS"));
            assertThat(CatalogDiffer.create().diff(again.selected().model(), CwmJdbcImport.load(c, again.readBack()),
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("mysql"))).lines()).isEmpty();
            assertThat(CwmJdbcExport.write(c, model, rules, settings).skipped()).as("now they are there").isEmpty();
        }
    }
}
