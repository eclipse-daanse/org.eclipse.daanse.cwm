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
package org.eclipse.daanse.cwm.model.daanse.dcat.util;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.daanse.dcat.Catalog;
import org.eclipse.daanse.cwm.model.daanse.dcat.CatalogedResource;
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Portal;

/**
 * Accessors around {@link Portal}, the place that provides catalogs. A portal owns its catalogs and
 * its one access service ({@code ownedElement}) and imports the catalogs it takes over from other
 * portals ({@code importedElement}): ownership is the home of a catalog, which resources a catalog
 * lists stays {@link Catalog#getResource()}. From a catalog to the portals that take it over reads
 * the opposite {@code importer} and needs no index; from a listed resource to its portals goes
 * through the hidden role inCatalog and needs
 * {@link InverseReferences#install(org.eclipse.emf.common.notify.Notifier)} on the ResourceSet —
 * without it the scan of the model answers.
 */
public final class Portals {

    private Portals() {
    }

    /** The portal that owns {@code catalog}, its home; empty for a catalog that lives elsewhere. */
    public static Optional<Portal> portalOf(Catalog catalog) {
        return catalog.getNamespace() instanceof Portal portal ? Optional.of(portal) : Optional.empty();
    }

    /**
     * The access service of {@code portal}: the data service it owns, whose endpoint is the portal's
     * catalog interface (rule OneAccessService: exactly one).
     */
    public static Optional<DataService> accessService(Portal portal) {
        return Namespaces.ownedElementStream(portal, DataService.class).findFirst();
    }

    /** The catalogs {@code portal} shows: those it owns, in {@code ownedElement} order, then those it takes over. */
    public static List<Catalog> catalogs(Portal portal) {
        return Packages.available(portal, Catalog.class);
    }

    /** The catalogs {@code portal} owns, without those it takes over. */
    public static List<Catalog> ownCatalogs(Portal portal) {
        return Namespaces.ownedElements(portal, Catalog.class);
    }

    /** The catalogs {@code portal} takes over from other portals: the catalogs among its {@code importedElement}. */
    public static List<Catalog> takenOverCatalogs(Portal portal) {
        return portal.getImportedElement().stream()
                .filter(Catalog.class::isInstance)
                .map(Catalog.class::cast)
                .toList();
    }

    /** The portals that show {@code catalog}: the portal that owns it first, then the portals that take it over. */
    public static List<Portal> portalsShowing(Catalog catalog) {
        return Stream.concat(portalOf(catalog).stream(),
                catalog.getImporter().stream().filter(Portal.class::isInstance).map(Portal.class::cast))
                .distinct()
                .toList();
    }

    /**
     * The portals that show {@code resource}: the portals showing a catalog that lists it
     * ({@link Catalog#getResource()}), each once.
     */
    public static List<Portal> portalsListing(CatalogedResource resource) {
        return Catalogs.catalogsListing(resource).stream()
                .flatMap(catalog -> portalsShowing(catalog).stream())
                .distinct()
                .toList();
    }
}
