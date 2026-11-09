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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.TypeAlias;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * What the phases of one conversion share: the options, the Ecore elements made so far and the
 * CWM elements they come from, the notes, and the rules every phase applies alike (names, types,
 * the hint for a type outside the converted packages).
 */
final class ConversionState {

    static final EcoreFactory F = EcoreFactory.eINSTANCE;
    static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    static final String BODY = "body";

    final Options options;
    final Map<String, String> dataTypes;
    final List<EPackage> roots = new ArrayList<>();
    final Map<ModelElement, EModelElement> trace = new LinkedHashMap<>();
    final Set<EModelElement> created = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class> classes = new ArrayList<>();
    final List<Classifier> interfaces = new ArrayList<>();
    final List<Note> notes = new ArrayList<>();

    ConversionState(Options options) {
        this.options = options;
        this.dataTypes = options.dataTypes();
    }

    void record(ModelElement source, EModelElement target) {
        trace.put(source, target);
        created.add(target);
        controlTags(source, target);
    }

    /**
     * Applies the control tags {@code daanse.ecore.<property>} of an element to the Ecore element
     * made from it, after the mapping, so a tag wins over what the mapping derived: the tag names an
     * Ecore attribute of the target ({@code transient}, {@code serializable},
     * {@code instanceTypeName}, …), the value is read as that attribute's type. {@code id} stands
     * for {@code iD}. The name is not settable: other tools find Ecore elements by the CWM name.
     */
    private void controlTags(ModelElement source, EModelElement target) {
        for (TaggedValue tv : source.getTaggedValue()) {
            String tag = tv.getTag();
            if (tag == null || !tag.startsWith(CwmAnnotations.CONTROL_TAG_PREFIX)
                    || tag.equals(CwmAnnotations.TAG_E_CLASSIFIER)
                    || tag.equals(CwmAnnotations.TAG_INSTANCE_CLASS_NAME) && target instanceof EDataType) {
                // the mapping reads these itself
                continue;
            }
            String property = tag.substring(CwmAnnotations.CONTROL_TAG_PREFIX.length());
            property = property.equals("id") ? "iD" : property;
            EStructuralFeature f = target.eClass().getEStructuralFeature(property);
            String path = CwmAnnotations.pathOf(source);
            if (!(f instanceof EAttribute a) || !a.isChangeable() || a.isDerived() || property.equals("name")) {
                notes.add(Note.warning("TAG", path, "tag " + tag + ": " + target.eClass().getName()
                        + " has no settable property '" + property + "'"));
                continue;
            }
            try {
                target.eSet(a, EcoreUtil.createFromString(a.getEAttributeType(), tv.getValue()));
            } catch (RuntimeException e) {
                notes.add(Note.warning("TAG", path, "tag " + tag + ": '" + tv.getValue() + "' is no "
                        + a.getEAttributeType().getName()));
            }
        }
    }

    /**
     * The registered EClassifier a URI {@code <nsURI>#//<name>} (or {@code #//<sub>/<name>} for a
     * nested package) names, or {@code null}.
     */
    static EClassifier registered(String uri) {
        int hash = uri == null ? -1 : uri.indexOf('#');
        if (hash < 0 || !uri.startsWith("//", hash + 1)) {
            return null;
        }
        EPackage p = EPackage.Registry.INSTANCE.getEPackage(uri.substring(0, hash));
        String[] path = uri.substring(hash + 3).split("/");
        for (int i = 0; i < path.length - 1 && p != null; i++) {
            String name = path[i];
            p = p.getESubpackages().stream().filter(sp -> name.equals(sp.getName())).findFirst().orElse(null);
        }
        return p == null ? null : p.getEClassifier(path[path.length - 1]);
    }

    /** The Ecore name of an element; a name that is no Java identifier is adjusted and reported. */
    String name(ModelElement me) {
        String original = me.getName();
        String n = javaName(original);
        if (!n.equals(original)) {
            notes.add(Note.warning("NAME", CwmAnnotations.pathOf(me), "name '" + original
                    + "' is no Java identifier, '" + n + "' is used"));
        }
        return n;
    }

    /**
     * A Java identifier for a CWM name: {@code byte[]} becomes {@code byteArray}, other characters
     * that are not allowed are dropped and the next letter is upper-cased, a leading digit gets an
     * underscore. The Fennec EORM converter finds Ecore elements by the same rule.
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

    /** The converted classifier of a CWM type, or the reused Ecore data type; {@code null} if none. */
    EClassifier classifierOf(Classifier type) {
        if (type == null) {
            return null;
        }
        if (trace.get(type) instanceof EClassifier c) {
            return c;
        }
        if (options.reuseEcoreDataTypes() && type instanceof DataType dt && !(type instanceof Enumeration)) {
            return ecoreDataType(instanceClassName(dt));
        }
        return null;
    }

    /**
     * The Java instance class of a data type: the TaggedValue {@code daanse.ecore.instanceClassName},
     * else the {@code dataTypes} option by name, else for a type alias the class of the aliased data
     * type, else the name itself when it is a qualified Java name; {@code null} when none applies.
     */
    String instanceClassName(DataType dt) {
        return instanceClassName(dt, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private String instanceClassName(DataType dt, Set<DataType> seen) {
        String tagged = tag(dt, CwmAnnotations.TAG_INSTANCE_CLASS_NAME);
        if (tagged != null) {
            return tagged;
        }
        String mapped = dataTypes.get(dt.getName());
        if (mapped != null) {
            return mapped;
        }
        if (dt instanceof TypeAlias alias) {
            return seen.add(dt) && alias.getType() instanceof DataType target && !(target instanceof Enumeration)
                    ? instanceClassName(target, seen)
                    : null;
        }
        String n = dt.getName();
        if (n != null && n.contains(".") && n.chars().allMatch(ch -> ch == '.' || Character.isJavaIdentifierPart(ch))) {
            return n;
        }
        return null;
    }

    /** The {@code EcorePackage} data type with the given instance class, or {@code null}. */
    static EDataType ecoreDataType(String instanceClass) {
        if (instanceClass == null) {
            return null;
        }
        for (EClassifier c : EcorePackage.eINSTANCE.getEClassifiers()) {
            if (c instanceof EDataType d && !(c instanceof EEnum) && instanceClass.equals(d.getInstanceClassName())) {
                return d;
            }
        }
        return null;
    }

    /**
     * The hint for a type outside the converted classifiers whose package was not selected: which
     * package to add to {@code packages}, or, for a data type, to reuse the Ecore data types.
     */
    String unselected(ModelElement type) {
        if (type == null || !(type.eContainer() instanceof Package p) || trace.containsKey(p)) {
            return "";
        }
        return "; its package " + CwmAnnotations.pathOf(p) + " is not converted: add it to the packages"
                + (type instanceof DataType && !(type instanceof Enumeration) ? " or reuse the Ecore data types" : "");
    }

    /** The EClass of a converted CWM class, or {@code null}. */
    EClass eClassOf(ModelElement type) {
        return trace.get(type) instanceof EClass c ? c : null;
    }

    /** The value of a tagged value of an element, or {@code null}. */
    static String tag(ModelElement me, String tag) {
        for (TaggedValue tv : me.getTaggedValue()) {
            if (tag.equals(tv.getTag())) {
                return tv.getValue();
            }
        }
        return null;
    }

    /**
     * Everything in the resource set of the sources, else everything below the sources, visited
     * lazily: resource after resource, each in containment order.
     */
    static Iterator<EObject> contents(List<Package> scope) {
        ResourceSet rs = null;
        for (Package p : scope) {
            Resource r = p.eResource();
            if (r != null && r.getResourceSet() != null) {
                rs = r.getResourceSet();
                break;
            }
        }
        List<Iterator<EObject>> parts = new ArrayList<>();
        if (rs != null) {
            for (Resource r : List.copyOf(rs.getResources())) {
                parts.add(r.getAllContents());
            }
        } else {
            for (Package p : scope) {
                EObject root = EcoreUtil.getRootContainer(p);
                parts.add(List.of(root).iterator());
                parts.add(root.eAllContents());
            }
        }
        return new Iterator<>() {
            private int i;

            @Override
            public boolean hasNext() {
                while (i < parts.size() && !parts.get(i).hasNext()) {
                    i++;
                }
                return i < parts.size();
            }

            @Override
            public EObject next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return parts.get(i).next();
            }
        };
    }

    /** All elements of exactly the metaclass {@code type} below the sources, sources included. */
    static <T> List<T> all(List<Package> scope, Class<T> javaType, EClass type) {
        List<T> out = new ArrayList<>();
        Set<EObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Package p : scope) {
            if (p.eClass() == type && seen.add(p)) {
                out.add(javaType.cast(p));
            }
            for (Iterator<EObject> it = p.eAllContents(); it.hasNext();) {
                EObject o = it.next();
                if (o.eClass() == type && seen.add(o)) {
                    out.add(javaType.cast(o));
                }
            }
        }
        return out;
    }
}
