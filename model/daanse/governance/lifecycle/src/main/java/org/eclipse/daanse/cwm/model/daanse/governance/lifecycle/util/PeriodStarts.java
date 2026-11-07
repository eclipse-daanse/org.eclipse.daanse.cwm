/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Event;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.EventTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.ValueTime;

/**
 * Creates the starts of periods and computes from where the periods run: the start time moved to
 * the end of its month, quarter or year in the zone of the start, then by the offset.
 */
public final class PeriodStarts {

    private static final LifecycleFactory F = LifecycleFactory.eINSTANCE;

    private PeriodStarts() {
    }

    /** The periods start at the time of each value or row. */
    public static ValueTime valueTime() {
        return F.createValueTime();
    }

    /** The periods start at the time {@code feature} holds. */
    public static FeatureTime feature(StructuralFeature feature) {
        FeatureTime start = F.createFeatureTime();
        start.setFeature(Objects.requireNonNull(feature, "feature"));
        return start;
    }

    /** The periods start at {@code event}, abstractly: its time per row is not named. */
    public static EventTime event(Event event) {
        EventTime start = F.createEventTime();
        start.setEvent(Objects.requireNonNull(event, "event"));
        return start;
    }

    /** The periods start at {@code event}, whose time {@code feature} holds for each row. */
    public static EventTime event(Event event, StructuralFeature feature) {
        EventTime start = event(event);
        start.setFeature(Objects.requireNonNull(feature, "feature"));
        return start;
    }

    /** Moves the start to the end of its month, quarter or year in {@code zone} (an IANA zone id). */
    public static <S extends PeriodStart> S aligned(S start, Alignment alignment, String zone) {
        start.setAlignment(Objects.requireNonNull(alignment, "alignment"));
        start.setZone(ZoneId.of(Objects.requireNonNull(zone, "zone")).getId());
        return start;
    }

    /** Adds {@code period} (a {@code Period} literal) to the aligned start. */
    public static <S extends PeriodStart> S offset(S start, String period) {
        Periods.parse(period);
        start.setOffset(period);
        return start;
    }

    /** The zone of a start; UTC when it names none. */
    public static ZoneId zone(PeriodStart start) {
        return start.getZone() == null ? ZoneOffset.UTC : ZoneId.of(start.getZone());
    }

    /**
     * Where the periods run from, for data whose start time is {@code time}: the time moved to the
     * end of its month, quarter or year — the first instant of the next one, in the zone of the
     * start — and then by the offset.
     */
    public static ZonedDateTime begin(PeriodStart start, Instant time) {
        ZoneId zone = zone(start);
        ZonedDateTime t = Objects.requireNonNull(time, "time").atZone(zone);
        LocalDate day = t.toLocalDate();
        ZonedDateTime aligned = switch (start.getAlignment()) {
            case END_OF_MONTH -> day.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone);
            case END_OF_QUARTER -> day.withDayOfMonth(1).withMonth((day.getMonthValue() - 1) / 3 * 3 + 1).plusMonths(3)
                    .atStartOfDay(zone);
            case END_OF_YEAR -> day.withDayOfYear(1).plusYears(1).atStartOfDay(zone);
            default -> t;
        };
        return start.getOffset() == null ? aligned : Periods.parse(start.getOffset()).addTo(aligned);
    }

    /** Whether two starts are the same: of one kind, on the same feature or event, aligned and offset alike. */
    public static boolean same(PeriodStart a, PeriodStart b) {
        if (a == null || b == null || a.eClass() != b.eClass() || a.getAlignment() != b.getAlignment()
                || !Objects.equals(a.getOffset(), b.getOffset()) || !Objects.equals(a.getZone(), b.getZone())) {
            return false;
        }
        if (a instanceof FeatureTime x && b instanceof FeatureTime y) {
            return x.getFeature() == y.getFeature();
        }
        if (a instanceof EventTime x && b instanceof EventTime y) {
            return x.getEvent() == y.getEvent() && x.getFeature() == y.getFeature();
        }
        return true;
    }

    /**
     * The text of a start, e.g. {@code the time of the value} or
     * {@code document_date, end of year in Europe/Berlin, + P1M}.
     */
    public static String render(PeriodStart start) {
        StringBuilder sb = new StringBuilder();
        if (start instanceof FeatureTime f) {
            sb.append(f.getFeature() == null ? "?" : f.getFeature().getName());
        } else if (start instanceof EventTime e) {
            sb.append("event ").append(e.getEvent() == null ? "?" : e.getEvent().getName());
            if (e.getFeature() != null) {
                sb.append(" (").append(e.getFeature().getName()).append(')');
            }
        } else {
            sb.append("the time of the value");
        }
        if (start.getAlignment() != Alignment.NONE) {
            sb.append(", end of ").append(switch (start.getAlignment()) {
                case END_OF_MONTH -> "month";
                case END_OF_QUARTER -> "quarter";
                default -> "year";
            });
            if (start.getZone() != null) {
                sb.append(" in ").append(start.getZone());
            }
        }
        if (start.getOffset() != null) {
            sb.append(", + ").append(start.getOffset());
        }
        return sb.toString();
    }
}
