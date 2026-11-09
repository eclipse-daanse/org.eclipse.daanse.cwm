/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.attribute;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.cls;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.dataType;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.enumeration;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.model;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.pkg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Writing: one file per root package, named or not, relative cross references, the optional
 * genmodel. The model: {@code model} holds the packages {@code types} (the data type
 * {@code Integer}) and {@code shop} (the class {@code Customer}), which holds the package
 * {@code enumerations} (the enumeration {@code Status}).
 */
class EcoreWriterTest {

    @Test
    void twoRootPackagesGiveTwoFilesWithRelativeReferencesAndAGenModel(@TempDir Path out) throws IOException {
        Result r = CwmToEcore.convert(List.of(shopModel()));
        assertThat(r.errors()).isEmpty();

        List<Path> files = EcoreWriter.save(r, out,
                new EcoreWriter.GenModelSettings("org.example", null, "/example/src/main/java", "example"));

        assertThat(files).extracting(p -> p.getFileName().toString())
                .containsExactly("types.ecore", "shop.ecore", "types.genmodel");
        assertThat(Files.readString(out.resolve("shop.ecore"))).contains("types.ecore#//Integer")
                .doesNotContain(out.toString());

        ResourceSet rs = CwmPackages.newResourceSet();
        GenModel gm = genModel(rs, files.get(2));
        assertThat(gm.getGenPackages()).extracting(gp -> gp.getEcorePackage().getName() + ":" + gp.getBasePackage())
                .containsExactly("types:org.example", "shop:org.example");
        assertThat(gm.getModelDirectory()).isEqualTo("/example/src/main/java");
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(rs)).isEmpty();
    }

    @Test
    void splitPackagesGoIntoNamedFilesThatReferToEachOther(@TempDir Path out) throws IOException {
        Result r = CwmToEcore.convert(List.of(shopModel()), Options.defaults().withSplitPackages(true));
        assertThat(r.errors()).isEmpty();

        List<Path> files = EcoreWriter.save(r.ePackages(), out,
                new EcoreWriter.GenModelSettings("org.example", null, "/example/src/main/java", "example"),
                Map.of("types", "shop-types", "model.shop", "shop-classes.ecore", "shop.enumerations", "shop-enums"));

        assertThat(files).extracting(p -> p.getFileName().toString()).containsExactly("shop-types.ecore",
                "shop-classes.ecore", "shop-enums.ecore", "shop-types.genmodel");
        assertThat(Files.readString(out.resolve("shop-classes.ecore")))
                .contains("shop-types.ecore#//Integer", "shop-enums.ecore#//Status").doesNotContain("eSubpackages");

        ResourceSet rs = CwmPackages.newResourceSet();
        GenModel gm = genModel(rs, files.get(3));
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(rs)).isEmpty();
        assertThat(gm.getGenPackages()).extracting(gp -> gp.getEcorePackage().getName() + ":" + gp.getBasePackage())
                .as("the enumerations keep the Java package they had inside shop")
                .containsExactly("types:org.example", "shop:org.example", "enumerations:org.example.shop");
        for (Path f : files.subList(0, 3)) {
            EPackage p = (EPackage) rs.getResource(URI.createFileURI(f.toString()), true).getContents().get(0);
            assertThat(Diagnostician.INSTANCE.validate(p).getSeverity()).as(f.toString()).isLessThan(Diagnostic.ERROR);
        }
    }

    @Test
    void theBasePackageOfTheConversionReachesTheGenModel(@TempDir Path out) throws IOException {
        Result r = CwmToEcore.convert(List.of(shopModel()),
                Options.defaults().withSplitPackages(true).withBasePackage("org.example"));

        List<Path> files = EcoreWriter.save(r, out, EcoreWriter.GenModelSettings.defaults());

        assertThat(genModel(CwmPackages.newResourceSet(), files.get(files.size() - 1)).getGenPackages())
                .extracting(gp -> gp.getEcorePackage().getName() + ":" + gp.getBasePackage())
                .containsExactly("types:org.example", "shop:org.example", "enumerations:org.example.shop");
    }

    @Test
    void aFileNameEntryNamesExactlyOnePackageAndEachFileOnlyOnce(@TempDir Path out) {
        Result r = CwmToEcore.convert(List.of(shopModel()), Options.defaults().withSplitPackages(true));

        assertThatThrownBy(() -> EcoreWriter.save(r.ePackages(), out, null, Map.of("orders", "x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'orders' names 0");
        assertThatThrownBy(() -> EcoreWriter.save(r.ePackages(), out, null, Map.of("types", "same", "shop", "same.ecore")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("two packages");
    }

    private static GenModel genModel(ResourceSet rs, Path file) {
        GenModelPackage.eINSTANCE.eClass();
        GenModel gm = (GenModel) CwmPackages.load(rs, file).getContents().get(0);
        EcoreUtil.resolveAll(rs);
        return gm;
    }

    private static Model shopModel() {
        Model model = model("model");
        DataType integer = dataType(pkg(model, "types"), "Integer");
        Package shop = pkg(model, "shop");
        Enumeration status = enumeration(pkg(shop, "enumerations"), "Status", "OPEN", "CLOSED");
        Class customer = cls(shop, "Customer");
        attribute(customer, "id", integer);
        attribute(customer, "status", status);
        return model;
    }
}
