/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.data.source.csv;

import java.nio.file.Path;

import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFile;
import org.eclipse.daanse.cwm.data.api.RawRecord;
import org.eclipse.daanse.cwm.data.api.RecordSource;
import org.osgi.service.component.annotations.Component;

/**
 * Factory service for creating {@link CsvRecordPublisher} instances configured
 * from CWM RecordFile and RecordDef models.
 */
@Component(service = CsvRecordPublisherFactory.class)
public class CsvRecordPublisherFactory {

    public RecordSource<RawRecord> create(Path csvFile, RecordFile recordFile, RecordDef recordDef) {
        return new CsvRecordPublisher(csvFile, recordFile, recordDef);
    }
}
