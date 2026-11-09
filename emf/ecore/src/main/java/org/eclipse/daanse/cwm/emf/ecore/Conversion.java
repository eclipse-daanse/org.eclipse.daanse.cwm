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
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.TypeAlias;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Method;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.VisibilityKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsPackage;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * One conversion run, in phases: packages and classifiers, generalizations, attributes and
 * associations ({@link FeatureConversion}), operations, methods and constraints
 * ({@link BehaviourConversion}), annotations, validation. An Ecore element can only refer to
 * another once both exist, so every phase only uses what the earlier phases made.
 */
final class Conversion {

    private static final String KEY_BASE_PACKAGE = "basePackage";
    private static final String KEY_PREFIX = "prefix";

    private final ConversionState s;
    private Set<EObject> usedDataTypes = Set.of();

    Conversion(Options options) {
        this.s = new ConversionState(options);
    }

    Result run(Collection<? extends Package> sources) {
        List<Package> scope = new ArrayList<>(sources);
        usedDataTypes = usedDataTypes(scope);
        for (String key : s.options.ignoredGenModelAnnotations()) {
            s.notes.add(Note.warning("GENMODEL", key, "not taken from the GenModel annotations: "
                    + (key.equals(KEY_PREFIX) ? "the prefix is derived per package"
                            : "the base package has a setting of its own")));
        }

        // phase 1: packages and classifiers, no content yet
        for (Package p : scope) {
            select(p, null, false);
        }
        dropEmpty(s.roots);

        // phase 2: inheritance
        for (Generalization g : ConversionState.all(scope, Generalization.class,
                RelationshipsPackage.Literals.GENERALIZATION)) {
            generalization(g);
        }

        // phase 3: attributes, then associations
        FeatureConversion features = new FeatureConversion(s);
        for (var c : s.classes) {
            EClass owner = s.eClassOf(c);
            for (Feature f : c.getFeature()) {
                if (f.eClass() == CorePackage.Literals.ATTRIBUTE) {
                    features.attribute((Attribute) f, owner);
                } else if (f.eClass() != BehavioralPackage.Literals.OPERATION
                        && f.eClass() != BehavioralPackage.Literals.METHOD) {
                    s.notes.add(Note.warning("FEATURE", CwmAnnotations.pathOf(f),
                            "feature of kind " + f.eClass().getName() + " is not converted"));
                }
            }
        }
        for (Association a : ConversionState.all(scope, Association.class, RelationshipsPackage.Literals.ASSOCIATION)) {
            features.association(a);
        }

        // phase 4: behaviour — the operations of classes and interfaces, then the methods of the
        // classes, which realize their own or inherited operations
        BehaviourConversion behaviour = new BehaviourConversion(s);
        List<Classifier> declaring = new ArrayList<>(s.classes);
        declaring.addAll(s.interfaces);
        for (Classifier c : declaring) {
            for (Feature f : c.getFeature()) {
                if (f.eClass() == BehavioralPackage.Literals.OPERATION) {
                    behaviour.operation((Operation) f, s.eClassOf(c));
                }
            }
        }
        for (var c : s.classes) {
            for (Feature f : c.getFeature()) {
                if (f.eClass() == BehavioralPackage.Literals.METHOD) {
                    behaviour.method((Method) f, s.eClassOf(c));
                }
            }
        }

        // phase 5: constraints
        if (s.options.constraints()) {
            for (Iterator<EObject> it = ConversionState.contents(scope); it.hasNext();) {
                if (it.next() instanceof Constraint c) {
                    behaviour.constraint(c);
                }
            }
        }

        // phase 6: trace, documentation, stereotypes, tagged values, visibility
        Map<EObject, String> docs = s.options.documentation() ? documentation(scope) : Map.of();
        Map<EObject, List<String>> stereotypes = stereotypes(scope);
        s.trace.forEach((source, target) -> {
            if (s.created.contains(target)) {
                annotate(source, target, docs.get(source), stereotypes.get(source));
            }
        });

        // phase 7: validation
        if (s.options.validate()) {
            for (EPackage p : s.roots) {
                collect(Diagnostician.INSTANCE.validate(p));
            }
        }
        return new Result(List.copyOf(s.roots), Collections.unmodifiableMap(s.trace), List.copyOf(s.notes));
    }

    /**
     * Phase 1 selection. Without a {@code packages} option a package is converted when it holds a
     * class, an interface, an enumeration or a data type the class model uses (data types only
     * record fields or columns use stay out); a package nested in a converted one is converted
     * when it holds any of these; a package named in the option is converted when anything
     * convertible lies below it.
     */
    private void select(Package p, EPackage parent, boolean inside) {
        boolean explicit = !s.options.packages().isEmpty() && matches(p);
        boolean free = s.options.packages().isEmpty();
        EPackage ep = null;
        if (inside || explicit ? containsClassifiers(p) : free && hasClassModelContent(p)) {
            if (s.options.splitPackages() && parent != null) {
                ep = ePackage(p, null);
                String around = EcoreUtil.getAnnotation(parent, CwmAnnotations.PACKAGE,
                        CwmAnnotations.KEY_SUPER_PACKAGE);
                CwmAnnotations.put(ep, CwmAnnotations.PACKAGE, CwmAnnotations.KEY_SUPER_PACKAGE,
                        around == null ? parent.getName() : around + "." + parent.getName());
            } else {
                ep = ePackage(p, parent);
            }
            genModelAnnotations(ep, parent == null || s.options.splitPackages());
            if (!p.getImportedElement().isEmpty()) {
                s.notes.add(Note.info("IMPORT", CwmAnnotations.pathOf(p), "imports "
                        + String.join(", ", p.getImportedElement().stream().map(CwmAnnotations::pathOf).toList())
                        + "; imports have no Ecore form, each element is converted where it is owned"));
            }
        }
        for (ModelElement o : p.getOwnedElement()) {
            if (isPackage(o)) {
                select((Package) o, ep, ep != null);
            }
        }
    }

    /**
     * The GenModel properties of an EPackage, read by EMF code generation: {@code prefix}, the
     * capitalized package name, on every EPackage; on a root EPackage the {@code basePackage} of
     * the options, followed by the converted packages around it when the package was taken out of
     * them, and the further properties of the options.
     */
    private void genModelAnnotations(EPackage ep, boolean root) {
        String name = ep.getName();
        CwmAnnotations.put(ep, ConversionState.GENMODEL, KEY_PREFIX,
                Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (!root) {
            return;
        }
        String base = s.options.basePackage();
        String around = EcoreUtil.getAnnotation(ep, CwmAnnotations.PACKAGE, CwmAnnotations.KEY_SUPER_PACKAGE);
        if (around != null) {
            base = base == null ? around : base + "." + around;
        }
        CwmAnnotations.put(ep, ConversionState.GENMODEL, KEY_BASE_PACKAGE, base);
        s.options.genModelAnnotations().forEach((key, value) -> CwmAnnotations.put(ep, ConversionState.GENMODEL, key, value));
    }

    private boolean matches(Package p) {
        String path = CwmAnnotations.pathOf(p);
        for (String wanted : s.options.packages()) {
            if (path.equals(wanted) || path.endsWith("." + wanted)) {
                return true;
            }
        }
        return false;
    }

    private EPackage ePackage(Package p, EPackage parent) {
        EPackage ep = ConversionState.F.createEPackage();
        String name = s.name(p);
        ep.setName(name);
        ep.setNsPrefix(name.replace('$', '_'));
        ep.setNsURI(nsUri(p));
        if (parent == null) {
            s.roots.add(ep);
        } else {
            parent.getESubpackages().add(ep);
        }
        s.record(p, ep);
        for (ModelElement o : p.getOwnedElement()) {
            classifier(o, ep);
        }
        return ep;
    }

    /** The EClassifier of a class, interface, enumeration, data type or type alias, added to {@code ep}. */
    private void classifier(ModelElement o, EPackage ep) {
        EClass type = o.eClass();
        String registered = ConversionState.tag(o, CwmAnnotations.TAG_E_CLASSIFIER);
        if (registered != null && isConvertible(o)) {
            external(o, registered);
        } else if (type == CorePackage.Literals.CLASS || type == BehavioralPackage.Literals.INTERFACE) {
            EClass ec = ConversionState.F.createEClass();
            ec.setName(s.name(o));
            ec.setAbstract(((Classifier) o).isIsAbstract());
            if (type == BehavioralPackage.Literals.INTERFACE) {
                ec.setInterface(true);
                ec.setAbstract(true);
                s.interfaces.add((Classifier) o);
            } else {
                s.classes.add((org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class) o);
            }
            ep.getEClassifiers().add(ec);
            s.record(o, ec);
        } else if (type == DatatypesPackage.Literals.ENUMERATION) {
            ep.getEClassifiers().add(eEnum((Enumeration) o));
        } else if (type == CorePackage.Literals.DATA_TYPE
                || type == DatatypesPackage.Literals.TYPE_ALIAS && aliasConvertible((TypeAlias) o)) {
            EDataType dt = eDataType((DataType) o);
            if (dt != null) {
                ep.getEClassifiers().add(dt);
            }
        }
    }

    /**
     * A classifier that stands for one of another metamodel ({@code daanse.ecore.eClassifier}):
     * the registered EClassifier takes its place wherever it is referred to — as super type, type
     * of an attribute, association end or parameter. It is not converted itself, so its features
     * are not either.
     */
    private void external(ModelElement o, String uri) {
        String path = CwmAnnotations.pathOf(o);
        EClassifier found = ConversionState.registered(uri);
        if (found == null) {
            s.notes.add(Note.error("TYPE", path, "stands for " + uri + ", which is not registered: register its"
                    + " EPackage before the conversion"));
            return;
        }
        s.trace.put(o, found);
        if (o instanceof Classifier c && !c.getFeature().isEmpty()) {
            s.notes.add(Note.warning("EXTERNAL", path, "stands for " + uri + "; its own features are not converted"));
        }
    }

    /**
     * Whether a type alias becomes an EDataType: an alias of a data type does, with the Java class
     * of the type it aliases; an alias of a class or an enumeration has no Ecore form, and an alias
     * that leads back to itself is invalid (C-4-1).
     */
    private boolean aliasConvertible(TypeAlias alias) {
        Set<ModelElement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Classifier target = alias;
        while (target instanceof TypeAlias a) {
            if (!seen.add(a)) {
                s.notes.add(Note.error("TYPE_ALIAS", CwmAnnotations.pathOf(alias), "the alias leads back to itself (C-4-1)"));
                return false;
            }
            target = a.getType();
        }
        if (!(target instanceof DataType) || target instanceof Enumeration) {
            s.notes.add(Note.warning("TYPE_ALIAS", CwmAnnotations.pathOf(alias), "alias of "
                    + (target == null ? "nothing" : CwmAnnotations.pathOf(target))
                    + " is not converted: only an alias of a data type becomes an EDataType"));
            return false;
        }
        return true;
    }

    /**
     * An EEnum: the value of a literal is the integer in its value expression, else its position;
     * a value already taken moves on to the next free one. Ecore has no ordered enumeration, so
     * {@code isOrdered} is annotated.
     */
    private EEnum eEnum(Enumeration en) {
        EEnum ee = ConversionState.F.createEEnum();
        ee.setName(s.name(en));
        if (en.isIsOrdered()) {
            CwmAnnotations.put(ee, CwmAnnotations.ENUMERATION, "isOrdered", "true");
        }
        int position = 0;
        Set<Integer> used = new LinkedHashSet<>();
        for (EnumerationLiteral lit : en.getLiteral()) {
            EEnumLiteral el = ConversionState.F.createEEnumLiteral();
            el.setName(s.name(lit));
            el.setLiteral(lit.getName());
            Integer value = intValue(lit);
            if (value != null && used.contains(value)) {
                s.notes.add(Note.warning("ENUM_VALUE", CwmAnnotations.pathOf(lit),
                        "value " + value + " is taken, the next free value from the position on is used"));
                value = null;
            }
            if (value == null) {
                value = position;
                while (used.contains(value)) {
                    value++;
                }
            }
            used.add(value);
            el.setValue(value);
            ee.getELiterals().add(el);
            s.record(lit, el);
            position++;
        }
        s.record(en, ee);
        return ee;
    }

    private static Integer intValue(EnumerationLiteral lit) {
        if (lit.getValue() == null || lit.getValue().getBody() == null) {
            return null;
        }
        try {
            return Integer.valueOf(lit.getValue().getBody().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A new EDataType, or {@code null} when the Ecore data type is reused. */
    private EDataType eDataType(DataType dt) {
        String instanceClass = s.instanceClassName(dt);
        if (s.options.reuseEcoreDataTypes()) {
            EDataType ecore = ConversionState.ecoreDataType(instanceClass);
            if (ecore != null) {
                s.trace.put(dt, ecore);
                return null;
            }
            s.notes.add(Note.info("TYPE", CwmAnnotations.pathOf(dt), "no Ecore data type for "
                    + instanceClass + ", an own EDataType is generated"));
        }
        EDataType edt = ConversionState.F.createEDataType();
        edt.setName(s.name(dt));
        if (instanceClass != null && instanceClass.contains("<")) {
            // a parameterized Java type: Ecore keeps the full type name, its erasure is the class
            edt.setInstanceTypeName(instanceClass);
        } else if (instanceClass != null) {
            edt.setInstanceClassName(instanceClass);
        } else {
            s.notes.add(Note.error("TYPE", CwmAnnotations.pathOf(dt), "data type '" + dt.getName()
                    + "' has no Java instance class: add it to the dataTypes option or tag it "
                    + CwmAnnotations.TAG_INSTANCE_CLASS_NAME));
        }
        s.record(dt, edt);
        return edt;
    }

    /** Drops packages that ended up without classifiers and sub-packages (reused data types). */
    private static void dropEmpty(List<EPackage> packages) {
        for (Iterator<EPackage> it = packages.iterator(); it.hasNext();) {
            EPackage p = it.next();
            dropEmpty(p.getESubpackages());
            if (p.getEClassifiers().isEmpty() && p.getESubpackages().isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * A generalization adds its parent to the child's {@code eSuperTypes}, in the order the
     * generalizations appear. A parent that already inherits from the child would close a cycle,
     * which Ecore forbids: it is reported and left out.
     */
    private void generalization(Generalization g) {
        EClass child = s.eClassOf(g.getChild());
        if (child == null || g.getParent() == null) {
            return;
        }
        if (!s.created.contains(child)) {
            s.notes.add(Note.warning("EXTERNAL", CwmAnnotations.pathOf(g), child.getName()
                    + " is a classifier of another metamodel and gets no super type"));
            return;
        }
        EClass parent = s.eClassOf(g.getParent());
        if (parent == null) {
            s.notes.add(Note.error("UNRESOLVED", CwmAnnotations.pathOf(g), "parent "
                    + CwmAnnotations.pathOf(g.getParent()) + " is not among the converted classes"
                    + s.unselected(g.getParent())));
        } else if (parent == child || parent.getEAllSuperTypes().contains(child)) {
            s.notes.add(Note.error("CYCLE", CwmAnnotations.pathOf(g), "generalization would make "
                    + child.getName() + " its own super type"));
        } else if (!child.getESuperTypes().contains(parent)) {
            child.getESuperTypes().add(parent);
        }
    }

    private void annotate(ModelElement source, EModelElement target, String doc, List<String> stereotypes) {
        CwmAnnotations.put(target, CwmAnnotations.ELEMENT, CwmAnnotations.KEY_ID, CwmAnnotations.idOf(source));
        CwmAnnotations.put(target, CwmAnnotations.ELEMENT, CwmAnnotations.KEY_PATH, CwmAnnotations.pathOf(source));
        if (doc != null) {
            EcoreUtil.setDocumentation(target, doc);
        }
        if (stereotypes != null) {
            CwmAnnotations.put(target, CwmAnnotations.STEREOTYPE, "name", String.join(",", stereotypes));
        }
        for (TaggedValue tv : source.getTaggedValue()) {
            if (tv.getTag() != null && !tv.getTag().startsWith(CwmAnnotations.CONTROL_TAG_PREFIX)) {
                CwmAnnotations.put(target, CwmAnnotations.TAGGED_VALUE, tv.getTag(),
                        tv.getValue() == null ? "" : tv.getValue());
            }
        }
        VisibilityKind v = source.getVisibility();
        if (v != VisibilityKind.VK_PUBLIC && v != VisibilityKind.VK_NOTAPPLICABLE) {
            CwmAnnotations.put(target, CwmAnnotations.VISIBILITY, "value", v.getLiteral());
        }
    }

    /**
     * The documentation per described element: the bodies of the {@code Description}s of type
     * {@code remark} pointing at it, else those of type {@code documentation}.
     */
    private static Map<EObject, String> documentation(List<Package> scope) {
        Map<EObject, List<String>> remarks = new IdentityHashMap<>();
        Map<EObject, List<String>> docs = new IdentityHashMap<>();
        for (Iterator<EObject> it = ConversionState.contents(scope); it.hasNext();) {
            if (it.next() instanceof Description d && d.getBody() != null && d.getType() != null) {
                String type = d.getType().toLowerCase(Locale.ROOT);
                Map<EObject, List<String>> target = "remark".equals(type) ? remarks
                        : "documentation".equals(type) ? docs : null;
                if (target != null) {
                    for (ModelElement me : d.getModelElement()) {
                        target.computeIfAbsent(me, k -> new ArrayList<>()).add(d.getBody());
                    }
                }
            }
        }
        Map<EObject, String> out = new IdentityHashMap<>();
        docs.forEach((k, v) -> out.put(k, String.join("\n\n", v)));
        remarks.forEach((k, v) -> out.put(k, String.join("\n\n", v)));
        return out;
    }

    /** The names of the stereotypes that extend each element, in the order the stereotypes appear. */
    private static Map<EObject, List<String>> stereotypes(List<Package> scope) {
        Map<EObject, List<String>> out = new IdentityHashMap<>();
        for (Iterator<EObject> it = ConversionState.contents(scope); it.hasNext();) {
            if (it.next() instanceof Stereotype st && st.getName() != null) {
                for (ModelElement e : st.getExtendedElement()) {
                    out.computeIfAbsent(e, k -> new ArrayList<>()).add(st.getName());
                }
            }
        }
        return out;
    }

    private void collect(Diagnostic d) {
        if (d.getChildren().isEmpty()) {
            if (d.getSeverity() == Diagnostic.ERROR) {
                String where = !d.getData().isEmpty() && d.getData().get(0) instanceof EModelElement e
                        && CwmAnnotations.path(e) != null ? CwmAnnotations.path(e) : "";
                s.notes.add(Note.error("ECORE", where, d.getMessage()));
            }
            return;
        }
        for (Diagnostic child : d.getChildren()) {
            collect(child);
        }
    }

    private String nsUri(Package p) {
        List<String> segments = new ArrayList<>();
        for (EObject o = p; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                segments.add(0, URI.encodeSegment(me.getName().trim().toLowerCase(Locale.ROOT).replace(' ', '-'),
                        false));
            }
        }
        String base = s.options.nsUriBase();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + String.join("/", segments);
    }

    private static boolean isPackage(EObject o) {
        return o.eClass() == CorePackage.Literals.PACKAGE || o.eClass() == CorePackage.Literals.MODEL
                || o.eClass() == CorePackage.Literals.SUBSYSTEM;
    }

    /** A data type or a type alias: part of the class model only when classes use it. */
    private static boolean isDataType(EObject o) {
        return o.eClass() == CorePackage.Literals.DATA_TYPE || o.eClass() == DatatypesPackage.Literals.TYPE_ALIAS;
    }

    private static boolean isConvertible(EObject o) {
        return o.eClass() == CorePackage.Literals.CLASS || isDataType(o)
                || o.eClass() == DatatypesPackage.Literals.ENUMERATION
                || o.eClass() == BehavioralPackage.Literals.INTERFACE;
    }

    private boolean hasClassModelContent(Package p) {
        for (ModelElement o : p.getOwnedElement()) {
            if (isDataType(o) ? usedDataTypes.contains(o) : isConvertible(o)) {
                return true;
            }
        }
        return false;
    }

    /** The data types that attributes and operation parameters of classes below the sources use. */
    private static Set<EObject> usedDataTypes(List<Package> scope) {
        Set<EObject> used = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Classifier> owners = new ArrayList<>();
        owners.addAll(ConversionState.all(scope, Classifier.class, CorePackage.Literals.CLASS));
        owners.addAll(ConversionState.all(scope, Classifier.class, BehavioralPackage.Literals.INTERFACE));
        for (Classifier c : owners) {
            for (Feature f : c.getFeature()) {
                if (f instanceof Attribute a && a.getType() != null) {
                    used.add(a.getType());
                } else if (f instanceof Operation op) {
                    for (Parameter p : op.getParameter()) {
                        if (p.getType() != null) {
                            used.add(p.getType());
                        }
                    }
                }
            }
        }
        return used;
    }

    private static boolean containsClassifiers(Package p) {
        for (ModelElement o : p.getOwnedElement()) {
            if (isConvertible(o) || isPackage(o) && containsClassifiers((Package) o)) {
                return true;
            }
        }
        return false;
    }
}
