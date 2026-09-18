/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.dml.render;

/** The statement templates a table can yield. */
public enum TemplateKind {
    /** {@code SELECT cols FROM t WHERE pk = ?} — absent if the table has no primary key. */
    SELECT_BY_PK,
    /** {@code SELECT cols FROM t}. */
    SELECT_ALL,
    /** {@code SELECT cols FROM t ORDER BY pk OFFSET ? FETCH ?} (dialect pagination). */
    SELECT_PAGE,
    /** {@code INSERT INTO t (cols) VALUES (?, …)}. */
    INSERT,
    /** {@code UPDATE t SET nonPk = ? … WHERE pk = ?} — absent without pk or for pk-only tables. */
    UPDATE_BY_PK,
    /** {@code DELETE FROM t WHERE pk = ?} — absent if the table has no primary key. */
    DELETE_BY_PK
}
