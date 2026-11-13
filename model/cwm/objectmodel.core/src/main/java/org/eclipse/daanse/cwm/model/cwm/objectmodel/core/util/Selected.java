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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;

/**
 * The result of {@link Selections#copy}: the cut copy, what it leaves out and
 * what it holds without being selected.
 *
 * @param model    the copy, detached from any resource
 * @param leftOut  every element left out in its own right, in the order it was
 *                 left out; what it contains goes with it and is not listed
 * @param retained every element the copy holds because an element that stays
 *                 needs it ({@link ConsequenceRule.Consequence#RETAIN}) or, for a
 *                 stereotype, because it brands one, in the order it was taken
 *                 in; its contents and owners come along and are not listed
 */
public record Selected<T extends ModelElement>(T model, List<LeftOut> leftOut, List<Retained> retained) {

    public Selected {
        Objects.requireNonNull(model, "model");
        leftOut = List.copyOf(leftOut);
        retained = List.copyOf(retained);
    }

    /**
     * One element the copy leaves out.
     *
     * @param path   its {@linkplain QualifiedNames qualified name} in the original model
     * @param kind   the name of its class, such as {@code Attribute}
     * @param reason the rule that left it out as it reads ({@code name:tmp_*}),
     *               {@code not included} when include rules exist and none
     *               reaches it, or the consequence with the reference and the
     *               element it lost ({@code required reference (type: shop.types.text)})
     */
    public record LeftOut(String path, String kind, String reason) {

        @Override
        public String toString() {
            return kind + " " + path + " (" + reason + ")";
        }
    }

    /**
     * One element the copy holds without selecting it.
     *
     * @param path   its {@linkplain QualifiedNames qualified name} in the original model
     * @param kind   the name of its class, such as {@code DataType}
     * @param reason {@code needed by} and the element and reference that need it
     *               ({@code needed by shop.sales.orders.id.type}), or
     *               {@code brands} and the element a stereotype brands
     */
    public record Retained(String path, String kind, String reason) {

        @Override
        public String toString() {
            return kind + " " + path + " (" + reason + ")";
        }
    }
}
