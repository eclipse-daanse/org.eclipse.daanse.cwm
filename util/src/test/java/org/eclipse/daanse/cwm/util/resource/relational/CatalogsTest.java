/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.util.resource.relational;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.junit.jupiter.api.Test;

class CatalogsTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void schemas_returnsOnlySchemas() {
        Catalog cat = RF.createCatalog();
        Schema a = RF.createSchema();
        a.setName("A");
        Schema b = RF.createSchema();
        b.setName("B");
        cat.getOwnedElement().add(a);
        cat.getOwnedElement().add(b);
        assertThat(Catalogs.schemas(cat)).extracting(Schema::getName).containsExactly("A", "B");
    }

    @Test
    void findSchema_byName() {
        Catalog cat = RF.createCatalog();
        Schema s = RF.createSchema();
        s.setName("PUBLIC");
        cat.getOwnedElement().add(s);
        assertThat(Catalogs.findSchema(cat, "PUBLIC")).isPresent();
        assertThat(Catalogs.findSchema(cat, "MISSING")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Catalogs.schemas(null)).isEmpty();
        assertThat(Catalogs.findSchema(null, "X")).isEmpty();
        assertThat(Catalogs.findSchema(RF.createCatalog(), null)).isEmpty();
    }
}
