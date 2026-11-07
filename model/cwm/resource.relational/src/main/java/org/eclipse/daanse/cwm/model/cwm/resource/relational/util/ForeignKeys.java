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
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Classifiers;

public final class ForeignKeys {

    private ForeignKeys() {
    }

    /**
     * A ForeignKey over {@code columns} referencing {@code target} (a
     * PrimaryKey or UniqueConstraint), owned by {@code parent} via
     * {@code ownedElement}. Sets the single-valued
     * {@code KeyRelationship.uniqueKey}; EMF maintains the opposite
     * {@code UniqueKey.keyRelationship}. Creation rule: see
     * {@code objectmodel.core.util.Models}.
     */
    public static ForeignKey create(Table parent, String name, UniqueKey target, Column... columns) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(target, "target");
        ForeignKey fk = RelationalFactory.eINSTANCE.createForeignKey();
        fk.setName(name);
        fk.getFeature().addAll(Arrays.asList(columns));
        fk.setUniqueKey(target);
        parent.getOwnedElement().add(fk);
        return fk;
    }

    /** Columns participating in the foreign key, in declared order. */
    public static List<Column> columns(ForeignKey fk) {
        return columnStream(fk).toList();
    }

    /** Stream of FK columns. */
    public static Stream<Column> columnStream(ForeignKey fk) {
        return Classifiers.featureStream(fk == null ? null : fk.getFeature(), Column.class);
    }

    /** Referenced unique key (PK or UK on the parent table). */
    public static Optional<UniqueKey> targetUniqueKey(ForeignKey fk) {
        return fk == null ? Optional.empty() : Optional.ofNullable(fk.getUniqueKey());
    }

    /** Parent table of the referenced unique key, via a target column's owner. */
    public static Optional<Table> targetTable(ForeignKey fk) {
        return targetUniqueKey(fk).flatMap(uk -> Classifiers.featureStream(uk.getFeature(), Column.class).findFirst())
                .flatMap(Columns::tableOwner);
    }
}
