/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator;

/**
 * A CWM-ORM mapping that breaks the explicit profile ({@link OrmModelValidator}): the conversion
 * stops before it writes anything, the findings say why.
 */
public final class InvalidMappingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<OrmModelValidator.Finding> findings;

    /** An exception for the given error findings. */
    public InvalidMappingException(String mapping, List<OrmModelValidator.Finding> findings) {
        super("mapping '" + mapping + "' breaks the explicit profile: " + findings.size() + " error(s), first: "
                + (findings.isEmpty() ? "-" : findings.get(0)));
        this.findings = List.copyOf(findings);
    }

    /** The error findings. */
    public List<OrmModelValidator.Finding> findings() {
        return findings;
    }
}
