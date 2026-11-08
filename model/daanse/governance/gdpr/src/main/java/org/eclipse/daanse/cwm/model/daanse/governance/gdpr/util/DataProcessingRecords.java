/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessing;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingRecord;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.ProcessingFactory;

/** Creates records of processing activities (Art. 30 GDPR) and lists their processings. */
public final class DataProcessingRecords {

    private DataProcessingRecords() {
    }

    /**
     * A record in the {@code ownedElement} of {@code owner}, kept by {@code controller}, internally
     * by {@code internalController}, with {@code privacyOfficer} as the data protection officer.
     */
    public static DataProcessingRecord create(Namespace owner, String name, ResponsibleParty controller,
            ResponsibleParty internalController, ResponsibleParty privacyOfficer) {
        DataProcessingRecord record = ProcessingFactory.eINSTANCE.createDataProcessingRecord();
        record.setName(Objects.requireNonNull(name, "name"));
        record.setContactControllerGlobal(Objects.requireNonNull(controller, "controller"));
        record.setContactControllerInternal(Objects.requireNonNull(internalController, "internalController"));
        record.setContactPrivacyOfficer(Objects.requireNonNull(privacyOfficer, "privacyOfficer"));
        owner.getOwnedElement().add(record);
        return record;
    }

    /** The processings of a record: what it owns. */
    public static List<DataProcessing> processings(DataProcessingRecord record) {
        return record.getOwnedElement().stream().filter(DataProcessing.class::isInstance)
                .map(DataProcessing.class::cast).toList();
    }
}
