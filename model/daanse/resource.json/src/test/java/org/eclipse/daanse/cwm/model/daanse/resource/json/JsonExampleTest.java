/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** The example of the README: a schema with an object type, its properties and their types. */
class JsonExampleTest {

    private static final JsonFactory F = JsonFactory.eINSTANCE;

    /** Lines of the XMI {@link #aSchemaWithAnObjectType()} writes. */
    private static final String XMI = """
            <ownedElement xsi:type="json:PrimitiveType" name="OrderId" valueKind="json_integer" \
            minimum="1"/>
            <ownedElement xsi:type="json:PrimitiveType" name="Tag" valueKind="json_string" maxLength="20"/>
            <ownedElement xsi:type="json:ArrayType" name="Tags" itemType="_Tag" maxItems="10" \
            uniqueItems="true"/>
            <ownedElement xsi:type="json:ObjectType" name="Order" allowsAdditionalProperties="false">
            <feature xsi:type="json:Property" name="id" type="_OrderId" isRequired="true"/>
            <feature xsi:type="json:Property" name="tags" type="_Tags"/>
            """;

    @Test
    void aSchemaWithAnObjectType() throws IOException {
        Schema root = F.createSchema();
        root.setName("order");
        root.setDialect("https://json-schema.org/draft/2020-12/schema");
        root.setIdentifier("https://example.org/order.schema.json");
        PrimitiveType orderId = F.createPrimitiveType();
        orderId.setName("OrderId");
        orderId.setValueKind(JsonValueKind.JSON_INTEGER);
        orderId.setMinimum("1");
        root.getOwnedElement().add(orderId);
        PrimitiveType tag = F.createPrimitiveType();
        tag.setName("Tag");
        tag.setValueKind(JsonValueKind.JSON_STRING);
        tag.setMaxLength(20);
        root.getOwnedElement().add(tag);
        ArrayType tags = F.createArrayType();
        tags.setName("Tags");
        tags.setItemType(tag);
        tags.setMaxItems(10);
        tags.setUniqueItems(true);
        root.getOwnedElement().add(tags);

        ObjectType order = F.createObjectType();
        order.setName("Order");
        order.setAllowsAdditionalProperties(false);
        Property id = F.createProperty();
        id.setName("id");
        id.setType(orderId);
        id.setIsRequired(true);
        order.getFeature().add(id);
        Property tagList = F.createProperty();
        tagList.setName("tags");
        tagList.setType(tags);
        order.getFeature().add(tagList);
        root.getOwnedElement().add(order);
        String xmi = xmi(root, "json-example.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** {@code root} as XMI with short ids and without the ids, written to {@code target/<file>} as well. */
    private static String xmi(EObject root, String file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI(file));
        r.getContents().add(root);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target", file), xmi, StandardCharsets.UTF_8);
        return xmi;
    }
}
