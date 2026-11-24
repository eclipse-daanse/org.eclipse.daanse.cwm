/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPackage;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.ecore.OCL;
import org.eclipse.ocl.helper.OCLHelper;
import org.junit.jupiter.api.Test;

/**
 * Where each rule of {@link SpatialModelValidator} is checked: as an OCL invariant of the metamodel, or in Java
 * ({@link SpatialModelValidator#CHECKED_IN_JAVA}), never both and never neither; and the rule table and the list of the
 * rules in Java of the README say the same, so that README, metamodel and validator cannot drift.
 */
class SpatialRulesTest {

    private static final String OCL_SOURCE = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final Pattern ROW = Pattern.compile("\\| (SP\\d+) \\| (error|warning) \\| (model|Java) \\| .+ \\|");
    private static final Pattern JAVA_ONLY = Pattern.compile("- (SP\\d+): .+");

    /** The invariants of the metamodel by rule: the classes that state each. */
    private static Map<String, List<EClass>> invariants() {
        Map<String, List<EClass>> invariants = new LinkedHashMap<>();
        for (EClassifier c : SpatialPackage.eINSTANCE.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (String name : EcoreUtil.getConstraints(cls)) {
                    invariants.computeIfAbsent(name, k -> new ArrayList<>()).add(cls);
                }
            }
        }
        return invariants;
    }

    @Test
    void everyRuleIsAnInvariantOrCheckedInJavaNeverBoth() {
        Map<String, List<EClass>> invariants = invariants();
        List<String> inJava = SpatialModelValidator.CHECKED_IN_JAVA.stream().map(Rule::code).toList();
        assertThat(SpatialModelValidator.RULES).allSatisfy(rule -> assertThat(invariants.containsKey(rule.code()))
                .as(rule.code() + " is an invariant exactly where it is not checked in Java")
                .isNotEqualTo(inJava.contains(rule.code())));
        assertThat(invariants.keySet()).as("every invariant is named by a rule of the validator")
                .isSubsetOf(SpatialModelValidator.RULES.stream().map(Rule::code).toList());
        assertThat(inJava).as("the rules in Java are rules of the validator, each once").doesNotHaveDuplicates()
                .isSubsetOf(SpatialModelValidator.RULES.stream().map(Rule::code).toList());
    }

    /** Each invariant states the short name, the sentence and the level of its rule, as the validator does. */
    @Test
    void everyInvariantStatesItsRule() {
        Map<String, Rule> rules = new LinkedHashMap<>();
        SpatialModelValidator.RULES.forEach(rule -> rules.put(rule.code(), rule));
        invariants().forEach((code, classes) -> {
            for (EClass c : classes) {
                EAnnotation metadata = c.getEAnnotation(SpatialPackage.eNS_URI + Findings.CONSTRAINTS);
                assertThat(metadata).as(c.getName() + " states the metadata of its invariants").isNotNull();
                assertThat(metadata.getDetails().get(code + ".name")).as(code + " of " + c.getName()).isNotBlank();
                assertThat(metadata.getDetails().get(code + ".description")).as(code + " of " + c.getName())
                        .isEqualTo(rules.get(code).says());
                assertThat(metadata.getDetails().get(code + ".level")).as(code + " of " + c.getName())
                        .isEqualTo(rules.get(code).severity().name().toLowerCase(Locale.ROOT));
            }
        });
    }

    /** Every invariant is OCL the classic Eclipse OCL reads in the context of its class. */
    @Test
    void everyInvariantParses() {
        OCL ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
        invariants().forEach((code, classes) -> {
            for (EClass c : classes) {
                OCLHelper<EClassifier, ?, ?, Constraint> helper = ocl.createOCLHelper();
                helper.setContext(c);
                String body = c.getEAnnotation(OCL_SOURCE).getDetails().get(code);
                try {
                    helper.createInvariant(body);
                } catch (Exception e) {
                    throw new AssertionError(code + " of " + c.getName() + " does not parse: " + e.getMessage(), e);
                }
            }
        });
    }

    /** The rule table of the README names each rule with its severity and where it is checked. */
    @Test
    void theReadmeSaysWhereEachRuleIsChecked() throws IOException {
        Map<String, List<EClass>> invariants = invariants();
        List<String> rows = new ArrayList<>();
        List<String> javaOnly = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("user.dir"), "README.md"))) {
            Matcher row = ROW.matcher(line);
            if (row.matches()) {
                Rule rule = SpatialModelValidator.RULES.stream().filter(r -> r.code().equals(row.group(1)))
                        .findFirst().orElseThrow(() -> new AssertionError("no rule " + row.group(1)));
                assertThat(row.group(2)).as(rule.code()).isEqualTo(rule.severity().name().toLowerCase(Locale.ROOT));
                assertThat(row.group(3)).as("where " + rule.code() + " is checked")
                        .isEqualTo(invariants.containsKey(rule.code()) ? "model" : "Java");
                rows.add(rule.code());
            }
            Matcher item = JAVA_ONLY.matcher(line);
            if (item.matches()) {
                javaOnly.add(item.group(1));
            }
        }
        assertThat(rows).as("the rule table").containsExactlyElementsOf(
                SpatialModelValidator.RULES.stream().map(Rule::code).toList());
        assertThat(javaOnly).as("the rules in Java, each with its reason").containsExactlyElementsOf(
                SpatialModelValidator.CHECKED_IN_JAVA.stream().map(Rule::code).toList());
    }
}
