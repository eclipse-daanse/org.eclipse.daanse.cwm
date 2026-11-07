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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Classifiers;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;

public final class PrimaryKeys {

    private PrimaryKeys() {
    }

    /**
     * A PrimaryKey over {@code columns} (in key order), owned by
     * {@code parent} via {@code ownedElement}. Creation rule: see
     * {@code objectmodel.core.util.Models}.
     */
    public static PrimaryKey create(Table parent, String name, Column... columns) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        PrimaryKey pk = RelationalFactory.eINSTANCE.createPrimaryKey();
        pk.setName(name);
        pk.getFeature().addAll(Arrays.asList(columns));
        parent.getOwnedElement().add(pk);
        return pk;
    }

    /** Columns that make up the primary key, in declared order. */
    public static List<Column> columns(PrimaryKey pk) {
        return columnStream(pk).toList();
    }

    /** Stream of columns of the primary key. */
    public static Stream<Column> columnStream(PrimaryKey pk) {
        return Classifiers.featureStream(pk == null ? null : pk.getFeature(), Column.class);
    }
}
