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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * The checked-in {@code model/orm.ecore} is exactly what {@link OrmEcoreBuilder} writes. Run
 * with {@code -Dregenerate.ecore=true} to rewrite it after a change to the builder (then build
 * the module with {@code clean}, the code generator only runs on an empty target).
 */
class OrmEcoreUpToDateTest {

    @Test
    void checkedInEcore_matchesTheBuilder() throws Exception {
        File baseDir = new File(System.getProperty("user.dir"));
        File built = new File(baseDir, "target/orm-ecore/orm.ecore");
        OrmEcoreBuilder.generate(baseDir, built);
        File checkedIn = new File(baseDir, "model/orm.ecore");
        String expected = Files.readString(built.toPath(), StandardCharsets.UTF_8);
        if (Boolean.getBoolean("regenerate.ecore")) {
            Files.writeString(checkedIn.toPath(), expected, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(checkedIn.toPath(), StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}
