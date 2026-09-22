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
 * A table split detected by the differ: {@code oldTable} is claimed as
 * predecessor by two or more new tables ({@code newTables}), each of those
 * new tables claiming no other predecessor. Detected only from predecessor
 * Dependency links — {@link DiffSettings#useDependencyLinks()} must be on.
 *
 * @param oldTable  the table as it existed in the old schema
 * @param newTables the tables it was decomposed into (n &ge; 2)
 */
public record TableSplit(Table oldTable, List<Table> newTables) {

    public TableSplit {
        newTables = List.copyOf(newTables);
    }
}
