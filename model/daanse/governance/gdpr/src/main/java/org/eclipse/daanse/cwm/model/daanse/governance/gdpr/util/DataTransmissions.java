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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessing;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataTransmission;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.ProcessingFactory;

/** Creates the transmissions of a processing: to a recipient, of data classes, de-identified by a policy. */
public final class DataTransmissions {

    private DataTransmissions() {
    }

    /** A transmission of the data of the given classes to {@code recipient}. */
    public static DataTransmission create(DataProcessing processing, String name, ResponsibleParty recipient,
            Classifier... dataClasses) {
        DataTransmission transmission = ProcessingFactory.eINSTANCE.createDataTransmission();
        transmission.setName(Objects.requireNonNull(name, "name"));
        transmission.setContactRecipient(Objects.requireNonNull(recipient, "recipient"));
        transmission.getDataClasses().addAll(List.of(dataClasses));
        processing.getDataTransmissions().add(transmission);
        return transmission;
    }

    /** The data is de-identified by {@code policy} before it is transmitted. */
    public static DataTransmission policy(DataTransmission transmission, DeidentificationPolicy policy) {
        transmission.setPolicy(Objects.requireNonNull(policy, "policy"));
        return transmission;
    }
}
