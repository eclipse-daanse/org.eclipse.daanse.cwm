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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standalone.EcoreIndex;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.RetentionHolds;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An error of any validator — the mapping model's, the recording and retention rules' or the
 * units' — stops the conversion and the generator, unless lenient; warnings never stop it. A rule
 * Fennec cannot carry stops the generator, unless lenient.
 */
class RejectsInvalidModelTest {

    @TempDir
    Path dir;

    private final Examples.Case battery = Corpus.BATTERY.build();
    private final List<EPackage> packages = AtlasGenerator.loadEcore(Corpus.BATTERY.ecoreFiles());

    private Resource level() {
        return battery.resource("battery-sensor", "battery", "level");
    }

    /** Two recording rules on one resource: an error of the recording validator. */
    private void twoRecordingRules() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RecordingRules.absolute(rules, "abs", 0.1, level());
        RecordingRules.relative(rules, "pct", 5.0, level());
    }

    @Test
    void anErrorOfTheMappingModelStopsTheConversion() {
        level().setType(null);
        assertThatThrownBy(() -> CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults()))
                .isInstanceOfSatisfying(InvalidMappingException.class, e -> assertThat(e.findings()).isNotEmpty()
                        .allMatch(n -> n.severity() == Note.Severity.ERROR && n.rule().equals(AtlasNotes.VALIDATION))
                        .anySatisfy(n -> assertThat(n.message()).startsWith("P2 ").contains("has no type")));
    }

    @Test
    void anErrorOfTheRecordingRulesStopsTheConversion() {
        twoRecordingRules();
        assertThatThrownBy(() -> CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults()))
                .isInstanceOfSatisfying(InvalidMappingException.class, e -> assertThat(e.findings())
                        .singleElement().satisfies(n -> {
                            assertThat(n.message()).startsWith("R2 ").contains("abs, pct");
                            assertThat(n.element()).endsWith("level");
                        }));
    }

    @Test
    void anErrorOfTheRetentionSchedulesStopsTheConversion() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RetentionSchedules.values(rules, "keep-90d", "P90D", level());
        RetentionSchedules.values(rules, "keep-1y", "P365D", level());
        assertThatThrownBy(() -> CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults()))
                .isInstanceOfSatisfying(InvalidMappingException.class, e -> assertThat(e.findings())
                        .singleElement().satisfies(n -> assertThat(n.message()).startsWith("L2 ")));
    }

    @Test
    void anErrorOfTheObligationsStopsTheConversion() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RetentionHolds.release(RetentionHolds.create(rules, "dispute", "warranty dispute",
                Instant.parse("2026-11-01T00:00:00Z"), level()), Instant.parse("2026-10-01T00:00:00Z"));
        assertThatThrownBy(() -> CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults()))
                .isInstanceOfSatisfying(InvalidMappingException.class, e -> assertThat(e.findings())
                        .singleElement().satisfies(n -> assertThat(n.message()).startsWith("O2 ")));
    }

    @Test
    void aRuleFennecCannotCarryStopsTheGeneratorUnlessLenient() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RecordingRules.heartbeat(RecordingRules.absolute(rules, "abs", 0.1, level()), Duration.ofHours(1));
        AtlasGenerator.Output strict = AtlasGenerator.withEcore(battery.mappings(), Corpus.BATTERY.ecoreFiles(), dir,
                Options.defaults());
        assertThat(strict.result().errors()).singleElement()
                .satisfies(n -> assertThat(n.rule()).isEqualTo(AtlasNotes.RULE));
        assertThat(strict.mappings()).isEmpty();
        assertThat(dir).isEmptyDirectory();
        AtlasGenerator.Output lenient = AtlasGenerator.withEcore(battery.mappings(), Corpus.BATTERY.ecoreFiles(), dir,
                Options.defaults(), Map.of(), true);
        assertThat(lenient.result().errors()).isEmpty();
        assertThat(lenient.mappings()).hasSize(2);
    }

    @Test
    void anErrorOfTheUnitsStopsTheConversion() {
        Quantity volts = Quantities.of(level()).orElseThrow();
        Quantities.create(volts.getNamespace(), "levelAgain", volts.getUnit(), level());
        assertThatThrownBy(() -> CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults()))
                .isInstanceOfSatisfying(InvalidMappingException.class, e -> assertThat(e.findings())
                        .singleElement().satisfies(n -> {
                            assertThat(n.message()).startsWith("U3 ").contains("has 2 quantities");
                            assertThat(n.element()).endsWith("level");
                        }));
    }

    @Test
    void theGeneratorWritesNothingOnAValidationError() {
        twoRecordingRules();
        assertThatThrownBy(() -> AtlasGenerator.withEcore(battery.mappings(), Corpus.BATTERY.ecoreFiles(), dir,
                Options.defaults())).isInstanceOf(InvalidMappingException.class);
        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void lenientlyTheGeneratorWritesAndReportsTheValidationErrors() {
        twoRecordingRules();
        AtlasGenerator.Output out = AtlasGenerator.withEcore(battery.mappings(), Corpus.BATTERY.ecoreFiles(), dir,
                Options.defaults(), Map.of(), true);
        assertThat(out.result().errors()).singleElement().satisfies(n -> {
            assertThat(n.rule()).isEqualTo(AtlasNotes.VALIDATION);
            assertThat(n.message()).startsWith("R2 ");
        });
        assertThat(out.mappings()).hasSize(2);
        assertThat(out.profiles()).hasSize(1);
    }

    @Test
    void anErrorOfTheConversionStopsTheGeneratorAlsoWhenLenient() {
        AtlasGenerator.Output out = AtlasGenerator.withEcore(battery.mappings(),
                List.of(Corpus.resources().resolve("ecore/lorawan-uplink.ecore")), dir, Options.defaults(), Map.of(), true);
        assertThat(out.result().errors()).isNotEmpty().allMatch(n -> n.rule().equals(AtlasNotes.UNRESOLVED));
        assertThat(out.mappings()).isEmpty();
        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void warningsDoNotStopIt() {
        Examples.Case weather = Examples.weather();
        CwmToAtlas.Result result = CwmToAtlas.convert(weather.mappings(),
                EcoreIndex.of(AtlasGenerator.loadEcore(Corpus.WEATHER.ecoreFiles())), Options.defaults().withProfiles(false));
        assertThat(result.errors()).isEmpty();
        assertThat(result.notes()).filteredOn(n -> n.rule().equals(AtlasNotes.VALIDATION)).isNotEmpty()
                .allMatch(n -> n.severity() == Note.Severity.WARNING);
        assertThat(result.mappings()).hasSize(1);
    }
}
