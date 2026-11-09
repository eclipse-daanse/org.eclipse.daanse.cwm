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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.TypeAlias;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Interface;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Method;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Subsystem;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AggregationKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;

/**
 * Builds CWM class model elements for the tests. The unowned forms make an element alone; the
 * forms with an owner add it to that namespace (or, for features, to that classifier).
 */
final class CwmModels {

    static final CoreFactory CORE = CoreFactory.eINSTANCE;
    static final RelationshipsFactory REL = RelationshipsFactory.eINSTANCE;
    static final BehavioralFactory BEHAVIORAL = BehavioralFactory.eINSTANCE;
    static final DatatypesFactory DATATYPES = DatatypesFactory.eINSTANCE;

    private CwmModels() {
    }

    /** Adds {@code element} to the owned elements of {@code owner} and returns it. */
    static <T extends ModelElement> T in(Namespace owner, T element) {
        owner.getOwnedElement().add(element);
        return element;
    }

    static Model model(String name) {
        Model m = CORE.createModel();
        m.setName(name);
        return m;
    }

    static Package pkg(String name) {
        Package p = CORE.createPackage();
        p.setName(name);
        return p;
    }

    static Package pkg(Namespace owner, String name) {
        return in(owner, pkg(name));
    }

    static Subsystem subsystem(Namespace owner, String name) {
        Subsystem s = CORE.createSubsystem();
        s.setName(name);
        return in(owner, s);
    }

    static Class cls(String name) {
        Class c = CORE.createClass();
        c.setName(name);
        return c;
    }

    static Class cls(Namespace owner, String name) {
        return in(owner, cls(name));
    }

    static Interface iface(Namespace owner, String name) {
        Interface i = BEHAVIORAL.createInterface();
        i.setName(name);
        return in(owner, i);
    }

    static DataType dataType(String name) {
        DataType d = CORE.createDataType();
        d.setName(name);
        return d;
    }

    static DataType dataType(Namespace owner, String name) {
        return in(owner, dataType(name));
    }

    static TypeAlias alias(Namespace owner, String name, Classifier type) {
        TypeAlias a = DATATYPES.createTypeAlias();
        a.setName(name);
        a.setType(type);
        return in(owner, a);
    }

    static Enumeration enumeration(String name, String... literals) {
        Enumeration e = DATATYPES.createEnumeration();
        e.setName(name);
        for (String l : literals) {
            EnumerationLiteral lit = DATATYPES.createEnumerationLiteral();
            lit.setName(l);
            e.getLiteral().add(lit);
        }
        return e;
    }

    static Enumeration enumeration(Namespace owner, String name, String... literals) {
        return in(owner, enumeration(name, literals));
    }

    static Attribute attribute(String name, Classifier type) {
        Attribute a = CORE.createAttribute();
        a.setName(name);
        a.setType(type);
        return a;
    }

    static Attribute attribute(Classifier owner, String name, Classifier type) {
        Attribute a = attribute(name, type);
        owner.getFeature().add(a);
        return a;
    }

    static Multiplicity multiplicity(long lower, long upper) {
        Multiplicity m = CORE.createMultiplicity();
        m.getRange().add(range(lower, upper));
        return m;
    }

    static MultiplicityRange range(long lower, long upper) {
        MultiplicityRange r = CORE.createMultiplicityRange();
        r.setLower(lower);
        r.setUpper(upper);
        return r;
    }

    /** Sets the multiplicity of a feature, {@code -1} as upper bound for unbounded. */
    static <T extends StructuralFeature> T bounds(T f, long lower, long upper) {
        f.setMultiplicity(multiplicity(lower, upper));
        return f;
    }

    static TaggedValue tag(String tag, String value) {
        TaggedValue t = CORE.createTaggedValue();
        t.setTag(tag);
        t.setValue(value);
        return t;
    }

    /** Adds a tagged value to {@code element} and returns the element. */
    static <T extends ModelElement> T tagged(T element, String tag, String value) {
        element.getTaggedValue().add(tag(tag, value));
        return element;
    }

    /** An expression without language: a literal. */
    static Expression literal(String body) {
        Expression e = CORE.createExpression();
        e.setBody(body);
        return e;
    }

    static Expression expression(String body) {
        return expression(body, "Java");
    }

    static Expression expression(String body, String language) {
        Expression e = CORE.createExpression();
        e.setBody(body);
        e.setLanguage(language);
        return e;
    }

    static Generalization generalization(Classifier child, Classifier parent) {
        Generalization g = REL.createGeneralization();
        g.setName(child.getName() + " is a " + parent.getName());
        g.setChild(child);
        g.setParent(parent);
        return g;
    }

    static Generalization generalization(Namespace owner, Classifier child, Classifier parent) {
        return in(owner, generalization(child, parent));
    }

    static AssociationEnd end(String name, Classifier type, boolean navigable, long lower, long upper) {
        AssociationEnd e = REL.createAssociationEnd();
        e.setName(name);
        e.setType(type);
        e.setIsNavigable(navigable);
        e.setMultiplicity(multiplicity(lower, upper));
        return e;
    }

    /** An end whose classifier is the whole of the association (or shares its parts). */
    static AssociationEnd whole(AssociationEnd end, AggregationKind kind) {
        end.setAggregation(kind);
        return end;
    }

    static Association association(String name, StructuralFeature... ends) {
        Association a = REL.createAssociation();
        a.setName(name);
        a.getFeature().addAll(List.of(ends));
        return a;
    }

    static Association association(Namespace owner, String name, StructuralFeature... ends) {
        return in(owner, association(name, ends));
    }

    static Parameter parameter(String name, Classifier type, ParameterDirectionKind kind) {
        Parameter p = BEHAVIORAL.createParameter();
        p.setName(name);
        p.setType(type);
        p.setKind(kind);
        return p;
    }

    /** An operation; with a type, its {@code pdk_return} parameter, then the parameters given. */
    static Operation operation(String name, Classifier returnType, Parameter... parameters) {
        Operation op = BEHAVIORAL.createOperation();
        op.setName(name);
        if (returnType != null) {
            op.getParameter().add(parameter("return", returnType, ParameterDirectionKind.PDK_RETURN));
        }
        op.getParameter().addAll(List.of(parameters));
        return op;
    }

    static Operation operation(Classifier owner, String name, Classifier returnType, Parameter... parameters) {
        Operation op = operation(name, returnType, parameters);
        owner.getFeature().add(op);
        return op;
    }

    static Method method(String name, Operation specification, String language, String body) {
        Method m = BEHAVIORAL.createMethod();
        m.setName(name);
        m.setSpecification(specification);
        ProcedureExpression e = CORE.createProcedureExpression();
        e.setLanguage(language);
        e.setBody(body);
        m.setBody(e);
        return m;
    }

    static Method method(Classifier owner, String name, Operation specification, String language, String body) {
        Method m = method(name, specification, language, body);
        owner.getFeature().add(m);
        return m;
    }

    static Constraint constraint(String name, String language, String body, ModelElement... constrained) {
        Constraint c = CORE.createConstraint();
        c.setName(name);
        BooleanExpression e = CORE.createBooleanExpression();
        e.setLanguage(language);
        e.setBody(body);
        c.setBody(e);
        c.getConstrainedElement().addAll(List.of(constrained));
        return c;
    }

    static Constraint constraint(Namespace owner, String name, String language, String body,
            ModelElement... constrained) {
        return in(owner, constraint(name, language, body, constrained));
    }

    static Description description(String body, String type, ModelElement about) {
        Description d = BusinessinformationFactory.eINSTANCE.createDescription();
        d.setName("Doc " + about.getName());
        d.setBody(body);
        d.setType(type);
        d.getModelElement().add(about);
        return d;
    }

    static Description description(Namespace owner, String name, String body, String type, ModelElement about) {
        Description d = description(body, type, about);
        d.setName(name);
        return in(owner, d);
    }

    static Stereotype stereotype(Namespace owner, String name, ModelElement... extended) {
        Stereotype s = CORE.createStereotype();
        s.setName(name);
        s.getExtendedElement().addAll(List.of(extended));
        return in(owner, s);
    }
}
