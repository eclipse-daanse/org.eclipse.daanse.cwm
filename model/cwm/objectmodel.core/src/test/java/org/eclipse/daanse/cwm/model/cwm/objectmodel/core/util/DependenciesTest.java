/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

class DependenciesTest {

    @Test
    void create_wiresClientAndSupplier_andNavigatesBothSides() {
        Model root = Models.create("m");
        Package task = Packages.create(root, "task");
        Package connection = Packages.create(root, "connection");
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        Resource resource = new ResourceImpl(URI.createURI("test:/deps"));
        set.getResources().add(resource);
        resource.getContents().add(root);

        Dependency reads = Dependencies.create(root, "task_reads_connection", "reads", task, connection);
        Dependencies.create(root, "task_writes_connection", "writes", task, connection);

        assertThat(reads.getKind()).isEqualTo("reads");
        assertThat(reads.getClient()).containsExactly(task);
        assertThat(reads.getSupplier()).containsExactly(connection);
        assertThat(root.getOwnedElement()).contains(reads);
        assertThat(Dependencies.ofClient(task, "reads")).containsExactly(reads);
        assertThat(Dependencies.ofClient(task, null)).hasSize(2);
        assertThat(Dependencies.ofSupplier(connection, "reads")).containsExactly(reads);
    }

    @Test
    void expressions_carryLanguageAndBody() {
        assertThat(Expressions.procedure("SQL", "SELECT 1").getBody()).isEqualTo("SELECT 1");
        assertThat(Expressions.bool("OCL", "true").getLanguage()).isEqualTo("OCL");
        assertThat(Expressions.expression(null, "x").getLanguage()).isNull();
    }
}
