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
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;

/**
 * One statement of a script and the model element it is written for: the
 * schema of {@code CREATE SCHEMA}, the table of {@code CREATE TABLE} (with what
 * the dialect declares in it), the description of a comment, the key, index,
 * view or trigger of its statements, the party of {@code CREATE ROLE}, the
 * privilege of a {@code GRANT}. A reader that shows the statements of one
 * element takes them by this element, not by searching the text.
 *
 * @param element the element the statement is written for
 * @param sql     the statement, without a delimiter
 */
public record DdlStatement(ModelElement element, String sql) {

    public DdlStatement {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(sql, "sql");
    }

    /** The SQL of {@code statements}, in their order. */
    public static List<String> sql(List<DdlStatement> statements) {
        return statements.stream().map(DdlStatement::sql).toList();
    }

    @Override
    public String toString() {
        return sql;
    }
}
