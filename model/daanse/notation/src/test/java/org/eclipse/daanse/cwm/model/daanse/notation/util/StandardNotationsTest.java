/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The usual notations as a standard set: the checked-in file is what the code writes, every element has a UUID of its
 * own, the set keeps the rules of the model, every notation reads and writes its form, and the attributes of a model
 * file typed by its data types are read in their notations.
 */
class StandardNotationsTest {

    private static final Path DIR = Path.of(System.getProperty("user.dir"),
            "src/main/resources/org/eclipse/daanse/cwm/model/daanse/notation/util");

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    /** Run with -Ddaanse.standard.write=true to write the file anew after a change to the code. */
    @Test
    void theCheckedInFileIsWhatTheCodeWrites() {
        assertThat(StandardSets.upToDate(StandardNotations.standardSet(), DIR.resolve(StandardNotations.SET_FILE)))
                .as(StandardNotations.SET_FILE + " is what the code writes; run the test with -D" + StandardSets.WRITE
                        + "=true to write it again")
                .isTrue();
    }

    @Test
    void everyElementHasAUuidOfItsOwn() {
        Resource set = StandardNotations.load(new ResourceSetImpl()).eResource();
        List<String> ids = new ArrayList<>();
        set.getAllContents().forEachRemaining(e -> ids.add(set.getURIFragment(e)));
        assertThat(ids).as("the package and, per notation, its data type, tagged value, notation and form")
                .hasSize(57).doesNotHaveDuplicates().allMatch(StandardSets.SHORT_UUID.asMatchPredicate());
    }

    /**
     * A count since an epoch refers to its unit in the set of the SI by URI and UUID; where the set of the notations
     * is mapped, the SI is mapped beside it and the unit is read from the jar of {@code units}.
     */
    @Test
    void aCountRefersToItsUnitInTheSetOfTheSi() {
        ResourceSet resourceSet = new ResourceSetImpl();
        SystemOfUnits si = SI.load(resourceSet);
        String secondId = si.eResource().getURIFragment(SI.unit(si, SI.SECOND));
        assertThat(StandardSets.text(StandardNotations.standardSet())).as("the unit by the URI of the SI and its UUID")
                .contains("href=\"" + SI.SET_URI + "#" + secondId + "\"");
        ResourceSet mapped = new ResourceSetImpl();
        Package notations = StandardNotations.load(mapped);
        TemporalNumber unixSeconds = (TemporalNumber) Notations.own(type(notations, StandardNotations.UNIX_SECONDS))
                .get(0).getForm();
        assertThat(unixSeconds.getUnit().eIsProxy()).as("resolved").isFalse();
        assertThat(unixSeconds.getUnit().eResource().getURI().toString()).as("from the set of the SI")
                .isEqualTo(SI.SET_URI);
        assertThat(unixSeconds.getUnit().getName()).as("the second").isEqualTo(SI.SECOND);
    }

    @Test
    void everyNotationReadsAndWritesItsForm() {
        Package notations = StandardNotations.load(new ResourceSetImpl());
        assertThat(read(notations, StandardNotations.ISO_DATE, "2026-11-19")).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(read(notations, StandardNotations.ISO_TIME, "10:15:30")).isEqualTo(LocalTime.of(10, 15, 30));
        assertThat(read(notations, StandardNotations.ISO_DATE_TIME, "2026-11-19T10:15:30"))
                .isEqualTo(LocalDateTime.of(2026, 11, 19, 10, 15, 30));
        assertThat(read(notations, StandardNotations.ISO_DATE_TIME_WITH_OFFSET, "2026-11-19T10:15:30+01:00"))
                .isEqualTo(OffsetDateTime.parse("2026-11-19T10:15:30+01:00"));
        assertThat(read(notations, StandardNotations.GERMAN_DATE, "19.11.2026")).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(read(notations, StandardNotations.UNIX_SECONDS, "86400")).isEqualTo(Instant.parse(
                "1970-01-02T00:00:00Z"));
        assertThat(read(notations, StandardNotations.UNIX_MILLISECONDS, "1500"))
                .isEqualTo(Instant.parse("1970-01-01T00:00:01.500Z"));
        assertThat(read(notations, StandardNotations.SPREADSHEET_DAYS, "61"))
                .isEqualTo(LocalDateTime.of(1900, 3, 1, 0, 0));
        assertThat((BigDecimal) read(notations, StandardNotations.ENGLISH_NUMBER, "1,234,567.5"))
                .isEqualByComparingTo("1234567.5");
        assertThat((BigDecimal) read(notations, StandardNotations.GERMAN_NUMBER, "1.234.567,5"))
                .isEqualByComparingTo("1234567.5");
        assertThat(read(notations, StandardNotations.TRUE_FALSE, "TRUE")).isEqualTo(Boolean.TRUE);
        assertThat(read(notations, StandardNotations.ONE_ZERO, "0")).isEqualTo(Boolean.FALSE);
        assertThat(read(notations, StandardNotations.YES_NO, "No")).isEqualTo(Boolean.FALSE);
        assertThat(read(notations, StandardNotations.JA_NEIN, "Ja")).isEqualTo(Boolean.TRUE);
        assertThat(codec(notations, StandardNotations.GERMAN_NUMBER).write(new BigDecimal("1234567.5")))
                .as("a German number written").isEqualTo("1.234.567,5");
        assertThat(codec(notations, StandardNotations.UNIX_MILLISECONDS).write(Instant.parse("1970-01-01T00:00:02Z")))
                .as("milliseconds written").isEqualTo("2000");
    }

    @Test
    void theSetKeepsTheRulesOfTheModel() {
        Package notations = StandardNotations.load(new ResourceSetImpl());
        assertThat(NotationModelValidator.check(notations)).as("findings of the notation rules").isEmpty();
        assertThat(Diagnostician.INSTANCE.validate(notations).getChildren())
                .as("findings of the EMF check, OCL included").isEmpty();
    }

    /**
     * Two attributes of a class are typed by two data types of the set, written as {@code href="<uri>#<uuid>"}; read
     * in a fresh resource set where the set is only mapped, they reach the data types of the set in the jar and are
     * read in their notations.
     */
    @Test
    void aClassTypesTwoAttributesByDataTypesOfTheSet(@TempDir Path dir) throws Exception {
        ResourceSet writing = new ResourceSetImpl();
        Package notations = StandardNotations.load(writing);
        Path file = dir.resolve("ferries.xmi");
        Resource model = new XMIResourceImpl(URI.createFileURI(file.toString()));
        writing.getResources().add(model);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class ferry = CoreFactory.eINSTANCE.createClass();
        ferry.setName("ferry");
        model.getContents().add(ferry);
        DataType withOffset = type(notations, StandardNotations.ISO_DATE_TIME_WITH_OFFSET);
        DataType germanNumber = type(notations, StandardNotations.GERMAN_NUMBER);
        ferry.getFeature().add(attribute("departs", withOffset));
        ferry.getFeature().add(attribute("fare", germanNumber));
        model.save(Map.of());
        String withOffsetId = notations.eResource().getURIFragment(withOffset);
        String germanNumberId = notations.eResource().getURIFragment(germanNumber);
        assertThat(Files.readString(file)).as("the references by the URI of the set and the UUIDs").contains(
                "href=\"" + StandardNotations.SET_URI + "#" + withOffsetId + "\"",
                "href=\"" + StandardNotations.SET_URI + "#" + germanNumberId + "\"");

        ResourceSet reading = new ResourceSetImpl();
        StandardNotations.map(reading);
        Resource again = new XMIResourceImpl(URI.createFileURI(file.toString()));
        reading.getResources().add(again);
        again.load(Map.of());
        var features = ((org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class) again.getContents().get(0))
                .getFeature();
        Attribute departs = (Attribute) features.get(0);
        Attribute fare = (Attribute) features.get(1);
        Resource set = reading.getResource(URI.createURI(StandardNotations.SET_URI), false);
        assertThat(departs.getType()).as("departs is typed by the set").isSameAs(set.getEObject(withOffsetId));
        assertThat(fare.getType()).as("fare is typed by the set").isSameAs(set.getEObject(germanNumberId));
        assertThat(Notations.of(fare).orElseThrow().eResource()).as("the notation of fare is its data type's")
                .isSameAs(set);
        assertThat((BigDecimal) Codec.of(Notations.of(fare).orElseThrow()).read("12,50")).as("a fare read")
                .isEqualByComparingTo("12.50");
    }

    private static Object read(Package notations, String name, String text) {
        return codec(notations, name).read(text);
    }

    private static Codec codec(Package notations, String name) {
        DataType type = type(notations, name);
        return Codec.of(Notations.own(type).get(0));
    }

    private static DataType type(Package notations, String name) {
        return StandardNotations.type(notations, name).orElseThrow();
    }

    private static Attribute attribute(String name, DataType type) {
        Attribute a = CoreFactory.eINSTANCE.createAttribute();
        a.setName(name);
        a.setType(type);
        return a;
    }
}
