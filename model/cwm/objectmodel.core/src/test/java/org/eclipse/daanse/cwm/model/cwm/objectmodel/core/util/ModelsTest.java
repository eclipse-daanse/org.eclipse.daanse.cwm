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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.junit.jupiter.api.Test;

class ModelsTest {

    @Test
    void create_root_and_nested() {
        Model root = Models.create("Warehouse");
        Model sub = Models.create(root, "Handbuch");
        Package pkg = Packages.create(sub, "Deployment");

        assertThat(root.getName()).isEqualTo("Warehouse");
        assertThat(root.eContainer()).isNull();
        assertThat(root.getOwnedElement()).containsExactly(sub);
        assertThat(sub.getNamespace()).isSameAs(root);
        assertThat(sub.getOwnedElement()).containsExactly(pkg);
        assertThat(pkg.getName()).isEqualTo("Deployment");
    }

    @Test
    void create_requiresParentAndName() {
        assertThatNullPointerException().isThrownBy(() -> Models.create(null, "x"));
        assertThatNullPointerException().isThrownBy(() -> Models.create((String) null));
    }

    @Test
    void dataTypes_ownedIn_isIdempotent() {
        Model root = Models.create("m");
        DataType a = DataTypes.ownedIn(root, "java.lang.String");
        DataType b = DataTypes.ownedIn(root, "java.lang.String");
        DataType c = DataTypes.create(root, "java.lang.Long");

        assertThat(b).isSameAs(a);
        assertThat(root.getOwnedElement()).containsExactly(a, c);
    }
}
