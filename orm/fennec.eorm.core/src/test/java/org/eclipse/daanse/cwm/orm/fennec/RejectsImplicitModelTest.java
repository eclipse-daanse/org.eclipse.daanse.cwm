/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.daanse.orm.util.Basics;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Entities;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Ids;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Transients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A mapping that leaves something to JPA defaults is rejected before anything is written. */
class RejectsImplicitModelTest {

    /** Publisher with its id mapped, but its name neither mapped nor transient (E1) and a basic without column (E2). */
    private static Fixture implicit() {
        Fixture f = new Fixture();
        var p = Entities.create(f.mappings, f.publisher, f.publisherTable);
        Ids.create(p, f.publisherId, f.publisherIdCol);
        return f;
    }

    @Test
    void unmappedAttributeIsRejected() {
        Fixture f = implicit();

        assertThatThrownBy(f::convertStrict).isInstanceOf(InvalidMappingException.class)
                .satisfies(e -> assertThat(((InvalidMappingException) e).findings())
                        .extracting(OrmModelValidator.Finding::rule).contains("E1"));
    }

    @Test
    void basicWithoutColumnIsRejected() {
        Fixture f = implicit();
        Basics.create(f.mappings.getEntity().get(0), f.publisherName);

        assertThatThrownBy(f::convertStrict).isInstanceOf(InvalidMappingException.class)
                .satisfies(e -> assertThat(((InvalidMappingException) e).findings())
                        .extracting(OrmModelValidator.Finding::rule).contains("E2"));
    }

    @Test
    void lenientConversionReportsTheFindingsAsNotes() {
        Fixture f = implicit();

        var r = CwmOrmToEorm.convert(f.mappings, f.index(), true);

        assertThat(r.eorm().getEntity()).hasSize(1);
        assertThat(r.errors()).extracting(Note::rule).containsOnly(EormNotes.VALIDATION);
        assertThat(r.errors()).extracting(Note::message).anyMatch(m -> m.startsWith("E1"));
    }

    @Test
    void generatorWritesNothingForARejectedMapping(@TempDir Path out) throws IOException {
        Fixture f = implicit();

        assertThatThrownBy(() -> EormGenerator.withEcore(f.mappings, Fixture.ecoreFile(), out, "library.eorm", false))
                .isInstanceOf(InvalidMappingException.class);
        try (Stream<Path> files = Files.list(out)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void explicitMappingPasses() {
        Fixture f = implicit();
        Basics.create(f.mappings.getEntity().get(0), f.publisherName, f.publisherNameCol);
        Transients.create(f.mappings.getEntity().get(0), f.booksOfPublisher);

        var r = f.convertStrict();

        assertThat(r.errors()).isEmpty();
        assertThat(r.eorm().getEntity().get(0).getAttributes().getBasic()).hasSize(1);
    }
}
