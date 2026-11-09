/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;

/**
 * The outcome of a {@link CwmToEcore} conversion.
 *
 * @param ePackages the root EPackages (nested packages are their {@code eSubpackages})
 * @param trace     every converted CWM element and the Ecore element made from it: package to
 *                  EPackage, class to EClass, data type to EDataType (or the reused Ecore data
 *                  type), enumeration to EEnum, literal to EEnumLiteral, attribute to EAttribute or
 *                  containment EReference, navigable association end to EReference, operation to
 *                  EOperation, parameter to EParameter
 * @param notes     the findings, in the order they arose
 */
public record Result(List<EPackage> ePackages, Map<ModelElement, EModelElement> trace, List<Note> notes) {

    /** Whether a note of severity {@link Note.Severity#ERROR} exists. */
    public boolean hasErrors() {
        return notes.stream().anyMatch(n -> n.severity() == Note.Severity.ERROR);
    }

    /** The notes of severity {@link Note.Severity#ERROR}. */
    public List<Note> errors() {
        return notes.stream().filter(n -> n.severity() == Note.Severity.ERROR).toList();
    }

    /** The Ecore element made from {@code element}, cast to the expected type, or {@code null}. */
    public <T extends EModelElement> T get(ModelElement element, Class<T> type) {
        EModelElement e = trace.get(element);
        return type.isInstance(e) ? type.cast(e) : null;
    }
}
