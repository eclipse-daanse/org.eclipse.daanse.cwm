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
package org.eclipse.daanse.cwm.model.daanse.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.junit.jupiter.api.Test;

/**
 * A finding names its rule by module and code, weighs as an error, a warning or a note, and refuses what would not read
 * back as a rule id.
 */
class FindingTest {

    private final Package erfurt = named("Erfurt");

    @Test
    void theIdIsModuleAndRule() {
        Finding finding = Finding.error("spatial", "SP12", erfurt, "the relation relates two systems without a "
                + "working one");

        assertThat(finding.id()).as("id").isEqualTo("spatial/SP12");
        assertThat(finding.module()).as("module").isEqualTo("spatial");
        assertThat(finding.rule()).as("rule").isEqualTo("SP12");
        assertThat(finding.severity()).as("severity").isEqualTo(Severity.ERROR);
        assertThat(finding.isError()).as("an error").isTrue();
        assertThat(finding.element()).as("element").isSameAs(erfurt);
    }

    @Test
    void eachSeverityHasItsMaker() {
        assertThat(List.of(Finding.error("units", "U1", erfurt, "a"), Finding.warning("units", "U9", erfurt, "b"),
                Finding.info("units", "U10", null, "c"))).as("severities").extracting(Finding::severity)
                .containsExactly(Severity.ERROR, Severity.WARNING, Severity.INFO);
        assertThat(Finding.warning("units", "U9", erfurt, "b").isError()).as("a warning is no error").isFalse();
    }

    @Test
    void theLineIsRuleAndMessageAndTheTextAddsSeverityAndModule() {
        Finding finding = Finding.warning("recording", "R8", erfurt, "the anchor of Weimar is no time");

        assertThat(finding.line()).as("line").startsWith("R8 ").endsWith(finding.message());
        assertThat(finding.toString()).as("text").startsWith("WARNING recording/R8: ").endsWith(finding.message());
    }

    @Test
    void aModuleOrRuleThatIsNoIdIsRefused() {
        assertThatThrownBy(() -> Finding.error("Spatial", "SP1", erfurt, "m")).as("a module in capitals")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("module 'Spatial'");
        assertThatThrownBy(() -> Finding.error("spatial", "SP 1", erfurt, "m")).as("a rule with a blank")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rule 'SP 1'");
        assertThatThrownBy(() -> Finding.error("spatial", "SP1", erfurt, " ")).as("a blank message")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("spatial/SP1 says nothing");
        assertThatThrownBy(() -> new Finding("spatial", "SP1", null, erfurt, "m")).as("no severity")
                .isInstanceOf(NullPointerException.class).hasMessageContaining("severity");
    }

    @Test
    void theLinesOfTheErrorsLeaveTheWarningsOut() {
        List<Finding> found = List.of(Finding.error("gdpr", "G1", erfurt, "first"),
                Finding.warning("gdpr", "G2", erfurt, "second"), Finding.error("gdpr", "G3", erfurt, "third"));

        assertThat(Findings.errorLines(found)).as("errors").containsExactly(found.get(0).line(), found.get(2).line());
        assertThat(Findings.lines(found)).as("all").hasSize(3);
    }

    private static Package named(String name) {
        Package p = CoreFactory.eINSTANCE.createPackage();
        p.setName(name);
        return p;
    }
}
