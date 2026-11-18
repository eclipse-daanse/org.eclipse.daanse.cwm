/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial.util;

import java.util.Objects;
import java.util.OptionalLong;

import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;

/**
 * What a reference system is called outside the model: its OGC URI, and for an EPSG system the number the spatial
 * functions of a database take (PostGIS {@code ST_Transform(g, 25832)}, the SRID of a geometry). Two systems are the
 * same where their URIs are: authority, version and code.
 */
public final class ReferenceSystems {

    /** The start of every OGC URI of a coordinate reference system. */
    public static final String OGC_CRS = "http://www.opengis.net/def/crs/";

    private ReferenceSystems() {
    }

    /**
     * The OGC URI {@code http://www.opengis.net/def/crs/{authority}/{version}/{code}}; a system without a version has
     * the version {@code 0}.
     */
    public static String uri(ReferenceSystem system) {
        Objects.requireNonNull(system, "system");
        return OGC_CRS + system.getAuthority().getName() + "/" + version(system) + "/" + code(system);
    }

    /** The EPSG code of an EPSG system as a number, as the spatial functions of a database take it; else none. */
    public static OptionalLong epsg(ReferenceSystem system) {
        if (system == null || system.getAuthority() != ReferenceSystemAuthority.EPSG) {
            return OptionalLong.empty();
        }
        try {
            long code = Long.parseLong(code(system));
            return code > 0 ? OptionalLong.of(code) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Whether two systems are one: the same element, or the same authority, version and code. How the columns of each
     * store their coordinates (the order, the epoch) is said by their geometry features, not by the system.
     */
    public static boolean same(ReferenceSystem a, ReferenceSystem b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getAuthority() == b.getAuthority() && version(a).equals(version(b)) && code(a).equals(code(b));
    }

    /** The authority and the code, {@code EPSG 25832}, as messages name a system. */
    public static String name(ReferenceSystem system) {
        return system.getAuthority().getName() + " " + code(system);
    }

    private static String code(ReferenceSystem system) {
        return system.getCode() == null ? "" : system.getCode().trim();
    }

    private static String version(ReferenceSystem system) {
        String version = system.getVersion() == null ? "" : system.getVersion().trim();
        return version.isEmpty() ? "0" : version;
    }
}
