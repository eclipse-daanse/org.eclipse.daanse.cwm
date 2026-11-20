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
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.TypeLibraries;
import org.junit.jupiter.api.Test;

/**
 * The XMLA codes of the server's types as an Analysis Services server reports them, chosen by the list a type lies
 * in; the untyped ones and the one shared code; and the codes of a type of another library, through its same types,
 * then its external types, then its broader types, else regular.
 */
class XmlaCodesTest {

    private final Model model = Models.create("harbour");
    private final TypeLibrary own = TypeLibraries.create(model, "harbour", "https://types.example.org/harbour");
    private final TypeLibrary attributes;
    private final TypeLibrary dimensions;

    XmlaCodesTest() {
        ServerTypes.create(model);
        attributes = ServerTypes.attributes(model);
        dimensions = ServerTypes.dimensions(model);
    }

    @Test
    void theCodesAServerReports() {
        assertThat(XmlaCodes.levelType(attribute("Years"))).as("LEVEL_TYPE Years").isEqualTo(0x14);
        assertThat(XmlaCodes.levelType(attribute("FiscalYears"))).as("LEVEL_TYPE FiscalYears").isEqualTo(0x10FA);
        assertThat(XmlaCodes.levelType(attribute("Date"))).as("LEVEL_TYPE Date").isEqualTo(0x10C1);
        assertThat(XmlaCodes.levelType(attribute("Country"))).as("LEVEL_TYPE Country").isEqualTo(0x2003);
        assertThat(XmlaCodes.levelType(attribute("City"))).as("LEVEL_TYPE City").isEqualTo(0x2006);
        assertThat(XmlaCodes.levelType(attribute("Account"))).as("LEVEL_TYPE Account").isEqualTo(0x1014);
        assertThat(XmlaCodes.levelType(attribute("PersonContact"))).as("LEVEL_TYPE PersonContact").isEqualTo(0x11A3);
        assertThat(XmlaCodes.levelType(attribute("DayOfWeek"))).as("LEVEL_TYPE DayOfWeek").isEqualTo(0x10CD);
        assertThat(XmlaCodes.levelType(attribute("WeekOfYear"))).as("LEVEL_TYPE WeekOfYear").isEqualTo(0x1297);
        assertThat(XmlaCodes.levelType(attribute("TenDay"))).as("LEVEL_TYPE TenDay").isEqualTo(0x1266);
        assertThat(XmlaCodes.propertyContentType(attribute("PersonContact"))).as("PROPERTY_CONTENT_TYPE PersonContact")
                .isEqualTo(0x87);
        assertThat(XmlaCodes.propertyContentType(attribute("City"))).as("PROPERTY_CONTENT_TYPE City").isEqualTo(0x111);
        assertThat(XmlaCodes.propertyContentType(attribute("Months"))).as("PROPERTY_CONTENT_TYPE Months")
                .isEqualTo(0x1F7);
        assertThat(XmlaCodes.propertyContentType(attribute("Years"))).as("PROPERTY_CONTENT_TYPE Years")
                .isEqualTo(0x321);
        assertThat(XmlaCodes.dimensionType(dimension("Geography"))).as("DIMENSION_TYPE Geography").isEqualTo(17);
        assertThat(XmlaCodes.dimensionType(dimension("Time"))).as("DIMENSION_TYPE Time").isEqualTo(1);
        assertThat(XmlaCodes.dimensionType(dimension("Accounts"))).as("DIMENSION_TYPE Accounts").isEqualTo(6);
        assertThat(XmlaCodes.dimensionType(dimension("Regular"))).as("DIMENSION_TYPE Regular").isEqualTo(3);
        assertThat(XmlaCodes.dimensionType(dimension("BillOfMaterials"))).as("DIMENSION_TYPE BillOfMaterials")
                .isEqualTo(16);
        assertThat(XmlaCodes.dimensionType(dimension("Rates"))).as("DIMENSION_TYPE Rates").isEqualTo(12);
    }

    /** The list a type lies in chooses its codes: the two types Channel have codes of their own. */
    @Test
    void theListOfATypeChoosesItsCodes() {
        assertThat(XmlaCodes.levelType(attribute("Channel"))).as("LEVEL_TYPE of the attribute type Channel")
                .isEqualTo(0x1061);
        assertThat(XmlaCodes.dimensionType(attribute("Channel"))).as("no DIMENSION_TYPE of an attribute type")
                .isEqualTo(3);
        assertThat(XmlaCodes.dimensionType(dimension("Channel"))).as("DIMENSION_TYPE of the dimension type Channel")
                .isEqualTo(13);
        assertThat(XmlaCodes.levelType(dimension("Channel"))).as("no LEVEL_TYPE of a dimension type").isZero();
        SemanticType asset = ServerTypes.type(ServerTypes.accounts(model), "Asset");
        assertThat(XmlaCodes.levelType(asset)).as("no LEVEL_TYPE of an account type").isZero();
        assertThat(XmlaCodes.dimensionType(asset)).as("no DIMENSION_TYPE of an account type").isEqualTo(3);
        assertThat(XmlaCodes.lists(asset)).as("the list of an account type").containsExactly(ServerTypes.ACCOUNT_URI);
    }

    /** The specification names two property codes the server does not report; the server decides. */
    @Test
    void whereTheSpecificationDiffersTheServerDecides() {
        assertThat(XmlaCodes.propertyContentType(attribute("FormattingSubtotal")))
                .as("PROPERTY_CONTENT_TYPE FormattingSubtotal, the code of FormattingColor").isEqualTo(0xA1);
        assertThat(XmlaCodes.propertyContentTypeCode("FormattingSubtotal")).as("and the specification's")
                .contains(new XmlaCodes.Code(0xA1, 0xA6));
        assertThat(XmlaCodes.propertyContentTypeCode("PersonFullName")).as("PROPERTY_CONTENT_TYPE PersonFullName")
                .contains(new XmlaCodes.Code(0x214, 0x82));
        assertThat(XmlaCodes.levelTypeCode("Hours")).as("a level code the specification does not name")
                .contains(new XmlaCodes.Code(0x304, null));
        assertThat(XmlaCodes.levelTypeCode("Harbour")).as("no type of the server").isEmpty();
    }

    @Test
    void anUntypedElementIsRegularAndTheExtendedTypeHasCodesOfItsOwn() {
        assertThat(XmlaCodes.levelType(null)).as("LEVEL_TYPE untyped").isZero();
        assertThat(XmlaCodes.propertyContentType(null)).as("PROPERTY_CONTENT_TYPE untyped").isZero();
        assertThat(XmlaCodes.dimensionType(null)).as("DIMENSION_TYPE untyped").isEqualTo(3);
        assertThat(XmlaCodes.levelType(attribute("Regular"))).as("LEVEL_TYPE Regular").isZero();
        assertThat(XmlaCodes.levelType(attribute("ExtendedType"))).as("LEVEL_TYPE ExtendedType").isEqualTo(0x12B1);
        assertThat(XmlaCodes.propertyContentType(attribute("ExtendedType"))).as("PROPERTY_CONTENT_TYPE ExtendedType")
                .isEqualTo(0x322);
        assertThat(XmlaCodes.MEASURE_DIMENSION_TYPE).as("DIMENSION_TYPE of the measures").isEqualTo(2);
    }

    /** Only FormattingColor and FormattingSubtotal share a code: the property content type 0xA1. */
    @Test
    void onlyTwoTypesShareACode() {
        List<SemanticType> types = SemanticTypes.of(attributes);
        assertThat(shared(types.stream().collect(Collectors.groupingBy(XmlaCodes::levelType))))
                .as("LEVEL_TYPE codes of more than one type").isEmpty();
        Map<Integer, List<SemanticType>> property = shared(types.stream()
                .collect(Collectors.groupingBy(XmlaCodes::propertyContentType)));
        assertThat(property).as("PROPERTY_CONTENT_TYPE codes of more than one type").containsOnlyKeys(0xA1);
        assertThat(property.get(0xA1)).as("the types of 0xA1").extracting(SemanticType::getName)
                .containsExactly("FormattingColor", "FormattingSubtotal");
        assertThat(SemanticTypes.of(dimensions).stream().map(XmlaCodes::dimensionType))
                .as("DIMENSION_TYPE codes of more than one type").doesNotHaveDuplicates();
    }

    @Test
    void aTypeOfAnotherLibraryTakesTheCodesOfTheSameTypeFirst() {
        SemanticType harbourTown = SemanticTypes.broader(SemanticTypes.sameAs(SemanticTypes.create(own,
                "HarbourTown"), attribute("City")), attribute("Region"));
        assertThat(XmlaCodes.serverName(harbourTown, ServerTypes.ATTRIBUTE_URI)).as("the server's type reached first")
                .contains("City");
        assertThat(XmlaCodes.levelType(harbourTown)).as("LEVEL_TYPE of the city it is").isEqualTo(0x2006);
    }

    @Test
    void thenTheExternalTypesThatNameAListOfTheServer() {
        SemanticType berth = SemanticTypes.create(own, "Berth");
        SemanticTypes.sameAsExternal(berth, "https://types.example.org/gazetteer", "Point", null);
        SemanticTypes.sameAsExternal(berth, ServerTypes.ATTRIBUTE_URI, "Point", null);
        assertThat(XmlaCodes.levelType(berth)).as("by the name there").isEqualTo(0x2008);
        SemanticType article = SemanticTypes.create(own, "Article");
        SemanticTypes.sameAsExternal(article, ServerTypes.ATTRIBUTE_URI, null, 165L);
        assertThat(XmlaCodes.serverName(article, ServerTypes.ATTRIBUTE_URI)).as("by the number there")
                .contains("ProductSKU");
        SemanticType sku = SemanticTypes.create(own, "Sku");
        SemanticTypes.sameAsExternal(sku, ServerTypes.ATTRIBUTE_URI, "ProductSku", null);
        assertThat(XmlaCodes.serverName(sku, ServerTypes.ATTRIBUTE_URI)).as("by the other spelling")
                .contains("ProductSKU");
        SemanticType channel = SemanticTypes.create(own, "SalesRoute");
        SemanticTypes.sameAsExternal(channel, ServerTypes.DIMENSION_URI, "Channel", null);
        assertThat(XmlaCodes.dimensionType(channel)).as("the list the external type names").isEqualTo(13);
        assertThat(XmlaCodes.levelType(channel)).as("not the attribute type of that name").isZero();
    }

    @Test
    void thenTheBroaderTypesNearestFirst() {
        SemanticType quay = SemanticTypes.create(own, "Quay");
        SemanticType berth = SemanticTypes.broader(SemanticTypes.create(own, "Berth"), quay);
        SemanticTypes.broader(quay, attribute("Point"));
        assertThat(XmlaCodes.levelType(berth)).as("through a type of its own to Point").isEqualTo(0x2008);
        TypeLibrary coast = TypeLibraries.create(model, "coast", "https://types.example.org/coast");
        SemanticType coastPort = SemanticTypes.sameAs(SemanticTypes.create(coast, "Port"), attribute("City"));
        SemanticType port = SemanticTypes.sameAs(SemanticTypes.create(own, "Port"), coastPort);
        assertThat(XmlaCodes.levelType(port)).as("through the same type of another library").isEqualTo(0x2006);
    }

    /**
     * One type of one's own types the table of harbours (its rows) and the column of their names (its values): it
     * reaches a dimension type and an attribute type, and each place takes the code of its list.
     */
    @Test
    void oneTypeReachesSeveralLists() {
        SemanticType harbour = SemanticTypes.broader(SemanticTypes.create(own, "Harbour"), attribute("City"),
                dimension("Geography"));
        assertThat(XmlaCodes.lists(harbour)).as("the lists it reaches").containsExactly(ServerTypes.ATTRIBUTE_URI,
                ServerTypes.DIMENSION_URI);
        assertThat(XmlaCodes.levelType(harbour)).as("LEVEL_TYPE for the column").isEqualTo(0x2006);
        assertThat(XmlaCodes.dimensionType(harbour)).as("DIMENSION_TYPE for the table").isEqualTo(17);
    }

    @Test
    void elseRegular() {
        SemanticType pier = SemanticTypes.create(own, "Pier");
        assertThat(XmlaCodes.lists(pier)).as("a type that reaches no server's type").isEmpty();
        assertThat(XmlaCodes.levelType(pier)).as("LEVEL_TYPE of a type of its own").isZero();
        assertThat(XmlaCodes.propertyContentType(pier)).as("PROPERTY_CONTENT_TYPE of a type of its own").isZero();
        assertThat(XmlaCodes.dimensionType(pier)).as("DIMENSION_TYPE of a type of its own").isEqualTo(3);
        assertThat(XmlaCodes.levelType(ServerTypes.type(ServerTypes.groups(model), "Geography")))
                .as("a group is no server's type").isZero();
        SemanticType loop = SemanticTypes.create(own, "Loop");
        SemanticTypes.broader(loop, SemanticTypes.broader(SemanticTypes.create(own, "Round"), loop));
        assertThat(XmlaCodes.levelType(loop)).as("a cycle of broader types ends").isZero();
    }

    private SemanticType attribute(String name) {
        return ServerTypes.type(attributes, name);
    }

    private SemanticType dimension(String name) {
        return ServerTypes.type(dimensions, name);
    }

    private static <T> Map<Integer, List<T>> shared(Map<Integer, List<T>> byCode) {
        return byCode.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
