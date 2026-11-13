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

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * Writes what CWM relational {@link Schema}s hold as ordered, dialect-specific
 * DDL statements: everything in them, nothing else. To write a part, cut the
 * model to a selection first. An instance pairs a dialect with immutable
 * {@link DdlSettings}, so it is reusable and thread-safe. Obtain one from a
 * {@link DdlGeneratorFactory}.
 *
 * <p>
 * One script covers all the schemas it is given, in one order: every
 * {@code CREATE SCHEMA}, every {@code CREATE TABLE}, the table and column
 * comments, the unique and check constraints, the indexes, the foreign keys,
 * the views, each after the views it reads, and the triggers. So a foreign key
 * or a view may refer into a schema that comes later in the list. Where the
 * dialect cannot add a constraint to an existing table, {@code CREATE TABLE}
 * declares it, and the tables come after the tables they refer to, across the
 * schemas. The drop script is the exact reverse. Keys, constraints, indexes
 * and triggers without a name get the name of {@code RelationalNames}, in the
 * create script as in the drop script.
 * </p>
 */
public interface DdlGenerator {

    /** The settings this generator was created with. */
    DdlSettings settings();

    /**
     * The {@code CREATE} script of {@code schemas}, each statement with the
     * element it is written for.
     *
     * @throws IllegalArgumentException with {@link DdlSettings#includeSchema()}
     *                                  off and more than one schema
     * @throws IllegalStateException    where the dialect declares foreign keys
     *                                  in {@code CREATE TABLE} and tables refer
     *                                  to each other in a cycle, or where views
     *                                  read each other in a cycle; the message
     *                                  names them
     * @throws UnwritableException      where the settings are strict
     *                                  ({@link DdlSettings#strict()}) and the
     *                                  schemas hold what the dialect cannot
     *                                  write as they have it, with every such
     *                                  case; not strict, it is written plainer
     *                                  or left out, and the log names each case
     */
    List<DdlStatement> create(List<? extends Schema> schemas);

    /**
     * The {@code DROP} script of {@code schemas}: the exact reverse of
     * {@link #create}, statement by statement.
     *
     * @throws IllegalArgumentException as {@link #create}
     * @throws IllegalStateException    as {@link #create}
     * @throws UnwritableException      as {@link #create}, with the same cases;
     *                                  not strict, the log does not name them
     *                                  again
     */
    List<DdlStatement> drop(List<? extends Schema> schemas);

    /** The SQL of {@link #create} for {@code schema} alone. */
    default List<String> createSchema(Schema schema) {
        return DdlStatement.sql(create(List.of(schema)));
    }

    /** The SQL of {@link #drop} for {@code schema} alone. */
    default List<String> dropSchema(Schema schema) {
        return DdlStatement.sql(drop(List.of(schema)));
    }

    /**
     * The statements that create {@code element} on its own, for a database that holds the rest of
     * its schema, as a migration that adds it needs them: the statements the create script writes for
     * it, with the same settings, names and decisions of the dialect.
     * <ul>
     * <li>a schema: {@code CREATE SCHEMA}, where the settings write one;</li>
     * <li>a table: {@code CREATE TABLE} with its primary key and the constraints the dialect declares
     * in it ({@link #inCreateTable});</li>
     * <li>a unique constraint, check or foreign key: {@code ALTER TABLE … ADD}; nothing where the
     * dialect declares it in {@code CREATE TABLE};</li>
     * <li>an index: {@code CREATE INDEX}, with {@code WHERE} for one that filters its rows; nothing
     * where the create script leaves it out, as for such an index where the dialect has no partial
     * indexes, or for any where it has no index DDL;</li>
     * <li>a view: {@code CREATE VIEW}, or {@code CREATE MATERIALIZED VIEW} for a materialized one
     * ({@link #materialized(View)}); a trigger: its procedure, where the dialect keeps the body in
     * one, and the trigger;</li>
     * <li>a description of the comment type: the comment on the table or column it describes.</li>
     * </ul>
     * A table of another schema the element refers to is qualified also without
     * {@link DdlSettings#includeSchema()}.
     *
     * @throws IllegalArgumentException for an element of another kind
     * @throws UnwritableException      as {@link #create}, for the element alone
     */
    List<DdlStatement> createElement(ModelElement element);

    /**
     * The statements that drop {@code element} again, as the drop script does: the reverse of
     * {@link #createElement}. Nothing for a comment, a primary key or a constraint the dialect declares
     * in {@code CREATE TABLE}: they go with their table.
     *
     * @throws IllegalArgumentException for an element of another kind
     * @throws UnwritableException      as {@link #drop}, for the element alone
     */
    List<DdlStatement> dropElement(ModelElement element);

    /**
     * Whether {@code view} is written as a materialized view ({@code CREATE MATERIALIZED VIEW}): the
     * model says it is one, and the dialect writes materialized views. Where the dialect writes none,
     * such a view is written as a plain view, a case the dialect cannot write
     * ({@link DdlSettings#strict()}).
     */
    boolean materialized(View view);

    /**
     * Whether the dialect declares {@code constraint} in {@code CREATE TABLE}, as it can neither add
     * it to an existing table nor drop it from one: a primary key always, a unique constraint, check or
     * foreign key where the dialect says so.
     */
    boolean inCreateTable(ModelElement constraint);

    /**
     * The statement that sets the comment of a table, or of a column of one, to {@code text};
     * {@code null} removes it. Empty where the dialect writes no comment on the element: a case it
     * cannot write, which the log names, where {@code text} is a comment.
     *
     * @throws IllegalArgumentException for an element that is no table or column
     * @throws UnwritableException      where the settings are strict and the dialect writes no comment
     *                                  on the element
     */
    Optional<String> comment(ModelElement tableOrColumn, String text);

    /**
     * The dialect {@link TableReference} for a CWM table or view, qualified as the settings say: by its
     * schema with {@link DdlSettings#includeSchema()}, bare without.
     */
    TableReference tableReference(NamedColumnSet table);

    /** The dialect column definitions for a CWM table or view. */
    List<ColumnDefinition> columnDefinitions(NamedColumnSet table);
}
