/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.privilege;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;

/** The parties a privilege load puts into the catalog, next to the schemas. */
final class CatalogParties {

    private CatalogParties() {
    }

    static List<ResponsibleParty> of(Catalog catalog) {
        return catalog.getOwnedElement().stream().filter(ResponsibleParty.class::isInstance)
                .map(ResponsibleParty.class::cast).toList();
    }

    static ResponsibleParty named(Catalog catalog, String name) {
        return of(catalog).stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }
}
