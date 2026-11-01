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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every known package is on the class path and registers; a model loads under any file extension.
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

    @Test
    void aModelLoadsUnderAnyFileExtension(@TempDir Path dir) throws Exception {
        Catalog catalog = F.createCatalog();
        catalog.setName("shop");
        Schema schema = F.createSchema();
        schema.setName("sales");
        catalog.getOwnedElement().add(schema);
        Path file = dir.resolve("shop.cwm");
        Resource written = CwmPackages.newResourceSet().createResource(URI.createFileURI(file.toString()));
        written.getContents().add(catalog);
        written.save(Map.of());

        CwmPackages.registerKnown();
        Resource loaded = CwmPackages.load(CwmPackages.newResourceSet(), file);

        assertThat(loaded.getErrors()).isEmpty();
        Catalog again = (Catalog) loaded.getContents().get(0);
        assertThat(again.getName()).isEqualTo("shop");
        assertThat(again.getOwnedElement()).singleElement().isInstanceOf(Schema.class);
    }
}
