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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.api.dbcheck;

/**
 * Expected value at one cell of a {@link DatabaseQueryCheck} result. Compared
 * on the string form, case-sensitive; {@code null} {@link #expectedValue}
 * asserts SQL NULL.
 *
 * @param name          display name for reports
 * @param rowIndex      0-based row index
 * @param columnIndex   0-based column index
 * @param expectedValue expected value as a string, or {@code null} for SQL NULL
 */
public record DatabaseCellCheck(String name, int rowIndex, int columnIndex, String expectedValue) {
}
