/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleRule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Durations;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SamplingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SwingingDoorRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;

/**
 * Checks the recording and historisation rules in a model; the findings are those of the
 * lifecycle models ({@link Finding}, {@link Severity}).
 *
 * <ul>
 * <li>{@code R1} — a rule governs features, classifiers or namespaces; a recording rule acts on
 * values.</li>
 * <li>{@code R2} — at most one recording rule names an element, and at most one historisation
 * rule of a scope.</li>
 * <li>{@code R3} — parameters: durations parse and are positive, the minimum interval is not
 * above the maximum one, a throttle has a minimum interval, deltas, percents and deviations are
 * non-negative decimal numbers, a range percent is at most 100 and its low end below its high end,
 * {@code every} is at least 1.</li>
 * <li>{@code R4} (warning) — a rule that compares numbers on a feature whose type is no number:
 * every value passes it.</li>
 * <li>{@code R5} — the status feature is a feature of the governed classifier (or of an ancestor),
 * or of the owner of the governed feature.</li>
 * <li>{@code R6} — a historisation rule: its kind fits its scope, it names the anchors its kind
 * needs, the anchors are features of the governed classifier, its history classifier is another one
 * and (warning) has the governed features.</li>
 * <li>{@code R7} (warning) — an element that does not resolve; the body is not the text of the
 * rule.</li>
 * </ul>
 */
public final class RecordingModelValidator {

    private static final Set<String> NUMERIC_NAMES = Set.of("integer", "int", "long", "short", "byte", "float",
            "double", "real", "decimal", "numeric", "number", "bigint", "smallint", "tinyint", "money",
            "biginteger", "bigdecimal", "unlimitedinteger");
    private static final Set<String> NUMERIC_CLASSES = Set.of("long", "int", "short", "byte", "float", "double",
            "java.lang.Long", "java.lang.Integer", "java.lang.Short", "java.lang.Byte", "java.lang.Float",
            "java.lang.Double", "java.math.BigDecimal", "java.math.BigInteger");
    /** JDBC type codes of numbers: TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL. */
    private static final Set<Long> NUMERIC_JDBC = Set.of(-6L, 5L, 4L, -5L, 6L, 7L, 8L, 2L, 3L);

    private RecordingModelValidator() {
    }

    /** All findings for the recording and historisation rules in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        Map<ModelElement, List<LifecycleRule>> byElement = new LinkedHashMap<>();
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            visit(it.next(), findings, byElement);
        }
        visit(root, findings, byElement);
        byElement.forEach((element, rules) -> conflicts(element, rules, findings));
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void visit(EObject o, List<Finding> findings, Map<ModelElement, List<LifecycleRule>> byElement) {
        if (!(o instanceof RecordingRule) && !(o instanceof HistorisationRule)) {
            return;
        }
        LifecycleRule rule = (LifecycleRule) o;
        for (ModelElement e : rule.getConstrainedElement()) {
            byElement.computeIfAbsent(e, k -> new ArrayList<>()).add(rule);
            if (e.eIsProxy()) {
                findings.add(new Finding(Severity.WARNING, "R7", rule, name(rule) + ": element "
                        + ((InternalEObject) e).eProxyURI() + " does not resolve."));
            } else if (!(e instanceof StructuralFeature || e instanceof Namespace)) {
                findings.add(new Finding(Severity.ERROR, "R1", rule, name(rule)
                        + ": a rule governs features, classifiers or namespaces, not the "
                        + e.eClass().getName() + " " + path(e) + "."));
            }
        }
        if (rule instanceof RecordingRule recording) {
            recording(recording, findings);
        } else {
            historisation((HistorisationRule) rule, findings);
        }
    }

    private static void recording(RecordingRule rule, List<Finding> findings) {
        if (rule.getScope() != DataScope.VALUE) {
            findings.add(new Finding(Severity.ERROR, "R1", rule, name(rule)
                    + ": a recording rule acts on values, its scope is " + rule.getScope().getLiteral() + "."));
        }
        Duration minimum = duration(rule, "minimumInterval", rule.getMinimumInterval(), findings);
        Duration maximum = duration(rule, "maximumInterval", rule.getMaximumInterval(), findings);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": the minimum interval "
                    + rule.getMinimumInterval() + " is above the maximum interval " + rule.getMaximumInterval() + "."));
        }
        if (rule instanceof ThrottleRule && rule.getMinimumInterval() == null) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": a throttle needs a minimum interval."));
        } else if (rule instanceof AbsoluteChangeRule r) {
            decimal(rule, "delta", r.getDelta(), findings);
        } else if (rule instanceof RelativeChangeRule r) {
            decimal(rule, "percent", r.getPercent(), findings);
        } else if (rule instanceof SwingingDoorRule r) {
            decimal(rule, "deviation", r.getDeviation(), findings);
        } else if (rule instanceof RangeChangeRule r) {
            range(r, findings);
        } else if (rule instanceof SamplingRule r && r.getEvery() < 1) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": every is " + r.getEvery()
                    + ", it must be at least 1."));
        }
        if (rule instanceof AbsoluteChangeRule || rule instanceof RelativeChangeRule || rule instanceof RangeChangeRule
                || rule instanceof SwingingDoorRule) {
            for (ModelElement e : rule.getConstrainedElement()) {
                if (e instanceof StructuralFeature f && f.getType() != null && !isNumeric(f.getType())) {
                    findings.add(new Finding(Severity.WARNING, "R4", rule, name(rule) + " compares numbers, but "
                            + path(f) + " is of type " + f.getType().getName() + ": every value passes it."));
                }
            }
        }
        StructuralFeature status = rule.getStatusFeature();
        if (status != null) {
            anchor(rule, "R5", "status feature", status, findings);
        }
        body(rule, RecordingRules.render(rule), findings);
    }

    private static void range(RangeChangeRule rule, List<Finding> findings) {
        BigDecimal percent = decimal(rule, "percent", rule.getPercent(), findings);
        if (percent != null && percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": percent is " + rule.getPercent()
                    + ", a percent of the range is at most 100."));
        }
        BigDecimal low = number(rule.getLow());
        BigDecimal high = number(rule.getHigh());
        if (low == null || high == null) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": the range '" + rule.getLow() + "' to '"
                    + rule.getHigh() + "' is no pair of decimal numbers."));
        } else if (low.compareTo(high) >= 0) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": the low end " + rule.getLow()
                    + " is not below the high end " + rule.getHigh() + "."));
        }
    }

    private static void historisation(HistorisationRule rule, List<Finding> findings) {
        HistorisationKind kind = rule.getKind();
        if (rule.getScope() == DataScope.ROW
                && (kind == HistorisationKind.PREVIOUS_VALUE || kind == HistorisationKind.VERSION_AND_CURRENT)) {
            findings.add(new Finding(Severity.ERROR, "R6", rule, name(rule) + ": " + kind.getLiteral()
                    + " keeps the history of an attribute; its scope is value, not row."));
        }
        if (rule.getScope() == DataScope.VALUE && kind == HistorisationKind.SNAPSHOT) {
            findings.add(new Finding(Severity.ERROR, "R6", rule, name(rule)
                    + ": a snapshot keeps the state of rows; its scope is row, not value."));
        }
        needs(rule, kind == HistorisationKind.SNAPSHOT
                || kind == HistorisationKind.NEW_VERSION && rule.getScope() == DataScope.ROW,
                "validFrom", rule.getValidFrom(), findings);
        needs(rule, kind == HistorisationKind.PREVIOUS_VALUE, "previousValue", rule.getPreviousValue(), findings);
        needs(rule, kind == HistorisationKind.VERSION_AND_CURRENT, "currentValue", rule.getCurrentValue(), findings);
        needs(rule, kind == HistorisationKind.HISTORY_TABLE, "history", rule.getHistory(), findings);
        for (StructuralFeature f : new StructuralFeature[] { rule.getValidFrom(), rule.getValidTo(),
                rule.getCurrentFlag(), rule.getPreviousValue(), rule.getCurrentValue() }) {
            if (f != null) {
                anchor(rule, "R6", "anchor", f, findings);
            }
        }
        Classifier history = rule.getHistory();
        if (history != null) {
            for (ModelElement e : rule.getConstrainedElement()) {
                Classifier holder = e instanceof Classifier c ? c : e instanceof StructuralFeature s ? s.getOwner() : null;
                if (holder != null && !history.eIsProxy() && LifecycleRules.lineage(holder).contains(history)) {
                    findings.add(new Finding(Severity.ERROR, "R6", rule, name(rule) + ": the history " + path(history)
                            + " is " + path(holder) + " itself or an ancestor of it; the history goes elsewhere."));
                    continue;
                }
                for (StructuralFeature governed : governedFeatures(e)) {
                    if (feature(history, governed.getName()) == null) {
                        findings.add(new Finding(Severity.WARNING, "R6", rule, name(rule) + ": the history "
                                + path(history) + " has no feature " + governed.getName() + " for " + path(governed)
                                + "."));
                    }
                }
            }
        }
        body(rule, HistorisationRules.render(rule), findings);
    }

    private static void needs(HistorisationRule rule, boolean needed, String anchor, Object value,
            List<Finding> findings) {
        if (needed && value == null) {
            findings.add(new Finding(Severity.ERROR, "R6", rule, name(rule) + ": " + rule.getKind().getLiteral()
                    + " needs " + anchor + "."));
        }
    }

    /** An anchor of a rule belongs to the governed classifier, or to the owner of the governed feature. */
    private static void anchor(LifecycleRule rule, String code, String what, StructuralFeature anchor,
            List<Finding> findings) {
        for (ModelElement e : rule.getConstrainedElement()) {
            Classifier holder = e instanceof Classifier c ? c : e instanceof StructuralFeature f ? f.getOwner() : null;
            if (holder == null) {
                if (!e.eIsProxy()) {
                    findings.add(new Finding(Severity.ERROR, code, rule, name(rule) + ": a " + what + " needs a "
                            + "classifier or a feature as governed element, " + path(e) + " is a "
                            + e.eClass().getName() + "."));
                }
            } else if (!LifecycleRules.lineage(holder).contains(anchor.getOwner())) {
                findings.add(new Finding(Severity.ERROR, code, rule, name(rule) + ": the " + what + " "
                        + path(anchor) + " is no feature of " + path(holder) + "."));
            }
        }
    }

    private static List<StructuralFeature> governedFeatures(ModelElement e) {
        if (e instanceof StructuralFeature f) {
            return List.of(f);
        }
        if (e instanceof Classifier c) {
            List<StructuralFeature> features = new ArrayList<>();
            for (Feature f : c.getFeature()) {
                if (f instanceof StructuralFeature s) {
                    features.add(s);
                }
            }
            return features;
        }
        return List.of();
    }

    private static StructuralFeature feature(Classifier classifier, String name) {
        for (Classifier c : LifecycleRules.lineage(classifier)) {
            for (Feature f : c.getFeature()) {
                if (f instanceof StructuralFeature s && name != null && name.equals(s.getName())) {
                    return s;
                }
            }
        }
        return null;
    }

    private static void conflicts(ModelElement element, List<LifecycleRule> rules, List<Finding> findings) {
        List<LifecycleRule> recording = rules.stream().filter(RecordingRule.class::isInstance).toList();
        if (recording.size() > 1) {
            findings.add(new Finding(Severity.ERROR, "R2", element, path(element) + " is named by " + recording.size()
                    + " recording rules (" + names(recording) + "); one applies to an element, its parameters combine "
                    + "the criteria."));
        }
        for (DataScope scope : DataScope.values()) {
            List<LifecycleRule> historisation = rules.stream()
                    .filter(r -> r instanceof HistorisationRule && r.getScope() == scope).toList();
            if (historisation.size() > 1) {
                findings.add(new Finding(Severity.ERROR, "R2", element, path(element) + " is named by "
                        + historisation.size() + " historisation rules of scope " + scope.getLiteral() + " ("
                        + names(historisation) + "); one applies to an element."));
            }
        }
    }

    private static void body(LifecycleRule rule, String expected, List<Finding> findings) {
        if (!LifecycleRules.hasBody(rule, expected)) {
            findings.add(new Finding(Severity.WARNING, "R7", rule, name(rule) + ": the body is not the text of the rule '"
                    + expected + "' in " + LifecycleRules.LANGUAGE + "."));
        }
    }

    private static Duration duration(LifecycleRule rule, String feature, String literal, List<Finding> findings) {
        if (literal == null) {
            return null;
        }
        try {
            Duration d = Durations.parse(literal);
            if (d.isZero()) {
                findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": " + feature
                        + " is zero, it must be positive."));
                return null;
            }
            return d;
        } catch (IllegalArgumentException e) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": " + feature + " '" + literal
                    + "' is no ISO-8601 duration of days, hours, minutes and seconds (P90D, PT10M)."));
            return null;
        }
    }

    private static BigDecimal decimal(LifecycleRule rule, String feature, String literal, List<Finding> findings) {
        BigDecimal value = number(literal);
        if (value == null || value.signum() < 0) {
            findings.add(new Finding(Severity.ERROR, "R3", rule, name(rule) + ": " + feature + " '" + literal
                    + "' is no non-negative decimal number."));
            return null;
        }
        return value;
    }

    private static BigDecimal number(String literal) {
        try {
            return literal == null ? null : new BigDecimal(literal.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether a type holds numbers: by its name, its JDBC type code or the Java class it stands for. */
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

    private static String name(LifecycleRule rule) {
        return rule.eClass().getName() + " '" + rule.getName() + "'";
    }

    private static String names(List<LifecycleRule> rules) {
        return String.join(", ", rules.stream().map(LifecycleRule::getName).toList());
    }

    private static String path(EObject element) {
        return LifecycleRules.path(element);
    }
}
