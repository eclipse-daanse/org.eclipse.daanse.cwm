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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Method;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EGenericType;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * The behavioural phase of a conversion: operations and parameters become EOperations and
 * EParameters, methods their bodies, constraints invariants. OCL goes under the OCL delegate
 * source, Java into the GenModel {@code body} that the EMF generator writes into the generated
 * code.
 */
final class BehaviourConversion {

    private final ConversionState s;
    private final Map<EClass, Map<EOperation, EOperation>> overrides = new IdentityHashMap<>();

    BehaviourConversion(ConversionState s) {
        this.s = s;
    }

    /**
     * An operation and its parameters: the {@code pdk_return} parameter gives the type of the
     * EOperation, every other parameter becomes an EParameter with its direction annotated. What
     * Ecore has no place for is annotated: an abstract or a query operation, the default value of
     * a parameter.
     */
    void operation(Operation o, EClass owner) {
        EOperation op = ConversionState.F.createEOperation();
        op.setName(s.name(o));
        if (o.isIsAbstract()) {
            CwmAnnotations.put(op, CwmAnnotations.OPERATION, "isAbstract", "true");
        }
        if (o.isIsQuery()) {
            CwmAnnotations.put(op, CwmAnnotations.OPERATION, "isQuery", "true");
        }
        for (Parameter p : o.getParameter()) {
            EClassifier type = s.classifierOf(p.getType());
            if (p.getType() != null && type == null) {
                s.notes.add(Note.error("TYPE", CwmAnnotations.pathOf(p), "parameter type "
                        + CwmAnnotations.pathOf(p.getType()) + " is not among the converted classifiers"
                        + s.unselected(p.getType())));
            }
            if (p.getKind() == ParameterDirectionKind.PDK_RETURN) {
                op.setEType(type);
            } else {
                EParameter ep = ConversionState.F.createEParameter();
                ep.setName(s.name(p));
                ep.setEType(type);
                CwmAnnotations.put(ep, CwmAnnotations.PARAMETER, "kind", p.getKind().getLiteral());
                Expression defaultValue = p.getDefaultValue();
                if (defaultValue != null && defaultValue.getBody() != null && !defaultValue.getBody().isBlank()) {
                    CwmAnnotations.put(ep, CwmAnnotations.PARAMETER, "default", defaultValue.getBody().strip());
                }
                op.getEParameters().add(ep);
                s.record(p, ep);
            }
        }
        owner.getEOperations().add(op);
        s.record(o, op);
    }

    /**
     * A CWM {@code Method} is the implementation of an {@code Operation} of its class, possibly an
     * inherited one (C-4-10). Its body goes onto the EOperation of that operation, or, when the
     * operation is inherited, onto an EOperation of the method's class that overrides it (same
     * name and parameter types, so {@code EClass.getOverride} finds it). An {@code OCL} body
     * becomes the {@code body} of the OCL delegate, evaluated by the invocation delegate; a
     * {@code Java} body becomes the GenModel {@code body}, which the EMF generator writes into the
     * generated method and prefers over the delegate. Other languages have no Ecore form.
     */
    void method(Method m, EClass owner) {
        String path = CwmAnnotations.pathOf(m);
        Operation spec = m.getSpecification();
        if (spec == null || !(s.trace.get(spec) instanceof EOperation op)) {
            s.notes.add(Note.error("METHOD", path, spec == null ? "method without the operation it realizes"
                    : "realizes " + CwmAnnotations.pathOf(spec) + ", which is not among the converted operations"));
            return;
        }
        Expression body = m.getBody();
        String language = body == null ? null : body.getLanguage();
        String text = body == null ? null : body.getBody();
        String source;
        String kind;
        if ("OCL".equalsIgnoreCase(language)) {
            source = s.options.oclDelegateUri();
            kind = "OCL";
        } else if ("Java".equalsIgnoreCase(language)) {
            source = ConversionState.GENMODEL;
            kind = "Java";
        } else {
            s.notes.add(Note.warning("METHOD_LANGUAGE", path, "method in language '" + language
                    + "' is not converted; only OCL and Java bodies have an Ecore form"));
            return;
        }
        if (text == null || text.isBlank()) {
            s.notes.add(Note.warning("METHOD", path, "method without a body"));
            return;
        }
        if (kind.equals("OCL") && op.getEType() == null) {
            s.notes.add(Note.warning("METHOD", path, "OCL body of an operation without pdk_return parameter is"
                    + " not converted: an OCL expression yields a value and changes nothing"));
            return;
        }
        EOperation target = op.getEContainingClass() == owner ? op : override(op, spec, owner, m, path);
        if (target == null) {
            return;
        }
        if (EcoreUtil.getAnnotation(target, source, ConversionState.BODY) != null) {
            s.notes.add(Note.error("METHOD", path, "a second " + kind + " method for "
                    + CwmAnnotations.pathOf(spec) + " in the same class (C-4-12)"));
            return;
        }
        if (target.getEAnnotation(CwmAnnotations.METHOD) != null) {
            s.notes.add(Note.warning("METHOD", path, "a second method for " + CwmAnnotations.pathOf(spec)
                    + " in the same class (C-4-12); both bodies are kept, the generated Java code uses"
                    + " the Java one"));
        }
        EcoreUtil.setAnnotation(target, source, ConversionState.BODY, text);
        CwmAnnotations.put(target, CwmAnnotations.METHOD, kind, CwmAnnotations.idOf(m));
        if (kind.equals("OCL")) {
            addDelegate(target.getEContainingClass().getEPackage(), Delegate.INVOCATION);
        }
    }

    /**
     * The EOperation of {@code owner} that overrides the inherited {@code op}; made on first use
     * from the method {@code m}, which it is traced to. Null, with an error, when {@code op} is no
     * operation of an ancestor or the class declares an overriding operation of its own (then the
     * method has to realize that one, C-4-11).
     */
    private EOperation override(EOperation op, Operation spec, EClass owner, Method m, String path) {
        if (!owner.getEAllSuperTypes().contains(op.getEContainingClass())) {
            s.notes.add(Note.error("METHOD", path, "realizes " + CwmAnnotations.pathOf(spec)
                    + ", which is no operation of its class or of an ancestor (C-4-10)"));
            return null;
        }
        Map<EOperation, EOperation> made = overrides.computeIfAbsent(owner, k -> new IdentityHashMap<>());
        if (made.containsKey(op)) {
            return made.get(op);
        }
        for (EOperation own : owner.getEOperations()) {
            if (own.isOverrideOf(op)) {
                s.notes.add(Note.error("METHOD", path, "realizes " + CwmAnnotations.pathOf(spec) + " although its class"
                        + " declares the overriding operation " + own.getName() + " (C-4-11)"));
                return null;
            }
        }
        EOperation o = ConversionState.F.createEOperation();
        o.setName(op.getName());
        o.setEType(op.getEType());
        o.setLowerBound(op.getLowerBound());
        o.setUpperBound(op.getUpperBound());
        for (EParameter p : op.getEParameters()) {
            EParameter c = ConversionState.F.createEParameter();
            c.setName(p.getName());
            c.setEType(p.getEType());
            c.setLowerBound(p.getLowerBound());
            c.setUpperBound(p.getUpperBound());
            String kind = EcoreUtil.getAnnotation(p, CwmAnnotations.PARAMETER, "kind");
            if (kind != null) {
                CwmAnnotations.put(c, CwmAnnotations.PARAMETER, "kind", kind);
            }
            o.getEParameters().add(c);
        }
        owner.getEOperations().add(o);
        s.record(m, o);
        made.put(op, o);
        return o;
    }

    /**
     * A CWM {@code Constraint} in the language {@code OCL} becomes an invariant of the one
     * classifier it constrains: its name in the Ecore {@code constraints} of the classifier, its
     * body under the OCL delegate, evaluated by the validation delegate. One in {@code Java} becomes
     * an invariant operation of the class (see {@link #javaInvariant}). A constraint attached to an
     * attribute or a navigable association end constrains the instances of the class that holds
     * that feature, which is then the context ({@code self}). A constraint of a stereotype applies
     * to every element the stereotype extends. Constraints on elements outside the converted class
     * model (tables, columns, packages of other models) are left alone.
     */
    void constraint(Constraint c) {
        // a constraint of a stereotype constrains each extended element on its own
        List<List<ModelElement>> groups = new ArrayList<>();
        if (c.eContainer() instanceof Stereotype st) {
            st.getExtendedElement().forEach(e -> groups.add(List.of(e)));
        } else {
            groups.add(c.getConstrainedElement());
        }
        List<Set<EClassifier>> contexts = new ArrayList<>();
        List<List<String>> elsewhere = new ArrayList<>();
        boolean concerned = false;
        for (List<ModelElement> group : groups) {
            Set<EClassifier> cx = new LinkedHashSet<>();
            List<String> other = new ArrayList<>();
            for (ModelElement e : group) {
                EModelElement t = s.trace.get(e);
                if (t instanceof EClassifier ec) {
                    cx.add(ec);
                } else if (t instanceof EStructuralFeature f) {
                    cx.add(f.getEContainingClass());
                } else if (t != null || touchesClassModel(e)) {
                    other.add(CwmAnnotations.pathOf(e));
                }
            }
            contexts.add(cx);
            elsewhere.add(other);
            concerned |= !cx.isEmpty() || !other.isEmpty();
        }
        if (!concerned) {
            return;
        }
        String path = CwmAnnotations.pathOf(c);
        Expression body = c.getBody();
        String language = body == null ? null : body.getLanguage();
        boolean java = "Java".equalsIgnoreCase(language);
        if (!java && !"OCL".equalsIgnoreCase(language)) {
            s.notes.add(Note.warning("CONSTRAINT_LANGUAGE", path, "constraint in language '" + language
                    + "' is not converted; OCL and Java constraints become Ecore invariants"));
            return;
        }
        if (body.getBody() == null || body.getBody().isBlank()) {
            s.notes.add(Note.warning("CONSTRAINT", path, "constraint without a body"));
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).size() == 1 && s.trace.get(groups.get(i).get(0)) instanceof EStructuralFeature f
                    && f.isDerived() && s.created.contains(f)) {
                derivation(f, c, java, body.getBody());
                continue;
            }
            if (!elsewhere.get(i).isEmpty()) {
                s.notes.add(Note.warning("CONSTRAINT", path, "constrains " + String.join(", ", elsewhere.get(i))
                        + ", which has no place for an invariant in Ecore (only classifiers and the"
                        + " features of classes have)"));
            }
            Set<EClassifier> cx = contexts.get(i);
            if (cx.size() > 1) {
                s.notes.add(Note.warning("CONSTRAINT", path, "constrains several classifiers ("
                        + String.join(", ", cx.stream().map(EClassifier::getName).toList())
                        + "); an OCL invariant has exactly one context, the constraint is not converted"));
            } else if (cx.size() == 1 && java) {
                javaInvariant(cx.iterator().next(), c, body.getBody());
            } else if (cx.size() == 1) {
                invariant(cx.iterator().next(), c, body.getBody());
            }
        }
    }

    /**
     * The derivation of a derived feature: a Java constraint becomes the GenModel {@code get}, the
     * body of the generated getter; an OCL one the {@code derivation} under the OCL delegate source,
     * evaluated by the setting delegate the package then declares.
     */
    private void derivation(EStructuralFeature f, Constraint c, boolean java, String body) {
        String source = java ? ConversionState.GENMODEL : s.options.oclDelegateUri();
        String key = java ? "get" : "derivation";
        String path = CwmAnnotations.pathOf(c);
        if (EcoreUtil.getAnnotation(f, source, key) != null) {
            s.notes.add(Note.warning("CONSTRAINT", path, "a second " + (java ? "Java" : "OCL") + " derivation of "
                    + f.getEContainingClass().getName() + "." + f.getName() + " is not converted"));
            return;
        }
        EcoreUtil.setAnnotation(f, source, key, body);
        CwmAnnotations.put(f, CwmAnnotations.CONSTRAINT, c.getName() == null ? "derivation" : c.getName(),
                CwmAnnotations.idOf(c));
        if (!java) {
            addDelegate(f.getEContainingClass().getEPackage(), Delegate.SETTING);
        }
    }

    private void invariant(EClassifier context, Constraint c, String body) {
        String name = invariantName(context, c);
        List<String> names = new ArrayList<>(EcoreUtil.getConstraints(context));
        names.add(name);
        EcoreUtil.setConstraints(context, names);
        EcoreUtil.setAnnotation(context, s.options.oclDelegateUri(), name, body);
        CwmAnnotations.put(context, CwmAnnotations.CONSTRAINT, name, CwmAnnotations.idOf(c));
        addDelegate(context.getEPackage(), Delegate.VALIDATION);
    }

    /**
     * A constraint in Java becomes what EMF calls an invariant operation of the class: an
     * EOperation returning {@code boolean} with the parameters {@code DiagnosticChain diagnostics}
     * and {@code Map<Object, Object> context}, whose GenModel {@code body} tests the boolean Java
     * expression of the constraint on {@code this} and reports a violation as an error diagnostic.
     * The validator EMF generates calls it like any other invariant; on dynamic instances it is not
     * evaluated, Java needs the generated code. A data type has no operations, so a Java constraint
     * on one has no Ecore form.
     */
    private void javaInvariant(EClassifier context, Constraint c, String expression) {
        if (!(context instanceof EClass owner)) {
            s.notes.add(Note.warning("CONSTRAINT", CwmAnnotations.pathOf(c), "Java constraint on the data type "
                    + context.getName() + " is not converted: a Java invariant is an operation of a class"));
            return;
        }
        String name = invariantName(owner, c);
        EOperation op = ConversionState.F.createEOperation();
        op.setName(name);
        op.setEType(EcorePackage.Literals.EBOOLEAN);
        EParameter diagnostics = ConversionState.F.createEParameter();
        diagnostics.setName("diagnostics");
        diagnostics.setEType(EcorePackage.Literals.EDIAGNOSTIC_CHAIN);
        EParameter map = ConversionState.F.createEParameter();
        map.setName("context");
        EGenericType type = ConversionState.F.createEGenericType();
        type.setEClassifier(EcorePackage.Literals.EMAP);
        for (int i = 0; i < 2; i++) {
            EGenericType argument = ConversionState.F.createEGenericType();
            argument.setEClassifier(EcorePackage.Literals.EJAVA_OBJECT);
            type.getETypeArguments().add(argument);
        }
        map.setEGenericType(type);
        op.getEParameters().add(diagnostics);
        op.getEParameters().add(map);
        EcoreUtil.setAnnotation(op, ConversionState.GENMODEL, ConversionState.BODY, javaInvariantBody(name, expression));
        owner.getEOperations().add(op);
        CwmAnnotations.put(owner, CwmAnnotations.CONSTRAINT, name, CwmAnnotations.idOf(c));
        if (s.trace.containsKey(c)) {
            // a stereotype constraint on a further class: traced by annotation only
            s.created.add(op);
            CwmAnnotations.put(op, CwmAnnotations.ELEMENT, CwmAnnotations.KEY_ID, CwmAnnotations.idOf(c));
            CwmAnnotations.put(op, CwmAnnotations.ELEMENT, CwmAnnotations.KEY_PATH, CwmAnnotations.pathOf(c));
        } else {
            s.record(c, op);
        }
    }

    static String javaInvariantBody(String name, String expression) {
        return "if (!(" + expression.strip() + ")) {\n"
                + "\tif (diagnostics != null) {\n"
                + "\t\tdiagnostics.add(new org.eclipse.emf.common.util.BasicDiagnostic(\n"
                + "\t\t\t\torg.eclipse.emf.common.util.Diagnostic.ERROR, eClass().getEPackage().getNsURI(), 0,\n"
                + "\t\t\t\t\"The '" + name + "' constraint is violated on '\"\n"
                + "\t\t\t\t\t\t+ org.eclipse.emf.ecore.util.EObjectValidator.getObjectLabel(this, context) + \"'\",\n"
                + "\t\t\t\tnew Object[] { this }));\n"
                + "\t}\n"
                + "\treturn false;\n"
                + "}\n"
                + "return true;";
    }

    /**
     * The name of an invariant made from {@code c} on {@code context}: the constraint's name, with
     * a suffix when an invariant or an operation of that name is already there (the generated
     * validator has one method per invariant).
     */
    private String invariantName(EClassifier context, Constraint c) {
        Set<String> taken = new HashSet<>(EcoreUtil.getConstraints(context));
        if (context instanceof EClass ec) {
            ec.getEAllOperations().forEach(o -> taken.add(o.getName()));
        }
        String name = s.name(c);
        if (taken.contains(name)) {
            String base = name;
            for (int i = 2; taken.contains(name); i++) {
                name = base + "_" + i;
            }
            s.notes.add(Note.warning("NAME", CwmAnnotations.pathOf(c), "a second invariant or operation '" + base
                    + "' on " + context.getName() + ", '" + name + "' is used"));
        }
        return name;
    }

    /**
     * Elements of the class model without an Ecore element of their own: a non-navigable end of a
     * converted association, the association itself, a parameter of a converted operation.
     */
    private boolean touchesClassModel(ModelElement e) {
        if (e instanceof AssociationEnd end) {
            return s.eClassOf(end.getType()) != null;
        }
        if (e instanceof Association as) {
            for (Feature f : as.getFeature()) {
                if (f instanceof AssociationEnd end && s.eClassOf(end.getType()) != null) {
                    return true;
                }
            }
            return false;
        }
        return e instanceof Parameter p && p.getBehavioralFeature() != null
                && s.trace.containsKey(p.getBehavioralFeature());
    }

    /** The OCL delegates a package declares. */
    private enum Delegate {
        VALIDATION, INVOCATION, SETTING
    }

    /**
     * Declares the OCL delegate on the package: as validation delegate for invariants, as
     * invocation delegate for operation bodies, as setting delegate for derivations. EMF looks the
     * delegates up on the package of the classifier, so every package with OCL declares them
     * itself.
     */
    private void addDelegate(EPackage p, Delegate kind) {
        String uri = s.options.oclDelegateUri();
        List<String> delegates = new ArrayList<>(switch (kind) {
        case VALIDATION -> EcoreUtil.getValidationDelegates(p);
        case INVOCATION -> EcoreUtil.getInvocationDelegates(p);
        case SETTING -> EcoreUtil.getSettingDelegates(p);
        });
        if (!delegates.contains(uri)) {
            delegates.add(uri);
            switch (kind) {
            case VALIDATION -> EcoreUtil.setValidationDelegates(p, delegates);
            case INVOCATION -> EcoreUtil.setInvocationDelegates(p, delegates);
            case SETTING -> EcoreUtil.setSettingDelegates(p, delegates);
            }
        }
    }
}
