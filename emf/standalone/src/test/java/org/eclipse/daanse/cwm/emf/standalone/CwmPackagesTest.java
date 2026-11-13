/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.standalone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every known package is on the class path and registers; a model saved with readable ids loads
 * again in a fresh resource set.
 */
class CwmPackagesTest {

    private static final RelationalFactory F = RelationalFactory.eINSTANCE;

    @Test
    void everyKnownPackageIsOnTheClassPathAndRegisters() {
        List<EPackage> found = CwmPackages.registerKnown();

        assertThat(found).hasSize(CwmPackages.KNOWN.size());
        assertThat(found).allSatisfy(p -> assertThat(EPackage.Registry.INSTANCE.getEPackage(p.getNsURI())).isSameAs(p));
        assertThatThrownBy(() -> CwmPackages.register("no.such.Package", getClass().getClassLoader()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no.such.Package");
    }

    /** {@code shop.sales.orders} with the column {@code id}. */
    private static Catalog shop() {
        Catalog catalog = F.createCatalog();
        catalog.setName("shop");
        Schema schema = F.createSchema();
        schema.setName("sales");
        Table table = F.createTable();
        table.setName("orders");
        Column id = F.createColumn();
        id.setName("id");
        table.getFeature().add(id);
        schema.getOwnedElement().add(table);
        catalog.getOwnedElement().add(schema);
        return catalog;
    }

    @Test
    void savedModelHasReadableIdsAndLoadsAgain(@TempDir Path dir) throws Exception {
        Catalog catalog = shop();

        Path file = dir.resolve("out/shop.xmi");
        CwmPackages.save(file, List.of(catalog));

        assertThat(Files.readString(file)).contains("xmi:id=\"Column_shop.sales.orders.id\"")
                .contains("encoding=\"UTF-8\"");
        CwmPackages.registerKnown();
        Resource loaded = CwmPackages.load(CwmPackages.newResourceSet(), file);
        Catalog again = (Catalog) loaded.getContents().get(0);
        assertThat(again.getName()).isEqualTo("shop");
        assertThat(loaded.getEObject("Table_shop.sales.orders")).isInstanceOf(Table.class);
    }

    /**
     * Saving writes the model as it is and leaves it as it was: an element lives in one resource, so
     * putting the caller's roots into the resource that is written would take them out of wherever
     * they were, out of a loaded file as well as out of the file they were saved to before.
     */
    @Test
    void savingLeavesTheModelWhereItWas(@TempDir Path dir) throws Exception {
        Catalog catalog = shop();

        Resource first = CwmPackages.save(dir.resolve("first.xmi"), List.of(catalog));
        Resource second = CwmPackages.save(dir.resolve("second.xmi"), List.of(catalog));
        Resource loaded = CwmPackages.loadModel(dir.resolve("first.xmi"), getClass().getClassLoader(), null);
        Catalog again = (Catalog) loaded.getContents().get(0);
        CwmPackages.save(dir.resolve("copy.xmi"), List.of(again));

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(catalog.eResource()).as("the model stays out of every resource").isNull();
            soft.assertThat(first.getContents()).as("the second save leaves the first resource alone").hasSize(1)
                    .allMatch(Catalog.class::isInstance);
            soft.assertThat(second.getContents()).hasSize(1).doesNotContain(catalog);
            soft.assertThat(again.eResource()).as("a loaded model stays in its file").isSameAs(loaded);
            soft.assertThat(loaded.getEObject("Table_shop.sales.orders")).isInstanceOf(Table.class);
        });
        assertThat(Files.readString(dir.resolve("second.xmi"))).isEqualTo(Files.readString(dir.resolve("first.xmi")))
                .contains("xmi:id=\"Column_shop.sales.orders.id\"");
        assertThat(Files.readString(dir.resolve("copy.xmi"))).isEqualTo(Files.readString(dir.resolve("first.xmi")));
    }

    /**
     * What the saved roots refer to outside them is written as a reference into the file it lives in,
     * also over a reference with an opposite end; the element outside does not learn of the copy that
     * is written.
     */
    @Test
    void referencesOutOfTheSavedRootsAreWrittenAndLeaveTheOtherEndAlone(@TempDir Path dir) {
        Catalog keys = F.createCatalog();
        keys.setName("keys");
        Table customer = F.createTable();
        customer.setName("customer");
        Column id = F.createColumn();
        id.setName("id");
        customer.getFeature().add(id);
        PrimaryKey key = F.createPrimaryKey();
        key.setName("pk_customer");
        key.getFeature().add(id);
        customer.getOwnedElement().add(key);
        keys.getOwnedElement().add(customer);
        CwmPackages.save(dir.resolve("keys.xmi"), List.of(keys));
        PrimaryKey loadedKey = (PrimaryKey) CwmPackages.loadModel(dir.resolve("keys.xmi"), getClass().getClassLoader(),
                null).getEObject("PrimaryKey_keys.customer.pk_customer");

        Catalog shop = F.createCatalog();
        shop.setName("shop");
        Table orders = F.createTable();
        orders.setName("orders");
        Column customerId = F.createColumn();
        customerId.setName("customer_id");
        orders.getFeature().add(customerId);
        ForeignKey fk = F.createForeignKey();
        fk.setName("fk_orders_customer");
        fk.getFeature().add(customerId);
        fk.setUniqueKey(loadedKey);
        orders.getOwnedElement().add(fk);
        shop.getOwnedElement().add(orders);

        CwmPackages.save(dir.resolve("shop.xmi"), List.of(shop));

        assertThat(loadedKey.getKeyRelationship()).as("the key outside knows the model's foreign key only")
                .containsExactly(fk);
        Resource shopAgain = CwmPackages.loadModel(dir.resolve("shop.xmi"), getClass().getClassLoader(), null);
        ForeignKey written = (ForeignKey) shopAgain.getEObject("ForeignKey_shop.orders.fk_orders_customer");
        assertThat(written.getUniqueKey().eIsProxy()).isFalse();
        assertThat(written.getUniqueKey().getName()).isEqualTo("pk_customer");
        assertThat(written.getFeature()).extracting(f -> f.getName()).containsExactly("customer_id");
    }

    @Test
    void loadModelResolvesWhatOtherFilesHold(@TempDir Path dir) {
        Catalog types = F.createCatalog();
        types.setName("types");
        SQLSimpleType integerType = F.createSQLSimpleType();
        integerType.setName("INTEGER");
        types.getOwnedElement().add(integerType);
        CwmPackages.save(dir.resolve("types.xmi"), List.of(types));
        // a reference into another file is one to the element as that file holds it
        SQLSimpleType integer = (SQLSimpleType) CwmPackages
                .loadModel(dir.resolve("types.xmi"), getClass().getClassLoader(), null)
                .getEObject("SQLSimpleType_types.INTEGER");

        Catalog shop = F.createCatalog();
        shop.setName("shop");
        Table table = F.createTable();
        table.setName("orders");
        Column id = F.createColumn();
        id.setName("id");
        id.setType(integer);
        table.getFeature().add(id);
        shop.getOwnedElement().add(table);
        CwmPackages.save(dir.resolve("shop.xmi"), List.of(shop));

        Resource model = CwmPackages.loadModel(dir.resolve("shop.xmi"), getClass().getClassLoader(), null);

        Column loaded = (Column) model.getEObject("Column_shop.orders.id");
        assertThat(loaded.getType().eIsProxy()).isFalse();
        assertThat(loaded.getType().getName()).isEqualTo("INTEGER");
        assertThat(loaded.getType().eResource().getURI().lastSegment()).isEqualTo("types.xmi");
        assertThat(InverseReferences.isIndexed(loaded)).as("reverse lookups without a scan").isTrue();
        assertThatThrownBy(() -> CwmPackages.loadModel(dir.resolve("shop.xmi"), getClass().getClassLoader(),
                List.of("no.such.Package"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no.such.Package");
    }
}
