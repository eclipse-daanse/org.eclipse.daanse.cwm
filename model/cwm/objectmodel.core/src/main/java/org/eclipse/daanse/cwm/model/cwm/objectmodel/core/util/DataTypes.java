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

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;

/**
 * Creates plain {@code core::DataType}s — the type of attributes outside the
 * relational world, e.g. the Java types of a {@code typemapping::TypeSystem}
 * or the field types of a {@code record::RecordFile}. Creation rule: see
 * {@link Models}.
 */
public final class DataTypes {

    private DataTypes() {
    }

    /** A DataType owned by {@code parent} via {@code ownedElement}. */
    public static DataType create(Namespace parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        DataType type = CoreFactory.eINSTANCE.createDataType();
        type.setName(name);
        parent.getOwnedElement().add(type);
        return type;
    }

    /** The DataType named {@code name} owned by {@code parent}, created on first use. */
    public static DataType ownedIn(Namespace parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        return Namespaces.findOwnedByName(parent, DataType.class, name)
                .orElseGet(() -> create(parent, name));
    }
}
