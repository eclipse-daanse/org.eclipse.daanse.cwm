/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionClass;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.junit.jupiter.api.Test;

/** The helpers that create duties, sources, holds and classes, and the text of each. */
class ObligationsTest {

    private final Lab lab = new Lab();

    @Test
    void helpersOwnNameGovernAndWriteTheBody() {
        RetentionObligation invoices = Obligations.retain(lab.governance, "invoices-8y", "P8Y",
                PeriodStarts.aligned(PeriodStarts.feature(lab.issuedOn), Alignment.END_OF_YEAR, "Europe/Berlin"),
                lab.invoice);
        assertThat(invoices.getNamespace()).isSameAs(lab.governance);
        assertThat(invoices.getConstrainedElement()).containsExactly(lab.invoice);
        assertThat(invoices.getScope()).isEqualTo(DataScope.VALUE);
        assertThat(invoices.getBody().getLanguage()).isEqualTo(LifecycleRules.LANGUAGE);
        assertThat(invoices.getBody().getBody()).isEqualTo("keep at least P8Y from issuedOn, end of year in Europe/Berlin");
        Obligations.immutable(invoices);
        assertThat(invoices.getBody().getBody()).endsWith("; immutable");

        DeletionObligation customers = Obligations.delete(lab.governance, "customers-3y", "P3Y",
                PeriodStarts.event(lab.contractEnded, lab.contractEnd), lab.customer);
        assertThat(customers.getBody().getBody()).isEqualTo("delete within P3Y from event contractEnded (contractEnd)");
        Obligations.anonymizationSatisfies(customers);
        assertThat(customers.getBody().getBody()).endsWith("; anonymization satisfies");
        assertThat(customers.isAnonymizationSatisfies()).isTrue();
    }

    @Test
    void aPeriodIsAPeriod() {
        assertThatThrownBy(() -> Obligations.retain(lab.governance, "quarters", "P8Q", PeriodStarts.valueTime(),
                lab.invoice)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeletionClasses.create(lab.governance, "none", PeriodStarts.valueTime(), "soon",
                lab.invoice)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legalSourcesNameTheirDuties() {
        RetentionObligation invoices = Obligations.retain(lab.governance, "invoices-8y", "P8Y", PeriodStarts.valueTime(),
                lab.invoice);
        LegalSource ao = LegalSources.cite(LegalSources.validity(LegalSources.provision(LegalSources.create(lab.law,
                "ao-147", "https://www.gesetze-im-internet.de/ao_1977/__147.html", LegalSources.STATUTE), "§ 147 (3)",
                "DE"), "2025-01-01", null), invoices);
        assertThat(ao.getModelElement()).containsExactly(invoices);
        assertThat(LegalSources.of(invoices)).containsExactly(ao);
        assertThat(LegalSources.inForce(ao, Instant.parse("2026-11-06T00:00:00Z"))).isTrue();
        assertThat(LegalSources.inForce(ao, Instant.parse("2024-12-31T00:00:00Z"))).isFalse();
        LegalSources.validity(ao, "2010-01-01", "2025-01-01");
        assertThat(LegalSources.inForce(ao, Instant.parse("2026-11-06T00:00:00Z"))).as("superseded").isFalse();
        assertThat(LegalSources.instant("2026-11-01T10:00:00+01:00")).isEqualTo(Instant.parse("2026-11-01T09:00:00Z"));
        assertThat(LegalSources.instant("soon")).isNull();
    }

    @Test
    void holdsApplyAlongThePathWhileTheyLast() {
        RetentionHold audit = RetentionHolds.holder(RetentionHolds.create(lab.governance, "audit", "tax audit 2026",
                Instant.parse("2026-11-01T00:00:00Z"), lab.data), lab.legal);
        assertThat(audit.getBody().getBody()).isEqualTo("hold since 2026-11-01T00:00:00Z: tax audit 2026");
        assertThat(RetentionHolds.of(lab.amount)).containsExactly(audit);
        assertThat(RetentionHolds.active(lab.amount, Instant.parse("2026-10-31T00:00:00Z"))).isEmpty();
        assertThat(RetentionHolds.active(lab.amount, Instant.parse("2026-11-01T00:00:00Z"))).containsExactly(audit);
        RetentionHolds.release(audit, Instant.parse("2027-02-01T00:00:00Z"));
        assertThat(audit.getBody().getBody()).isEqualTo("hold since 2026-11-01T00:00:00Z until 2027-02-01T00:00:00Z: "
                + "tax audit 2026");
        assertThat(RetentionHolds.suspension(lab.amount).at(Instant.parse("2027-01-31T00:00:00Z"))).isTrue();
        assertThat(RetentionHolds.suspension(lab.amount).at(Instant.parse("2027-02-01T00:00:00Z"))).isFalse();
        assertThat(audit.getHolder()).isSameAs(lab.legal);
    }

    @Test
    void theNearestDeletionClassApplies() {
        DeletionClass logs = DeletionClasses.create(lab.governance, "logs", PeriodStarts.valueTime(), "P90D", lab.data);
        DeletionClass contracts = DeletionClasses.create(lab.governance, "contracts",
                PeriodStarts.event(lab.contractEnded), "P3Y", lab.customer);
        assertThat(DeletionClasses.effective(lab.contractEnd, DataScope.VALUE)).containsSame(contracts);
        assertThat(DeletionClasses.effective(lab.amount, DataScope.VALUE)).containsSame(logs);
        assertThat(DeletionClasses.effective(lab.amount, DataScope.ROW)).isEmpty();
        DeletionClasses.onRows(logs);
        assertThat(DeletionClasses.effective(lab.amount, DataScope.ROW)).containsSame(logs);
        assertThat(DeletionClasses.effective(lab.amount, DataScope.VALUE)).isEmpty();
        assertThat(contracts.getBody().getBody()).isEqualTo("class: delete P3Y after event contractEnded");
    }
}
