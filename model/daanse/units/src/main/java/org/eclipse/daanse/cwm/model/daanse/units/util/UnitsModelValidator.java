/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.daanse.units.BaseUnit;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.LogarithmicUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.PrefixedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.ReferringUnit;
import org.eclipse.daanse.cwm.model.daanse.units.ScaledUnit;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitFactor;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Checks quantities, units and their definitions beyond what the metamodel enforces.
 *
 * <ul>
 * <li>{@code U1} where a quantity attaches — to an attribute (a column, a record field, a measure,
 * a resource), a parameter or a data type; not to an association end, a class or a package; and
 * no data type owns a quantity (CWM 1.1, C-3-2).</li>
 * <li>{@code U2} dimensions — a base unit has one base dimension to the power 1; the stated
 * dimension of every other unit is the one its definition gives (the product of its factors, the
 * dimension of its reference).</li>
 * <li>{@code U3} one quantity per element; the kind of a quantity has the dimension of its unit;
 * (warning) the kind is none of the unit's kinds nor narrower than one.</li>
 * <li>{@code U4} definitions — decimal numbers parse (multiplier not zero, logarithm base above
 * zero and not one, divisor not zero); prefixes have base 2 or 10 and an exponent other than
 * zero; no unit is defined through itself, no kind is narrower than itself, a broader kind has
 * the same dimension; a factor of a product has no offset and is not logarithmic; (warnings) two
 * units of a system with one symbol, an allowed unit with a symbol the system already uses, a
 * prefix on a prefixed unit, a scaled unit with multiplier 1 and no offset (an alternate
 * unit).</li>
 * <li>{@code U5} (warning) a quantity on an attribute or parameter whose type is not a number —
 * neither by its name, its JDBC type code or its Java class, nor a data type with a unit of its
 * own.</li>
 * <li>{@code U6} (warning) the own quantity of an element and the quantity of its data type
 * differ in dimension.</li>
 * <li>{@code U7} (warning) a reference that does not resolve.</li>
 * </ul>
 */
public final class UnitsModelValidator {

    /** Severity of a finding; only {@link #ERROR} makes a model invalid. */
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

    private static final String BEHAVIORAL = "http://www.omg.org/spec/CWM/1.1/objectmodel/behavioral";
    private static final Set<String> NUMERIC_NAMES = Set.of("integer", "int", "long", "short", "byte", "float",
            "double", "real", "decimal", "numeric", "number", "bigint", "smallint", "tinyint", "money",
            "biginteger", "bigdecimal", "unlimitedinteger");
    private static final Set<String> NUMERIC_CLASSES = Set.of("long", "int", "short", "byte", "float", "double",
            "java.lang.Long", "java.lang.Integer", "java.lang.Short", "java.lang.Byte", "java.lang.Float",
            "java.lang.Double", "java.math.BigDecimal", "java.math.BigInteger");
    /** JDBC type codes of numbers: TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL. */
    private static final Set<Long> NUMERIC_JDBC = Set.of(-6L, 5L, 4L, -5L, 6L, 7L, 8L, 2L, 3L);

    private UnitsModelValidator() {
    }

    /** All findings for the quantities, units and their definitions in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        Map<EObject, List<Quantity>> byElement = new LinkedHashMap<>();
        for (EObject o : all) {
            if (o instanceof Unit u) {
                unit(u, findings);
            } else if (o instanceof Prefix p) {
                prefix(p, findings);
            } else if (o instanceof QuantityKind k) {
                kind(k, findings);
            } else if (o instanceof Quantity q) {
                quantity(q, findings);
                for (ModelElement e : q.getModelElement()) {
                    byElement.computeIfAbsent(e, k -> new ArrayList<>()).add(q);
                }
            }
            if (o instanceof SystemOfUnits s) {
                symbols(s, findings);
            }
        }
        for (Map.Entry<EObject, List<Quantity>> e : byElement.entrySet()) {
            if (e.getValue().size() > 1) {
                findings.add(error("U3", e.getKey(), path(e.getKey()) + " has " + e.getValue().size()
                        + " quantities: " + String.join(", ", e.getValue().stream().map(UnitsModelValidator::path)
                                .toList()) + "; an element has one"));
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void unit(Unit u, List<Finding> findings) {
        if (u.getDimension() == null) {
            findings.add(error("U2", u, path(u) + " has no dimension"));
        } else if (u.getDimension().eIsProxy()) {
            findings.add(warning("U7", u, path(u) + ": the dimension does not resolve"));
        }
        if (u instanceof ReferringUnit r && r.getReference() != null && r.getReference().eIsProxy()) {
            findings.add(warning("U7", u, path(u) + ": the reference does not resolve"));
            return;
        }
        if (u instanceof BaseUnit && u.getDimension() != null && !u.getDimension().eIsProxy()) {
            long[] e = Dimensions.exponents(u.getDimension());
            if (Arrays.stream(e).filter(x -> x != 0).count() != 1 || Arrays.stream(e).noneMatch(x -> x == 1)) {
                findings.add(error("U2", u, path(u) + " is a base unit of " + Dimensions.code(e)
                        + ", not of one base dimension to the power 1"));
            }
        } else if (u.getDimension() != null && !u.getDimension().eIsProxy()) {
            try {
                long[] defined = Units.definedDimension(u);
                if (!Arrays.equals(defined, Dimensions.exponents(u.getDimension()))) {
                    findings.add(error("U2", u, path(u) + " states the dimension " + Dimensions.code(u.getDimension())
                            + ", its definition gives " + Dimensions.code(defined)));
                }
            } catch (IllegalArgumentException e) {
                findings.add(error("U4", u, path(u) + ": " + e.getMessage()));
                return;
            }
        }
        if (u instanceof ScaledUnit s) {
            BigDecimal multiplier = decimal(s, "multiplier", s.getMultiplier(), findings);
            BigDecimal offset = s.getOffset() == null ? null : decimal(s, "offset", s.getOffset(), findings);
            if (multiplier != null && multiplier.signum() == 0) {
                findings.add(error("U4", s, path(s) + ": the multiplier is zero"));
            }
            if (multiplier != null && multiplier.compareTo(BigDecimal.ONE) == 0
                    && (s.getOffset() == null || offset != null && offset.signum() == 0)) {
                findings.add(warning("U4", s, path(s) + " scales by 1 without offset; an alternate unit says that"));
            }
        } else if (u instanceof LogarithmicUnit l) {
            BigDecimal base = decimal(l, "base", l.getBase(), findings);
            BigDecimal divisor = decimal(l, "divisor", l.getDivisor(), findings);
            if (base != null && (base.signum() <= 0 || base.compareTo(BigDecimal.ONE) == 0)) {
                findings.add(error("U4", l, path(l) + ": the base of the logarithm is above zero and not one"));
            }
            if (divisor != null && divisor.signum() == 0) {
                findings.add(error("U4", l, path(l) + ": the divisor is zero"));
            }
        } else if (u instanceof PrefixedUnit p && p.getReference() instanceof PrefixedUnit) {
            findings.add(warning("U4", p, path(p) + " prefixes the prefixed unit " + p.getReference().getSymbol()));
        } else if (u instanceof DerivedUnit d) {
            for (UnitFactor f : d.getFactor()) {
                if (f.getUnit() == null || f.getUnit().eIsProxy()) {
                    findings.add(warning("U7", d, path(d) + ": a factor unit does not resolve"));
                    continue;
                }
                try {
                    if (Units.toBase(f.getUnit()).term().signum() != 0) {
                        findings.add(error("U4", d, path(d) + ": the factor " + f.getUnit().getSymbol()
                                + " has an offset and is no factor of a product"));
                    }
                } catch (IllegalArgumentException e) {
                    findings.add(error("U4", d, path(d) + ": the factor " + f.getUnit().getSymbol()
                            + " is no factor of a product: " + e.getMessage()));
                }
            }
        }
    }

    private static void prefix(Prefix p, List<Finding> findings) {
        if (p.getBase() != 2 && p.getBase() != 10) {
            findings.add(error("U4", p, path(p) + " has base " + p.getBase() + "; a prefix has base 2 or 10"));
        }
        if (p.getExponent() == 0) {
            findings.add(error("U4", p, path(p) + " has exponent 0"));
        }
    }

    private static void kind(QuantityKind k, List<Finding> findings) {
        if (k.getDimension() != null && k.getDimension().eIsProxy()) {
            findings.add(warning("U7", k, path(k) + ": the dimension does not resolve"));
            return;
        }
        Set<QuantityKind> seen = new HashSet<>();
        for (QuantityKind b = k.getBroader(); b != null; b = b.getBroader()) {
            if (b == k || !seen.add(b)) {
                findings.add(error("U4", k, path(k) + " is narrower than itself"));
                return;
            }
        }
        QuantityKind broader = k.getBroader();
        if (broader != null && !broader.eIsProxy() && !Dimensions.same(k.getDimension(), broader.getDimension())) {
            findings.add(error("U4", k, path(k) + " has the dimension " + code(k.getDimension()) + ", its broader kind "
                    + path(broader) + " " + code(broader.getDimension())));
        }
    }

    private static void quantity(Quantity q, List<Finding> findings) {
        if (q.getNamespace() instanceof DataType) {
            findings.add(error("U1", q, path(q) + " is owned by a data type, which owns nothing (CWM 1.1, C-3-2)"));
        }
        Unit unit = q.getUnit();
        if (unit == null || unit.eIsProxy()) {
            findings.add(warning("U7", q, path(q) + ": the unit does not resolve"));
            return;
        }
        QuantityKind kind = q.getQuantityKind();
        if (kind != null && !kind.eIsProxy()) {
            if (!Dimensions.same(kind.getDimension(), unit.getDimension())) {
                findings.add(error("U3", q, path(q) + ": the kind " + kind.getName() + " has the dimension "
                        + code(kind.getDimension()) + ", the unit " + unit.getSymbol() + " "
                        + code(unit.getDimension())));
            } else if (!unit.getQuantityKind().isEmpty()
                    && unit.getQuantityKind().stream().noneMatch(k -> QuantityKinds.isA(kind, k))) {
                findings.add(warning("U3", q, path(q) + ": the unit " + unit.getSymbol() + " measures "
                        + String.join(", ", unit.getQuantityKind().stream().map(QuantityKind::getName).toList())
                        + ", not " + kind.getName()));
            }
        }
        for (ModelElement e : q.getModelElement()) {
            if (e.eIsProxy()) {
                findings.add(warning("U7", q, path(q) + ": an element does not resolve"));
                continue;
            }
            boolean typed = e instanceof Attribute || isParameter(e);
            if (!typed && !(e instanceof DataType)) {
                findings.add(error("U1", q, path(q) + " names " + e.eClass().getName() + " " + path(e)
                        + "; a quantity names attributes, parameters and data types"));
                continue;
            }
            Classifier type = typed ? typeOf(e) : null;
            if (type != null && !type.eIsProxy() && !isNumeric(type)
                    && !(type instanceof DataType d && !Quantities.own(d).isEmpty())) {
                findings.add(warning("U5", q, path(q) + ": " + path(e) + " is of type " + type.getName()
                        + ", not a number"));
            }
            if (typed && type instanceof DataType d) {
                Quantities.own(d).stream().filter(other -> other.getUnit() != null && !other.getUnit().eIsProxy())
                        .filter(other -> !Dimensions.same(other.getUnit().getDimension(), unit.getDimension()))
                        .findFirst().ifPresent(other -> findings.add(warning("U6", q, path(q) + ": " + path(e)
                                + " is measured in " + unit.getSymbol() + ", its type " + d.getName() + " in "
                                + other.getUnit().getSymbol() + " of another dimension")));
            }
        }
    }

    private static void symbols(SystemOfUnits system, List<Finding> findings) {
        Set<String> seen = new HashSet<>();
        for (Unit u : Units.of(system)) {
            if (u.getSymbol() != null && !seen.add(u.getSymbol())) {
                findings.add(warning("U4", u, path(u) + ": the symbol " + u.getSymbol() + " is used twice in "
                        + path(system)));
            }
        }
        for (Unit u : system.getAllowedUnit()) {
            if (!u.eIsProxy() && u.eContainer() != system && u.getSymbol() != null && !seen.add(u.getSymbol())) {
                findings.add(warning("U4", system, path(system) + ": allows " + path(u) + ", whose symbol "
                        + u.getSymbol() + " another unit of the system already has"));
            }
        }
    }

    private static BigDecimal decimal(Unit u, String feature, String literal, List<Finding> findings) {
        try {
            return Units.decimal(literal, feature);
        } catch (IllegalArgumentException e) {
            findings.add(error("U4", u, path(u) + ": " + e.getMessage()));
            return null;
        }
    }

    private static boolean isParameter(EObject o) {
        for (EClass c : allTypes(o.eClass())) {
            if ("Parameter".equals(c.getName()) && c.getEPackage() != null
                    && BEHAVIORAL.equals(c.getEPackage().getNsURI())) {
                return true;
            }
        }
        return false;
    }

    private static List<EClass> allTypes(EClass c) {
        List<EClass> types = new ArrayList<>(List.of(c));
        types.addAll(c.getEAllSuperTypes());
        return types;
    }

    private static Classifier typeOf(EObject e) {
        EStructuralFeature type = e.eClass().getEStructuralFeature("type");
        return type != null && !type.isMany() && e.eGet(type) instanceof Classifier c ? c : null;
    }

    /**
     * Whether a type holds numbers: a numeric name (CWM, SQL, Ecore: Integer, DECIMAL, EDouble), a
     * numeric JDBC type code, or a numeric Java class named by the tag
     * {@code daanse.ecore.instanceClassName}.
     */
    static boolean isNumeric(Classifier type) {
        if (type.getName() != null && NUMERIC_NAMES.contains(plain(type.getName()))) {
            return true;
        }
        EStructuralFeature typeNumber = type.eClass().getEStructuralFeature("typeNumber");
        if (typeNumber != null && type.eGet(typeNumber) instanceof Number n && NUMERIC_JDBC.contains(n.longValue())) {
            return true;
        }
        return TaggedValues.value(type, "daanse.ecore.instanceClassName").map(NUMERIC_CLASSES::contains).orElse(false);
    }

    /** A type name in lower case, an Ecore type name in its plain form ({@code EDouble} → {@code double}). */
    private static String plain(String name) {
        String n = name;
        if (n.length() > 1 && n.charAt(0) == 'E' && Character.isUpperCase(n.charAt(1))) {
            n = n.substring(1);
            if (n.endsWith("Object")) {
                n = n.substring(0, n.length() - "Object".length());
            }
        }
        return n.toLowerCase(Locale.ROOT);
    }

    private static String code(Dimension d) {
        return d == null || d.eIsProxy() ? "?" : Dimensions.code(d);
    }

    private static Finding error(String rule, EObject element, String message) {
        return new Finding(Severity.ERROR, rule, element, message);
    }

    private static Finding warning(String rule, EObject element, String message) {
        return new Finding(Severity.WARNING, rule, element, message);
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
