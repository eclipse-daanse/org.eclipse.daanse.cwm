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
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;

/**
 * Serialises a CWM relational {@link Schema} to an ordered list of dialect-
 * specific DDL statements. An instance pairs a dialect with immutable
 * {@link DdlSettings}, so it is reusable and thread-safe. Obtain one from a
 * {@link DdlGeneratorFactory}.
 */
public interface DdlGenerator {

    /** The settings this generator was created with. */
    DdlSettings settings();

    /** Serialise all features of {@code schema}. */
    List<String> createSchema(Schema schema);

    /** Serialise {@code schema}, emitting only the entities in {@code features}. */
    List<String> createSchema(Schema schema, Set<Feature> features);

    /** Reverse of {@link #createSchema(Schema)} — ordered {@code DROP}s. */
    List<String> dropSchema(Schema schema);

    /** Reverse of {@link #createSchema(Schema, Set)} for the given {@code features}. */
    List<String> dropSchema(Schema schema, Set<Feature> features);

    /** {@code CREATE TABLE} for a single table (unqualified). */
    String createTable(Table table);

    /** {@code CREATE TABLE} for a table qualified by {@code schema}. */
    String createTable(Schema schema, Table table);

    /** The dialect {@link TableReference} for a CWM table/view. */
    TableReference tableReference(NamedColumnSet table);

    /** The dialect column definitions for a CWM table/view. */
    List<ColumnDefinition> columnDefinitions(NamedColumnSet table);
}
