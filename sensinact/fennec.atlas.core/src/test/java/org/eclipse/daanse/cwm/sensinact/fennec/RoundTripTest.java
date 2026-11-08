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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.event.atlas.mapping.MappingProfileRegistry;
import org.eclipse.fennec.event.atlas.mapping.impl.MappingProfileRegistryImpl;
import org.eclipse.fennec.event.atlas.model.mapping.MappingPackage;
import org.eclipse.fennec.event.atlas.model.mapping.MappingProfile;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping;
import org.eclipse.fennec.event.atlas.model.mapping.util.MappingResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The written files load the way a Fennec runtime loads them — a resource set with the mapping
 * package, the source packages registered by namespace URI and Fennec's resource factory — with
 * every reference resolved to the registered packages, and pass Fennec's own profile validation.
 */
class RoundTripTest {

    @TempDir
    Path dir;

    @ParameterizedTest
    @EnumSource(Corpus.class)
    void theFilesLoadInFennecsWayAndPassItsProfileValidation(Corpus corpus) throws Exception {
        Examples.Case c = corpus.build();
        AtlasGenerator.Output out = AtlasGenerator.withEcore(corpus.mappings(c), corpus.ecoreFiles(), dir, corpus.options);
        assertThat(out.result().errors()).isEmpty();
        assertThat(out.mappings()).hasSize(corpus.mappings(c).size()).allMatch(Files::isRegularFile);
        assertThat(out.profiles()).hasSize(corpus.options.profiles() ? 1 : 0);

        List<EPackage> registered = AtlasGenerator.loadEcore(corpus.ecoreFiles());
        ResourceSet rs = new ResourceSetImpl();
        rs.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        rs.getPackageRegistry().put(MappingPackage.eNS_URI, MappingPackage.eINSTANCE);
        registered.forEach(p -> rs.getPackageRegistry().put(p.getNsURI(), p));
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new MappingResourceFactoryImpl());

        MappingProfileRegistryImpl profiles = new MappingProfileRegistryImpl();
        for (Path file : out.mappings()) {
            ProviderMapping mapping = (ProviderMapping) rs.getResource(URI.createFileURI(file.toString()), true)
                    .getContents().get(0);
            EcoreUtil.resolveAll(rs);
            assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(rs)).as(file.getFileName().toString()).isEmpty();
            assertThat(mapping.getProviderClasses()).allMatch(e -> registered.contains(e.getEPackage()));
            MappingProfile profile = mapping.getProfile();
            if (corpus.options.profiles()) {
                assertThat(profile).isNotNull();
                if (profiles.getProfile(profile.getProfileId()).isEmpty()) {
                    profiles.registerProfile(profile);
                }
                MappingProfileRegistry.ValidationResult result = profiles.validateMapping(mapping);
                assertThat(result.getErrors()).as(file.getFileName().toString()).isEmpty();
                assertThat(result.getWarnings()).as(file.getFileName().toString()).isEmpty();
                assertThat(result.isValid()).isTrue();
            } else {
                assertThat(profile).isNull();
            }
        }
    }

    @Test
    void referencesGoByNamespaceUriAndProfilesRelative() throws Exception {
        Examples.Case c = Corpus.BATTERY.build();
        AtlasGenerator.Output out = AtlasGenerator.withEcore(Corpus.BATTERY.mappings(c), Corpus.BATTERY.ecoreFiles(), dir,
                Corpus.BATTERY.options);
        Path dragino = dir.resolve("mappings/dragino-battery-sensor.xmi");
        assertThat(out.mappings()).contains(dragino);
        assertThat(out.profiles()).containsExactly(dir.resolve("profiles/battery-sensor-profile.xmi"));
        String xmi = Files.readString(dragino, StandardCharsets.UTF_8);
        assertThat(xmi).contains("href=\"https://eclipse.org/fennec/lorawan/dragino#//DraginoLSE01Uplink\"")
                .contains("href=\"https://eclipse.org/fennec/lorawan#//UplinkMessage/deduplicationId\"")
                .contains("href=\"https://eclipse.org/fennec/lorawan/dragino#//DecodedObject/batV\"")
                .contains("href=\"http://www.eclipse.org/emf/2002/Ecore#//EDouble\"")
                .contains("href=\"../profiles/battery-sensor-profile.xmi#battery-sensor\"")
                .contains("timestamp=\"//@timestamp\"")
                .doesNotContain(".ecore#");
        String profile = Files.readString(out.profiles().get(0), StandardCharsets.UTF_8);
        assertThat(profile).contains("profileId=\"battery-sensor\"").contains("providerStrategy=\"UNIFIED\"")
                .contains("requiresFriendlyName=\"true\"");
    }

    @Test
    void withAnErrorNothingIsWritten() {
        Examples.Case c = Corpus.BATTERY.build();
        AtlasGenerator.Output out = AtlasGenerator.withEcore(Corpus.BATTERY.mappings(c),
                List.of(Corpus.resources().resolve("ecore/lorawan-uplink.ecore")), dir, Corpus.BATTERY.options);
        assertThat(out.result().errors()).isNotEmpty()
                .allMatch(n -> n.rule().equals(AtlasNotes.UNRESOLVED));
        assertThat(out.mappings()).isEmpty();
        assertThat(dir.resolve("mappings")).doesNotExist();
    }
}
