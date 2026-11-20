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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.groups.Tuple;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.TypeLibraries;
import org.junit.jupiter.api.Test;

/** The delivered libraries of the server's types: one per list, exactly the rows of its table, and the groups. */
class ServerTypesTest {

    private final Model model = Models.create("server");
    private final TypeLibrary attributes;
    private final TypeLibrary dimensions;
    private final TypeLibrary accounts;
    private final TypeLibrary groups;

    ServerTypesTest() {
        ServerTypes.create(model);
        attributes = ServerTypes.attributes(model);
        dimensions = ServerTypes.dimensions(model);
        accounts = ServerTypes.accounts(model);
        groups = ServerTypes.groups(model);
    }

    @Test
    void eachListIsALibraryOfExactlyTheRowsOfItsTable() throws IOException {
        assertThat(namesAndNumbers(attributes)).as("the attribute types").hasSize(240)
                .containsExactlyElementsOf(table("attribute-types.tsv"));
        assertThat(namesAndNumbers(dimensions)).as("the dimension types").hasSize(15)
                .containsExactlyElementsOf(table("dimension-types.tsv"));
        assertThat(namesAndNumbers(accounts)).as("the account types").hasSize(7)
                .containsExactlyElementsOf(table("account-types.tsv"));
        for (TypeLibrary list : List.of(attributes, dimensions, accounts)) {
            assertThat(SemanticTypes.of(list)).as("the name there of each type of " + list.getName())
                    .allSatisfy(t -> assertThat(t.getSourceName()).isEqualTo(t.getName()));
            assertThat(SemanticTypes.of(list)).as("the names of " + list.getName()).extracting(SemanticType::getName)
                    .doesNotHaveDuplicates();
            assertThat(SemanticTypes.of(list)).as("the numbers of " + list.getName())
                    .extracting(SemanticType::getSourceNumber).doesNotHaveDuplicates();
        }
    }

    @Test
    void theLibrariesAreKnownByTheirUris() {
        assertThat(TypeLibraries.all(model)).as("the libraries and their URIs").extracting(TypeLibrary::getUri)
                .containsExactly(
                        "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla/attribute",
                        "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla/dimension",
                        "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla/account",
                        "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla/groups");
        assertThat(ServerTypes.LIBRARIES).as("the delivered libraries").containsExactlyInAnyOrderElementsOf(
                TypeLibraries.all(model).stream().map(TypeLibrary::getUri).toList());
        assertThat(List.of(attributes, dimensions, accounts)).as("the system of the three lists")
                .allSatisfy(list -> assertThat(list.getSourceSystem()).isEqualTo("Analysis Services"));
        assertThat(groups.getSourceSystem()).as("the groups are not the server's").isNull();
    }

    /** The seven names that the server uses in two lists are two types in two libraries. */
    @Test
    void aNameOfTwoListsIsTwoTypes() {
        Set<String> twice = SemanticTypes.of(attributes).stream().map(SemanticType::getName)
                .filter(name -> SemanticTypes.named(dimensions, name).isPresent()).collect(Collectors.toSet());
        assertThat(twice).as("the names of both lists").containsExactlyInAnyOrder("Channel", "Customers",
                "Promotion", "Quantitative", "Regular", "Scenario", "Utility");
        SemanticType attributeChannel = ServerTypes.type(attributes, "Channel");
        SemanticType dimensionChannel = ServerTypes.type(dimensions, "Channel");
        assertThat(attributeChannel).as("two types").isNotSameAs(dimensionChannel);
        assertThat(SemanticTypes.uriOf(attributeChannel)).as("the attribute type Channel")
                .contains(ServerTypes.ATTRIBUTE_URI + "/Channel");
        assertThat(SemanticTypes.uriOf(dimensionChannel)).as("the dimension type Channel")
                .contains(ServerTypes.DIMENSION_URI + "/Channel");
        assertThat(ServerTypes.listOf(attributeChannel)).as("its list").contains(ServerTypes.ATTRIBUTE_URI);
        assertThat(ServerTypes.listOf(dimensionChannel)).as("its list").contains(ServerTypes.DIMENSION_URI);
    }

    @Test
    void everyAttributeTypeIsNarrowerThanItsGroup() {
        SemanticType time = ServerTypes.type(groups, "Time");
        assertThat(ServerTypes.type(attributes, "City").getBroader()).as("the group of City")
                .containsExactly(ServerTypes.type(groups, "Geography"));
        assertThat(SemanticTypes.isA(ServerTypes.type(attributes, "FiscalYears"), time)).as("a fiscal year is a time")
                .isTrue();
        assertThat(SemanticTypes.narrowerThan(time)).as("the types of time").extracting(SemanticType::getName)
                .contains("Years", "FiscalYears", "Iso8601Weeks", "IsHoliday", "TimeFiscal")
                .doesNotContain("City", "DateStart");
        assertThat(SemanticTypes.of(groups)).as("the groups").hasSize(30);
        assertThat(SemanticTypes.of(attributes)).as("every attribute type has its group")
                .allSatisfy(t -> assertThat(t.getBroader()).hasSize(1));
        assertThat(SemanticTypes.of(dimensions)).as("no dimension type has a group")
                .allSatisfy(t -> assertThat(t.getBroader()).isEmpty());
        assertThat(ServerTypes.isServerType(ServerTypes.type(groups, "Geography"))).as("a group").isFalse();
    }

    @Test
    void theLibrariesKeepTheRulesOfTheModel() {
        assertThat(SemanticTypeModelValidator.check(model)).as("findings of the delivered libraries").isEmpty();
        assertThat(XmlaTypesValidator.check(model)).as("findings of the server's rules").isEmpty();
    }

    @Test
    void aReaderTakesEitherSpelling() {
        assertThat(ServerTypes.type(attributes, "ProductSku").getName()).as("ProductSku").isEqualTo("ProductSKU");
        assertThat(ServerTypes.type(attributes, "TenDays").getName()).as("TenDays").isEqualTo("TenDay");
        assertThat(ServerTypes.type(attributes, "Id").getName()).as("Id").isEqualTo("ID");
        assertThat(ServerTypes.nameOf("City")).as("the schema's spelling").contains("City");
        assertThat(ServerTypes.nameOf("Harbour")).as("no type of the server").isEmpty();
        assertThatThrownBy(() -> ServerTypes.type(dimensions, "Harbours")).as("no dimension type Harbours")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Harbours");
        assertThat(ServerTypes.type(accounts, "Asset").getSourceNumber()).as("the number of Asset").isEqualTo(4);
    }

    private static List<Tuple> namesAndNumbers(TypeLibrary library) {
        return SemanticTypes.of(library).stream().map(t -> tuple(t.getName(), t.getSourceNumber())).toList();
    }

    /** The name and number of every row of a table, read here on their own. */
    private static List<Tuple> table(String name) throws IOException {
        try (InputStream in = ServerTypes.class.getResourceAsStream(name)) {
            List<String> lines = Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                    .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
            assertThat(lines.get(0)).as("the header of " + name).startsWith("name\tnumber");
            return lines.subList(1, lines.size()).stream().map(line -> line.split("\t", -1))
                    .map(cells -> tuple(cells[0], Long.parseLong(cells[1]))).toList();
        }
    }
}
