/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

import java.util.List;

/** Mappings the validators reject: the errors of the sensiNact, lifecycle and units validators. */
public class InvalidMappingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<Note> findings;

    public InvalidMappingException(List<Note> findings) {
        super(findings.size() + " error(s) in the mappings: " + String.join("; ",
                findings.stream().map(Note::message).toList()));
        this.findings = List.copyOf(findings);
    }

    /** The errors, as notes of rule {@link AtlasNotes#VALIDATION}. */
    public List<Note> findings() {
        return findings;
    }
}
