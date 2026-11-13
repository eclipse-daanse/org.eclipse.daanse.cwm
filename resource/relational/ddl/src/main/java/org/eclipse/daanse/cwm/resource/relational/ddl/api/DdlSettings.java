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

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;

/**
 * How a {@link DdlGenerator} writes what the model holds. What it writes is the
 * model's: to leave something out, cut the model to a selection first. Never
 * changes; start from {@link #defaults()} and derive with the {@code with}
 * methods.
 *
 * @param includeSchema {@code true}: tables, views, indexes, triggers and the
 *                      targets of grants are written {@code schema.name};
 *                      {@code false}: bare, for the schema the connection
 *                      uses. A script then covers one schema; a table of
 *                      another schema it refers to is still qualified. The
 *                      catalog is never written.
 * @param createSchema  whether {@code CREATE SCHEMA} (and in the drop script
 *                      {@code DROP SCHEMA}) is written for the named schemas;
 *                      {@code false} where the schema exists already, such as
 *                      an Oracle user. Only with {@code includeSchema}: bare
 *                      names do not go into the schema.
 * @param ifNotExists   {@code CREATE … IF NOT EXISTS} for schemas, tables and
 *                      indexes, and {@code DROP … IF EXISTS} for everything
 *                      dropped, where the dialect has the clause
 * @param cascade       {@code CASCADE} on dropped tables and schemas, where the
 *                      dialect has it
 * @param commentType   the {@code type} of the descriptions written as table
 *                      and column comments; the type the reading side gives
 *                      the comments of a database. Leave comments out with
 *                      the selection ({@code !is:comment}).
 * @param strict        what to do with what the dialect cannot write as the
 *                      model holds it, such as a materialized view where the
 *                      database has none, or a descending index column where
 *                      it keeps every column ascending. {@code false}: it is
 *                      written plainer or left out, as the generator says for
 *                      each such case, and the log names every case.
 *                      {@code true}: nothing is written; the generation fails
 *                      with an {@link UnwritableException} that names every
 *                      case of the run ({@link Unwritable}).
 */
public record DdlSettings(boolean includeSchema, boolean createSchema, boolean ifNotExists, boolean cascade,
        String commentType, boolean strict) {

    public DdlSettings {
        Objects.requireNonNull(commentType, "commentType");
        if (commentType.isBlank()) {
            throw new IllegalArgumentException("commentType must not be blank; leave comments out with !is:comment");
        }
    }

    /**
     * Qualified names, {@code CREATE SCHEMA}, {@code IF [NOT] EXISTS}, no
     * {@code CASCADE}, the comments as {@link Descriptions#JDBC_REMARKS}, and
     * what the dialect cannot write written plainer or left out, not strict.
     */
    public static DdlSettings defaults() {
        return new DdlSettings(true, true, true, false, Descriptions.JDBC_REMARKS, false);
    }

    public DdlSettings withIncludeSchema(boolean value) {
        return new DdlSettings(value, createSchema, ifNotExists, cascade, commentType, strict);
    }

    public DdlSettings withCreateSchema(boolean value) {
        return new DdlSettings(includeSchema, value, ifNotExists, cascade, commentType, strict);
    }

    public DdlSettings withIfNotExists(boolean value) {
        return new DdlSettings(includeSchema, createSchema, value, cascade, commentType, strict);
    }

    public DdlSettings withCascade(boolean value) {
        return new DdlSettings(includeSchema, createSchema, ifNotExists, value, commentType, strict);
    }

    public DdlSettings withCommentType(String type) {
        return new DdlSettings(includeSchema, createSchema, ifNotExists, cascade, type, strict);
    }

    public DdlSettings withStrict(boolean value) {
        return new DdlSettings(includeSchema, createSchema, ifNotExists, cascade, commentType, value);
    }
}
