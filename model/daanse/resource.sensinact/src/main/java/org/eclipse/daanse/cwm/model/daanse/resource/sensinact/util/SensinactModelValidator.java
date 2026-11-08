/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;

/**
 * Checks the structure of sensiNact providers: what becomes an id in the twin is unique and plain,
 * every resource has a type.
 *
 * <ul>
 * <li>{@code P1} names — services unique in a provider, resources unique in a service.</li>
 * <li>{@code P2} types — every resource is typed.</li>
 * <li>{@code P3} (warning) ids — the names of providers, services and resources are plain ids:
 * letters, digits, {@code _}, {@code .} and {@code -}.</li>
 * </ul>
 */
public final class SensinactModelValidator {

    /** Severity of a finding; only {@link #ERROR} makes a provider invalid. */
    public enum Severity {
        ERROR, WARNING
    }

    /** One finding: severity, rule code, the element it is about, message. */
    public record Finding(Severity severity, String rule, EObject element, String message) {
        @Override
        public String toString() {
            return severity + " " + rule + ": " + message;
        }
    }

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]+");

    private SensinactModelValidator() {
    }

    /** All findings for the providers in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        if (root instanceof Provider p) {
            structure(p, findings);
        }
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            if (it.next() instanceof Provider p) {
                structure(p, findings);
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void structure(Provider provider, List<Finding> findings) {
        Set<String> services = new HashSet<>();
        for (Service s : SensinactStructure.services(provider)) {
            if (!services.add(s.getName())) {
                findings.add(error("P1", s, "provider " + provider.getName() + " has two services named " + s.getName()));
            }
            id(s, findings);
            Set<String> resources = new HashSet<>();
            for (Resource r : SensinactStructure.resources(s)) {
                if (!resources.add(r.getName())) {
                    findings.add(error("P1", r, "service " + path(s) + " has two resources named " + r.getName()));
                }
                if (r.getType() == null) {
                    findings.add(error("P2", r, "resource " + path(r) + " has no type"));
                }
                id(r, findings);
            }
        }
        id(provider, findings);
    }

    private static void id(ModelElement e, List<Finding> findings) {
        if (e.getName() != null && !ID.matcher(e.getName()).matches()) {
            findings.add(warning("P3", e, path(e) + ": the name '" + e.getName()
                    + "' becomes an id in the twin; letters, digits, '_', '.' and '-' are safe"));
        }
    }

    private static Finding error(String rule, EObject element, String message) {
        return new Finding(Severity.ERROR, rule, element, message + ".");
    }

    private static Finding warning(String rule, EObject element, String message) {
        return new Finding(Severity.WARNING, rule, element, message + ".");
    }

    /** The names of an element and its named containers, dot separated. */
    static String path(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
