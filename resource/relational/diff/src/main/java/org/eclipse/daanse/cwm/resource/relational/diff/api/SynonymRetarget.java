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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;

/**
 * A same-named synonym whose target differs between the old and new schema —
 * target catalog, schema or name, DB link or the {@code PUBLIC} flag.
 *
 * @param oldSynonym the synonym as it existed in the old schema
 * @param newSynonym the same synonym's declaration in the new schema
 */
public record SynonymRetarget(Synonym oldSynonym, Synonym newSynonym) {
}
