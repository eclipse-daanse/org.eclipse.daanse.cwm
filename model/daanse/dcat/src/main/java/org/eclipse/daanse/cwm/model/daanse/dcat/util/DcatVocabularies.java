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

/**
 * Well-known IRI bases and values for the {@code ...Iri} attributes of the dcat model — the
 * controlled vocabularies DCAT profiles (DCAT-AP, DCAT-AP.de) bind: EU authority tables and
 * IANA media types. Constants, not model classes: a model holds the IRI as a string, so that any
 * vocabulary a profile binds fits without a change of the model.
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
