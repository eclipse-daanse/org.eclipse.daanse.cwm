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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.persistence.eorm.Embeddable;
import org.eclipse.fennec.persistence.eorm.Entity;
import org.eclipse.fennec.persistence.eorm.EntityMappings;
import org.eclipse.fennec.persistence.eorm.MappedSuperclass;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every corpus mapping set: Ecore and EORM written side by side, the {@code .eorm} reloaded the
 * way Fennec loads it, every reference resolved into the {@code .ecore} files next to it, the
 * reloaded model equal to the written one, as many entities and attribute mappings as the CWM
 * mapping, and only documented notes.
 */
class CorpusTest {

    private static final Pattern HREF = Pattern.compile("href=\"([^\"#]*)#");

    static Stream<String> files() {
        return Corpus.files().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    void writesReloadsAndKeepsTheCounts(String file, @TempDir Path out) throws IOException {
        var cwm = Corpus.mapping(file);
        EormGenerator.Output written = EormGenerator.withEcore(cwm, Corpus.ecoreFiles(file), out,
                Corpus.eormName(file), false);

        assertThat(written.result().errors()).as("errors").isEmpty();
        assertThat(written.result().notes()).extracting(Note::rule).allMatch(EormNotes.ALL::contains);
        assertThat(written.eorm()).isRegularFile().hasParent(out);

        ResourceSet rs = EormWriter.newResourceSet();
        EntityMappings reloaded = EormWriter.load(rs, written.eorm());
        EcoreUtil.resolveAll(rs);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(rs)).as("unresolved proxies").isEmpty();
        assertThat(EcoreUtil.equals(written.result().eorm(), reloaded)).as("reloaded equals written").isTrue();

        List<String> ecoreNames = written.ecoreFiles().stream().map(p -> p.getFileName().toString()).toList();
        List<String> hrefs = hrefs(Files.readString(written.eorm()));
        assertThat(hrefs).isNotEmpty().allMatch(ecoreNames::contains);
        for (Entity e : reloaded.getEntity()) {
            assertThat(e.getClass_().eResource().getURI().lastSegment()).isIn(ecoreNames);
            assertThat(e.getClass_().eIsProxy()).isFalse();
        }

        assertThat(reloaded.getEntity()).hasSameSizeAs(cwm.getEntity());
        assertThat(reloaded.getMappedSuperclass()).hasSameSizeAs(cwm.getMappedSuperclass());
        assertThat(reloaded.getEmbeddable()).hasSameSizeAs(cwm.getEmbeddable());
        int cwmMappings = OrmBindings.managedTypes(cwm).stream().mapToInt(mt -> OrmBindings.mappings(mt).size())
                .sum();
        assertThat(attributeMappings(reloaded)).isEqualTo(cwmMappings);
        for (ManagedType mt : OrmBindings.managedTypes(cwm)) {
            assertThat(mt.getCwmClass()).isNotNull();
        }
    }

    /** The distinct files the {@code href}s of an EORM file point to. */
    static List<String> hrefs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = HREF.matcher(xml);
        while (m.find()) {
            if (!out.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    /** The attribute mappings of all managed types of an EORM mapping set. */
    static int attributeMappings(EntityMappings em) {
        int n = 0;
        List<EObject> containers = new ArrayList<>();
        em.getEntity().stream().map(Entity::getAttributes).forEach(containers::add);
        em.getMappedSuperclass().stream().map(MappedSuperclass::getAttributes).forEach(containers::add);
        em.getEmbeddable().stream().map(Embeddable::getAttributes).forEach(containers::add);
        for (EObject attributes : containers) {
            if (attributes == null) {
                continue;
            }
            for (EStructuralFeature f : attributes.eClass().getEAllStructuralFeatures()) {
                if (f instanceof EReference r && r.isContainment()) {
                    n += r.isMany() ? ((List<?>) attributes.eGet(r)).size() : attributes.eIsSet(r) ? 1 : 0;
                }
            }
        }
        return n;
    }

    /** All objects of a type below a root. */
    static <T> List<T> all(EObject root, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            EObject o = it.next();
            if (type.isInstance(o)) {
                out.add(type.cast(o));
            }
        }
        return out;
    }
}
