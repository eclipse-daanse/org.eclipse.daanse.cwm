/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.xmla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.TypeLibraries;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.Typings;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.junit.jupiter.api.Test;

/**
 * The rules of the server's types over a register of ferry crossings: table {@code crossing} with the columns
 * {@code port}, {@code kind}, {@code valid_from}; the server's libraries and a library of the register's own types in
 * the model, and a package {@code types} for the typings.
 */
class XmlaTypesValidatorTest {

    private final Model register = Models.create("ferries");
    private final Package types = Packages.create(register, "types");
    private final TypeLibrary own = TypeLibraries.create(register, "ferry", "https://types.example.org/ferry");
    private final Table crossing = Tables.create(Schemas.create(Catalogs.create(register, "ferries"), "public"),
            "crossing");
    private final SQLSimpleType varchar = SQLSimpleTypes.ownedIn(crossing.getNamespace(), "VARCHAR");
    private final Column port = Columns.create(crossing, "port", varchar);
    private final Column kind = Columns.create(crossing, "kind", varchar);
    private final Column validFrom = Columns.create(crossing, "valid_from", varchar);
    private final TypeLibrary attributes;
    private final TypeLibrary dimensions;

    XmlaTypesValidatorTest() {
        ServerTypes.create(register);
        attributes = ServerTypes.attributes(register);
        dimensions = ServerTypes.dimensions(register);
    }

    @Test
    void typingsOfTheServersTypesKeepTheRules() {
        Typings.create(types, "port", ServerTypes.type(attributes, "City"), port);
        Typings.create(types, "crossing", ServerTypes.type(dimensions, "Geography"), crossing);
        Typings.create(types, "validFrom", ServerTypes.type(attributes, "DateStart"), validFrom);
        assertThat(XmlaTypesValidator.check(register)).as("findings of well-typed crossings").isEmpty();
        assertThat(SemanticTypeModelValidator.check(register)).as("findings of the model's rules").isEmpty();
    }

    @Test
    void t1AnAttributeTypeOfTheServerTypesNoTable() {
        Typings.create(types, "crossing", ServerTypes.type(attributes, "City"), crossing);
        assertOnly("T1", "names Table ferries.ferries.public.crossing");
    }

    @Test
    void t2ADimensionTypeOfTheServerTypesNoColumn() {
        Typings.create(types, "port", ServerTypes.type(dimensions, "Geography"), port);
        assertOnly("T2", "names Column ferries.ferries.public.crossing.port");
    }

    @Test
    void t3AnAccountTypeOfTheServerTypesNoColumn() {
        Typings.create(types, "kind", ServerTypes.type(ServerTypes.accounts(register), "Asset"), kind);
        assertOnly("T3", "names Column ferries.ferries.public.crossing.kind");
    }

    /** A type of one's own reaches the server's lists through its broader types, and the rules follow. */
    @Test
    void aTypeOfOnesOwnFollowsTheListsItReaches() {
        SemanticType ferryPort = SemanticTypes.broader(SemanticTypes.create(own, "FerryPort"),
                ServerTypes.type(attributes, "City"));
        Typings.create(types, "crossing", ferryPort, crossing);
        assertOnly("T1", "which reaches the server's attribute type City");
        SemanticTypes.broader(ferryPort, ServerTypes.type(dimensions, "Geography"));
        assertThat(XmlaTypesValidator.check(register)).as("a type that reaches both lists, on a table").isEmpty();
        Typings.create(types, "port", ferryPort, port);
        assertThat(XmlaTypesValidator.check(register)).as("and on a column").isEmpty();
        Typings.create(types, "pier", SemanticTypes.create(own, "Pier"), crossing, port);
        assertThat(XmlaTypesValidator.check(register)).as("a type that reaches no list").isEmpty();
    }

    @Test
    void t4AnElementReachesOneTypeOfEachList() {
        SemanticType ferryPort = SemanticTypes.broader(SemanticTypes.create(own, "FerryPort"),
                ServerTypes.type(attributes, "City"));
        Typings.create(types, "port", ferryPort, port);
        Typings.create(types, "portAgain", ServerTypes.type(attributes, "City"), port);
        assertThat(XmlaTypesValidator.check(register)).as("two typings that reach one server's type").isEmpty();
        Typings.create(types, "portPoint", ServerTypes.type(attributes, "Point"), port);
        assertOnly("T4", "is typed as the server's attribute types City, Point; a level has one");
        assertThat(XmlaTypesValidator.check(register).get(0).element()).as("the element of T4").isSameAs(port);
    }

    @Test
    void t6ATypingOfRegularSaysNothing() {
        Typings.create(types, "kind", ServerTypes.type(attributes, "Regular"), kind);
        Typings.create(types, "crossing", ServerTypes.type(dimensions, "Regular"), crossing);
        assertThat(XmlaTypesValidator.check(register)).as("findings of Regular").hasSize(2)
                .allSatisfy(f -> assertThat(f.rule()).isEqualTo("T6"))
                .allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.WARNING));
        assertThat(XmlaTypesValidator.validate(register)).as("errors").isEmpty();
    }

    @Test
    void t7TheHistoryTypesComeFromTheHistoryRules() {
        Typings.create(types, "validFrom", ServerTypes.type(attributes, "ScdStartDate"), validFrom);
        assertThat(XmlaTypesValidator.check(register)).as("findings of ScdStartDate").singleElement()
                .satisfies(f -> assertThat(f.severity()).isEqualTo(Severity.WARNING))
                .extracting(Finding::message).asString().contains("types as ScdStartDate");
    }

    @Test
    void t15AnExternalTypeNamesAListOfTheServerAndATypeInIt() {
        SemanticType pier = SemanticTypes.create(own, "Pier");
        SemanticTypes.sameAsExternal(pier, ServerTypes.ATTRIBUTE_URI, "Point", null);
        SemanticTypes.sameAsExternal(pier, "https://types.example.org/gazetteer", "Jetty", null);
        assertThat(XmlaTypesValidator.check(register)).as("an external type of the attribute list").isEmpty();
        pier.getSameAsExternal().get(0).setLibraryUri(ServerTypes.URI);
        assertOnly("T15", "is none of the server's lists");
        pier.getSameAsExternal().get(0).setLibraryUri(ServerTypes.GROUPS_URI);
        assertOnly("T15", "is none of the server's lists");
        pier.getSameAsExternal().get(0).setLibraryUri(ServerTypes.DIMENSION_URI);
        assertOnly("T15", "names a type of " + ServerTypes.DIMENSION_URI + " that is not in it: Point");
    }

    @Test
    void everyRuleIsStatedOnce() {
        assertThat(XmlaTypesValidator.RULES).as("the rules").extracting(Rule::code).containsExactly("T1", "T2", "T3",
                "T4", "T6", "T7", "T15");
        assertThat(SemanticTypeModelValidator.RULES).as("the rules of the model, whose codes these do not repeat")
                .extracting(Rule::code).doesNotContainAnyElementsOf(XmlaTypesValidator.RULES.stream().map(Rule::code)
                        .toList());
        assertThat(XmlaTypesValidator.RULES).as("the module").allSatisfy(rule -> assertThat(rule.module())
                .isEqualTo(XmlaTypesValidator.MODULE));
        assertThat(XmlaTypesValidator.RULES.stream().filter(rule -> rule.severity() == Severity.WARNING)
                .map(Rule::code)).as("the warnings").containsExactly("T6", "T7");
    }

    private void assertOnly(String rule, String saying) {
        List<Finding> findings = XmlaTypesValidator.check(register);
        assertThat(findings).as("the rules found").extracting(Finding::rule).containsOnly(rule);
        assertThat(findings).as("the severity").allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.ERROR));
        assertThat(findings).as("a finding of " + rule + " says").anySatisfy(f -> assertThat(f.message())
                .contains(saying));
    }
}
