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
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Dataset;
import org.eclipse.daanse.cwm.model.daanse.dcat.DcatPackage;
import org.eclipse.daanse.cwm.model.daanse.dcat.Distribution;

/**
 * Accessors around {@link Distribution}. dcat:isDistributionOf is the container; a distribution is
 * accessed through at most one service, so the same dataset through two services is two
 * distributions. The hidden role servedDistribution needs
 * {@link InverseReferences#install(org.eclipse.emf.common.notify.Notifier)} on the ResourceSet.
 */
public final class Distributions {

    private Distributions() {
    }

    /** dcat:isDistributionOf — the owning dataset (the eContainer). */
    public static Optional<Dataset> datasetOf(Distribution distribution) {
        return distribution.eContainer() instanceof Dataset d ? Optional.of(d) : Optional.empty();
    }

    /** Hidden role servedDistribution — distributions declaring dcat:accessService {@code service}. */
    public static List<Distribution> distributionsServedBy(DataService service) {
        return InverseReferences
                .referencing(service, DcatPackage.Literals.DISTRIBUTION__ACCESS_SERVICE, Distribution.class)
                .toList();
    }

    /**
     * The distribution of {@code dataset} accessed through {@code service}: its access form in that
     * service, whose access URL gives the path ({@link DataServices#pathOf}). Empty when the service
     * names the dataset without such a distribution (rule ServedDatasetHasDistribution).
     */
    public static Optional<Distribution> servedBy(Dataset dataset, DataService service) {
        return dataset.getDistribution().stream()
                .filter(d -> d.getAccessService() == service)
                .findFirst();
    }

    /** The distributions of {@code dataset} with the given IANA media type. */
    public static Stream<Distribution> withMediaType(Dataset dataset, String mediaType) {
        return dataset.getDistribution().stream().filter(d -> mediaType.equals(d.getMediaType()));
    }
}
