/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureControl;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DominanceRule;

/**
 * Creates disclosure controls and finds the one that applies: the control of a measure, else the
 * control of its cube — a measure is an attribute of the cube, a cube a class.
 */
public final class DisclosureControls {

    private DisclosureControls() {
    }

    /** A control in the {@code ownedElement} of {@code owner} on cubes or measures, protecting by {@code method}. */
    public static DisclosureControl create(Namespace owner, String name, DisclosureMethod method,
            ModelElement... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("a disclosure control governs at least one element");
        }
        DisclosureControl control = DeidentificationFactory.eINSTANCE.createDisclosureControl();
        control.setName(Objects.requireNonNull(name, "name"));
        control.setMethod(Objects.requireNonNull(method, "method"));
        for (ModelElement e : elements) {
            control.getConstrainedElement().add(Objects.requireNonNull(e, "element"));
        }
        owner.getOwnedElement().add(control);
        return refreshed(control);
    }

    /** A cell of fewer than {@code count} contributors is protected. */
    public static DisclosureControl minimumCellCount(DisclosureControl control, long count) {
        control.setMinimumCellCount(count);
        return refreshed(control);
    }

    /** A cell whose {@code n} largest contributors make up more than {@code k} percent is protected. */
    public static DisclosureControl dominance(DisclosureControl control, long n, double k) {
        DominanceRule rule = DeidentificationFactory.eINSTANCE.createDominanceRule();
        rule.setN(n);
        rule.setK(Policies.decimal(k));
        control.setDominance(rule);
        return refreshed(control);
    }

    /** The control of {@code element}: its own, else that of the classifier that owns it. */
    public static Optional<DisclosureControl> effective(ModelElement element) {
        Optional<DisclosureControl> own = own(element);
        if (own.isEmpty() && element instanceof Feature f && f.getOwner() != null) {
            return own(f.getOwner());
        }
        return own;
    }

    private static Optional<DisclosureControl> own(ModelElement element) {
        for (Constraint c : element.getConstraint()) {
            if (c instanceof DisclosureControl d) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /** The text of a control, e.g. {@code at least 3 per cell; (2, 85) dominance; suppression}. */
    public static String render(DisclosureControl control) {
        StringBuilder sb = new StringBuilder();
        if (control.isSetMinimumCellCount()) {
            sb.append("at least ").append(control.getMinimumCellCount()).append(" per cell; ");
        }
        if (control.getDominance() != null) {
            sb.append("(").append(control.getDominance().getN()).append(", ").append(control.getDominance().getK())
                    .append(") dominance; ");
        }
        return sb.append(control.getMethod() == null ? "?" : control.getMethod().getLiteral()).toString();
    }

    /** Sets the {@code body} of a control to its {@linkplain #render text}. */
    public static DisclosureControl refreshed(DisclosureControl control) {
        control.setBody(Expressions.bool(Policies.LANGUAGE, render(control)));
        return control;
    }
}
