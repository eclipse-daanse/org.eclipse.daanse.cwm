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
/**
 * Comparing CWM relational models, and turning a difference into a migration.
 *
 * <h2>Comparing</h2>
 * <p>
 * A {@link SchemaDiffer} compares two schemas into a {@link SchemaDiff}, a {@link CatalogDiffer}
 * two catalogs into a {@link CatalogDiff}: the schemas only one of them holds, in both directions,
 * the {@code SchemaDiff} of every schema both hold, and the database principals (roles, users,
 * {@code PUBLIC}) with their stereotypes, privileges and memberships ({@link PartyDiff}). Both give
 * the differences as structure and as readable lines, {@code lines()}: one per difference, sorted,
 * {@code <kind> <qualified name>: <difference>} with the difference {@code only left},
 * {@code only right} or {@code <aspect> <left value> left, <right value> right}, for example
 * {@code column shop.sales.customer.name: size (100) left, (50) right}. Left is the first model (the
 * old one of a migration, the written one of a round trip), right the second.
 * </p>
 * <p>
 * Elements are matched by the name they have in a database: a key, constraint, index or trigger
 * without a name of its own by the name {@code RelationalNames} gives it. A column compares by type,
 * by size where the type takes one in DDL, by nullability and default; a foreign key by its columns,
 * the schema and table it refers to, the referenced columns and its rules; an index by its columns
 * with their order, its uniqueness and its filter; a routine is told apart by its name and the kinds
 * and types of its parameters, and compares by kind, parameter names and body.
 * </p>
 *
 * <h2>Tolerances</h2>
 * <p>
 * A comparison counts everything it looks at, unless {@link DiffSettings#tolerances()} lets a kind
 * of difference pass. Each {@link Tolerance} is applied where its kind of value is compared, for
 * every database alike: {@code TYPE_BY_JDBC_CODE}, {@code INTEGER_AS_NUMERIC},
 * {@code IGNORE_EXPRESSION_TEXT}, {@code IGNORE_CHARACTER_LENGTH}, {@code IGNORE_PRIMARY_KEY_NAMES},
 * {@code IGNORE_CONSTRAINT_NAMES}, {@code IGNORE_FK_RULES}, {@code IGNORE_GRANTOR},
 * {@code IGNORE_EXTRA_PARTIES}, {@code CASE_INSENSITIVE_NAMES}. To compare a model with what a
 * database made of it, take {@link Tolerance#roundTrip(String)} of that database:
 * </p>
 *
 * <pre>
 * CatalogDiff diff = CatalogDiffer.create().diff(written, readBack,
 *         DiffSettings.defaults().withTolerances(Tolerance.roundTrip("postgresql")));
 * diff.lines().forEach(System.out::println);
 * </pre>
 *
 * <h2>Migrating</h2>
 * <p>
 * A {@link ChangePlanner} orders a {@code SchemaDiff} into {@link ChangeOp}s, a
 * {@link MigrationEmitter} writes them as SQL of one dialect, with the {@code DdlSettings} of the DDL
 * generator and by its code for every element the generator writes.
 * </p>
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.cwm.resource.relational.diff.api;
