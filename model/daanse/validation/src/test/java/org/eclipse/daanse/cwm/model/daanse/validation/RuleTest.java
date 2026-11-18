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

class RuleTest {

    @Test
    void aFindingOfARuleCarriesItsModuleCodeAndSeverity() {
        Package gotha = CoreFactory.eINSTANCE.createPackage();
        gotha.setName("Gotha");
        Rule rule = Rule.warning("units", "U9", "A unit of a measure is one the catalog knows.");

        Finding finding = rule.at(gotha, "the unit of Amount in Gotha is unknown");

        assertThat(rule.id()).as("the id of the rule").isEqualTo("units/U9");
        assertThat(finding.id()).as("the id of its finding").isEqualTo(rule.id());
        assertThat(finding.severity()).as("the severity of its finding").isEqualTo(Severity.WARNING);
        assertThat(finding.element()).as("the element").isSameAs(gotha);
        assertThat(finding.message()).as("the message").isEqualTo("the unit of Amount in Gotha is unknown");
    }

    @Test
    void eachSeverityHasItsMaker() {
        assertThat(List.of(Rule.error("feature", "FC1", "a"), Rule.warning("feature", "FC2", "b"),
                Rule.info("feature", "FC30", "c"))).as("the severities").extracting(Rule::severity)
                .containsExactly(Severity.ERROR, Severity.WARNING, Severity.INFO);
    }

    @Test
    void aRuleIsRefusedWithoutAModuleACodeOrASentence() {
        assertThatThrownBy(() -> Rule.error("Feature", "FC1", "a")).as("a module in capitals")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Feature");
        assertThatThrownBy(() -> Rule.error("feature", "FC-1", "a")).as("a code with a dash")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FC-1");
        assertThatThrownBy(() -> Rule.error("feature", "FC1", " ")).as("a blank sentence")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("feature/FC1");
        assertThatThrownBy(() -> new Rule("feature", "FC1", null, "a")).as("no severity")
                .isInstanceOf(NullPointerException.class).hasMessageContaining("feature/FC1");
    }
}
