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
package org.eclipse.daanse.cwm.util.resource.relational;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.util.objectmodel.core.Classifiers;

public final class ForeignKeys {

    private ForeignKeys() {
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
