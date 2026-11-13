/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import java.util.List;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;

/**
 * The DDL of a model for one SQL dialect: the statements that create what it
 * holds, and those that drop it again, the exact reverse.
 *
 * @param dialect the dialect's display name
 * @param create  the {@code CREATE} statements, in the order they have to run
 * @param drop    the {@code DROP} statements, in the order they have to run
 */
public record DdlScript(String dialect, List<DdlStatement> create, List<DdlStatement> drop) {

    public DdlScript {
        create = List.copyOf(create);
        drop = List.copyOf(drop);
    }

    /** The SQL of {@link #create()}. */
    public List<String> createSql() {
        return DdlStatement.sql(create);
    }

    /** The SQL of {@link #drop()}. */
    public List<String> dropSql() {
        return DdlStatement.sql(drop);
    }
}
