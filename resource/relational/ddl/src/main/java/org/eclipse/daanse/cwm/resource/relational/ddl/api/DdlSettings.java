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
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

/**
 * Instance configuration for a {@link DdlGenerator}. Immutable; use
 * {@link #defaults()} plus the {@code with*} methods to derive a variant.
 *
 * <ul>
 * <li>{@code includeSchema} — qualify table references as {@code schema.table}
 * ({@code true}, default) or emit bare {@code table} names ({@code false}, e.g.
 * for connection-scoped databases). The catalog is never emitted.</li>
 * <li>{@code ifNotExists} — emit {@code CREATE ... IF NOT EXISTS} /
 * {@code DROP ... IF EXISTS} where the dialect supports it.</li>
 * <li>{@code cascade} — append {@code CASCADE} to table/schema drops (ignored by
 * dialects that don't support it).</li>
 * <li>{@code commentType} — the {@code Description} type that holds the
 * database comment of tables and columns (default
 * {@link #COMMENT_TYPE_JDBC_REMARKS}); {@code null} writes no comments.</li>
 * </ul>
 */
public record DdlSettings(boolean includeSchema, boolean ifNotExists, boolean cascade, String commentType) {

    /** The Description type the JDBC loader writes table and column {@code REMARKS} into. */
    public static final String COMMENT_TYPE_JDBC_REMARKS = "JDBC-REMARKS";

    public static DdlSettings defaults() {
        return new DdlSettings(true, true, false, COMMENT_TYPE_JDBC_REMARKS);
    }

    public DdlSettings withIncludeSchema(boolean value) {
        return new DdlSettings(value, ifNotExists, cascade, commentType);
    }

    public DdlSettings withIfNotExists(boolean value) {
        return new DdlSettings(includeSchema, value, cascade, commentType);
    }

    public DdlSettings withCascade(boolean value) {
        return new DdlSettings(includeSchema, ifNotExists, value, commentType);
    }

    public DdlSettings withCommentType(String type) {
        return new DdlSettings(includeSchema, ifNotExists, cascade, type);
    }
}
