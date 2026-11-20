/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.base.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.junit.jupiter.api.Test;

/** Each rule of {@link SemanticTypeModelValidator}: a model that keeps it, and one that breaks it. */
class SemanticTypeModelValidatorTest {

    private final Register r = new Register();

    /** A type says a meaning: one type types a column (its values) and a table (its rows) alike. */
    @Test
    void typingsOfAnyElementKeepEveryRule() {
        Typings.create(r.types, "town", r.harbourTown, r.town, r.plainClass("Town"));
        Typings.create(r.types, "postcode", r.postalCode, r.postcodeType, r.postcode);
        Typings.create(r.types, "harbour", r.harbour, r.port, r.name);
        Typings.create(r.types, "quays", r.asset, r.quays);
        Typings.create(r.types, "dues", r.revenue, r.dues);
        assertThat(SemanticTypeModelValidator.check(r.model)).as("findings of a typed register").isEmpty();
        assertThat(SemanticTypeModelValidator.validate(r.model)).as("errors of a typed register").isEmpty();
    }

    @Test
    void st1UrisAreAbsoluteAndWellFormed() {
        SemanticTypes.uri(r.place, "https://types.example.org/harbour/place");
        SemanticTypes.sameAsExternal(r.townType, "https://types.example.org/gazetteer", "Town", null);
        assertThat(rules()).as("absolute URIs").isEmpty();
        r.library.setUri("harbour");
        assertOnly("ST1", "the URI harbour is no absolute, well-formed URI");
        r.library.setUri(Register.HARBOUR);
        SemanticTypes.uri(r.place, "https://types.example.org/a b");
        assertOnly("ST1", "is no absolute, well-formed URI");
        r.place.setUri(null);
        r.townType.getSameAsExternal().get(0).setLibraryUri("");
        assertOnly("ST1", "has no library URI of an external type");
    }

    @Test
    void st2NoTwoLibrariesOrTypesShareAUri() {
        TypeLibrary other = TypeLibraries.create(r.model, "harbourAgain", "https://types.example.org/other");
        SemanticTypes.create(other, "Quay");
        assertThat(rules()).as("libraries of their own URIs").isEmpty();
        other.setUri(Register.HARBOUR);
        assertThat(errors("ST2")).as("two libraries of one URI").hasSize(2);
        other.setUri("https://types.example.org/other");
        SemanticTypes.uri(SemanticTypes.create(other, "Wharf"), Register.HARBOUR + "/Town");
        assertThat(errors("ST2")).as("a stated URI that another type derives").hasSize(2)
                .allSatisfy(f -> assertThat(f.message()).contains(Register.HARBOUR + "/Town"));
    }

    @Test
    void st3ATypeLiesInALibrary() {
        assertThat(rules()).isEmpty();
        Packages.create(r.model, "loose").getOwnedElement().add(r.revenue);
        assertOnly("ST3", "lies in no library");
    }

    @Test
    void st4NamesAndNumbersAreUniqueInALibrary() {
        TypeLibrary other = TypeLibraries.create(r.model, "other", "https://types.example.org/other");
        SemanticTypes.source(SemanticTypes.create(other, "Town"), "Place", 1);
        SemanticTypes.source(r.place, "Place", 1);
        assertThat(rules()).as("one name, name there and number in two libraries").isEmpty();
        SemanticTypes.create(r.library, "Town");
        assertOnly("ST4", "has 2 types of the name Town");
        r.library.getOwnedElement().remove(r.library.getOwnedElement().size() - 1);
        SemanticTypes.sourceNumber(r.townType, 1);
        assertOnly("ST4", "of the number there 1");
        r.townType.unsetSourceNumber();
        SemanticTypes.sourceName(r.postalCode, "Place");
        assertOnly("ST4", "of the name there Place");
    }

    @Test
    void st5NoTypeIsNarrowerThanItself() {
        assertThat(rules()).isEmpty();
        SemanticTypes.broader(r.place, r.harbourTown);
        assertThat(errors("ST5")).as("the three types on the cycle").extracting(Finding::element)
                .containsExactlyInAnyOrder(r.place, r.townType, r.harbourTown);
    }

    @Test
    void st7TheSameTypeLiesElsewhere() {
        Model atlas = Register.atlas();
        TypeLibrary coast = TypeLibraries.byUri(atlas, "https://types.example.org/coast").orElseThrow();
        r.model.getOwnedElement().add(coast);
        SemanticType lighthouse = SemanticTypes.named(coast, "Lighthouse").orElseThrow();
        SemanticTypes.sameAs(r.harbourTown, lighthouse);
        SemanticTypes.sameAsExternal(r.townType, "https://types.example.org/gazetteer", null, 7L);
        assertThat(rules()).as("the same types in other libraries").isEmpty();
        SemanticTypes.sameAs(r.harbourTown, r.place);
        assertOnly("ST7", "of its own library");
        r.harbourTown.getSameAs().remove(r.place);
        SemanticTypes.sameAsExternal(r.place, "https://types.example.org/coast", "Lighthouse", null);
        assertOnly("ST7", "it lies in the model, sameAs names its type");
        r.place.getSameAsExternal().clear();
        SemanticTypes.sameAsExternal(r.place, Register.HARBOUR, "Place", null);
        assertOnly("ST7", "names its own library");
        r.place.getSameAsExternal().clear();
        r.townType.getSameAsExternal().get(0).unsetSourceNumber();
        assertOnly("ST7", "by neither name nor number");
    }

    @Test
    void st8ATypingNamesATypeOfTheModelOrOfADeliveredLibrary() {
        Model atlas = Register.atlas();
        TypeLibrary coast = TypeLibraries.byUri(atlas, "https://types.example.org/coast").orElseThrow();
        SemanticType lighthouse = SemanticTypes.named(coast, "Lighthouse").orElseThrow();
        Typing typing = Typings.create(r.types, "name", lighthouse, r.name);
        assertOnly("ST8", "neither in the model nor delivered");
        assertThat(SemanticTypeModelValidator.check(r.model, Set.of("https://types.example.org/coast")))
                .as("findings with the coast library delivered").isEmpty();
        SemanticTypes.broader(r.place, lighthouse);
        assertOnly("ST8", "the broader type atlas.coast.Lighthouse lies in the library");
        r.place.getBroader().clear();
        typing.setType(null);
        assertOnly("ST8", "names no type");
    }

    @Test
    void everyRuleIsStatedOnceWithOneSeverity() {
        assertThat(SemanticTypeModelValidator.RULES).as("the rules").extracting(Rule::code).doesNotHaveDuplicates()
                .containsExactly("ST1", "ST2", "ST3", "ST4", "ST5", "ST7", "ST8");
        assertThat(SemanticTypeModelValidator.RULES).as("the module of the rules")
                .allSatisfy(rule -> assertThat(rule.module()).isEqualTo(SemanticTypeModelValidator.MODULE));
        assertThat(SemanticTypeModelValidator.RULES).as("the severities").allSatisfy(
                rule -> assertThat(rule.severity()).isEqualTo(Severity.ERROR));
    }

    private List<String> rules() {
        return SemanticTypeModelValidator.check(r.model).stream().map(Finding::rule).toList();
    }

    private List<Finding> errors(String rule) {
        return SemanticTypeModelValidator.check(r.model).stream().filter(f -> f.rule().equals(rule)).toList();
    }

    private void assertOnly(String rule, String saying) {
        List<Finding> findings = SemanticTypeModelValidator.check(r.model);
        assertThat(findings).as("the rules found").extracting(Finding::rule).containsOnly(rule);
        assertThat(findings).as("the severity").allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.ERROR));
        assertThat(findings).as("a finding of " + rule + " says").anySatisfy(f -> assertThat(f.message())
                .contains(saying));
    }
}
