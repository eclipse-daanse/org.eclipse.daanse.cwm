/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;

import org.eclipse.daanse.cwm.model.daanse.sql.select.QueryExpression;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Schreibt die eingecheckten Beispiel-Instanzen nach {@code example/<name>.sql}.
 *
 * <p>Gated wie der Ecore-Builder: laeuft nur mit {@code -Dwrite.example=true}.</p>
 *
 * <pre>
 * mvn -pl model/daanse/sql test -Dtest=SqlExampleWriterTest -Dwrite.example=true
 * </pre>
 */
class SqlExampleWriterTest {

    @Test
    @EnabledIfSystemProperty(named = "write.example", matches = "true")
    void writeExamples() throws Exception {
        File baseDir = new File(System.getProperty("user.dir"));
        File dir = new File(baseDir, "example");
        dir.mkdirs();

        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("sql", new XMIResourceFactoryImpl());

        for (Map.Entry<String, QueryExpression> e : SqlExamples.all().entrySet()) {
            File out = new File(dir, e.getKey() + ".sql");
            Resource res = rs.createResource(URI.createFileURI(out.getAbsolutePath()));
            res.getContents().add(e.getValue());
            res.save(null);
            assertThat(out).isFile();
        }
    }
}
