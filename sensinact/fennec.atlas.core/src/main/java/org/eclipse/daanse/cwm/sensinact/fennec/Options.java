/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

import java.time.Instant;
import java.util.Map;

/**
 * Settings of a conversion.
 *
 * @param profiles  write a {@code MappingProfile} per provider ({@code UNIFIED}: every mapping onto
 *                  the provider feeds one provider model named after the provider) and refer to it;
 *                  without, each mapping makes its own provider model, named after the mapping
 * @param dataTypes the Ecore type of resources typed by a CWM data type of a given name
 *                  ({@code Float → EFloat}); wins over the rules of the converter
 * @param at        the time the mappings are written for: a retention hold that is not over then
 *                  keeps a deletion rule back; {@code null} takes the time of the conversion
 */
public record Options(boolean profiles, Map<String, String> dataTypes, Instant at) {

    public Options {
        dataTypes = dataTypes == null ? Map.of() : Map.copyOf(dataTypes);
    }

    /** With or without profiles, with data type overrides, for the time of the conversion. */
    public Options(boolean profiles, Map<String, String> dataTypes) {
        this(profiles, dataTypes, null);
    }

    /** Profiles on, no data type overrides. */
    public static Options defaults() {
        return new Options(true, Map.of());
    }

    /** With or without profiles. */
    public Options withProfiles(boolean profiles) {
        return new Options(profiles, dataTypes, at);
    }

    /** With data type overrides: CWM data type name → Ecore data type name. */
    public Options withDataTypes(Map<String, String> dataTypes) {
        return new Options(profiles, dataTypes, at);
    }

    /** For the time {@code at}: what a hold keeps back is judged then. */
    public Options withAt(Instant at) {
        return new Options(profiles, dataTypes, at);
    }
}
