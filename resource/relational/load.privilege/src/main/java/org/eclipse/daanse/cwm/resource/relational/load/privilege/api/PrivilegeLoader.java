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
package org.eclipse.daanse.cwm.resource.relational.load.privilege.api;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;

/**
 * Builds the common core of database grants from a JDBC {@link MetaInfo}
 * snapshot: principals as {@code businessinformation::ResponsibleParty} owning
 * their table/routine/membership privileges ({@code ownedElement}), resolved
 * against the companion CWM {@link Catalog} and classified by
 * {@code core::Stereotype} markers ({@code databaseRole}/{@code databaseUser}).
 * Deliberately limited to what all databases share. Registered as an OSGi
 * service.
 */
public interface PrivilegeLoader {

    /**
     * Load the principals and privileges of {@code info}; privilege targets
     * resolve onto {@code cwmCatalog}.
     *
     * @param info       the JDBC snapshot, required
     * @param cwmCatalog the CWM catalog built from the same snapshot, required
     *                   (rows that do not resolve onto it are skipped)
     * @return parties plus their stereotype markers, all unattached — the
     *         caller places both via {@code ownedElement}
     */
    PrivilegeLoadResult load(MetaInfo info, Catalog cwmCatalog);
}
