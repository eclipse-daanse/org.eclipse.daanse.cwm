/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

class InverseReferencesTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    // Dependency.supplier ist eine MOF-Reference ohne eOpposite und wird daher vom
    // ECrossReferenceAdapter indiziert - genau der Fall, den diese Klasse bedient.
    private static final org.eclipse.emf.ecore.EReference DEP_SUPPLIER =
            CorePackage.Literals.DEPENDENCY__SUPPLIER;

    private static Dependency dependOn(DataType t) {
        Dependency d = CF.createDependency();
        d.setName("uses");
        d.getSupplier().add(t);
        return d;
    }

    @Test
    void withInstalledIndex_isIndexedAndResolves() {
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        Resource r = new ResourceImpl(URI.createURI("test:/a"));
        set.getResources().add(r);
        DataType t = CF.createDataType();
        t.setName("EMP");
        r.getContents().add(t);
        Dependency d = dependOn(t);
        r.getContents().add(d);

        assertThat(InverseReferences.isIndexed(t)).isTrue();
        assertThat(InverseReferences.referencingList(t, DEP_SUPPLIER, Dependency.class)).containsExactly(d);
    }

    @Test
    void withoutIndex_fallsBackButStillResolves() {
        // No install() — resource present, so the scan fallback (and one-time warning) applies.
        ResourceSet set = new ResourceSetImpl();
        Resource r = new ResourceImpl(URI.createURI("test:/b"));
        set.getResources().add(r);
        DataType t = CF.createDataType();
        t.setName("EMP");
        r.getContents().add(t);
        Dependency d = dependOn(t);
        r.getContents().add(d);

        assertThat(InverseReferences.isIndexed(t)).isFalse();
        // The result is identical; only the cost differs.
        assertThat(InverseReferences.referencingList(t, DEP_SUPPLIER, Dependency.class)).containsExactly(d);
    }

    @Test
    void bareInMemoryTree_resolvesViaContainmentRoot() {
        // No resource at all — legitimate throw-away use; anchors share a containment root.
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg = CF.createPackage();
        DataType t = CF.createDataType();
        t.setName("EMP");
        pkg.getOwnedElement().add(t);
        Dependency d = dependOn(t);
        pkg.getOwnedElement().add(d);

        assertThat(InverseReferences.isIndexed(t)).isFalse();
        assertThat(InverseReferences.referencingList(t, DEP_SUPPLIER, Dependency.class)).containsExactly(d);
    }

    @Test
    void referencingByName_matchesDynamicStyleLookup() {
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        Resource r = new ResourceImpl(URI.createURI("test:/c"));
        set.getResources().add(r);
        DataType t = CF.createDataType();
        t.setName("EMP");
        r.getContents().add(t);
        Dependency d = dependOn(t);
        r.getContents().add(d);

        assertThat(InverseReferences.referencingByName(t, "supplier", "Dependency").toList())
                .containsExactly(d);
        assertThat(InverseReferences.referencingByName(t, "supplier", "Nonexistent").toList())
                .isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(InverseReferences.referencing(null, DEP_SUPPLIER, Dependency.class)).isEmpty();
        assertThat(InverseReferences.referencingByName(null, "supplier", "Dependency")).isEmpty();
        assertThat(InverseReferences.isIndexed(null)).isFalse();
    }
}
