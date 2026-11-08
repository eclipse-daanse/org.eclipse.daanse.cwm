/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPackage;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DestroyLinkage;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Pseudonymization;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;

/**
 * Creates pseudonymization domains and answers what refers to them: the pseudonymizations in a
 * domain and the stages that destroy its linkage, found by looking for the elements that name the
 * domain (fast with {@link InverseReferences#install}).
 */
public final class PseudonymizationDomains {

    private PseudonymizationDomains() {
    }

    /** A domain in the {@code ownedElement} of {@code owner}, its key or mapping held by {@code keyHolder}. */
    public static PseudonymizationDomain create(Namespace owner, String name, ResponsibleParty keyHolder) {
        PseudonymizationDomain domain = DeidentificationFactory.eINSTANCE.createPseudonymizationDomain();
        domain.setName(Objects.requireNonNull(name, "name"));
        domain.setKeyHolder(Objects.requireNonNull(keyHolder, "keyHolder"));
        owner.getOwnedElement().add(domain);
        return domain;
    }

    /** Names the classifier whose rows map the pseudonyms of the domain to their values. */
    public static PseudonymizationDomain mapping(PseudonymizationDomain domain, Classifier mapping) {
        domain.setMapping(Objects.requireNonNull(mapping, "mapping"));
        return domain;
    }

    /** The treatments that make pseudonyms in {@code domain}. */
    public static List<Pseudonymization> pseudonymizations(PseudonymizationDomain domain) {
        return InverseReferences.referencingList(domain, DeidentificationPackage.Literals.PSEUDONYMIZATION__DOMAIN,
                Pseudonymization.class);
    }

    /** The stages that destroy the linkage of {@code domain}. */
    public static List<DestroyLinkage> destroyedBy(PseudonymizationDomain domain) {
        return InverseReferences.referencingList(domain, DeidentificationPackage.Literals.DESTROY_LINKAGE__DOMAIN,
                DestroyLinkage.class);
    }
}
