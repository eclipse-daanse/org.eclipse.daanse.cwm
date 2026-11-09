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

import java.util.ArrayDeque;
import java.util.Deque;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * The annotation sources the converter writes and the readers for the trace back to the CWM
 * source. Every CWM fact without an Ecore counterpart has exactly one source below the prefix
 * {@value #BASE}, so a reader finds it in one place.
 */
public final class CwmAnnotations {

    /** Common prefix of all sources. */
    public static final String BASE = "http://daanse.eclipse.org/cwm/";

    /** On every generated element: {@code id} (xmi:id of the CWM source) and {@code path} (qualified name). */
    public static final String ELEMENT = BASE + "Element";

    /** On elements whose CWM visibility is private, protected or package: {@code value}. */
    public static final String VISIBILITY = BASE + "Visibility";

    /** On EReferences made from association ends: {@code name}, {@code endA}, {@code endB}. */
    public static final String ASSOCIATION = BASE + "Association";

    /** On EReferences whose whole end is a shared aggregate: {@code kind=shared}. */
    public static final String AGGREGATION = BASE + "Aggregation";

    /** On elements with CWM tagged values: one detail per tag. */
    public static final String TAGGED_VALUE = BASE + "TaggedValue";

    /** On typed elements whose multiplicity has more than one range: {@code extra}. */
    public static final String MULTIPLICITY_RANGES = BASE + "Multiplicity/Ranges";

    /** On EParameters: {@code kind} (the CWM parameter direction), {@code default} (its default value). */
    public static final String PARAMETER = BASE + "Parameter";

    /** On EOperations whose CWM operation is abstract or a query: {@code isAbstract}, {@code isQuery}. */
    public static final String OPERATION = BASE + "Operation";

    /** On EEnums whose CWM enumeration is ordered: {@code isOrdered}. */
    public static final String ENUMERATION = BASE + "Enumeration";

    /** On elements that stereotypes extend: {@code name}, the stereotype names, comma separated. */
    public static final String STEREOTYPE = BASE + "Stereotype";

    /** On features whose CWM changeability is {@code ck_addOnly}: {@code value}. */
    public static final String CHANGEABILITY = BASE + "Changeability";

    /** On features with classifier scope: {@code ownerScope}, {@code targetScope}. */
    public static final String SCOPE = BASE + "Scope";

    /**
     * On a root EPackage that {@link Options#splitPackages()} took out of the package around it:
     * {@value #KEY_SUPER_PACKAGE}, the dot separated names of the converted packages around it,
     * from which {@link EcoreWriter} derives the Java package of the generated code.
     */
    public static final String PACKAGE = BASE + "Package";

    /** Detail key of {@link #PACKAGE}. */
    public static final String KEY_SUPER_PACKAGE = "superPackage";

    /**
     * On classifiers with invariants made from CWM constraints: one detail per invariant, its name
     * to the id of the CWM {@code Constraint}; on derived features: the constraint that is the
     * derivation.
     */
    public static final String CONSTRAINT = BASE + "Constraint";

    /**
     * On EOperations with a body made from a CWM {@code Method}: one detail per body, the language
     * ({@code OCL} or {@code Java}) to the id of the CWM method.
     */
    public static final String METHOD = BASE + "Method";

    /**
     * The EMOF annotation source. On a reference whose association end at the other side is not
     * navigable, {@link #KEY_OPPOSITE_ROLE_NAME} names that hidden end.
     */
    public static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";

    /** Detail key of {@link #EMOF}: the name of the non-navigable opposite end. */
    public static final String KEY_OPPOSITE_ROLE_NAME = "Property.oppositeRoleName";

    /** Detail key of {@link #ELEMENT}: the xmi:id of the CWM source. */
    public static final String KEY_ID = "id";

    /** Detail key of {@link #ELEMENT}: the qualified name of the CWM source. */
    public static final String KEY_PATH = "path";

    /**
     * Prefix of the tags that steer the conversion and are not copied as annotations. A tag
     * {@code daanse.ecore.<property>} sets the Ecore property of that name on the element made from
     * the tagged one, e.g. {@code daanse.ecore.transient}, {@code daanse.ecore.serializable},
     * {@code daanse.ecore.instanceTypeName}, {@code daanse.ecore.lowerBound}.
     */
    public static final String CONTROL_TAG_PREFIX = "daanse.ecore.";

    /** Tag on a CWM data type: the Java instance class name of its EDataType. */
    public static final String TAG_INSTANCE_CLASS_NAME = "daanse.ecore.instanceClassName";

    /** Tag on a CWM attribute: {@code true} makes its EAttribute the {@code iD}. */
    public static final String TAG_ID = "daanse.ecore.id";

    /**
     * Tag on a CWM classifier that stands for a classifier of another metamodel, as
     * {@code <nsURI>#//<name>} (e.g. {@code http://www.eclipse.org/emf/2002/Ecore#//EObject}): it is
     * not converted, the registered EClassifier is used wherever it is referred to.
     */
    public static final String TAG_E_CLASSIFIER = "daanse.ecore.eClassifier";

    /**
     * The UML standard tag on a structural feature whose value is computed: the feature becomes
     * derived, volatile, transient and unchangeable; a constraint on it is its derivation.
     */
    public static final String TAG_DERIVED = "derived";

    private CwmAnnotations() {
    }

    /** The CWM id recorded on an Ecore element, or {@code null}. */
    public static String id(EModelElement e) {
        return EcoreUtil.getAnnotation(e, ELEMENT, KEY_ID);
    }

    /** The CWM qualified name recorded on an Ecore element, or {@code null}. */
    public static String path(EModelElement e) {
        return EcoreUtil.getAnnotation(e, ELEMENT, KEY_PATH);
    }

    /**
     * The id of a CWM element as the converter records it: its {@code xmi:id}, else its URI fragment
     * within the resource.
     */
    public static String idOf(EObject element) {
        Resource r = element.eResource();
        if (r instanceof XMLResource x) {
            String id = x.getID(element);
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }
        String id = EcoreUtil.getID(element);
        if (id != null && !id.isEmpty()) {
            return id;
        }
        return r != null ? r.getURIFragment(element) : EcoreUtil.getURI(element).fragment();
    }

    /** The qualified name of a CWM element: the names from the model root, dot separated. */
    public static String pathOf(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }

    /** Puts a detail into the annotation {@code source} of {@code e}, creating the annotation. */
    static void put(EModelElement e, String source, String key, String value) {
        if (value == null) {
            return;
        }
        EAnnotation a = e.getEAnnotation(source);
        if (a == null) {
            a = EcoreFactory.eINSTANCE.createEAnnotation();
            a.setSource(source);
            e.getEAnnotations().add(a);
        }
        a.getDetails().put(key, value);
    }
}
