/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.eclipse.daanse.cwm.emf.ecore.CorpusModels.File;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Every corpus file is exactly what {@link CorpusModels} writes, so the files are never edited by
 * hand. Run with {@code -Dcwm.ecore.corpus.update=true} to write them anew after a change of the
 * models (the expected output of {@link CorpusTest} follows in the same run).
 */
class CorpusUpToDateTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(File.class)
    void checkedInFileIsWhatTheModelWrites(File file) throws IOException {
        Path built = CorpusModels.write(file, Path.of(System.getProperty("user.dir"), "target", "corpus"));
        Path checkedIn = CorpusCase.dir().resolve(file.fileName);
        if (CorpusCase.UPDATE) {
            Files.copy(built, checkedIn, StandardCopyOption.REPLACE_EXISTING);
        }
        assertThat(Files.readString(checkedIn)).isEqualTo(Files.readString(built));
    }
}
