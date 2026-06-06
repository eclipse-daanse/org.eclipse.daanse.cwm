/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.cwm.data.sink.jdbc;

/**
 * Thrown when writing records to the database fails.
 */
public class JdbcSinkException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JdbcSinkException(String message, Throwable cause) {
        super(message, cause);
    }

    public JdbcSinkException(String message) {
        super(message);
    }
}
