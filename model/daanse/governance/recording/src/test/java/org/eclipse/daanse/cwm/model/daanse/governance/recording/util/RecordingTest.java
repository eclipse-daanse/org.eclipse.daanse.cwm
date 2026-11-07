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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ChangedValueRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SamplingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SwingingDoorRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.Recording.Decision;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.Recording.Sample;
import org.junit.jupiter.api.Test;

/**
 * The evaluator follows Eclipse Fennec's change-rule filter case by case (its
 * {@code ChangeRuleFilterImplTest}): first value, last recorded value as baseline, zero baseline,
 * counting, throttling, values that are no numbers, state per element; plus what Fennec's mapping
 * model does not have: the heartbeat, the changed-value rule, a minimum interval on any rule,
 * the range dead band, status changes, late values and the swinging door.
 */
class RecordingTest {

    private static final RecordingFactory F = RecordingFactory.eINSTANCE;
    private final Instant base = Instant.parse("2026-11-06T08:00:00Z");

    @Test
    void withoutARuleEveryValueIsRecorded() {
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(null, state, 1.0d, base)).isTrue();
        assertThat(Recording.accepts(null, state, 1.0d, base.plusSeconds(1))).isTrue();
    }

    @Test
    void relativeRuleComparesWithTheLastRecordedValue() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, base)).as("the first value has nothing to compare to").isTrue();
        assertThat(Recording.accepts(rule, state, 105.0d, base.plusSeconds(1))).as("5%").isFalse();
        assertThat(Recording.accepts(rule, state, 109.0d, base.plusSeconds(2))).as("9% - still against 100").isFalse();
        assertThat(Recording.accepts(rule, state, 110.0d, base.plusSeconds(3))).as("10% reaches the threshold").isTrue();
        assertThat(Recording.accepts(rule, state, 115.0d, base.plusSeconds(4))).as("now measured against 110").isFalse();
    }

    @Test
    void relativeRuleAgainstZeroRecordsAnyMoveAwayFromZero() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 0.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 0.0d, base.plusSeconds(1))).as("still zero").isFalse();
        assertThat(Recording.accepts(rule, state, 0.1d, base.plusSeconds(2))).isTrue();
    }

    @Test
    void relativeRuleMeasuresTheSizeNotTheDirection() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 80.0d, base.plusSeconds(1))).as("-20%").isTrue();
    }

    @Test
    void absoluteRuleComparesTheChangeWithDelta() {
        AbsoluteChangeRule rule = F.createAbsoluteChangeRule();
        rule.setDelta("2.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 50, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 51, base.plusSeconds(1))).isFalse();
        assertThat(Recording.accepts(rule, state, 52, base.plusSeconds(2))).isTrue();
        assertThat(Recording.accepts(rule, state, 51, base.plusSeconds(3))).as("1 away from the recorded 52").isFalse();
    }

    @Test
    void samplingRuleRecordsOneValueOutOfEveryN() {
        SamplingRule rule = F.createSamplingRule();
        rule.setEvery(3);
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 1.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 500.0d, base.plusSeconds(1))).isFalse();
        assertThat(Recording.accepts(rule, state, 900.0d, base.plusSeconds(2))).isFalse();
        assertThat(Recording.accepts(rule, state, 2.0d, base.plusSeconds(3))).isTrue();
        assertThat(Recording.accepts(rule, state, 3.0d, base.plusSeconds(4))).isFalse();
    }

    @Test
    void samplingEveryOneOrUnsetRecordsEveryValue() {
        SamplingRule everyOne = F.createSamplingRule();
        everyOne.setEvery(1);
        SamplingRule unset = F.createSamplingRule();
        Recording.State one = new Recording.State();
        Recording.State zero = new Recording.State();
        assertThat(Recording.accepts(everyOne, one, 1.0d, base)).isTrue();
        assertThat(Recording.accepts(everyOne, one, 1.0d, base.plusSeconds(1))).isTrue();
        assertThat(Recording.accepts(unset, zero, 1.0d, base)).isTrue();
        assertThat(Recording.accepts(unset, zero, 1.0d, base.plusSeconds(1))).isTrue();
    }

    @Test
    void throttleMeasuresTheTimeSinceTheLastRecordedValue() {
        RecordingRule rule = throttle("PT10M");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 1.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 2.0d, base.plus(Duration.ofMinutes(9)))).isFalse();
        assertThat(Recording.accepts(rule, state, 3.0d, base.plus(Duration.ofMinutes(10)))).isTrue();
        assertThat(Recording.accepts(rule, state, 4.0d, base.plus(Duration.ofMinutes(19))))
                .as("the window restarts at the recorded value").isFalse();
    }

    @Test
    void throttleDropsAValueOlderThanTheRecordedOne() {
        RecordingRule rule = throttle("PT10M");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 1.0d, base.plus(Duration.ofMinutes(30)))).isTrue();
        assertThat(Recording.accepts(rule, state, 2.0d, base)).isFalse();
    }

    @Test
    void aNumericRuleRecordsValuesThatAreNoNumbers() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, "OPEN", base)).isTrue();
        assertThat(Recording.accepts(rule, state, "OPEN", base.plusSeconds(1))).as("never drops").isTrue();
        assertThat(Recording.accepts(rule, state, "CLOSED", base.plusSeconds(2))).isTrue();
    }

    @Test
    void aRuleWhoseParameterDoesNotParseRecordsEveryValue() {
        RecordingRule rule = relative("five");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 100.0d, base.plusSeconds(1))).isTrue();
    }

    @Test
    void stateIsPerElement() {
        RecordingRule rule = relative("10.0");
        Recording.State first = new Recording.State();
        Recording.State second = new Recording.State();
        assertThat(Recording.accepts(rule, first, 100.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, second, 5000.0d, base)).as("its own first value").isTrue();
        assertThat(Recording.accepts(rule, first, 101.0d, base.plusSeconds(1))).isFalse();
        assertThat(Recording.accepts(rule, second, 5050.0d, base.plusSeconds(1))).isFalse();
    }

    @Test
    void resetMakesTheNextValueTheFirst() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 101.0d, base.plusSeconds(1))).isFalse();
        state.reset();
        assertThat(Recording.accepts(rule, state, 101.0d, base.plusSeconds(2))).isTrue();
    }

    @Test
    void withoutTimestampTheValueIsDatedNow() {
        RecordingRule rule = throttle("PT10M");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 1.0d, null)).isTrue();
        assertThat(Recording.accepts(rule, state, 2.0d, null)).as("both now, inside the window").isFalse();
        assertThat(state.lastTimestamp()).isNotNull();
    }

    @Test
    void theStateIsRequired() {
        assertThatThrownBy(() -> Recording.accepts(relative("1"), null, 1.0d, base))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void heartbeatRecordsAnUnchangedValueOncePerInterval() {
        RecordingRule rule = relative("10.0");
        rule.setMaximumInterval("PT1H");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 100.0d, base.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(Recording.accepts(rule, state, 100.0d, base.plus(Duration.ofMinutes(60)))).isTrue();
        assertThat(Recording.accepts(rule, state, 101.0d, base.plus(Duration.ofMinutes(61)))).isFalse();
    }

    @Test
    void changedValueRuleRecordsChangesOnly() {
        ChangedValueRule rule = F.createChangedValueRule();
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, "OPEN", base)).isTrue();
        assertThat(Recording.accepts(rule, state, "OPEN", base.plusSeconds(1))).isFalse();
        assertThat(Recording.accepts(rule, state, "CLOSED", base.plusSeconds(2))).isTrue();
        assertThat(Recording.accepts(rule, state, 5, base.plusSeconds(3))).isTrue();
        assertThat(Recording.accepts(rule, state, 5.0d, base.plusSeconds(4))).as("numbers by value").isFalse();
    }

    @Test
    void minimumIntervalDeadbandAndHeartbeatCombineInOneRule() {
        AbsoluteChangeRule rule = F.createAbsoluteChangeRule();
        rule.setDelta("2.0");
        rule.setMinimumInterval("PT1M");
        rule.setMaximumInterval("PT10M");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 10.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 20.0d, base.plusSeconds(30))).as("a jump, but too soon").isFalse();
        assertThat(Recording.accepts(rule, state, 20.0d, base.plusSeconds(60))).as("a jump, a minute later").isTrue();
        assertThat(Recording.accepts(rule, state, 21.0d, base.plusSeconds(300))).as("inside the dead band").isFalse();
        assertThat(Recording.accepts(rule, state, 21.0d, base.plusSeconds(660))).as("heartbeat").isTrue();
    }

    @Test
    void rangeRuleMeasuresThePercentOfTheRange() {
        RangeChangeRule rule = F.createRangeChangeRule();
        rule.setPercent("1.0");
        rule.setLow("-20.0");
        rule.setHigh("80.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 20.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 20.9d, base.plusSeconds(1))).as("0.9 of a range of 100").isFalse();
        assertThat(Recording.accepts(rule, state, 21.0d, base.plusSeconds(2))).as("1 % of the range").isTrue();
        assertThat(Recording.accepts(rule, state, 20.5d, base.plusSeconds(3))).isFalse();
    }

    @Test
    void aRangeThatIsNoRangeRecordsEveryValue() {
        RangeChangeRule rule = F.createRangeChangeRule();
        rule.setPercent("1.0");
        rule.setLow("80.0");
        rule.setHigh("-20.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 20.0d, base)).isTrue();
        assertThat(Recording.accepts(rule, state, 20.0d, base.plusSeconds(1))).isTrue();
    }

    @Test
    void aChangedStatusRecordsAnUnchangedValue() {
        RecordingRule rule = relative("10.0");
        rule.setStatusFeature(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory.eINSTANCE.createAttribute());
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, "good", base)).isTrue();
        assertThat(Recording.accepts(rule, state, 100.0d, "good", base.plusSeconds(1))).isFalse();
        assertThat(Recording.accepts(rule, state, 100.0d, "uncertain", base.plusSeconds(2))).isTrue();
        assertThat(Recording.accepts(rule, state, 101.0d, "uncertain", base.plusSeconds(3))).isFalse();
        assertThat(state.lastStatus()).isEqualTo("uncertain");
    }

    @Test
    void withoutAStatusFeatureTheStatusDoesNotCount() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 100.0d, "good", base)).isTrue();
        assertThat(Recording.accepts(rule, state, 100.0d, "bad", base.plusSeconds(1))).isFalse();
    }

    @Test
    void aLateValueIsJudgedLikeAnyOtherByDefault() {
        AbsoluteChangeRule rule = F.createAbsoluteChangeRule();
        rule.setDelta("2.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.accepts(rule, state, 50.0d, base.plusSeconds(60))).isTrue();
        assertThat(Recording.accepts(rule, state, 51.0d, base)).as("late, inside the dead band").isFalse();
        assertThat(Recording.accepts(rule, state, 55.0d, base)).as("late, outside the dead band").isTrue();
        assertThat(state.lastTimestamp()).isEqualTo(base);
    }

    @Test
    void aLateValueIsDroppedOrInserted() {
        AbsoluteChangeRule drop = F.createAbsoluteChangeRule();
        drop.setDelta("2.0");
        drop.setLateValues(LateValueHandling.DROP);
        Recording.State dropping = new Recording.State();
        assertThat(Recording.accepts(drop, dropping, 50.0d, base.plusSeconds(60))).isTrue();
        assertThat(Recording.accepts(drop, dropping, 55.0d, base)).isFalse();

        AbsoluteChangeRule insert = F.createAbsoluteChangeRule();
        insert.setDelta("2.0");
        insert.setLateValues(LateValueHandling.INSERT);
        Recording.State inserting = new Recording.State();
        assertThat(Recording.accepts(insert, inserting, 50.0d, base.plusSeconds(60))).isTrue();
        assertThat(Recording.accepts(insert, inserting, 50.0d, base)).as("inserted, however small").isTrue();
        assertThat(inserting.lastTimestamp()).as("the newest value stays the last one").isEqualTo(base.plusSeconds(60));
        assertThat(Recording.accepts(insert, inserting, 51.0d, base.plusSeconds(61))).isFalse();
    }

    /**
     * A ramp of slope 0.2 within a deviation of 0.5, then a step: the ramp needs only its two ends,
     * the step its corner. The held value is recorded when the value after it shows that no line
     * fits any longer.
     */
    @Test
    void swingingDoorKeepsTheCornersOfTheCurve() {
        SwingingDoorRule rule = swingingDoor("0.5");
        Recording.State state = new Recording.State();
        List<Sample> recorded = new ArrayList<>();
        double[] values = { 0.0, 0.2, 0.4, 0.6, 0.8, 3.0, 3.0, 3.0 };
        List<Decision> decisions = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            Decision d = Recording.decide(rule, state, values[i], null, base.plusSeconds(i));
            decisions.add(d);
            state.released().ifPresent(recorded::add);
            if (d.recordsValue()) {
                recorded.add(new Sample(values[i], null, base.plusSeconds(i)));
            }
        }
        Recording.flush(state).ifPresent(recorded::add);
        assertThat(decisions).containsExactly(Decision.RECORD, Decision.HOLD, Decision.HOLD, Decision.HOLD,
                Decision.HOLD, Decision.RECORD_HELD_THEN_HOLD, Decision.RECORD_HELD_THEN_HOLD, Decision.HOLD);
        assertThat(recorded).extracting(Sample::value).containsExactly(0.0, 0.8, 3.0, 3.0);
        assertThat(recorded).extracting(Sample::timestamp).containsExactly(base, base.plusSeconds(4),
                base.plusSeconds(5), base.plusSeconds(7));
        assertThat(state.held()).isEmpty();
    }

    @Test
    void swingingDoorKeepsAFlatLineAsOneValue() {
        SwingingDoorRule rule = swingingDoor("0.5");
        Recording.State state = new Recording.State();
        assertThat(Recording.decide(rule, state, 5.0d, null, base)).isEqualTo(Decision.RECORD);
        for (int i = 1; i < 100; i++) {
            assertThat(Recording.decide(rule, state, 5.0d + (i % 2) * 0.4d, null, base.plusSeconds(i)))
                    .isEqualTo(Decision.HOLD);
        }
        assertThat(state.held()).map(Sample::timestamp).contains(base.plusSeconds(99));
    }

    @Test
    void swingingDoorHeartbeatRecordsTheHeldValueAndTheValue() {
        SwingingDoorRule rule = swingingDoor("0.5");
        rule.setMaximumInterval("PT1M");
        Recording.State state = new Recording.State();
        Recording.decide(rule, state, 5.0d, null, base);
        assertThat(Recording.decide(rule, state, 5.1d, null, base.plusSeconds(30))).isEqualTo(Decision.HOLD);
        assertThat(Recording.decide(rule, state, 5.2d, null, base.plusSeconds(60)))
                .isEqualTo(Decision.RECORD_HELD_AND_VALUE);
        assertThat(state.released()).map(Sample::value).contains(5.1d);
        assertThat(state.lastValue()).isEqualTo(5.2d);
        assertThat(Recording.decide(rule, state, 5.2d, null, base.plusSeconds(120))).as("nothing held")
                .isEqualTo(Decision.RECORD);
    }

    @Test
    void swingingDoorDropsALateValueUnlessItIsInserted() {
        SwingingDoorRule rule = swingingDoor("0.5");
        Recording.State state = new Recording.State();
        Recording.decide(rule, state, 5.0d, null, base);
        Recording.decide(rule, state, 5.1d, null, base.plusSeconds(10));
        assertThat(Recording.decide(rule, state, 9.0d, null, base.plusSeconds(10))).as("not newer than the held one")
                .isEqualTo(Decision.DROP);
        rule.setLateValues(LateValueHandling.INSERT);
        assertThat(Recording.decide(rule, state, 9.0d, null, base.plusSeconds(5))).isEqualTo(Decision.RECORD);
        assertThat(state.held()).map(Sample::value).contains(5.1d);
    }

    @Test
    void swingingDoorRecordsAValueThatIsNoNumber() {
        SwingingDoorRule rule = swingingDoor("0.5");
        Recording.State state = new Recording.State();
        Recording.decide(rule, state, 5.0d, null, base);
        Recording.decide(rule, state, 5.1d, null, base.plusSeconds(1));
        assertThat(Recording.decide(rule, state, "offline", null, base.plusSeconds(2)))
                .isEqualTo(Decision.RECORD_HELD_AND_VALUE);
        assertThat(Recording.flush(state)).isEmpty();
    }

    @Test
    void everyOtherRuleRecordsOrDrops() {
        RecordingRule rule = relative("10.0");
        Recording.State state = new Recording.State();
        assertThat(Recording.decide(rule, state, 100.0d, null, base)).isEqualTo(Decision.RECORD);
        assertThat(Recording.decide(rule, state, 101.0d, null, base.plusSeconds(1))).isEqualTo(Decision.DROP);
        assertThat(state.held()).isEmpty();
        assertThat(Decision.RECORD.recordsValue()).isTrue();
        assertThat(Decision.RECORD_HELD_THEN_HOLD.recordsValue()).isFalse();
        assertThat(Decision.RECORD_HELD_THEN_HOLD.recordsHeld()).isTrue();
        assertThat(Decision.HOLD.recordsHeld()).isFalse();
    }

    private static SwingingDoorRule swingingDoor(String deviation) {
        SwingingDoorRule rule = F.createSwingingDoorRule();
        rule.setDeviation(deviation);
        return rule;
    }

    private static RelativeChangeRule relative(String percent) {
        RelativeChangeRule rule = F.createRelativeChangeRule();
        rule.setPercent(percent);
        return rule;
    }

    private static ThrottleRule throttle(String interval) {
        ThrottleRule rule = F.createThrottleRule();
        rule.setMinimumInterval(interval);
        return rule;
    }
}
