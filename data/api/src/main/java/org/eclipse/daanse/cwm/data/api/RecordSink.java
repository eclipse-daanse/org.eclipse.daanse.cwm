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
package org.eclipse.daanse.cwm.data.api;

import java.util.concurrent.Flow;

/**
 * A sink that consumes records. Extends {@link Flow.Subscriber} to support
 * backpressure signaling.
 *
 * @param <T> the record type consumed
 */
public interface RecordSink<T> extends Flow.Subscriber<T> {
}
