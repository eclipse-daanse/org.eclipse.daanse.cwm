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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Finds the Ecore element of a CWM class model element by names — the names the Ecore model of the
 * class model carries, whoever wrote it. No annotation is read.
 *
 * <ul>
 * <li><b>Package.</b> An entry of the package map, else the EPackage with the package's name; when
 * several EPackages have that name, the one whose super packages match the CWM containers.</li>
 * <li><b>Classifier</b> (class, interface, enumeration, data type). The EClassifier of that name in
 * the EPackage of its package; when the package has no EPackage, the only EClassifier of that name
 * in all packages.</li>
 * <li><b>Attribute.</b> The structural feature of that name of the owner's EClass (inherited ones
 * included).</li>
 * <li><b>Navigable association end.</b> The structural feature named after the end at the EClass of
 * the <em>other</em> end's type (OMG CWM 1.1, 4.5: the end is the role of its type seen from the
 * other end).</li>
 * <li><b>Enumeration literal.</b> The literal of that name of the enumeration's EEnum.</li>
 * </ul>
 *
 * <p>A CWM name that is no Java identifier is matched in the form Ecore requires: characters that
 * are not allowed are dropped and the next letter is upper-cased, {@code byte[]} becomes
 * {@code byteArray}, a leading digit gets an underscore.</p>
 */
public final class EcoreIndex {

    private final List<EPackage> all = new ArrayList<>();
    private final Map<String, String> packageMap;
    private final Map<Package, EPackage> resolved = new IdentityHashMap<>();

    private EcoreIndex(List<EPackage> packages, Map<String, String> packageMap) {
        this.packageMap = packageMap == null ? Map.of() : Map.copyOf(packageMap);
        for (EPackage p : packages) {
            collect(p);
        }
    }

    private void collect(EPackage p) {
        all.add(p);
        p.getESubpackages().forEach(this::collect);
    }

    /** An index over EPackages read from {@code .ecore} files (or made in memory). */
    public static EcoreIndex of(List<EPackage> packages) {
        return new EcoreIndex(packages, Map.of());
    }

    /**
     * An index whose packages map CWM packages explicitly: the key names a CWM package by its
     * qualified name or a trailing part of it ({@code model.shop}, {@code shop}), the value an
     * EPackage by namespace URI, qualified name ({@code shop.enumerations}) or name.
     */
    public static EcoreIndex of(List<EPackage> packages, Map<String, String> packageMap) {
        return new EcoreIndex(packages, packageMap);
    }

    /** The Ecore element made from {@code cwm}, or {@code null}. */
    public EModelElement find(ModelElement cwm) {
        if (cwm instanceof Package p) {
            return ePackage(p);
        }
        if (cwm instanceof AssociationEnd end) {
            AssociationEnd other = otherEnd(end);
            EClass owner = other == null ? null : eClass(other.getType());
            return owner == null ? null : owner.getEStructuralFeature(javaName(end.getName()));
        }
        if (cwm instanceof Attribute a) {
            EClass owner = eClass(a.getOwner());
            return owner == null ? null : owner.getEStructuralFeature(javaName(a.getName()));
        }
        if (cwm instanceof Classifier c) {
            return classifier(c);
        }
        if (cwm instanceof EnumerationLiteral l) {
            EEnum e = eEnum(l.getEnumeration());
            return e == null ? null : e.getEEnumLiteral(javaName(l.getName()));
        }
        return null;
    }

    /** The EClass made from a CWM class, or {@code null}. */
    public EClass eClass(ModelElement cwm) {
        return find(cwm) instanceof EClass c ? c : null;
    }

    /** The EClassifier made from a CWM classifier, or {@code null}. */
    public EClassifier eClassifier(ModelElement cwm) {
        return find(cwm) instanceof EClassifier c ? c : null;
    }

    /** The EEnum made from a CWM enumeration, or {@code null}. */
    public EEnum eEnum(ModelElement cwm) {
        return find(cwm) instanceof EEnum e ? e : null;
    }

    /** The EAttribute or EReference made from a CWM attribute or navigable association end, or {@code null}. */
    public EStructuralFeature feature(ModelElement cwm) {
        return find(cwm) instanceof EStructuralFeature f ? f : null;
    }

    private EClassifier classifier(Classifier c) {
        String name = javaName(c.getName());
        Package owner = packageOf(c);
        EPackage ep = owner == null ? null : ePackage(owner);
        if (ep != null) {
            return ep.getEClassifier(name);
        }
        EClassifier only = null;
        for (EPackage p : all) {
            EClassifier hit = p.getEClassifier(name);
            if (hit != null) {
                if (only != null) {
                    return null;
                }
                only = hit;
            }
        }
        return only;
    }

    private EPackage ePackage(Package p) {
        if (resolved.containsKey(p)) {
            return resolved.get(p);
        }
        EPackage ep = mapped(p);
        if (ep == null) {
            ep = byName(p);
        }
        resolved.put(p, ep);
        return ep;
    }

    private EPackage mapped(Package p) {
        String path = CwmPath.of(p);
        for (Map.Entry<String, String> e : packageMap.entrySet()) {
            if (path.equals(e.getKey()) || path.endsWith("." + e.getKey())) {
                for (EPackage ep : all) {
                    if (e.getValue().equals(ep.getNsURI()) || e.getValue().equals(qualifiedName(ep))
                            || e.getValue().equals(ep.getName())) {
                        return ep;
                    }
                }
            }
        }
        return null;
    }

    /** The EPackage of the package's name; among several, the one whose super packages match best. */
    private EPackage byName(Package p) {
        String name = javaName(p.getName());
        EPackage best = null;
        int bestScore = -1;
        boolean tie = false;
        for (EPackage ep : all) {
            if (!name.equals(ep.getName())) {
                continue;
            }
            int score = 0;
            EObject cwm = p.eContainer();
            for (EPackage q = ep.getESuperPackage(); q != null && cwm instanceof ModelElement me;
                    q = q.getESuperPackage(), cwm = cwm.eContainer()) {
                if (!q.getName().equals(javaName(me.getName()))) {
                    break;
                }
                score++;
            }
            if (score > bestScore) {
                best = ep;
                bestScore = score;
                tie = false;
            } else if (score == bestScore) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private static Package packageOf(ModelElement e) {
        for (EObject o = e.eContainer(); o != null; o = o.eContainer()) {
            if (o instanceof Package p) {
                return p;
            }
        }
        return null;
    }

    private static AssociationEnd otherEnd(AssociationEnd end) {
        if (!(end.getOwner() instanceof Association a)) {
            return null;
        }
        AssociationEnd other = null;
        for (Object f : a.getFeature()) {
            if (f instanceof AssociationEnd e && e != end) {
                if (other != null) {
                    return null;
                }
                other = e;
            }
        }
        return other;
    }

    /**
     * The name a CWM name has in Ecore: {@code byte[]} becomes {@code byteArray}, characters that
     * are no Java identifier part are dropped and the next letter is upper-cased, a leading
     * character that cannot start a Java identifier gets an underscore.
     */
    static String javaName(String name) {
        if (name == null || name.isEmpty()) {
            return "_";
        }
        String n = name.endsWith("[]") ? name.substring(0, name.length() - 2) + "Array" : name;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < n.length();) {
            int cp = n.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isJavaIdentifierPart(cp) && !Character.isIdentifierIgnorable(cp)) {
                sb.appendCodePoint(upper && sb.length() > 0 ? Character.toUpperCase(cp) : cp);
                upper = false;
            } else {
                upper = true;
            }
        }
        if (sb.length() == 0 || !Character.isJavaIdentifierStart(sb.codePointAt(0))) {
            sb.insert(0, '_');
        }
        return sb.toString();
    }

    /**
     * The Java name of a classifier as the EORM strings use it: the instance class name when the
     * classifier has one, else the names of its EPackages and its own name, dot separated
     * (e.g. {@code shop.Customer}).
     */
    public static String qualifiedName(EClassifier c) {
        if (c == null) {
            return null;
        }
        if (c.getInstanceClassName() != null) {
            return c.getInstanceClassName();
        }
        return qualifiedName(c.getEPackage()) + "." + c.getName();
    }

    /** The names of an EPackage and its super packages, dot separated. */
    public static String qualifiedName(EPackage p) {
        Deque<String> names = new ArrayDeque<>();
        for (EPackage q = p; q != null; q = q.getESuperPackage()) {
            names.addFirst(q.getName());
        }
        return String.join(".", names);
    }
}
