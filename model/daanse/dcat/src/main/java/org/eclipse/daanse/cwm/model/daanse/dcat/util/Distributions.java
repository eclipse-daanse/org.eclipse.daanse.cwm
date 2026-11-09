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
 * Accessors around {@link Distribution}. dcat:isDistributionOf is the container; the hidden
 * role servedDistribution needs
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

    /** The distributions of {@code dataset} with the given IANA media type. */
    public static Stream<Distribution> withMediaType(Dataset dataset, String mediaType) {
        return dataset.getDistribution().stream().filter(d -> mediaType.equals(d.getMediaType()));
    }
}
