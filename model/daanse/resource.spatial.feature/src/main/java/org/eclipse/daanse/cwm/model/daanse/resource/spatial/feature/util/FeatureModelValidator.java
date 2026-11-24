/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeaturePackage;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;

/**
 * Checks feature collections beyond what the metamodel enforces: what becomes an id or a parameter of the API is
 * unique, plain and free; the key, the geometry and the time name properties that can be them; what a property says
 * of its values fits its type; the reference systems fit the geometry. Errors make a collection unusable for a
 * server, warnings do not. A reference system itself is checked by the spatial model's validator (SP34 to SP37).
 *
 * <p>Some rules stand in the metamodel itself, each an OCL invariant of the class it is about named by the code of its
 * rule, with the sentence and the severity of the rule beside it ({@code model/feature.ecore}): a client that loads the
 * metamodel evaluates them without this class. {@link #check} runs the EMF check of the invariants on every element of
 * the package, takes their findings, each naming the element and saying the sentence of its rule, and adds those of the
 * rules no invariant can state, {@link #CHECKED_IN_JAVA}.</p>
 *
 * <ul>
 * <li>{@code FC1} a collection stands in a package and owns only feature properties, each named uniquely and
 * typed; a feature property belongs to a collection.</li>
 * <li>{@code FC2} (warning) the names of collections and properties are plain ids: letters, digits, {@code _},
 * {@code .} and {@code -}.</li>
 * <li>{@code FC3} no property is named like a query parameter of the API ({@code limit}, {@code bbox},
 * {@code datetime} of Part 1; {@code crs}, {@code bbox-crs} of Part 2; {@code filter}, {@code filter-lang},
 * {@code filter-crs} of Part 3; {@code profile} of Part 5; {@code properties}, {@code exclude-properties} of Part 6;
 * {@code zoom-level} of Part 7; {@code sortby} of Part 8; {@code q} of Part 9; and {@code f}, {@code offset}, which
 * servers use): a simple queryable is a parameter too.</li>
 * <li>{@code FC4} the feature key is a property of the collection, never null, a string or an integer (OGC API
 * Features Part 5).</li>
 * <li>{@code FC5} the time properties are properties of the collection; where the primary geometry is held says
 * {@code FC19}.</li>
 * <li>There is no {@code FC6}: the collection names its primary geometry, a geometry feature, itself, where it named a
 * property that one geometry feature had to name.</li>
 * <li>{@code FC7} a property held whole by a geometry feature is of the data type Geometry, and a property of that type
 * is held whole by one. A property that holds a number of a geometry keeps its numeric type.</li>
 * <li>{@code FC8} a geometry is not the key nor a time, and carries no allowed values, bounds or pattern and is not
 * sortable.</li>
 * <li>There is no {@code FC9}: a code keeps its meaning, and the time of a feature has {@code FC18}.</li>
 * <li>{@code FC10} (warning) a time zone is a valid IANA name or offset, and stated only where a time property is a
 * timestamp without a zone.</li>
 * <li>{@code FC11} the minimum is not above the maximum; bounds are decimal numbers on numeric types only; a pattern
 * is a valid regular expression on text only; allowed values are on text, whole or decimal numbers only, and each
 * fits the type.</li>
 * <li>{@code FC12} (warning) allowed values beside an enumeration type: a copy of its literals that can drift.</li>
 * <li>{@code FC13} a reference system of three axes is offered only where the primary geometry holds a height.</li>
 * <li>{@code FC14} (warning) an offered system is the default (CRS84, CRS84h) or the storage system, which the
 * server lists anyway.</li>
 * <li>{@code FC15} a collection that holds a geometry in its properties (whole, or as numbers) names its primary
 * geometry; one without states no reference systems.</li>
 * <li>{@code FC16} (warning) a collection has a queryable property besides its key.</li>
 * <li>{@code FC17} (warning) a reference that does not resolve.</li>
 * <li>{@code FC18} the time of a feature is an instant or an interval (its start, its end or both; a start or an end
 * alone is half-bounded), as OGC API Features Part 5 names the roles, and each time property is of a date or a
 * timestamp type.</li>
 * <li>{@code FC19} at least one holding of the primary geometry lies entirely in properties of the collection: a
 * property of the type Geometry, or the numbers of a point, such as east and north; the server writes the geometry of
 * a feature from it.</li>
 * </ul>
 */
public final class FeatureModelValidator {

    /** The module the rules of this validator belong to. */
    public static final String MODULE = "feature";

    public static final Rule FC1 = Rule.error(MODULE, "FC1",
            "A collection stands in a package and owns only feature properties, each named uniquely and typed; a "
                + "feature property belongs to a collection.");

    public static final Rule FC2 = Rule.warning(MODULE, "FC2",
            "The names of collections and properties are plain ids.");

    public static final Rule FC3 = Rule.error(MODULE, "FC3", "No property is named like a query parameter of the API.");

    public static final Rule FC4 = Rule.error(MODULE, "FC4",
            "The feature key is a property of the collection, never null, a string or an integer.");

    public static final Rule FC5 = Rule.error(MODULE, "FC5", "The time properties are properties of the collection.");

    public static final Rule FC7 = Rule.error(MODULE, "FC7",
            "A property held whole by a geometry feature is of the data type Geometry, and a property of that type is "
                + "held whole by one.");

    public static final Rule FC8 = Rule.error(MODULE, "FC8",
            "A geometry is not the key nor a time, and carries no allowed values, bounds or pattern and is not "
                + "sortable.");

    public static final Rule FC10 = Rule.warning(MODULE, "FC10",
            "A time zone is a valid IANA name or offset, stated only for a timestamp without a zone.");

    public static final Rule FC11 = Rule.error(MODULE, "FC11",
            "Bounds, patterns and allowed values are valid and fit the type of their property.");

    public static final Rule FC12 = Rule.warning(MODULE, "FC12",
            "Allowed values do not copy the literals of an enumeration type.");

    public static final Rule FC13 = Rule.error(MODULE, "FC13",
            "A reference system of three axes is offered only where the primary geometry holds a height.");

    public static final Rule FC14 = Rule.warning(MODULE, "FC14",
            "An offered reference system is neither the default nor the storage system, which the server lists "
                + "anyway.");

    public static final Rule FC15 = Rule.error(MODULE, "FC15",
            "A collection that holds a geometry in its properties names its primary geometry; one without states no "
                + "reference systems.");

    public static final Rule FC16 = Rule.warning(MODULE, "FC16",
            "A collection has a queryable property besides its key.");

    public static final Rule FC17 = Rule.warning(MODULE, "FC17", "A reference resolves.");

    public static final Rule FC18 = Rule.error(MODULE, "FC18",
            "The time of a feature is an instant or an interval, and each time property is of a date or a timestamp "
                + "type.");

    public static final Rule FC19 = Rule.error(MODULE, "FC19",
            "At least one holding of the primary geometry lies entirely in properties of the collection.");

    /** Every rule of this validator, in the order of their codes; there is no FC6 and no FC9. */
    public static final List<Rule> RULES = List.of(FC1, FC2, FC3, FC4, FC5, FC7, FC8, FC10, FC11, FC12, FC13,
            FC14, FC15, FC16, FC17, FC18, FC19);

    /**
     * The rules no OCL invariant of the metamodel states, checked by this class; every other rule of {@link #RULES} is an
     * invariant of the class it is about. FC7, FC8 and FC15 ask which holdings name a property, found from the property
     * back through the whole model. FC4, FC10, FC11 and FC18 judge the type of a property by its family: by the JDBC
     * code of an SQL type, a class of a model the metamodel does not import, and by its name; FC10 asks the time zone
     * database of Java as well. FC12 recognises an enumeration type, a class of {@code datatypes}, which the metamodel
     * does not import. FC17 asks whether a reference resolves, which an invariant cannot see.
     */
    public static final List<Rule> CHECKED_IN_JAVA = List.of(FC4, FC7, FC8, FC10, FC11, FC12, FC15, FC17, FC18);

    /**
     * The query parameters of OGC API Features that a property name would hide: those of Parts 1 to 3 and 5 to 9, and
     * {@code f} and {@code offset}, which servers use.
     */
    public static final Set<String> RESERVED = Set.of("f", "limit", "offset", "bbox", "bbox-crs", "datetime", "crs",
            "filter", "filter-lang", "filter-crs", "profile", "properties", "exclude-properties", "zoom-level",
            "sortby", "q");

    static {
        // the metamodel names the classic Eclipse OCL as the delegate of its invariants; outside Eclipse no plugin
        // registry installs it, and the generated package looks its delegate domain up globally
        if (EValidator.ValidationDelegate.Registry.INSTANCE
                .getValidationDelegate(OCLConstants.OCL_DELEGATE_URI) == null) {
            OCLDelegateDomain.initialize(null);
        }
        DelegateDomain.Factory.Registry.INSTANCE.putIfAbsent(OCLConstants.OCL_DELEGATE_URI_LPG,
                new OCLDelegateDomainFactory());
    }

    private FeatureModelValidator() {
    }

    /**
     * All findings for the collections in {@code root} and below: those of the invariants of the metamodel, each
     * element of the package checked by EMF with OCL, and those of the rules checked here ({@link #CHECKED_IN_JAVA}).
     * An element one of whose references does not resolve (FC17) gets no finding of an invariant: an invariant would
     * read an element that is not there as one of default values.
     */
    public static List<Finding> check(EObject root) {
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        List<Finding> checked = new ArrayList<>();
        for (EObject o : all) {
            if (o instanceof FeatureCollection c) {
                collection(c, checked);
            }
        }
        Set<EObject> unresolved = new HashSet<>();
        for (Finding f : checked) {
            if (f.rule().equals(FC17.code())) {
                unresolved.add(f.element());
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (Finding f : Findings.ofInvariants(MODULE, FeaturePackage.eINSTANCE, all, FeatureModelValidator::path)) {
            if (!unresolved.contains(f.element())) {
                findings.add(f);
            }
        }
        findings.addAll(checked);
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return Findings.errorLines(check(root));
    }

    /**
     * A collection: what it names resolves (FC17); the key (FC4, FC8), the properties that hold a geometry (FC7, FC8,
     * FC15), what a property says of its values (FC11, FC12) and the time (FC18, FC10), each judged by what an
     * invariant cannot read.
     */
    private static void collection(FeatureCollection c, List<Finding> findings) {
        for (Object f : c.getFeature()) {
            if (!(f instanceof FeatureProperty p)) {
                continue;
            }
            if (p.getType() != null && p.getType().eIsProxy()) {
                findings.add(FC17.at(p, path(p) + ": the type does not resolve"));
            }
            property(c, p, findings);
        }
        key(c, findings);
        primaryGeometry(c, findings);
        boolean instantKnown = own(c, c.getInstant(), "the instant", findings);
        boolean startKnown = own(c, c.getIntervalStart(), "the interval start", findings);
        boolean endKnown = own(c, c.getIntervalEnd(), "the interval end", findings);
        time(c, instantKnown ? c.getInstant() : null, startKnown ? c.getIntervalStart() : null,
                endKnown ? c.getIntervalEnd() : null, findings);
        systems(c, findings);
        boolean anyGeometry = FeatureCollections.properties(c).stream()
                .anyMatch(p -> FeatureProperties.isGeometry(p) || !SpatialElements.holdings(p).isEmpty());
        if (anyGeometry && c.getGeometry() == null) {
            findings.add(FC15.at(c, path(c) + " holds a geometry in its properties but names no primary geometry; "
                    + "bbox would have nothing to be evaluated on"));
        }
    }

    private static void key(FeatureCollection c, List<Finding> findings) {
        FeatureProperty key = c.getFeatureKey();
        if (key == null) {
            findings.add(FC4.at(c, path(c) + " names no feature key"));
            return;
        }
        if (key.eIsProxy()) {
            findings.add(FC17.at(c, path(c) + ": the feature key does not resolve"));
            return;
        }
        if (key.getOwner() != c) {
            findings.add(FC4.at(c, path(c) + ": the feature key " + path(key) + " is no property of it"));
            return;
        }
        if (!FeatureProperties.required(key)) {
            findings.add(FC4.at(c, path(c) + ": the feature key " + key.getName() + " may be null; an id is "
                    + "always there (multiplicity 1..1)"));
        }
        TypeFamily family = FeatureProperties.family(key);
        if (FeatureProperties.isGeometry(key)) {
            findings.add(FC8.at(c, path(c) + ": the feature key " + key.getName() + " is a geometry"));
        } else if (family != TypeFamily.STRING && family != TypeFamily.INTEGER && family != TypeFamily.OTHER) {
            findings.add(FC4.at(c, path(c) + ": the feature key " + key.getName() + " is of the type "
                    + typeName(key) + "; an id is a string or an integer"));
        }
    }

    /**
     * Whether {@code p} is named, resolves (FC17) and is a property of {@code c}; one of another class is the matter of
     * an invariant (FC5), and its type is not judged here.
     */
    private static boolean own(FeatureCollection c, FeatureProperty p, String what, List<Finding> findings) {
        if (p == null) {
            return false;
        }
        if (p.eIsProxy()) {
            findings.add(FC17.at(c, path(c) + ": " + what + " does not resolve"));
            return false;
        }
        return p.getOwner() == c;
    }

    /** The primary geometry, where the collection names one, resolves (FC17). */
    private static void primaryGeometry(FeatureCollection c, List<Finding> findings) {
        GeometryFeature g = c.getGeometry();
        if (g != null && g.eIsProxy()) {
            findings.add(FC17.at(c, path(c) + ": the primary geometry does not resolve"));
        }
    }

    private static void property(FeatureCollection c, FeatureProperty p, List<Finding> findings) {
        GeometryHolding whole = SpatialElements.holdings(p).stream().filter(WholeHolding.class::isInstance)
                .findFirst().orElse(null);
        boolean typedGeometry = SpatialElements.isGeometryType(p.getType());
        if (whole != null && !typedGeometry) {
            findings.add(FC7.at(p, path(p) + " is held whole by the geometry feature " + path(whole.eContainer())
                    + " but is of the type " + typeName(p) + "; a property that holds a whole geometry is of the "
                    + "data type Geometry"));
        }
        if (typedGeometry && whole == null) {
            findings.add(FC7.at(p, path(p) + " is of the data type Geometry but no geometry feature holds it "
                    + "whole; nothing says what it holds"));
        }
        TypeFamily family = FeatureProperties.family(p);
        if (typedGeometry || whole != null) {
            if (!p.getAllowedValues().isEmpty() || p.getMinimum() != null || p.getMaximum() != null
                    || p.getPattern() != null) {
                findings.add(FC8.at(p, path(p) + " is a geometry and carries allowed values, a bound or a "
                        + "pattern"));
            }
            if (p.isSortable()) {
                findings.add(FC8.at(p, path(p) + " is a geometry and sortable; a geometry has no order"));
            }
            if (p == c.getInstant() || p == c.getIntervalStart() || p == c.getIntervalEnd()) {
                findings.add(FC8.at(p, path(p) + " is a geometry and a time"));
            }
            return;
        }
        values(p, family, findings);
    }

    private static void values(FeatureProperty p, TypeFamily family, List<Finding> findings) {
        BigDecimal min = bound(p, p.getMinimum(), "minimum", family, findings);
        BigDecimal max = bound(p, p.getMaximum(), "maximum", family, findings);
        if (min != null && max != null && min.compareTo(max) > 0) {
            findings.add(FC11.at(p, path(p) + ": the minimum " + p.getMinimum() + " lies above the maximum "
                    + p.getMaximum()));
        }
        if (p.getPattern() != null) {
            if (family != TypeFamily.STRING) {
                findings.add(FC11.at(p, path(p) + " has a pattern but is of the type " + typeName(p)
                        + "; a pattern is for text"));
            }
            try {
                Pattern.compile(p.getPattern());
            } catch (PatternSyntaxException e) {
                findings.add(FC11.at(p, path(p) + ": the pattern '" + p.getPattern()
                        + "' is no regular expression: " + e.getDescription()));
            }
        }
        if (!p.getAllowedValues().isEmpty()) {
            if (family != TypeFamily.STRING && !family.numeric()) {
                findings.add(FC11.at(p, path(p) + " has allowed values but is of the type " + typeName(p)
                        + "; values are listed for text and numbers"));
            } else {
                for (String value : p.getAllowedValues()) {
                    if (!fits(value, family)) {
                        findings.add(FC11.at(p, path(p) + ": the allowed value '" + value + "' is no "
                                + (family == TypeFamily.INTEGER ? "whole number" : "decimal number")));
                    }
                }
            }
            if (TypeFamily.isEnumeration(p.getType())) {
                findings.add(FC12.at(p, path(p) + " lists allowed values beside its enumeration type "
                        + typeName(p) + "; a copy of the literals can drift"));
            }
        }
    }

    private static BigDecimal bound(FeatureProperty p, String literal, String which, TypeFamily family,
            List<Finding> findings) {
        if (literal == null) {
            return null;
        }
        if (!family.numeric()) {
            findings.add(FC11.at(p, path(p) + " has a " + which + " but is of the type " + typeName(p)
                    + "; bounds are for numbers"));
            return null;
        }
        try {
            return new BigDecimal(literal.trim());
        } catch (NumberFormatException e) {
            findings.add(FC11.at(p, path(p) + ": the " + which + " '" + literal + "' is no decimal number"));
            return null;
        }
    }

    private static boolean fits(String value, TypeFamily family) {
        try {
            switch (family) {
            case INTEGER -> Long.parseLong(value.trim());
            case NUMBER -> new BigDecimal(value.trim());
            default -> {
                return true;
            }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * The time of a feature: an instant, or an interval of a start, an end or both, never the two (FC18); each of the
     * properties {@code instant}, {@code start} and {@code end} that is usable a date or a timestamp.
     */
    private static void time(FeatureCollection c, FeatureProperty instant, FeatureProperty start,
            FeatureProperty end, List<Finding> findings) {
        if (c.getInstant() != null && (c.getIntervalStart() != null || c.getIntervalEnd() != null)) {
            findings.add(FC18.at(c, path(c) + " names an instant and the ends of an interval; the time of a "
                    + "feature is one of the two"));
        }
        boolean local = false;
        for (FeatureProperty t : new FeatureProperty[] { instant, start, end }) {
            if (t == null || FeatureProperties.isGeometry(t)) {
                continue;
            }
            TypeFamily family = FeatureProperties.family(t);
            if (!family.temporal()) {
                findings.add(FC18.at(c, path(c) + ": the time property " + t.getName() + " is of the type "
                        + typeName(t) + "; a time is a date or a timestamp"));
            }
            local |= family == TypeFamily.TIMESTAMP;
        }
        String zone = c.getTimeZone();
        if (zone != null) {
            try {
                ZoneId.of(zone.trim());
            } catch (DateTimeException e) {
                findings.add(FC10.at(c, path(c) + ": the time zone '" + zone + "' is no IANA name or "
                        + "offset"));
            }
            if (!local) {
                findings.add(FC10.at(c, path(c) + " states the time zone " + zone + " but no time "
                        + "property is a timestamp without a zone; it reads nothing"));
            }
        }
    }

    /** The offered reference systems: none without a geometry (FC15), each resolving (FC17). */
    private static void systems(FeatureCollection c, List<Finding> findings) {
        if (c.getGeometry() == null && !c.getReferenceSystems().isEmpty()) {
            findings.add(FC15.at(c, path(c) + " has no geometry but offers reference systems"));
            return;
        }
        for (ReferenceSystem s : c.getReferenceSystems()) {
            if (s.eIsProxy()) {
                findings.add(FC17.at(c, path(c) + ": an offered reference system does not resolve"));
            }
        }
    }

    private static String typeName(FeatureProperty p) {
        return p.getType() == null ? "(none)" : p.getType().getName();
    }

    /** The names from the root down to {@code element}, joined by dots. */
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
