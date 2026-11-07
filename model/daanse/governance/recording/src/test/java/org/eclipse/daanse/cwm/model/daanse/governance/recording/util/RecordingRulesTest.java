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

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.Interpolation;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;
import org.junit.jupiter.api.Test;

/** The helpers that create recording and historisation rules, their text, and which rule applies. */
class RecordingRulesTest {

    private final Shop shop = new Shop();

    @Test
    void helpersOwnNameConstrainAndWriteTheBody() {
        AbsoluteChangeRule rule = RecordingRules.absolute(shop.sensors, "reading-2", 2.0, shop.reading, shop.level);
        assertThat(rule.getName()).isEqualTo("reading-2");
        assertThat(rule.getNamespace()).isSameAs(shop.sensors);
        assertThat(rule.getConstrainedElement()).containsExactly(shop.reading, shop.level);
        assertThat(shop.reading.getConstraint()).containsExactly(rule);
        assertThat(rule.getScope()).isEqualTo(DataScope.VALUE);
        assertThat(rule.getLateValues()).isEqualTo(LateValueHandling.COMPARE);
        assertThat(rule.getInterpolation()).isEqualTo(Interpolation.LINEAR);
        assertThat(rule.getBody().getLanguage()).isEqualTo(LifecycleRules.LANGUAGE);
        assertThat(rule.getBody().getBody()).isEqualTo("abs(value - last) >= 2.0");
    }

    @Test
    void theTextOfEachRule() {
        assertThat(text(RecordingRules.relative(shop.sensors, "a", 5.0, shop.reading)))
                .isEqualTo("abs(value - last) >= 5.0 % of abs(last)");
        assertThat(text(RecordingRules.range(shop.sensors, "b", 1.0, -20.0, 80.0, shop.reading)))
                .isEqualTo("abs(value - last) >= 1.0 % of (80.0 - -20.0)");
        assertThat(text(RecordingRules.sampling(shop.sensors, "c", 10, shop.reading)))
                .isEqualTo("one value out of every 10");
        assertThat(text(RecordingRules.throttle(shop.sensors, "d", Duration.ofMinutes(10), shop.reading)))
                .isEqualTo("time - lastTime >= PT10M");
        assertThat(text(RecordingRules.changedValue(shop.sensors, "e", shop.state))).isEqualTo("value <> last");
        assertThat(text(RecordingRules.swingingDoor(shop.sensors, "f", 0.5, shop.level)))
                .isEqualTo("swinging door within 0.5");
    }

    @Test
    void modifiersKeepTheBodyInStep() {
        RelativeChangeRule rule = RecordingRules.heartbeat(RecordingRules.relative(shop.sensors, "pct-5", 5.0,
                shop.reading), Duration.ofHours(1));
        assertThat(text(rule)).isEqualTo("(abs(value - last) >= 5.0 % of abs(last)) or time - lastTime >= PT1H");
        RecordingRules.status(rule, shop.quality);
        assertThat(text(rule)).isEqualTo("(abs(value - last) >= 5.0 % of abs(last)) or time - lastTime >= PT1H "
                + "or quality changed");
        RecordingRules.minimumInterval(rule, Duration.ofMinutes(1));
        RecordingRules.lateValues(rule, LateValueHandling.DROP);
        RecordingRules.interpolation(rule, Interpolation.STEPPED);
        assertThat(text(rule)).isEqualTo("time - lastTime >= PT1M and ((abs(value - last) >= 5.0 % of abs(last)) "
                + "or time - lastTime >= PT1H or quality changed); late values: drop; read stepped");
        assertThat(rule.getStatusFeature()).isSameAs(shop.quality);

        ThrottleRule throttle = RecordingRules.throttle(shop.sensors, "every-10m", Duration.ofMinutes(10), shop.level);
        assertThat(text(throttle)).as("the minimum interval is the criterion of a throttle")
                .isEqualTo("time - lastTime >= PT10M");
    }

    @Test
    void theNearestRecordingRuleApplies() {
        RecordingRule onModel = RecordingRules.changedValue(shop.model, "changes", shop.model);
        RecordingRule onClass = RecordingRules.throttle(shop.sensors, "meter-1m", Duration.ofMinutes(1), shop.meter);
        RecordingRule onFeature = RecordingRules.absolute(shop.sensors, "reading-2", 2.0, shop.reading);
        RetentionSchedules.values(shop.sensors, "keep-90d", "P90D", shop.reading);

        assertThat(RecordingRules.effective(shop.reading)).containsSame(onFeature);
        assertThat(RecordingRules.effective(shop.state)).containsSame(onClass);
        assertThat(RecordingRules.effective(shop.level)).containsSame(onModel);
        assertThat(RetentionSchedules.effective(shop.reading, DataScope.VALUE)).as("each kind resolves on its own")
                .isPresent();
        assertThat(RecordingRules.effective(new Shop().reading)).isEmpty();
    }

    @Test
    void aRuleConstrainsAtLeastOneElement() {
        assertThatThrownBy(() -> RecordingRules.changedValue(shop.sensors, "none"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecordingRules.status(RecordingRules.changedValue(shop.sensors, "s", shop.state), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void historisationOfRowsAndOfValues() {
        HistorisationRule versions = HistorisationRules.currentFlag(HistorisationRules.validity(
                HistorisationRules.rows(shop.crm, "customer-versions", HistorisationKind.NEW_VERSION, shop.customer),
                shop.validFrom, shop.validTo), shop.isCurrent);
        HistorisationRule previous = HistorisationRules.previousValue(HistorisationRules.values(shop.crm,
                "city-previous", HistorisationKind.PREVIOUS_VALUE, shop.city), shop.previousCity);
        HistorisationRule table = HistorisationRules.history(HistorisationRules.values(shop.crm, "name-history",
                HistorisationKind.HISTORY_TABLE, shop.name), shop.customerHistory);
        Shop other = new Shop();
        HistorisationRule current = HistorisationRules.currentValue(HistorisationRules.values(other.crm,
                "city-current", HistorisationKind.VERSION_AND_CURRENT, other.city), other.currentCity);

        assertThat(versions.getScope()).isEqualTo(DataScope.ROW);
        assertThat(text(versions)).isEqualTo("rows: newVersion; valid from validFrom to validTo; current isCurrent");
        assertThat(text(previous)).isEqualTo("values: previousValue; previous in previousCity");
        assertThat(text(table)).isEqualTo("values: historyTable; history in CustomerHistory");
        assertThat(text(current)).isEqualTo("values: versionAndCurrent; current value in currentCity");
        assertThat(HistorisationRules.effective(shop.city, DataScope.ROW)).containsSame(versions);
        assertThat(HistorisationRules.effective(shop.city, DataScope.VALUE)).containsSame(previous);
        assertThat(HistorisationRules.effective(shop.validTo, DataScope.VALUE)).isEmpty();
        assertThat(RecordingRules.effective(shop.city)).as("a historisation rule is no recording rule").isEmpty();
    }

    private static String text(RecordingRule rule) {
        assertThat(rule.getBody().getBody()).isEqualTo(RecordingRules.render(rule));
        return rule.getBody().getBody();
    }

    private static String text(HistorisationRule rule) {
        assertThat(rule.getBody().getBody()).isEqualTo(HistorisationRules.render(rule));
        return rule.getBody().getBody();
    }
}
