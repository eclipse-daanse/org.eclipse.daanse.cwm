/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.Scale;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Attaches units to the elements whose values they measure, and finds the unit of an element.
 *
 * <p>A quantity is owned by the namespace it is created in — a package of quantities next to the
 * model, for instance — and names its elements; it is not owned by the elements, because a data
 * type may own nothing (CWM 1.1, C-3-2). The unit of an element is its own quantity's unit, else,
 * for an attribute or parameter typed by a data type, the unit of the data type's quantity: a
 * data type {@code Celsius} with a quantity in degree Celsius gives every attribute of that type
 * its unit, and an attribute's own quantity comes first.</p>
 *
 * <p>The quantities of an element are found backwards through {@link InverseReferences}: with an
 * {@code ECrossReferenceAdapter} installed on the resource set in constant time, without one by a
 * scan of the model.</p>
 */
public final class Quantities {

    private Quantities() {
    }

    /** A quantity owned by {@code owner}: the values of {@code elements} are measured in {@code unit}. */
    public static Quantity create(Namespace owner, String name, Unit unit, ModelElement... elements) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(elements, "elements");
        if (elements.length == 0) {
            throw new IllegalArgumentException("a quantity names at least one element");
        }
        Quantity q = UnitsFactory.eINSTANCE.createQuantity();
        q.setName(Objects.requireNonNull(name, "name"));
        q.setUnit(Objects.requireNonNull(unit, "unit"));
        for (ModelElement e : elements) {
            q.getModelElement().add(Objects.requireNonNull(e, "element"));
        }
        owner.getOwnedElement().add(q);
        return q;
    }

    /** Names the kind of quantity, where the unit alone does not. */
    public static Quantity kind(Quantity quantity, QuantityKind kind) {
        quantity.setQuantityKind(Objects.requireNonNull(kind, "kind"));
        return quantity;
    }

    /** Marks the values as differences of values, which convert without the offset of a unit. */
    public static Quantity relative(Quantity quantity) {
        quantity.setScale(Scale.RELATIVE);
        return quantity;
    }

    /** The quantities that name {@code element}; more than one is an error of the model. */
    public static List<Quantity> own(ModelElement element) {
        return InverseReferences.referencingList(element, UnitsPackage.Literals.QUANTITY__MODEL_ELEMENT,
                Quantity.class);
    }

    /**
     * The quantity of {@code element}: its own, else the one of the data type that types it (an
     * attribute, a parameter).
     */
    public static Optional<Quantity> of(ModelElement element) {
        Objects.requireNonNull(element, "element");
        List<Quantity> own = own(element);
        if (!own.isEmpty()) {
            return Optional.of(own.get(0));
        }
        return typeOf(element).flatMap(type -> own(type).stream().findFirst());
    }

    /** The unit of {@code element}: the unit of its {@link #of(ModelElement) quantity}. */
    public static Optional<Unit> unitOf(ModelElement element) {
        return of(element).map(Quantity::getUnit);
    }

    /** The data type that types {@code element}, if it has one: of an attribute or a parameter. */
    public static Optional<DataType> typeOf(ModelElement element) {
        EStructuralFeature type = element.eClass().getEStructuralFeature("type");
        if (type == null || type.isMany()) {
            return Optional.empty();
        }
        return element.eGet(type) instanceof DataType d ? Optional.of(d) : Optional.empty();
    }
}
