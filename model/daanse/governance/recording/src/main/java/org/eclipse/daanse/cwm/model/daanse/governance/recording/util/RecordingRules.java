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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Durations;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ChangedValueRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.Interpolation;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SamplingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SwingingDoorRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;

/**
 * Creates recording rules: each in the {@code ownedElement} of {@code owner}, named,
 * constraining the given elements, its {@code body} set to the rule's text; the modifiers keep
 * the body in step. {@link #effective} is the recording rule that applies to an element.
 */
public final class RecordingRules {

    private static final RecordingFactory F = RecordingFactory.eINSTANCE;

    private RecordingRules() {
    }

    /** Records a value that differs from the last recorded one by at least {@code delta}. */
    public static AbsoluteChangeRule absolute(Namespace owner, String name, double delta, ModelElement... elements) {
        AbsoluteChangeRule rule = F.createAbsoluteChangeRule();
        rule.setDelta(Double.toString(delta));
        return refreshed(LifecycleRules.attach(owner, name, rule, elements));
    }

    /** Records a value that differs from the last recorded one by at least {@code percent} percent of it. */
    public static RelativeChangeRule relative(Namespace owner, String name, double percent, ModelElement... elements) {
        RelativeChangeRule rule = F.createRelativeChangeRule();
        rule.setPercent(Double.toString(percent));
        return refreshed(LifecycleRules.attach(owner, name, rule, elements));
    }

    /**
     * Records a value that differs from the last recorded one by at least {@code percent} percent
     * of the measurement range from {@code low} to {@code high}.
     */
    public static RangeChangeRule range(Namespace owner, String name, double percent, double low, double high,
            ModelElement... elements) {
        RangeChangeRule rule = F.createRangeChangeRule();
        rule.setPercent(Double.toString(percent));
        rule.setLow(Double.toString(low));
        rule.setHigh(Double.toString(high));
        return refreshed(LifecycleRules.attach(owner, name, rule, elements));
    }

    /** Records one value out of every {@code every}. */
    public static SamplingRule sampling(Namespace owner, String name, long every, ModelElement... elements) {
        SamplingRule rule = F.createSamplingRule();
        rule.setEvery(every);
        return refreshed(LifecycleRules.attach(owner, name, rule, elements));
    }

    /** Records at most one value per {@code minimumInterval}. */
    public static ThrottleRule throttle(Namespace owner, String name, Duration minimumInterval,
            ModelElement... elements) {
        ThrottleRule rule = F.createThrottleRule();
        rule.setMinimumInterval(Durations.format(minimumInterval));
        return refreshed(LifecycleRules.attach(owner, name, rule, elements));
    }

    /** Records a value only when it differs from the last recorded one. */
    public static ChangedValueRule changedValue(Namespace owner, String name, ModelElement... elements) {
        return refreshed(LifecycleRules.attach(owner, name, F.createChangedValueRule(), elements));
    }

    /** Compresses the values with a swinging door that keeps the curve within {@code deviation}. */
    public static SwingingDoorRule swingingDoor(Namespace owner, String name, double deviation,
            ModelElement... elements) {
        SwingingDoorRule rule = F.createSwingingDoorRule();
        rule.setDeviation(Double.toString(deviation));
        return refreshed(LifecycleRules.attach(owner, name, rule, elements));
    }

    /** Adds a heartbeat: a value is recorded regardless once the last one is older than {@code interval}. */
    public static <R extends RecordingRule> R heartbeat(R rule, Duration interval) {
        rule.setMaximumInterval(Durations.format(interval));
        return refreshed(rule);
    }

    /** Drops a value that arrives sooner than {@code interval} after the last recorded one. */
    public static <R extends RecordingRule> R minimumInterval(R rule, Duration interval) {
        rule.setMinimumInterval(Durations.format(interval));
        return refreshed(rule);
    }

    /** Records a value whose status, held in {@code feature}, differs from the status of the last one. */
    public static <R extends RecordingRule> R status(R rule, StructuralFeature feature) {
        rule.setStatusFeature(Objects.requireNonNull(feature, "feature"));
        return refreshed(rule);
    }

    /** Sets what happens to a value older than the last recorded one. */
    public static <R extends RecordingRule> R lateValues(R rule, LateValueHandling handling) {
        rule.setLateValues(Objects.requireNonNull(handling, "handling"));
        return refreshed(rule);
    }

    /** Sets how a value between two recorded values is read. */
    public static <R extends RecordingRule> R interpolation(R rule, Interpolation interpolation) {
        rule.setInterpolation(Objects.requireNonNull(interpolation, "interpolation"));
        return refreshed(rule);
    }

    /** The recording rule that applies to {@code element}: the nearest one along its path. */
    public static Optional<RecordingRule> effective(ModelElement element) {
        return LifecycleRules.nearest(element, RecordingRule.class);
    }

    /**
     * The text of a recording rule in the language {@value LifecycleRules#LANGUAGE}, e.g.
     * {@code abs(value - last) >= 2.0} or
     * {@code (abs(value - last) >= 5.0 % of abs(last)) or time - lastTime >= PT1H}.
     */
    public static String render(RecordingRule rule) {
        Objects.requireNonNull(rule, "rule");
        String text = criterion(rule);
        List<String> forced = new ArrayList<>();
        if (rule.getMaximumInterval() != null) {
            forced.add("time - lastTime >= " + rule.getMaximumInterval());
        }
        if (rule.getStatusFeature() != null) {
            forced.add(rule.getStatusFeature().getName() + " changed");
        }
        if (!forced.isEmpty()) {
            text = "(" + text + ") or " + String.join(" or ", forced);
        }
        if (rule.getMinimumInterval() != null && !(rule instanceof ThrottleRule)) {
            text = "time - lastTime >= " + rule.getMinimumInterval() + " and (" + text + ")";
        }
        if (rule.getLateValues() != LateValueHandling.COMPARE) {
            text += "; late values: " + rule.getLateValues().getLiteral();
        }
        if (rule.getInterpolation() != Interpolation.LINEAR) {
            text += "; read " + rule.getInterpolation().getLiteral();
        }
        return text;
    }

    private static String criterion(RecordingRule rule) {
        if (rule instanceof AbsoluteChangeRule r) {
            return "abs(value - last) >= " + r.getDelta();
        }
        if (rule instanceof RelativeChangeRule r) {
            return "abs(value - last) >= " + r.getPercent() + " % of abs(last)";
        }
        if (rule instanceof RangeChangeRule r) {
            return "abs(value - last) >= " + r.getPercent() + " % of (" + r.getHigh() + " - " + r.getLow() + ")";
        }
        if (rule instanceof SamplingRule r) {
            return "one value out of every " + r.getEvery();
        }
        if (rule instanceof ThrottleRule r) {
            return "time - lastTime >= " + r.getMinimumInterval();
        }
        if (rule instanceof ChangedValueRule) {
            return "value <> last";
        }
        if (rule instanceof SwingingDoorRule r) {
            return "swinging door within " + r.getDeviation();
        }
        return rule.eClass().getName();
    }

    /** Sets the {@code body} of a recording rule to its {@linkplain #render text}. */
    public static <R extends RecordingRule> R refreshed(R rule) {
        LifecycleRules.setBody(rule, render(rule));
        return rule;
    }
}
