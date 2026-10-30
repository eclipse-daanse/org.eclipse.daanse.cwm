/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.datatype.RecurringType;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.events.util.EventsValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The month, day-of-month, hour and minute rules of RecurringPointInTimeEvent
 * through the OCL validation delegate. These are optional Integers, mapped to
 * the primitive long: for month and day of month 0 is "not specified", for
 * hour and minute 0 is a valid value.
 */
class RecurringPointInTimeEventConstraintsTest {

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void dailyWithoutMonthAndDay() {
        assertThat(violated(event(RecurringType.EVERY_DAY, 0, 0, 2, 30))).isEmpty();
    }

    @Test
    void dailyAtMidnight() {
        assertThat(violated(event(RecurringType.EVERY_DAY, 0, 0, 0, 0))).isEmpty();
    }

    @Test
    void yearlyOnTwentiethOfSeptember() {
        assertThat(violated(event(RecurringType.EVERY_YEAR, 9, 20, 6, 0))).isEmpty();
    }

    @Test
    void yearlyWithoutMonthAndDayIsViolated() {
        assertThat(violated(event(RecurringType.EVERY_YEAR, 0, 0, 6, 0)))
                .containsExactlyInAnyOrder("MonthRequiredForEveryYear", "DayOfMonthRequired");
    }

    @Test
    void valuesOutOfRangeAreViolated() {
        assertThat(violated(event(RecurringType.EVERY_YEAR, 13, 32, 24, 60))).containsExactlyInAnyOrder(
                "MonthValidRange", "DayOfMonthValidRange", "HourValidRange", "MinuteValidRange");
    }

    private static RecurringPointInTimeEvent event(RecurringType type, long month, long dayOfMonth, long hour,
            long minute) {
        RecurringPointInTimeEvent event = EventsFactory.eINSTANCE.createRecurringPointInTimeEvent();
        event.setRecurringType(type);
        event.setMonth(month);
        event.setDayOfMonth(dayOfMonth);
        event.setHour(hour);
        event.setMinute(minute);
        return event;
    }

    /** The names of the violated rules among the six this test is about. */
    private static List<String> violated(RecurringPointInTimeEvent event) {
        EventsValidator validator = EventsValidator.INSTANCE;
        Map<Object, Object> context = new HashMap<>();
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        validator.validateRecurringPointInTimeEvent_MonthRequiredForEveryYear(event, diagnostic, context);
        validator.validateRecurringPointInTimeEvent_MonthValidRange(event, diagnostic, context);
        validator.validateRecurringPointInTimeEvent_DayOfMonthRequired(event, diagnostic, context);
        validator.validateRecurringPointInTimeEvent_DayOfMonthValidRange(event, diagnostic, context);
        validator.validateRecurringPointInTimeEvent_HourValidRange(event, diagnostic, context);
        validator.validateRecurringPointInTimeEvent_MinuteValidRange(event, diagnostic, context);
        List<String> violated = new ArrayList<>();
        for (Diagnostic child : diagnostic.getChildren()) {
            assertThat(child.getMessage()).contains("constraint is violated");
            violated.add(child.getMessage().replaceAll(".*?'(\\w+)' constraint is violated.*", "$1"));
        }
        return violated;
    }
}
