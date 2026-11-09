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
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ChangeableKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.OrderingKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ScopeKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AggregationKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypedElement;

/**
 * The structural phase of a conversion: attributes become EAttributes or containment
 * EReferences, binary associations one EReference per navigable end, multiplicity, ordering,
 * changeability and scope carry over.
 */
final class FeatureConversion {

    private final ConversionState s;

    FeatureConversion(ConversionState s) {
        this.s = s;
    }

    void attribute(Attribute a, EClass owner) {
        EClassifier type = s.classifierOf(a.getType());
        if (type == null) {
            s.notes.add(Note.error("TYPE", CwmAnnotations.pathOf(a), "type "
                    + (a.getType() == null ? "(none)" : CwmAnnotations.pathOf(a.getType()))
                    + " is not among the converted classifiers" + s.unselected(a.getType())));
            return;
        }
        EStructuralFeature f;
        if (type instanceof EClass) {
            // a class-typed attribute holds its value by value: a part of the owner, no opposite
            EReference r = ConversionState.F.createEReference();
            r.setContainment(true);
            r.setResolveProxies(false);
            f = r;
        } else {
            f = ConversionState.F.createEAttribute();
        }
        f.setName(s.name(a));
        f.setEType(type);
        typed(f, a);
        derived(f, a);
        initialValue(f, a);
        owner.getEStructuralFeatures().add(f);
        s.record(a, f);
    }

    /**
     * The initial value of an attribute becomes the {@code defaultValueLiteral} of its EAttribute.
     * Ecore holds a literal of the attribute's type, so only a body without language is taken; an
     * expression in a language, or an initial value of a class-typed attribute, is reported.
     */
    private void initialValue(EStructuralFeature f, Attribute a) {
        Expression init = a.getInitialValue();
        if (init == null || init.getBody() == null || init.getBody().isBlank()) {
            return;
        }
        String language = init.getLanguage();
        if (f instanceof EAttribute && (language == null || language.isBlank())) {
            f.setDefaultValueLiteral(init.getBody().strip());
        } else {
            s.notes.add(Note.warning("DEFAULT", CwmAnnotations.pathOf(a), "initial value '" + init.getBody().strip()
                    + "' is not converted: "
                    + (f instanceof EAttribute ? "an expression in " + language + " is no literal"
                            : "a class-typed attribute has no default value in Ecore")));
        }
    }

    /**
     * A feature tagged {@code derived} (the UML standard tag) is computed, not stored: derived,
     * volatile, transient and unchangeable in Ecore. A constraint on it becomes its derivation
     * ({@link BehaviourConversion#constraint}).
     */
    private static void derived(EStructuralFeature f, StructuralFeature source) {
        if ("true".equalsIgnoreCase(ConversionState.tag(source, CwmAnnotations.TAG_DERIVED))) {
            f.setDerived(true);
            f.setVolatile(true);
            f.setTransient(true);
            f.setChangeable(false);
        }
    }

    void association(Association as) {
        List<AssociationEnd> ends = new ArrayList<>();
        List<Feature> other = new ArrayList<>();
        for (Feature f : as.getFeature()) {
            if (f instanceof AssociationEnd end) {
                ends.add(end);
            } else {
                other.add(f);
            }
        }
        boolean touches = ends.stream().anyMatch(e -> s.trace.get(e.getType()) != null);
        if (!touches) {
            return;
        }
        String path = CwmAnnotations.pathOf(as);
        if (ends.size() != 2) {
            s.notes.add(Note.error("N_ARY", path, "association with " + ends.size()
                    + " ends; only binary associations are converted"));
            return;
        }
        if (!other.isEmpty()) {
            s.notes.add(Note.error("ASSOCIATION_CLASS", path, "association owns " + other.size()
                    + " features besides its ends; association classes are not converted"));
            return;
        }
        AssociationEnd a = ends.get(0);
        AssociationEnd b = ends.get(1);
        EClass ta = endClass(a, path);
        EClass tb = endClass(b, path);
        if (ta == null || tb == null || !aggregationValid(a, b, path)) {
            return;
        }
        // the reference for an end lives at the class of the other end and points to the type of
        // the end; it exists iff one can navigate towards that end and the other end's class is
        // one of the converted ones
        EReference ra = a.isIsNavigable() && owned(tb, a, path) ? reference(a, b, tb, ta) : null;
        EReference rb = b.isIsNavigable() && owned(ta, b, path) ? reference(b, a, ta, tb) : null;
        if (ra != null && rb != null) {
            ra.setEOpposite(rb);
            rb.setEOpposite(ra);
        } else if (ra != null) {
            oppositeRoleName(ra, b);
        } else if (rb != null) {
            oppositeRoleName(rb, a);
        }
        for (EReference r : new EReference[] { ra, rb }) {
            if (r != null) {
                CwmAnnotations.put(r, CwmAnnotations.ASSOCIATION, "name", as.getName());
                CwmAnnotations.put(r, CwmAnnotations.ASSOCIATION, "endA", a.getName());
                CwmAnnotations.put(r, CwmAnnotations.ASSOCIATION, "endB", b.getName());
            }
        }
        if (ra == null && rb == null) {
            CwmAnnotations.put(ta, CwmAnnotations.ASSOCIATION, as.getName(), a.getName() + "," + b.getName());
            s.notes.add(Note.info("NOT_NAVIGABLE", path, "no end is navigable; only an annotation on "
                    + ta.getName() + " records the association"));
        }
    }

    /**
     * Whether a reference towards {@code end} can live on {@code owner}: not on a classifier of
     * another metamodel, which the conversion does not change.
     */
    private boolean owned(EClass owner, AssociationEnd end, String path) {
        if (s.created.contains(owner)) {
            return true;
        }
        s.notes.add(Note.warning("EXTERNAL", path + "." + end.getName(), "no reference on "
                + owner.getName() + ", a classifier of another metamodel: the end stays hidden"));
        return false;
    }

    /**
     * At most one end of an association is an aggregate or a composite (OMG CWM 1.1, C-5-3), and a
     * part belongs to at most one composite (C-5-8): the composite end has an upper bound of 1. In
     * Ecore the reference towards a composite end is the container reference, which EMF only
     * allows single-valued.
     */
    private boolean aggregationValid(AssociationEnd a, AssociationEnd b, String path) {
        if (a.getAggregation() != AggregationKind.AK_NONE && b.getAggregation() != AggregationKind.AK_NONE) {
            s.notes.add(Note.error("AGGREGATION", path, "both ends are aggregates or composites"));
            return false;
        }
        for (AssociationEnd whole : List.of(a, b)) {
            if (whole.getAggregation() == AggregationKind.AK_COMPOSITE && upperOf(whole) != 1) {
                s.notes.add(Note.error("AGGREGATION", path + "." + whole.getName(), "composite end with upper bound "
                        + (upperOf(whole) < 0 ? "*" : String.valueOf(upperOf(whole)))
                        + ": a part belongs to at most one whole"));
                return false;
            }
        }
        return true;
    }

    /** The upper bound of the first multiplicity range; 1 when no multiplicity is given. */
    private static long upperOf(StructuralFeature f) {
        Multiplicity m = f.getMultiplicity();
        return m == null || m.getRange().isEmpty() ? 1 : m.getRange().get(0).getUpper();
    }

    /**
     * The name of the end one cannot navigate to, recorded the EMOF way on the one reference that
     * exists: Ecore then knows the opposite role (e.g. for OCL) without a feature for it.
     */
    private static void oppositeRoleName(EReference r, AssociationEnd hidden) {
        if (hidden.getName() != null) {
            CwmAnnotations.put(r, CwmAnnotations.EMOF, CwmAnnotations.KEY_OPPOSITE_ROLE_NAME, hidden.getName());
        }
    }

    private EClass endClass(AssociationEnd end, String path) {
        EClass c = s.eClassOf(end.getType());
        if (c == null) {
            s.notes.add(Note.error("UNRESOLVED", path + "." + end.getName(), "end type "
                    + (end.getType() == null ? "(none)" : CwmAnnotations.pathOf(end.getType()))
                    + " is not among the converted classes" + s.unselected(end.getType())));
        }
        return c;
    }

    /**
     * The EReference of a navigable end: it lives at the class of the other end and points to the
     * type of this end, with the multiplicity of this end (how many instances at this end belong to
     * one at the other). Aggregation describes the classifier at the end that carries it (OMG CWM
     * 1.1, 4.5.1: "the end is a composite; therefore, the other end is a part"), so a composite
     * other end makes this reference — from the whole to its parts — a containment.
     */
    private EReference reference(AssociationEnd end, AssociationEnd other, EClass owner, EClass type) {
        EReference r = ConversionState.F.createEReference();
        r.setName(s.name(end));
        r.setEType(type);
        typed(r, end);
        derived(r, end);
        if (other.getAggregation() == AggregationKind.AK_COMPOSITE) {
            r.setContainment(true);
            r.setResolveProxies(false);
        }
        if (other.getAggregation() == AggregationKind.AK_AGGREGATE) {
            CwmAnnotations.put(r, CwmAnnotations.AGGREGATION, "kind", "shared");
        }
        owner.getEStructuralFeatures().add(r);
        s.record(end, r);
        return r;
    }

    /**
     * Bounds, order and changeability of a feature. The first multiplicity range gives the bounds,
     * further ranges go into an annotation; a missing multiplicity keeps Ecore's 0..1. Only
     * many-valued features carry an order ({@code ok_ordered}); {@code unique} stays Ecore's
     * {@code true}. {@code ck_frozen} makes the feature unchangeable, {@code ck_addOnly} has no
     * Ecore counterpart and is annotated. Classifier-level scope (static) does not exist in Ecore:
     * annotated and reported.
     */
    private void typed(ETypedElement t, StructuralFeature f) {
        if (f.getChangeability() == ChangeableKind.CK_ADD_ONLY) {
            CwmAnnotations.put(t, CwmAnnotations.CHANGEABILITY, "value", f.getChangeability().getLiteral());
        }
        if (f.getOwnerScope() == ScopeKind.SK_CLASSIFIER || f.getTargetScope() == ScopeKind.SK_CLASSIFIER) {
            CwmAnnotations.put(t, CwmAnnotations.SCOPE, "ownerScope", f.getOwnerScope().getLiteral());
            CwmAnnotations.put(t, CwmAnnotations.SCOPE, "targetScope", f.getTargetScope().getLiteral());
            s.notes.add(Note.warning("SCOPE", CwmAnnotations.pathOf(f),
                    "classifier scope has no Ecore counterpart; the feature is an instance feature"));
        }
        Multiplicity m = f.getMultiplicity();
        if (m != null && !m.getRange().isEmpty()) {
            MultiplicityRange first = m.getRange().get(0);
            t.setLowerBound((int) first.getLower());
            t.setUpperBound(upper(first, f));
            if (m.getRange().size() > 1) {
                List<String> extra = new ArrayList<>();
                for (MultiplicityRange r : m.getRange().subList(1, m.getRange().size())) {
                    extra.add(r.getLower() + ".." + (r.getUpper() < 0 ? "*" : String.valueOf(r.getUpper())));
                }
                CwmAnnotations.put(t, CwmAnnotations.MULTIPLICITY_RANGES, "extra", String.join(",", extra));
            }
        }
        if (t.isMany()) {
            t.setOrdered(f.getOrdering() == OrderingKind.OK_ORDERED);
        }
        if (t instanceof EStructuralFeature sf) {
            sf.setChangeable(f.getChangeability() != ChangeableKind.CK_FROZEN);
        }
    }

    private int upper(MultiplicityRange r, StructuralFeature f) {
        if (r.getUpper() < 0) {
            return ETypedElement.UNBOUNDED_MULTIPLICITY;
        }
        if (r.getUpper() == 0) {
            s.notes.add(Note.warning("MULTIPLICITY", CwmAnnotations.pathOf(f), "upper bound 0 read as "
                    + Math.max(1, r.getLower())));
            return (int) Math.max(1, r.getLower());
        }
        return (int) r.getUpper();
    }
}
