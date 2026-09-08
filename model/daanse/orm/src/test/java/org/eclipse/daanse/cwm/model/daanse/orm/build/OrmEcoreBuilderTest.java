/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.eclipse.daanse.cwm.model.daanse.orm.build.OrmEcoreBuilder.CwmRefs;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Regeneriert {@code model/orm.ecore} aus dem Java-Builder.
 *
 * <p>Bewusst gated: laeuft nur mit {@code -Dregenerate.ecore=true}, damit ein normaler
 * {@code mvn install} die eingecheckte {@code .ecore} nicht ueberschreibt. Ablauf:</p>
 * <pre>
 * mvn -pl model/daanse/orm test \
 *     -Dtest=OrmEcoreBuilderTest -Dregenerate.ecore=true
 * git diff model/daanse/orm/model/orm.ecore
 * </pre>
 */
class OrmEcoreBuilderTest {

    @Test
    @EnabledIfSystemProperty(named = "regenerate.ecore", matches = "true")
    void regenerateEcore() throws Exception {
        File baseDir = new File(System.getProperty("user.dir"));
        File coreEcore = new File(baseDir, "../../cwm/objectmodel.core/model/core.ecore").getCanonicalFile();
        File relationshipsEcore = new File(baseDir,
                "../../cwm/objectmodel.relationships/model/relationships.ecore").getCanonicalFile();
        File relationalEcore = new File(baseDir,
                "../../cwm/resource.relational/model/relational.ecore").getCanonicalFile();
        File outFile = new File(baseDir, "model/orm.ecore");
        outFile.getParentFile().mkdirs();

        assertThat(coreEcore).as("core.ecore muss existieren: %s", coreEcore).isFile();
        assertThat(relationshipsEcore)
                .as("relationships.ecore muss existieren: %s", relationshipsEcore).isFile();
        assertThat(relationalEcore)
                .as("relational.ecore muss existieren: %s", relationalEcore).isFile();

        ResourceSet rs = OrmEcoreBuilder.newResourceSet();
        CwmRefs refs = OrmEcoreBuilder.loadCwmRefs(rs,
                URI.createFileURI(coreEcore.getAbsolutePath()),
                URI.createFileURI(relationshipsEcore.getAbsolutePath()),
                URI.createFileURI(relationalEcore.getAbsolutePath()));

        EPackage orm = new OrmEcoreBuilder(refs).build();
        OrmEcoreBuilder.save(orm, rs, URI.createFileURI(outFile.getAbsolutePath()));

        assertThat(outFile).isFile();
        assertThat(outFile.length()).isPositive();
    }
}
