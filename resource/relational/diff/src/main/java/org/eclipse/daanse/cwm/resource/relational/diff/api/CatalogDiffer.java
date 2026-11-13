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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.CatalogDifferImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;

/**
 * Compares two catalogs into a {@link CatalogDiff}, in both directions: what only the first holds and
 * what only the second holds count alike. Schemas are matched by name and compared by the
 * {@link SchemaDiffer}; parties are matched by name and compared by their stereotypes, privileges
 * and memberships. OSGi service.
 *
 * <p>
 * The parties compared are the database principals: a {@code ResponsibleParty} anywhere in the
 * catalog that carries the {@code databaseRole} or {@code databaseUser} stereotype or holds a
 * privilege. A privilege is told apart from another by what it grants on what: a table privilege by
 * its action, its table (schema and name) and its columns, a routine privilege by the routine's
 * schema and signature, a membership by the role.
 * </p>
 */
public interface CatalogDiffer {

    /** {@link #diff(Catalog, Catalog, DiffSettings)} with {@link DiffSettings#defaults()}. */
    default CatalogDiff diff(Catalog left, Catalog right) {
        return diff(left, right, DiffSettings.defaults());
    }

    /**
     * How {@code right} differs from {@code left}, under the identity sources, the comment type and
     * the tolerances of {@code settings}. For a model and what a database made of it, pass
     * {@link Tolerance#roundTrip(String)} as tolerances.
     */
    CatalogDiff diff(Catalog left, Catalog right, DiffSettings settings);

    /** A differ for plain Java, as the service registry would wire it. */
    static CatalogDiffer create() {
        return new CatalogDifferImpl(new SchemaDifferImpl());
    }
}
