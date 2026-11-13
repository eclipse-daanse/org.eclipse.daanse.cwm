/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * Assigns speaking {@code xmi:id}s to every object of a resource so cross-document
 * references become self-explanatory instead of positional.
 *
 * <p>Common pattern: {@code <EClassName>_<dotted.name.path>} — the classifier kind
 * first, then the shortest name path from the resource root down to the element,
 * e.g. {@code Table_SHOPDB.SALES.CUSTOMER},
 * {@code Column_SHOPDB.SALES.CUSTOMER.NAME}. A
 * reference then serializes as
 * {@code href="source.xmi#Column_SHOPDB.SALES.CUSTOMER.NAME"} instead of
 * {@code href="source.xmi#//@ownedElement.0/@ownedElement.0/@feature.1"}.</p>
 *
 * <p>Path segments come from {@code core::ModelElement.name}; unnamed elements fall
 * back to their containing reference name (plus an index inside many-valued
 * references with more than one child), an unnamed root to the name of its class
 * in lower case, the same in every default locale; a {@code TaggedValue} uses its
 * tag. Ids are sanitized to XML-NCName-friendly characters and de-duplicated with
 * a numeric suffix per resource.</p>
 */
public final class SpeakingIds {

    private SpeakingIds() {
    }

    /** Assigns ids to the root(s) and every contained object of {@code resource}. */
    public static void assign(Resource resource) {
        if (!(resource instanceof XMIResource xmi)) {
            return;
        }
        Set<String> used = new HashSet<>();
        for (EObject root : resource.getContents()) {
            xmi.setID(root, unique(idFor(root), used));
            for (Iterator<EObject> it = root.eAllContents(); it.hasNext();) {
                EObject next = it.next();
                xmi.setID(next, unique(idFor(next), used));
            }
        }
    }

    private static String idFor(EObject object) {
        StringBuilder path = new StringBuilder();
        buildPath(object, path);
        return sanitize(object.eClass().getName() + "_" + path);
    }

    private static void buildPath(EObject object, StringBuilder path) {
        EObject container = object.eContainer();
        if (container != null) {
            buildPath(container, path);
            path.append('.');
        }
        path.append(segment(object));
    }

    private static String segment(EObject object) {
        if (object instanceof TaggedValue tagged && tagged.getTag() != null) {
            return tagged.getTag();
        }
        if (object instanceof ModelElement element && element.getName() != null
                && !element.getName().isBlank()) {
            return element.getName();
        }
        EObject container = object.eContainer();
        if (container == null || object.eContainingFeature() == null) {
            return object.eClass().getName().toLowerCase(Locale.ROOT);
        }
        String feature = object.eContainingFeature().getName();
        Object value = container.eGet(object.eContainingFeature());
        if (value instanceof java.util.List<?> list && list.size() > 1) {
            return feature + (list.indexOf(object) + 1);
        }
        return feature;
    }

    private static String sanitize(String id) {
        return transliterate(id).replaceAll("[^A-Za-z0-9_.\\-]", "_");
    }

    /** German umlauts and the sharp s keep their spelling instead of collapsing to {@code _}. */
    private static String transliterate(String id) {
        return id.replace("\u00e4", "ae").replace("\u00f6", "oe").replace("\u00fc", "ue")
                .replace("\u00c4", "Ae").replace("\u00d6", "Oe").replace("\u00dc", "Ue")
                .replace("\u00df", "ss");
    }

    private static String unique(String id, Set<String> used) {
        String candidate = id;
        int n = 2;
        while (!used.add(candidate)) {
            candidate = id + "_" + n++;
        }
        return candidate;
    }
}
