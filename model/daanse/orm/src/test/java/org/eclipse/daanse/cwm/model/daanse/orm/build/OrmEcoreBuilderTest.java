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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Regenerates {@code model/orm.ecore} from the Java builder.
 *
 * <p>Deliberately gated: runs only with {@code -Dregenerate.ecore=true}, so that a regular
 * {@code mvn install} does not overwrite the checked-in {@code .ecore};
 * {@link OrmEcoreUpToDateTest} checks without the flag that builder and file agree.
 * Workflow:</p>
 * <pre>
 * mvn -pl model/daanse/orm test \
 *     -Dtest=OrmEcoreBuilderTest -Dregenerate.ecore=true
 * git diff model/daanse/orm/model/orm.ecore
 * mvn -pl model/daanse/orm clean install
 * </pre>
 */
class OrmEcoreBuilderTest {

    @Test
    @EnabledIfSystemProperty(named = "regenerate.ecore", matches = "true")
    void regenerateEcore() throws Exception {
        File baseDir = new File(System.getProperty("user.dir"));
        File outFile = new File(baseDir, "model/orm.ecore");
        OrmEcoreBuilder.generate(baseDir, outFile);

        assertThat(outFile).isFile();
        assertThat(outFile.length()).isPositive();
    }
}
