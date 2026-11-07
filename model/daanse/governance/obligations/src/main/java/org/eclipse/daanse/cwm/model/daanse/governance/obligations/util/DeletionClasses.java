/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.util;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionClass;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.ObligationsFactory;

/**
 * Creates deletion classes and finds the class of an element: the nearest one of a scope along its
 * path, as for the retention schedules.
 */
public final class DeletionClasses {

    private DeletionClasses() {
    }

    /** A class: data of the given kinds is deleted {@code term} (a {@code Period} literal) after {@code start}. */
    public static DeletionClass create(Namespace owner, String name, PeriodStart start, String term,
            ModelElement... elements) {
        Periods.parse(term);
        DeletionClass deletionClass = ObligationsFactory.eINSTANCE.createDeletionClass();
        deletionClass.setStart(Objects.requireNonNull(start, "start"));
        deletionClass.setTerm(term);
        return refreshed(LifecycleRules.attach(owner, name, deletionClass, elements));
    }

    /** The class is about the rows of the governed classifiers, not the values of their features. */
    public static DeletionClass onRows(DeletionClass deletionClass) {
        deletionClass.setScope(DataScope.ROW);
        return deletionClass;
    }

    /** The class of {@code scope} that applies to {@code element}: the nearest one along its path. */
    public static Optional<DeletionClass> effective(ModelElement element, DataScope scope) {
        return LifecycleRules.nearest(element, DeletionClass.class, scope);
    }

    /** The text of a class, e.g. {@code class: delete P3Y after event contractEnded}. */
    public static String render(DeletionClass deletionClass) {
        return "class: delete " + deletionClass.getTerm() + " after "
                + (deletionClass.getStart() == null ? "?" : PeriodStarts.render(deletionClass.getStart()));
    }

    /** Sets the {@code body} of a class to its {@linkplain #render text}. */
    public static DeletionClass refreshed(DeletionClass deletionClass) {
        LifecycleRules.setBody(deletionClass, render(deletionClass));
        return deletionClass;
    }
}
