/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;

/**
 * A table merge detected by the differ: {@code newTable} claims two or more
 * old tables ({@code oldTables}) as predecessors, none of those old tables
 * claimed by any other new table. Detected only from predecessor Dependency
 * links — {@link DiffSettings#useDependencyLinks()} must be on.
 *
 * @param oldTables the tables consolidated (n &ge; 2)
 * @param newTable  the table they were consolidated into
 */
public record TableMerge(List<Table> oldTables, Table newTable) {

    public TableMerge {
        oldTables = List.copyOf(oldTables);
    }
}
