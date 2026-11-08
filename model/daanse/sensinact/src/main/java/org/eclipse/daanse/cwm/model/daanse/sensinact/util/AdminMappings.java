/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.daanse.sensinact.AdminMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/** Creates and fills the admin mapping of a provider mapping. */
public final class AdminMappings {

    /** The name of every admin mapping, the id sensiNact gives the admin service. */
    public static final String NAME = "admin";

    private AdminMappings() {
    }

    /** The admin mapping of {@code mapping}, named {@value #NAME}. */
    public static AdminMapping create(ProviderMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        AdminMapping admin = SensinactFactory.eINSTANCE.createAdminMapping();
        admin.setName(NAME);
        mapping.setAdmin(admin);
        return admin;
    }

    /** The friendly name of a provider instance. */
    public static AdminMapping friendlyName(AdminMapping admin, ValueSource friendlyName) {
        admin.setFriendlyName(Objects.requireNonNull(friendlyName, "friendlyName"));
        return admin;
    }

    /** The description of a provider instance. */
    public static AdminMapping description(AdminMapping admin, ValueSource description) {
        admin.setDescription(Objects.requireNonNull(description, "description"));
        return admin;
    }

    /** The location of a provider instance. */
    public static AdminMapping location(AdminMapping admin, ValueSource latitude, ValueSource longitude) {
        admin.setLatitude(Objects.requireNonNull(latitude, "latitude"));
        admin.setLongitude(Objects.requireNonNull(longitude, "longitude"));
        return admin;
    }
}
