/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Data In Motion Consulting - initial implementation
 */
/**
 * CWM-EORM &rarr; Fennec EORM conversion. {@link
 * org.eclipse.daanse.cwm.model.cwmx.eorm.converter.CwmToEormConverter} turns a
 * Common Warehouse EORM model into the structurally equivalent Fennec
 * Persistence EORM model, dropping warehouse-only information that Fennec has no
 * place for.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.cwm.model.cwmx.eorm.converter;
