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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * Assigns short {@code xmi:id}s: an underscore, then the names from the element up to the package
 * that holds it, e.g. {@code _UplinkMessage.deviceInfo} for the attribute {@code deviceInfo} of the
 * class {@code UplinkMessage}, {@code _battery-sensor.battery.level} for a feature of a class that
 * a class owns. The underscore tells an id from a name at a glance; the kind of the element is left
 * out, the attribute that refers to it says it.
 *
 * <p>A package and an element a package owns are named by their own name. Where two of them would
 * get the same id, each takes the names of the packages around it, innermost first, one at a time,
 * until they differ ({@code _dragino.DecodedObject}, {@code _em310udl.DecodedObject}); everything
 * inside an element starts with the id of that element. Elements with the same name in the same
 * place keep apart by a number ({@code _T}, {@code _T-2}).</p>
 *
 * <p>An unnamed element takes the name of the feature that holds it (with its position when the
 * feature holds several), a tagged value its tag. Names are read reflectively, so objects of a
 * dynamic copy of the CWM metamodels get ids as well. Ids keep to the characters of an XML name,
 * like those of {@link SpeakingIds}.</p>
 */
public final class ShortIds {

    private static final String CORE = "http://www.omg.org/spec/CWM/1.1/objectmodel/core";

    private ShortIds() {
    }

    /** Assigns ids to the root(s) and every contained object of {@code resource}. */
    public static void assign(Resource resource) {
        if (!(resource instanceof XMIResource xmi)) {
            return;
        }
        List<EObject> all = new ArrayList<>();
        for (EObject root : resource.getContents()) {
            all.add(root);
            root.eAllContents().forEachRemaining(all::add);
        }
        of(all).forEach(xmi::setID);
    }

    /** The ids of {@code objects}, each container listed before what it contains. */
    public static Map<EObject, String> of(List<EObject> objects) {
        Set<EObject> inScope = new HashSet<>(objects);
        // the elements a package owns (and the roots) are named apart from each other first
        Map<List<String>, Candidate> chosen = new LinkedHashMap<>();
        for (EObject o : objects) {
            if (isTop(o, inScope)) {
                List<String> path = fullPath(o, inScope);
                chosen.computeIfAbsent(path, Candidate::new);
            }
        }
        widen(chosen.values());

        // everything inside starts with the id of its container; what still agrees gets a number
        Map<EObject, String> ids = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (EObject o : objects) {
            String base = isTop(o, inScope) ? chosen.get(fullPath(o, inScope)).id()
                    : ids.get(o.eContainer()) + "." + segment(o);
            String id = base;
            for (int n = 2; !used.add(id); n++) {
                id = base + "-" + n;
            }
            ids.put(o, id);
        }
        return ids;
    }

    /** A root, or an element a package owns: named on its own. */
    private static boolean isTop(EObject o, Set<EObject> inScope) {
        EObject container = o.eContainer();
        return container == null || !inScope.contains(container)
                || isPackage(container) && "ownedElement".equals(o.eContainingFeature().getName());
    }

    /** The names from the root down to {@code o}, the root first. */
    private static List<String> fullPath(EObject o, Set<EObject> inScope) {
        List<String> path = new ArrayList<>();
        for (EObject c = o; c != null && inScope.contains(c); c = c.eContainer()) {
            path.add(0, segment(c));
        }
        return path;
    }

    /** Lengthens colliding ids by one enclosing name at a time until no two agree. */
    private static void widen(Collection<Candidate> candidates) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, List<Candidate>> byId = new HashMap<>();
            for (Candidate c : candidates) {
                byId.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(c);
            }
            for (List<Candidate> same : byId.values()) {
                if (same.size() > 1) {
                    for (Candidate c : same) {
                        changed |= c.widen();
                    }
                }
            }
        }
    }

    private static String segment(EObject o) {
        String name = string(o, "name");
        if (name == null) {
            name = string(o, "tag");
        }
        if (name == null) {
            EStructuralFeature feature = o.eContainingFeature();
            if (o.eContainer() == null || feature == null) {
                name = o.eClass().getName().toLowerCase();
            } else {
                name = feature.getName();
                if (o.eContainer().eGet(feature) instanceof List<?> list && list.size() > 1) {
                    name += list.indexOf(o) + 1;
                }
            }
        }
        return sanitize(name);
    }

    private static String string(EObject o, String feature) {
        EStructuralFeature f = o.eClass().getEStructuralFeature(feature);
        return f != null && o.eGet(f) instanceof String s && !s.isBlank() ? s : null;
    }

    /** Whether {@code o} is a CWM package (a model, a subsystem, a catalog, a schema ...). */
    private static boolean isPackage(EObject o) {
        EClass eClass = o.eClass();
        if (isCorePackage(eClass)) {
            return true;
        }
        for (EClass superType : eClass.getEAllSuperTypes()) {
            if (isCorePackage(superType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCorePackage(EClass eClass) {
        return "Package".equals(eClass.getName()) && eClass.getEPackage() != null
                && CORE.equals(eClass.getEPackage().getNsURI());
    }

    private static String sanitize(String segment) {
        return segment.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
                .replace("ß", "ss").replaceAll("[^A-Za-z0-9_.\\-]", "_");
    }

    /** The full path of an element and how many of its enclosing names its id shows. */
    private static final class Candidate {

        private final List<String> path;
        private int shown = 1;

        Candidate(List<String> path) {
            this.path = path;
        }

        boolean widen() {
            if (shown >= path.size()) {
                return false;
            }
            shown++;
            return true;
        }

        String id() {
            return "_" + String.join(".", path.subList(path.size() - shown, path.size()));
        }
    }
}
