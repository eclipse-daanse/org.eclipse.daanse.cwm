/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinColumn;

/**
 * Creates {@link Join}s: from a catalog foreign key (the name is the key's name) or, when no
 * constraint exists, from explicit column pairs (the name is the joined column names).
 */
public final class Joins {

    private Joins() {
    }

    /** A join following {@code foreignKey}; its columns are the key's columns. */
    public static Join of(ForeignKey foreignKey) {
        Join j = CWMORMFactory.eINSTANCE.createJoin();
        j.setName(foreignKey.getName());
        j.setCwmForeignKey(foreignKey);
        return j;
    }

    /** A join over one column pair without constraint. */
    public static Join column(Column column, Column referenced) {
        return columns(List.of(column), List.of(referenced));
    }

    /** A join over column pairs without constraint; both lists are parallel. */
    public static Join columns(List<Column> columns, List<Column> referenced) {
        if (columns.size() != referenced.size() || columns.isEmpty()) {
            throw new IllegalArgumentException("join needs parallel, non-empty column lists");
        }
        Join j = CWMORMFactory.eINSTANCE.createJoin();
        j.setName(columns.stream().map(Column::getName).collect(Collectors.joining("_")));
        for (int i = 0; i < columns.size(); i++) {
            JoinColumn jc = CWMORMFactory.eINSTANCE.createJoinColumn();
            jc.setName(columns.get(i).getName());
            jc.setCwmColumn(columns.get(i));
            jc.setCwmReferencedColumn(referenced.get(i));
            j.getJoinColumn().add(jc);
        }
        return j;
    }

    /** Marks the join columns as neither inserted nor updated; returns {@code join}. */
    public static Join readOnly(Join join) {
        join.setInsertable(false);
        join.setUpdatable(false);
        return join;
    }
}
