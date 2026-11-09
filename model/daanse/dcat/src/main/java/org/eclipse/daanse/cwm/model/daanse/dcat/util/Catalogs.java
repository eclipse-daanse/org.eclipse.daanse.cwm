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

import org.eclipse.daanse.cwm.model.daanse.dcat.Catalog;
import org.eclipse.daanse.cwm.model.daanse.dcat.CatalogRecord;
import org.eclipse.daanse.cwm.model.daanse.dcat.CatalogedResource;
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Dataset;
import org.eclipse.daanse.cwm.model.daanse.dcat.DcatPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;

/**
 * Accessors around {@link Catalog}. The DCAT membership subsets (dcat:dataset, dcat:service,
 * dcat:catalog) are derivable from {@link Catalog#getResource()} and therefore type filters
 * here, not model features. Reverse lookups need
 * {@link InverseReferences#install(org.eclipse.emf.common.notify.Notifier)} on the ResourceSet
 * — without it they return empty.
 */
public final class Catalogs {

    private Catalogs() {
    }

    /** dcat:dataset — listed datasets, excluding nested catalogs (those are {@link #catalogs}). */
    public static List<Dataset> datasets(Catalog catalog) {
        return datasetStream(catalog).toList();
    }

    /** Stream form of {@link #datasets(Catalog)}. */
    public static Stream<Dataset> datasetStream(Catalog catalog) {
        return catalog.getResource().stream()
                .filter(Dataset.class::isInstance)
                .filter(r -> !(r instanceof Catalog))
                .map(Dataset.class::cast);
    }

    /** dcat:service — listed data services. */
    public static List<DataService> services(Catalog catalog) {
        return serviceStream(catalog).toList();
    }

    /** Stream form of {@link #services(Catalog)}. */
    public static Stream<DataService> serviceStream(Catalog catalog) {
        return catalog.getResource().stream()
                .filter(DataService.class::isInstance)
                .map(DataService.class::cast);
    }

    /** dcat:catalog — listed nested catalogs. */
    public static List<Catalog> catalogs(Catalog catalog) {
        return catalogStream(catalog).toList();
    }

    /** Stream form of {@link #catalogs(Catalog)}. */
    public static Stream<Catalog> catalogStream(Catalog catalog) {
        return catalog.getResource().stream()
                .filter(Catalog.class::isInstance)
                .map(Catalog.class::cast);
    }

    /** dcat:inCatalog (hidden role) — the catalogs listing {@code resource}. */
    public static List<Catalog> catalogsListing(CatalogedResource resource) {
        return InverseReferences
                .referencing(resource, DcatPackage.Literals.CATALOG__RESOURCE, Catalog.class)
                .toList();
    }

    /** The catalog record whose foaf:primaryTopic is {@code resource}, if the catalog keeps one. */
    public static Optional<CatalogRecord> recordFor(Catalog catalog, CatalogedResource resource) {
        return catalog.getRecord().stream()
                .filter(r -> r.getPrimaryTopic() == resource)
                .findFirst();
    }
}
