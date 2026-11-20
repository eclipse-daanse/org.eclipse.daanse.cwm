/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitFactor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The SI as a standard set: the checked-in file is what the code writes, every element has a UUID of its own, the set
 * keeps the rules of the model, {@code SI.create} builds the same SI, and a model file refers to its units by URI and
 * UUID.
 */
class SIStandardSetTest {

    private static final Path DIR = Path.of(System.getProperty("user.dir"),
            "src/main/resources/org/eclipse/daanse/cwm/model/daanse/units/util");

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    /** Run with -Ddaanse.standard.write=true to write the file anew after a change to the code. */
    @Test
    void theCheckedInFileIsWhatTheCodeWrites() {
        assertThat(StandardSets.upToDate(SI.standardSet(), DIR.resolve(SI.SET_FILE)))
                .as(SI.SET_FILE + " is what the code writes; run the test with -D" + StandardSets.WRITE
                        + "=true to write it again")
                .isTrue();
    }

    @Test
    void everyElementHasAUuidOfItsOwn() {
        Resource set = SI.load(new ResourceSetImpl()).eResource();
        assertThat(ids(set.getContents().get(0))).as("the system and its 196 elements").hasSize(197)
                .doesNotHaveDuplicates().allMatch(StandardSets.SHORT_UUID.asMatchPredicate());
    }

    /**
     * {@code SI.create} builds the same SI into a model of one's own; where the model lies in an XMI resource, its
     * elements carry the UUIDs of the set, so that the model and the file agree.
     */
    @Test
    void createBuildsTheSameSiWithTheSameUuids() {
        SystemOfUnits standard = (SystemOfUnits) SI.standardSet().getContents().get(0);
        Model lab = Models.create("lab");
        new XMIResourceImpl(URI.createURI("lab.xmi")).getContents().add(lab);
        SystemOfUnits built = SI.create(lab);
        assertThat(ids(built)).as("the UUIDs of the elements built into the lab").isEqualTo(ids(standard));
        built.setNamespace(null);
        assertThat(EcoreUtil.equals(standard, built)).as("the set and SI.create build the same SI").isTrue();
        Model loose = Models.create("loose");
        SystemOfUnits outside = SI.create(loose);
        new XMIResourceImpl(URI.createURI("loose.xmi")).getContents().add(loose);
        assertThat(outside.eResource().getURIFragment(outside)).as("built while in no resource: no UUID")
                .startsWith("/");
    }

    @Test
    void theFileInTheJarHoldsTheSi() {
        SystemOfUnits si = SI.load(new ResourceSetImpl());
        assertThat(si.eResource().getURI().toString()).as("the URI of the set")
                .isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.units/si.xmi");
        assertThat(si.getOwnedElement().stream().filter(Unit.class::isInstance)).as("the units").hasSize(59);
        assertThat(si.getOwnedElement().stream().filter(QuantityKind.class::isInstance)).as("the quantity kinds")
                .hasSize(33);
        assertThat(si.getOwnedElement().stream().filter(Prefix.class::isInstance)).as("the prefixes").hasSize(24);
        assertThat(si.getOwnedElement().stream().filter(Dimension.class::isInstance)).as("the dimensions").hasSize(30);
        assertThat(((DerivedUnit) SI.unit(si, SI.NEWTON)).getFactor()).as("the factors of the newton")
                .extracting(UnitFactor::getUnit).containsExactly(SI.unit(si, SI.KILOGRAM), SI.unit(si, SI.METRE),
                        SI.unit(si, SI.SECOND));
    }

    @Test
    void theSetKeepsTheRulesOfTheModel() {
        SystemOfUnits si = SI.load(new ResourceSetImpl());
        assertThat(UnitsModelValidator.check(si)).as("findings of the units rules").isEmpty();
        assertThat(Diagnostician.INSTANCE.validate(si).getChildren()).as("findings of the EMF check, OCL included")
                .isEmpty();
    }

    /**
     * A model file says a column is measured in degrees Celsius, a temperature, written as {@code href="<uri>#<uuid>"};
     * read in a fresh resource set where the set is only mapped, the quantity reaches the unit and the kind of the set
     * in the jar.
     */
    @Test
    void aModelFileRefersToAUnitAndAKindOfTheSetByUriAndUuid(@TempDir Path dir) throws Exception {
        ResourceSet writing = new ResourceSetImpl();
        SystemOfUnits si = SI.load(writing);
        Path file = dir.resolve("boilers.xmi");
        Resource model = new XMIResourceImpl(URI.createFileURI(file.toString()));
        writing.getResources().add(model);
        Model boilers = Models.create("boilers");
        model.getContents().add(boilers);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class boiler = CoreFactory.eINSTANCE.createClass();
        boiler.setName("boiler");
        boilers.getOwnedElement().add(boiler);
        Attribute water = CoreFactory.eINSTANCE.createAttribute();
        water.setName("water");
        boiler.getFeature().add(water);
        Unit celsius = SI.unit(si, SI.DEGREE_CELSIUS);
        QuantityKind temperature = SI.kind(si, "Temperature");
        Quantities.kind(Quantities.create(boilers, "water", celsius, water), temperature);
        model.save(Map.of());
        String celsiusId = si.eResource().getURIFragment(celsius);
        String temperatureId = si.eResource().getURIFragment(temperature);
        assertThat(Files.readString(file)).as("the references by the URI of the set and the UUIDs").contains(
                "href=\"" + SI.SET_URI + "#" + celsiusId + "\"", "href=\"" + SI.SET_URI + "#" + temperatureId + "\"");

        ResourceSet reading = new ResourceSetImpl();
        SI.map(reading);
        Resource again = new XMIResourceImpl(URI.createFileURI(file.toString()));
        reading.getResources().add(again);
        again.load(Map.of());
        Quantity read = (Quantity) ((Model) again.getContents().get(0)).getOwnedElement().get(1);
        Resource set = reading.getResource(URI.createURI(SI.SET_URI), false);
        assertThat(read.getUnit()).as("the unit of the set").isSameAs(set.getEObject(celsiusId));
        assertThat(read.getQuantityKind()).as("the kind of the set").isSameAs(set.getEObject(temperatureId));
        assertThat(Units.convert(new BigDecimal("20"), read.getUnit(), SI.unit(SI.load(reading), SI.KELVIN)))
                .as("it converts as the SI does").isEqualByComparingTo("293.15");
    }

    /** The UUIDs of {@code root} and of everything it holds, in the order of its contents. */
    private static List<String> ids(EObject root) {
        List<String> ids = new ArrayList<>();
        ids.add(root.eResource().getURIFragment(root));
        root.eAllContents().forEachRemaining(e -> ids.add(e.eResource().getURIFragment(e)));
        return ids;
    }
}
