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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessing;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingLawfulness;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingLawfulnessType;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingRecord;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingType;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.PersonalReferenceType;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.ProcessingFactory;

/**
 * Creates the processings of a record: what they do, with which personal data, on which legal
 * ground, from when. The legal sources of a processing are the documents that name it.
 */
public final class DataProcessings {

    private static final ProcessingFactory F = ProcessingFactory.eINSTANCE;

    private DataProcessings() {
    }

    /**
     * A processing of {@code record} for {@code purpose}, valid from {@code validFrom} (an ISO-8601
     * date), on the data of the given classes.
     */
    public static DataProcessing create(DataProcessingRecord record, String name, String purpose, String validFrom,
            Classifier... dataClasses) {
        DataProcessing processing = F.createDataProcessing();
        processing.setName(Objects.requireNonNull(name, "name"));
        processing.setDataProcessingPurpose(Objects.requireNonNull(purpose, "purpose"));
        processing.setValidFrom(Objects.requireNonNull(validFrom, "validFrom"));
        processing.getDataClasses().addAll(List.of(dataClasses));
        record.getOwnedElement().add(processing);
        return processing;
    }

    /** Names what the processing does and how its data refers to persons. */
    public static DataProcessing kinds(DataProcessing processing, List<DataProcessingType> types,
            List<PersonalReferenceType> references) {
        processing.getDataProcessingTypes().addAll(types);
        processing.getPersonalReferenceTypes().addAll(references);
        return processing;
    }

    /** The lawfulness of the processing (Art. 6 GDPR), with a note in prose. */
    public static DataProcessingLawfulness lawful(DataProcessing processing, String name,
            DataProcessingLawfulnessType type, String note) {
        DataProcessingLawfulness lawfulness = F.createDataProcessingLawfulness();
        lawfulness.setName(Objects.requireNonNull(name, "name"));
        lawfulness.setDataProcessingLawfulnessTypes(Objects.requireNonNull(type, "type"));
        lawfulness.setDataProcessingLawfulnessAnnotation(Objects.requireNonNull(note, "note"));
        processing.getDataProcessingLawfulness().add(lawfulness);
        return lawfulness;
    }

    /** The legal sources of the processing: the documents that name it. */
    public static List<LegalSource> sources(DataProcessing processing) {
        return LegalSources.of(processing);
    }
}
