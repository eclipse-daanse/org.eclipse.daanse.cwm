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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;

/**
 * The database comment of a table or column changed, appeared with a new
 * table or column, or was removed.
 *
 * @param table      the owning table as declared in the new schema
 * @param element    {@code table} itself or one of its columns (new side)
 * @param oldComment the previous comment, or {@code null} if there was none
 * @param newComment the comment to set, or {@code null} to remove it
 */
public record CommentChange(Table table, ModelElement element, String oldComment, String newComment) {
}
