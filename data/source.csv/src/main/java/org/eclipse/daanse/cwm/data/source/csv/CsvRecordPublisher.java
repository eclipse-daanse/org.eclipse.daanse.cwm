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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFile;
import org.eclipse.daanse.cwm.data.api.RawRecord;
import org.eclipse.daanse.cwm.data.api.RecordSource;
import org.eclipse.daanse.cwm.data.api.ValidationResult;
import org.eclipse.daanse.cwm.data.source.csv.record.RawRecordR;
import org.eclipse.emf.common.util.EList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import de.siegmar.fastcsv.reader.NamedCsvRecord;

/**
 * Reads CSV files according to CWM RecordDef/RecordFile definitions and
 * publishes rows as {@link RawRecord} items with backpressure support.
 */
public class CsvRecordPublisher implements RecordSource<RawRecord> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvRecordPublisher.class);

    private final Path csvFilePath;
    private final RecordFile recordFile;
    private final RecordDef recordDef;

    public CsvRecordPublisher(Path csvFilePath, RecordFile recordFile, RecordDef recordDef) {
        this.csvFilePath = csvFilePath;
        this.recordFile = recordFile;
        this.recordDef = recordDef;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super RawRecord> subscriber) {
        CsvSubscription subscription = new CsvSubscription(subscriber, csvFilePath, recordFile, recordDef);
        subscriber.onSubscribe(subscription);
    }

    private static class CsvSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super RawRecord> subscriber;
        private final Path csvFilePath;
        private final RecordFile recordFile;
        private final RecordDef recordDef;
        private final AtomicLong demand = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean started = new AtomicBoolean(false);

        private CloseableIterator<CsvRecord> iterator;
        private List<String> fieldNames;
        private long lineCounter;

        CsvSubscription(Flow.Subscriber<? super RawRecord> subscriber, Path csvFilePath, RecordFile recordFile,
                RecordDef recordDef) {
            this.subscriber = subscriber;
            this.csvFilePath = csvFilePath;
            this.recordFile = recordFile;
            this.recordDef = recordDef;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("request count must be positive: " + n));
                return;
            }
            if (cancelled.get()) {
                return;
            }

            demand.addAndGet(n);

            if (started.compareAndSet(false, true)) {
                try {
                    initialize();
                } catch (Exception e) {
                    subscriber.onError(e);
                    return;
                }
            }

            drain();
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                closeResources();
            }
        }

        private void initialize() throws IOException {
            char fieldSeparator = ',';
            char quoteCharacter = '"';

            String delimiter = recordDef.getFieldDelimiter();
            if (delimiter != null && !delimiter.isEmpty()) {
                fieldSeparator = delimiter.charAt(0);
            }
            String textDelim = recordDef.getTextDelimiter();
            if (textDelim != null && !textDelim.isEmpty()) {
                quoteCharacter = textDelim.charAt(0);
            }

            CsvReader.CsvReaderBuilder builder = CsvReader.builder().fieldSeparator(fieldSeparator)
                    .quoteCharacter(quoteCharacter).skipEmptyLines(true);

            boolean isSelfDescribing = recordFile.isIsSelfDescribing();

            EList<Feature> features = recordDef.getFeature();
            List<String> recordDefFieldNames = new ArrayList<>();
            for (Feature feature : features) {
                recordDefFieldNames.add(feature.getName());
            }

            if (isSelfDescribing) {
                CloseableIterator<NamedCsvRecord> namedIterator = builder.ofNamedCsvRecord(csvFilePath).iterator();
                if (!namedIterator.hasNext()) {
                    namedIterator.close();
                    throw new IllegalStateException("CSV file is empty, no header found: " + csvFilePath);
                }

                NamedCsvRecord firstRecord = namedIterator.next();
                List<String> csvHeaders = firstRecord.getHeader();

                ValidationResult validation = HeaderValidator.validate(csvHeaders, recordDef);
                if (!validation.isValid()) {
                    namedIterator.close();
                    throw new IllegalStateException("CSV header validation failed. Missing fields: "
                            + validation.missingFields() + ", Extra fields: " + validation.extraFields());
                }

                LOGGER.debug("CSV header validated successfully for {}", csvFilePath);
                fieldNames = csvHeaders;

                namedIterator.close();

                iterator = builder.ofCsvRecord(csvFilePath).iterator();
                lineCounter = 0;

                if (iterator.hasNext()) {
                    iterator.next();
                    lineCounter = 1;
                }

                long skipRecordsVal = recordFile.getSkipRecords();
                int skip = (int) skipRecordsVal;
                for (int i = 0; i < skip && iterator.hasNext(); i++) {
                    iterator.next();
                    lineCounter++;
                }
            } else {
                fieldNames = recordDefFieldNames;

                iterator = builder.ofCsvRecord(csvFilePath).iterator();
                lineCounter = 0;

                long skipRecordsVal = recordFile.getSkipRecords();
                int skip = (int) skipRecordsVal;
                for (int i = 0; i < skip && iterator.hasNext(); i++) {
                    iterator.next();
                    lineCounter++;
                }
            }
        }

        private void drain() {
            while (demand.get() > 0 && !cancelled.get()) {
                if (iterator == null || !iterator.hasNext()) {
                    if (!cancelled.getAndSet(true)) {
                        closeResources();
                        subscriber.onComplete();
                    }
                    return;
                }

                CsvRecord csvRecord = iterator.next();
                lineCounter++;
                demand.decrementAndGet();

                Map<String, String> fields = new LinkedHashMap<>();
                for (int i = 0; i < fieldNames.size() && i < csvRecord.getFieldCount(); i++) {
                    fields.put(fieldNames.get(i), csvRecord.getField(i));
                }

                RawRecord rawRecord = new RawRecordR(Map.copyOf(fields), lineCounter);

                try {
                    subscriber.onNext(rawRecord);
                } catch (Exception e) {
                    if (!cancelled.getAndSet(true)) {
                        closeResources();
                        subscriber.onError(e);
                    }
                    return;
                }
            }
        }

        private void closeResources() {
            try {
                if (iterator != null) {
                    iterator.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Error closing CSV iterator", e);
            }
        }
    }
}
