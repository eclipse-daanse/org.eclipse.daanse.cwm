/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.daanse.cwm.emf.standalone.EcoreIndex;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Every case of the corpus means at run time what Fennec's own file means: the converter's output
 * and Fennec's original, both normalized ({@link Normalizer}), are equal — source classes, id,
 * timestamps, admin values, services, selectors, resources with types, units, value paths and
 * rules. Profiles agree in provider id and admin requirements; ours are always {@code UNIFIED}.
 * Both normalized forms are written to {@code target/corpus/<case>-converted.txt} and
 * {@code <case>-fennec.txt} for a diff, the written files to {@code target/corpus/<case>/}.
 */
class CorpusTest {

    @ParameterizedTest
    @EnumSource(Corpus.class)
    void theConversionMeansWhatFennecsFileMeans(Corpus corpus) throws Exception {
        Examples.Case c = corpus.build();
        List<EPackage> packages = AtlasGenerator.loadEcore(corpus.ecoreFiles());
        CwmToAtlas.Result result = CwmToAtlas.convert(corpus.mappings(c), EcoreIndex.of(packages), corpus.options);

        assertThat(result.errors()).as("errors").isEmpty();
        assertThat(result.notes()).as("notes").allMatch(n -> n.severity() != Note.Severity.ERROR);

        ResourceSet expectedSet = AtlasWriter.newResourceSet();
        packages.forEach(p -> expectedSet.getPackageRegistry().put(p.getNsURI(), p));
        List<ProviderMapping> expected = corpus.expected.stream()
                .map(f -> (ProviderMapping) expectedSet.getResource(
                        URI.createFileURI(Corpus.resources().resolve(f).toString()), true).getContents().get(0))
                .toList();

        assertThat(result.mappings()).hasSameSizeAs(expected);
        Path dump = Path.of(System.getProperty("user.dir"), "target/corpus");
        Files.createDirectories(dump);
        Files.writeString(dump.resolve(corpus.name().toLowerCase() + "-converted.txt"),
                String.join("\n\n", result.mappings().stream().map(Normalizer::of).toList()) + "\n");
        Files.writeString(dump.resolve(corpus.name().toLowerCase() + "-fennec.txt"),
                String.join("\n\n", expected.stream().map(Normalizer::of).toList()) + "\n");
        AtlasGenerator.withEcore(corpus.mappings(corpus.build()), corpus.ecoreFiles(),
                dump.resolve(corpus.name().toLowerCase()), corpus.options);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(Normalizer.of(result.mappings().get(i))).as(expected.get(i).getMid())
                    .isEqualTo(Normalizer.of(expected.get(i)));
            if (corpus.options.profiles()) {
                assertThat(result.mappings().get(i).getProfile()).isNotNull();
                assertThat(Normalizer.of(result.mappings().get(i).getProfile()))
                        .isEqualTo(Normalizer.of(expected.get(i).getProfile()));
                assertThat(result.mappings().get(i).getProfile().getProviderStrategy()).isEqualTo(ProviderStrategy.UNIFIED);
            } else {
                assertThat(result.mappings().get(i).getProfile()).isNull();
            }
        }
    }
}
