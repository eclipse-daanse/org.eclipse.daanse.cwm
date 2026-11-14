/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.examples.jdbc.roundtrip;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.eclipse.daanse.cwm.examples.jdbc.roundtrip.CwmRoundTrip.Result;
import org.eclipse.daanse.cwm.examples.jdbc.roundtrip.CwmRoundTrip.Target;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.ExportSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The first run of {@link CwmRoundTrip#main} against a PostgreSQL in a container, set up as the
 * README starts one: everything of the shop goes in and comes back without a difference, twice, the
 * second run dropping what the first created.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresRoundTripTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void everythingGoesInAndComesBackTheSame(@TempDir Path out) throws Exception {
        Target postgres = new Target("postgresql", POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ExportSettings settings = ExportSettings.defaults().withDropFirst(true);
        ByteArrayOutputStream log = new ByteArrayOutputStream();

        Result first = CwmRoundTrip.roundTrip(ShopModel.build(), postgres, Selection.all(), settings, out,
                new PrintStream(log, true, UTF_8));
        Result again = CwmRoundTrip.roundTrip(ShopModel.build(), postgres, Selection.all(), settings, out,
                new PrintStream(log, true, UTF_8));

        assertThat(first.diff().lines()).isEmpty();
        assertThat(again.diff().lines()).isEmpty();
        assertThat(first.exported().selected().leftOut()).isEmpty();
        assertThat(first.exported().selected().retained()).isEmpty();
        assertThat(again.exported().skipped()).as("the second time everything is there to drop").isEmpty();
        assertThat(log.toString(UTF_8)).contains("5. no differences").doesNotContain("differences:");
        assertThat(out.resolve("shop-postgresql.sql")).isRegularFile();
        assertThat(out.resolve("shop-postgresql-drop.sql")).isRegularFile();
        assertThat(out.resolve("written.xmi")).isRegularFile();
        assertThat(out.resolve("read.xmi")).isRegularFile();

        Catalog read = first.readBack();
        assertThat(Catalogs.schemas(read)).as("not public, which PostgreSQL keeps for itself")
                .extracting(Schema::getName).containsExactlyInAnyOrder("sales", "stock");
        Schema sales = Catalogs.findSchema(read, "sales").orElseThrow();
        assertThat(Tables.foreignKeys(Schemas.findTable(sales, "customer").orElseThrow()))
                .as("the documentary key, as everything is written").extracting(fk -> fk.getName())
                .containsExactly("fk_customer_referred_by");
        assertThat(Namespaces.ownedElements(sales, Trigger.class)).extracting(t -> t.getName())
                .containsExactly("trg_orders_ordered_on");
        assertThat(Namespaces.ownedElements(sales, Procedure.class)).as("not the function of the trigger")
                .singleElement().satisfies(f -> {
                    assertThat(f.getName()).isEqualTo("orders_of");
                    assertThat(f.getBody().getLanguage()).isEqualTo("plpgsql");
                });

        ResponsibleParty writer = Namespaces.findOwnedByName(read, ResponsibleParty.class, "shop_writer")
                .orElseThrow();
        assertThat(writer.getOwnedElement()).filteredOn(TablePrivilege.class::isInstance)
                .map(TablePrivilege.class::cast).filteredOn(p -> p.getAction() == TablePrivilegeAction.UPDATE)
                .singleElement().satisfies(p -> assertThat(p.getColumn()).extracting(c -> c.getName())
                        .containsExactly("qty"));
        ResponsibleParty clerk = Namespaces.findOwnedByName(read, ResponsibleParty.class, "shop_clerk")
                .orElseThrow();
        assertThat(PrivilegeStereotypes.isDatabaseUser(clerk)).isTrue();
        assertThat(clerk.getOwnedElement()).filteredOn(RolePrivilege.class::isInstance)
                .map(RolePrivilege.class::cast).extracting(m -> m.getRole().getName())
                .containsExactly("shop_writer");
        assertThat(Namespaces.ownedElements(read, ResponsibleParty.class)).as("not the login user")
                .extracting(p -> p.getName()).containsExactlyInAnyOrder("shop_reader", "shop_writer", "shop_clerk");
    }
}
