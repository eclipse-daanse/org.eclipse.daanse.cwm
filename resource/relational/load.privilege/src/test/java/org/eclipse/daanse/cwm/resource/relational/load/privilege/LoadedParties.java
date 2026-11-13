/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.load.privilege;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.api.PrivilegeLoadResult;

/**
 * Puts what a privilege load returns into one package. A party does not refer to its stereotype
 * (that end is hidden), only the stereotype names its parties: a party finds its marker once both
 * belong to one model, as the catalog holds them after an import.
 */
final class LoadedParties {

    private LoadedParties() {
    }

    static Package holding(PrivilegeLoadResult result) {
        Package holder = CoreFactory.eINSTANCE.createPackage();
        holder.setName("loaded");
        holder.getOwnedElement().addAll(result.parties());
        holder.getOwnedElement().addAll(result.stereotypes());
        return holder;
    }
}
