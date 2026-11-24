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

/**
 * Well-known IRI bases and values for the {@code ...Iri} attributes of the dcat model — the
 * controlled vocabularies DCAT profiles (DCAT-AP, DCAT-AP.de) bind: EU authority tables and
 * IANA media types; the protocols a data service implements and the profile a portal speaks, both
 * for {@code conformsToIri}. Constants, not model classes: a model holds the IRI as a string, so
 * that any vocabulary a profile binds fits without a change of the model.
 *
 * <p>A protocol IRI names a family of specifications: an IRI in {@code DataService.conformsToIri}
 * that begins with it, such as a version or a conformance class of that specification, names the
 * same protocol ({@code http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core} names
 * {@link #PROTOCOL_OGC_API_FEATURES}). A data service names at most one of them (rule
 * {@code OneProtocol}). Where a protocol has no public specification IRI, this module coins one
 * below {@link #DAANSE_PROTOCOL}.</p>
 */
public final class DcatVocabularies {

    private DcatVocabularies() {
    }

    /** EU authority table base for dcat:theme ({@code themeIri}). */
    public static final String EU_DATA_THEME = "http://publications.europa.eu/resource/authority/data-theme/";
    /** EU authority table base for dcterms:accrualPeriodicity ({@code accrualPeriodicityIri}). */
    public static final String EU_FREQUENCY = "http://publications.europa.eu/resource/authority/frequency/";
    /** EU authority table base for dcterms:language ({@code language}). */
    public static final String EU_LANGUAGE = "http://publications.europa.eu/resource/authority/language/";
    /** EU authority table base for dcterms:format ({@code formatIri}). */
    public static final String EU_FILE_TYPE = "http://publications.europa.eu/resource/authority/file-type/";
    /** EU authority table base for dcterms:license ({@code licenseIri}). */
    public static final String EU_LICENCE = "http://publications.europa.eu/resource/authority/licence/";
    /** EU authority table base for adms:status ({@code statusIri}). */
    public static final String EU_DISTRIBUTION_STATUS =
            "http://publications.europa.eu/resource/authority/distribution-status/";
    /** EU authority table base for dcterms:accessRights ({@code accessRightsIri}). */
    public static final String EU_ACCESS_RIGHT = "http://publications.europa.eu/resource/authority/access-right/";
    /** dcterms:accessRights — public. */
    public static final String ACCESS_RIGHT_PUBLIC = EU_ACCESS_RIGHT + "PUBLIC";
    /** dcterms:accessRights — restricted. */
    public static final String ACCESS_RIGHT_RESTRICTED = EU_ACCESS_RIGHT + "RESTRICTED";
    /** dcterms:accessRights — non-public. */
    public static final String ACCESS_RIGHT_NON_PUBLIC = EU_ACCESS_RIGHT + "NON_PUBLIC";
    /** IANA media type registry base for dcat:mediaType/compressFormat/packageFormat. */
    public static final String IANA_MEDIA_TYPES = "https://www.iana.org/assignments/media-types/";

    /**
     * The base of the protocol IRIs this module coins, below the namespace of the dcat model, for
     * protocols without a public specification IRI.
     */
    public static final String DAANSE_PROTOCOL =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat/protocol/";
    /** OGC API - Features (all parts and versions; Part 1 Core 1.0 is {@code .../ogcapi-features-1/1.0}). */
    public static final String PROTOCOL_OGC_API_FEATURES = "http://www.opengis.net/spec/ogcapi-features-1/";
    /** OGC SensorThings API, Part 1 Sensing (all versions). */
    public static final String PROTOCOL_OGC_SENSORTHINGS = "http://www.opengis.net/spec/iot_sensing/";
    /** OASIS OData (protocol, formats and CSDL of all versions). */
    public static final String PROTOCOL_ODATA = "http://docs.oasis-open.org/odata/";
    /** GeoJSON (RFC 7946), served as plain files or by a simple feature endpoint. */
    public static final String PROTOCOL_GEOJSON = "https://www.rfc-editor.org/rfc/rfc7946";
    /** GraphQL (all editions of the specification). */
    public static final String PROTOCOL_GRAPHQL = "https://spec.graphql.org/";
    /** An HTTP API described by OpenAPI (all versions); {@code endpointDescription} points at the document. */
    public static final String PROTOCOL_OPENAPI = "https://spec.openapis.org/oas/";
    /**
     * OASIS MQTT (all versions). The service is the broker; the topic of a distribution is the path
     * of its access URL behind the broker's endpoint URL ({@link DataServices#topicOf}).
     */
    public static final String PROTOCOL_MQTT = "http://docs.oasis-open.org/mqtt/";
    /** XML for Analysis: an OLAP server answering Discover and Execute. Coined by this module. */
    public static final String PROTOCOL_XMLA = DAANSE_PROTOCOL + "xmla";
    /** A QGIS project file whose layers are the datasets. Coined by this module. */
    public static final String PROTOCOL_QGIS_PROJECT = DAANSE_PROTOCOL + "qgis-project";
    /**
     * A relational database reached through JDBC; the endpoint URL is the JDBC URL of the database.
     * Coined by this module.
     */
    public static final String PROTOCOL_JDBC = DAANSE_PROTOCOL + "jdbc";
    /** The known protocols, in the order {@link #protocolNamedBy(String)} tries them. */
    public static final List<String> PROTOCOLS = List.of(PROTOCOL_OGC_API_FEATURES, PROTOCOL_OGC_SENSORTHINGS,
            PROTOCOL_ODATA, PROTOCOL_GEOJSON, PROTOCOL_GRAPHQL, PROTOCOL_OPENAPI, PROTOCOL_MQTT, PROTOCOL_XMLA,
            PROTOCOL_QGIS_PROJECT, PROTOCOL_JDBC);

    /**
     * DCAT-AP.de 3.0, the German application profile of DCAT-AP: the {@code conformsToIri} of the
     * access service of a portal whose catalog interface speaks it, and of catalog records kept to it.
     */
    public static final String PROFILE_DCAT_AP_DE = "http://dcat-ap.de/def/dcatde/3.0/";

    /**
     * The known protocol {@code iri} names: the first of {@link #PROTOCOLS} it begins with, so that
     * a version or a conformance class names its protocol; empty for any other IRI.
     */
    public static Optional<String> protocolNamedBy(String iri) {
        if (iri == null) {
            return Optional.empty();
        }
        return PROTOCOLS.stream().filter(iri::startsWith).findFirst();
    }

    /** IRI of an EU data-theme code, e.g. {@code themeIri("ENVI")}. */
    public static String themeIri(String code) {
        return EU_DATA_THEME + code;
    }

    /** IRI of an EU frequency code, e.g. {@code frequencyIri("DAILY")}. */
    public static String frequencyIri(String code) {
        return EU_FREQUENCY + code;
    }

    /** IRI of an IANA media type, e.g. {@code mediaTypeIri("text/csv")}. */
    public static String mediaTypeIri(String mediaType) {
        return IANA_MEDIA_TYPES + mediaType;
    }
}
