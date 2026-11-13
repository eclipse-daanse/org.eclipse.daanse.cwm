/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Kind;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Written;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The script files of a model cut to a selection: what they hold, their names and their text. */
class CwmDdlScriptsTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    /** Catalog {@code SHOP} with schemas {@code SALES} (table {@code ORDERS}) and {@code STOCK}, in {@code shop.xmi}. */
    private static Resource model() {
        Catalog catalog = R.createCatalog();
        catalog.setName("SHOP");
        Schema sales = R.createSchema();
        sales.setName("SALES");
        SQLSimpleType integer = R.createSQLSimpleType();
        integer.setName("INTEGER");
        integer.setTypeNumber(Types.INTEGER);
        sales.getOwnedElement().add(integer);
        Table orders = R.createTable();
        orders.setName("ORDERS");
        Column id = R.createColumn();
        id.setName("ID");
        id.setType((Classifier) integer);
        orders.getFeature().add(id);
        sales.getOwnedElement().add(orders);
        Schema stock = R.createSchema();
        stock.setName("STOCK");
        catalog.getOwnedElement().add(sales);
        catalog.getOwnedElement().add(stock);
        Resource resource = new ResourceImpl(URI.createFileURI("/models/shop.xmi"));
        resource.getContents().add(catalog);
        return resource;
    }

    @Test
    void theSelectionChoosesTheSchemasAndAnIncludeRuleThatMatchesNothingFails(@TempDir Path dir) throws Exception {
        CwmDdlScripts scripts = new CwmDdlScripts(List.of("h2"), DdlSettings.defaults(), ";");

        Written<ModelElement> all = scripts.write(model(), Selection.all(), dir.resolve("all"), null, false);
        Written<ModelElement> stock = scripts.write(model(), CwmDdlScripts.selection(List.of("schema:SHOP.STOCK")),
                dir.resolve("stock"), null, false);

        assertThat(all.scripts()).singleElement().satisfies(s -> assertThat(s.sql())
                .anyMatch(sql -> sql.contains("\"SALES\".\"ORDERS\""))
                .anyMatch(sql -> sql.startsWith("CREATE SCHEMA") && sql.contains("\"STOCK\"")));
        assertThat(stock.scripts()).singleElement()
                .satisfies(s -> assertThat(s.sql()).noneMatch(sql -> sql.contains("SALES")));
        assertThat(stock.selected().leftOut()).extracting(LeftOut::path).contains("SHOP.SALES");
        assertThatThrownBy(() -> scripts.write(model(), CwmDdlScripts.selection(List.of("schema:DWH")), dir, null,
                false)).isInstanceOf(UnmatchedRulesException.class).hasMessageContaining("schema:DWH");
        assertThatThrownBy(() -> CwmDdlScripts.selection(List.of("feature:TABLE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("feature");
        assertThatThrownBy(() -> new CwmDdlScripts(List.of("db2"), null, ";"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("db2").hasMessageContaining("postgresql");
    }

    @Test
    void aModelWithoutSchemaIsRejected(@TempDir Path dir) {
        Resource empty = new ResourceImpl(URI.createFileURI("/models/empty.xmi"));
        empty.getContents().add(R.createCatalog());

        assertThatThrownBy(() -> new CwmDdlScripts(List.of("h2"), null, ";").write(empty, Selection.all(), dir, null,
                false)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty.xmi");
    }

    @Test
    void oneCreateAndOneDropScriptPerDialectNamedAfterTheModelFileThatRun(@TempDir Path dir) throws Exception {
        Selection sales = CwmDdlScripts.selection(List.of("schema:SALES"));
        CwmDdlScripts scripts = new CwmDdlScripts(List.of("H2", "pg"), DdlSettings.defaults(), ";");

        Written<ModelElement> written = scripts.write(model(), sales, dir, null, true);

        assertThat(written.scripts()).extracting(s -> s.file().getFileName().toString()).containsExactly("shop-h2.sql",
                "shop-postgresql.sql", "shop-h2-drop.sql", "shop-postgresql-drop.sql");
        assertThat(written.scripts()).extracting(CwmDdlScripts.Script::dialect).containsExactly("H2", "PostgreSQL",
                "H2", "PostgreSQL");
        assertThat(written.scripts(Kind.DROP)).extracting(CwmDdlScripts.Script::dialect).containsExactly("H2",
                "PostgreSQL");
        assertThat(scripts.write(model(), sales, dir.resolve("named"), " tables ", false).scripts())
                .extracting(s -> s.file().getFileName().toString())
                .containsExactly("tables-h2.sql", "tables-postgresql.sql");
        String create = Files.readString(dir.resolve("shop-h2.sql"));
        assertThat(create)
                .startsWith("-- CREATE script for H2: SHOP.SALES from shop.xmi\n-- selected by schema:SALES\n\n")
                .contains("CREATE TABLE");
        assertThat(statements(dir.resolve("shop-h2.sql"))).as("the file holds the statements handed back")
                .containsExactlyElementsOf(written.scripts().get(0).sql());
        assertThat(Files.readString(dir.resolve("shop-postgresql-drop.sql")))
                .startsWith("-- DROP script for PostgreSQL: SHOP.SALES from shop.xmi").contains("DROP TABLE");
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:scripts;DB_CLOSE_DELAY=-1");
                Statement s = c.createStatement()) {
            for (String statement : statements(dir.resolve("shop-h2.sql"))) {
                s.execute(statement);
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'SALES'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            for (String statement : statements(dir.resolve("shop-h2-drop.sql"))) {
                s.execute(statement);
            }
        }
    }

    @Test
    void theRolesAndTheirGrantsComeAfterTheTablesUnlessTheSelectionLeavesThemOut(@TempDir Path dir)
            throws Exception {
        Resource model = model();
        Catalog shop = (Catalog) model.getContents().get(0);
        Table orders = Schemas.tables(Catalogs.findSchema(shop, "SALES").orElseThrow()).get(0);
        Stereotype databaseRole = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
        shop.getOwnedElement().add(databaseRole);
        ResponsibleParty reader = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        reader.setName("reader");
        shop.getOwnedElement().add(reader);
        databaseRole.getExtendedElement().add(reader);
        TablePrivilege select = PrivilegeFactory.eINSTANCE.createTablePrivilege();
        select.setAction(TablePrivilegeAction.SELECT);
        select.setTable(orders);
        reader.getOwnedElement().add(select);
        CwmDdlScripts scripts = new CwmDdlScripts(List.of("postgresql"), DdlSettings.defaults(), ";");

        scripts.write(model, CwmDdlScripts.selection(List.of("schema:SALES", "is:role")), dir, "all", true);
        scripts.write(model, CwmDdlScripts.selection(List.of("schema:SALES")), dir, "structure", true);

        String create = Files.readString(dir.resolve("all-postgresql.sql"));
        assertThat(create).contains("CREATE ROLE \"reader\"")
                .contains("GRANT SELECT ON \"SALES\".\"ORDERS\" TO \"reader\"");
        assertThat(create.indexOf("CREATE ROLE")).as("after the tables").isGreaterThan(create.indexOf("CREATE TABLE"));
        String drop = Files.readString(dir.resolve("all-postgresql-drop.sql"));
        assertThat(drop.indexOf("REVOKE SELECT")).as("revoked first").isLessThan(drop.indexOf("DROP ROLE"));
        assertThat(drop.indexOf("DROP ROLE")).as("before the tables").isLessThan(drop.indexOf("DROP TABLE"));
        assertThat(Files.readString(dir.resolve("structure-postgresql.sql"))).as("an include rule on a schema"
                + " does not reach the parties").doesNotContain("ROLE").doesNotContain("GRANT");
    }

    /** The statements of a written script: separated by the delimiter and a blank line, comments dropped. */
    private static List<String> statements(Path script) throws Exception {
        return Stream.of(Files.readString(script).split(";\n\n")).map(String::strip)
                .map(s -> s.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + b + "\n").strip())
                .filter(s -> !s.isEmpty()).toList();
    }
}
