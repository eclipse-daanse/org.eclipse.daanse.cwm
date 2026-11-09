/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.r;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** The example of the README: an RDS file whose root object is a data frame with two columns. */
class RExampleTest {

    private static final RFactory F = RFactory.eINSTANCE;

    /** Lines of the XMI {@link #anRdsFileWithADataFrame()} writes. */
    private static final String XMI = """
            <ownedElement xsi:type="r:RVectorType" name="integer" sexpType="r_integer"/>
            <ownedElement xsi:type="r:RVectorType" name="double" sexpType="r_real"/>
            <ownedElement xsi:type="r:RDataFrameType" name="survey" sexpType="r_list" rowCount="120">
            <feature xsi:type="r:RComponent" name="age" type="_integer"/>
            <feature xsi:type="r:RComponent" name="score" type="_double" position="1"/>
            <rClass>data.frame</rClass>
            """;

    @Test
    void anRdsFileWithADataFrame() throws IOException {
        RdsFile root = F.createRdsFile();
        root.setName("survey");
        root.setSerializationFormat(SerializationFormat.RXDR);
        root.setSerializationVersion(3);
        root.setCompression(CompressionKind.RGZIP);
        RVectorType integers = vector(root, "integer", SexpType.RINTEGER);
        RVectorType doubles = vector(root, "double", SexpType.RREAL);

        RDataFrameType survey = F.createRDataFrameType();
        survey.setName("survey");
        survey.setSexpType(SexpType.RLIST);
        survey.getRClass().add("data.frame");
        survey.setRowCount(120);
        survey.getFeature().add(column("age", 0, integers));
        survey.getFeature().add(column("score", 1, doubles));
        root.getOwnedElement().add(survey);
        root.setRootType(survey);
        String xmi = xmi(root, "r-example.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    private static RVectorType vector(Namespace owner, String name, SexpType sexpType) {
        RVectorType v = F.createRVectorType();
        v.setName(name);
        v.setSexpType(sexpType);
        owner.getOwnedElement().add(v);
        return v;
    }

    private static RComponent column(String name, long position, RType type) {
        RComponent c = F.createRComponent();
        c.setName(name);
        c.setPosition(position);
        c.setType(type);
        return c;
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
