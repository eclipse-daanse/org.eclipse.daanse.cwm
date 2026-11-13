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

/**
 * The DDL statements of one object for one SQL dialect.
 *
 * @param dialect    the dialect's display name
 * @param statements the statements, in the order they have to run
 */
public record DialectDdl(String dialect, List<String> statements) {
}
