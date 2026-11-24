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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Distribution;

/**
 * Accessors around {@link DataService}: the protocol a service implements and where a dataset is
 * in it. A service has one root address ({@link DataService#getEndpointUrl()}); the access URL of
 * a distribution accessed through it begins with that root, and the part behind it is the path of
 * the dataset in the service — the collection of an OGC API Features service
 * ({@code collections/pools}), the entity set of an OData service ({@code ChargingStations}), the
 * qualified name of a table behind a JDBC URL ({@code leisure.pool}). For an MQTT broker the path is
 * the topic.
 */
public final class DataServices {

    private DataServices() {
    }

    /**
     * The known protocol {@code service} implements: the first of its {@code conformsToIri} values
     * that names one of {@link DcatVocabularies#PROTOCOLS}, as that protocol's IRI. Empty when it
     * names none — a portal's access service names a metadata profile, not a protocol.
     */
    public static Optional<String> protocolOf(DataService service) {
        return service.getConformsToIri().stream()
                .map(DcatVocabularies::protocolNamedBy)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * The path of {@code distribution} in its access service: the part of its first access URL that
     * begins with the service's endpoint URL, behind that URL and without the slash that separates
     * them. Empty without an access service or when no access URL begins with the endpoint URL (rule
     * AccessUrlUnderEndpoint); the empty string when an access URL is the endpoint URL itself.
     */
    public static Optional<String> pathOf(Distribution distribution) {
        DataService service = distribution.getAccessService();
        String endpoint = service == null ? null : service.getEndpointUrl();
        if (endpoint == null || endpoint.isEmpty()) {
            return Optional.empty();
        }
        return distribution.getAccessUrl().stream()
                .filter(url -> url != null && url.startsWith(endpoint))
                .map(url -> url.substring(endpoint.length()))
                .map(path -> path.startsWith("/") ? path.substring(1) : path)
                .findFirst();
    }

    /**
     * The MQTT topic of {@code distribution}: its path ({@link #pathOf}) when its access service
     * implements {@link DcatVocabularies#PROTOCOL_MQTT}, with percent escapes decoded so that the
     * multi-level wildcard is written {@code %23} in the access URL; a {@code +} stays the single-level
     * wildcard. Empty for a distribution of any other service. An access URL with a malformed escape
     * gives its path as written.
     */
    public static Optional<String> topicOf(Distribution distribution) {
        DataService service = distribution.getAccessService();
        if (service == null || !protocolOf(service).filter(DcatVocabularies.PROTOCOL_MQTT::equals).isPresent()) {
            return Optional.empty();
        }
        return pathOf(distribution).map(DataServices::percentDecoded);
    }

    private static String percentDecoded(String path) {
        if (path.indexOf('%') < 0) {
            return path;
        }
        try {
            return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return path;
        }
    }
}
