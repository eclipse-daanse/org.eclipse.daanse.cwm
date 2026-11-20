/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.xmla;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.Typings;
import org.eclipse.emf.common.util.Diagnostic;
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
 * The server's types as a standard set: the checked-in file is what the code writes, every element has a UUID of its
 * own, the set keeps the rules of the model, {@code ServerTypes.create} builds the same, and a model file refers to
 * its types by URI and UUID.
 */
class ServerTypesStandardSetTest {

    private static final Path DIR = Path.of(System.getProperty("user.dir"),
            "src/main/resources/org/eclipse/daanse/cwm/model/daanse/semantictype/xmla");

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    /** Run with -Ddaanse.standard.write=true to write the file anew after a change to the code or the tables. */
    @Test
    void theCheckedInFileIsWhatTheCodeWrites() {
        assertThat(StandardSets.upToDate(ServerTypes.standardSet(), DIR.resolve(ServerTypes.SET_FILE)))
                .as(ServerTypes.SET_FILE + " is what the code writes; run the test with -D" + StandardSets.WRITE
                        + "=true to write it again")
                .isTrue();
    }

    @Test
    void everyElementHasAUuidOfItsOwn() {
        Resource set = ServerTypes.load(new ResourceSetImpl()).eResource();
        assertThat(ids(set.getContents().get(0))).as("the package, the four libraries and their 292 types")
                .hasSize(297).doesNotHaveDuplicates().allMatch(StandardSets.SHORT_UUID.asMatchPredicate());
    }

    /**
     * {@code ServerTypes.create} builds the same libraries into a model of one's own; where the model lies in an XMI
     * resource, its elements carry the UUIDs of the set.
     */
    @Test
    void createBuildsTheSameLibrariesWithTheSameUuids() {
        Package standard = (Package) ServerTypes.standardSet().getContents().get(0);
        Model model = Models.create("cube");
        new XMIResourceImpl(URI.createURI("cube.xmi")).getContents().add(model);
        Package built = ServerTypes.create(model);
        assertThat(ids(built)).as("the UUIDs of the elements built into the model").isEqualTo(ids(standard));
        built.setNamespace(null);
        assertThat(EcoreUtil.equals(standard, built)).as("the set and ServerTypes.create build the same").isTrue();
    }

    @Test
    void theFileInTheJarHoldsTheFourLibraries() {
        Package server = ServerTypes.load(new ResourceSetImpl());
        assertThat(server.eResource().getURI().toString()).as("the URI of the set").isEqualTo(
                "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla/server-types.xmi");
        assertThat(SemanticTypes.of(ServerTypes.attributes(server))).as("the attribute types").hasSize(240);
        assertThat(SemanticTypes.of(ServerTypes.dimensions(server))).as("the dimension types").hasSize(15);
        assertThat(SemanticTypes.of(ServerTypes.accounts(server))).as("the account types").hasSize(7);
        assertThat(SemanticTypes.of(ServerTypes.groups(server))).as("the groups").hasSize(30);
        assertThat(ServerTypes.type(ServerTypes.accounts(server), "Asset").getSourceNumber()).as("Asset")
                .isEqualTo(4);
    }

    @Test
    void theSetKeepsTheRulesOfTheModel() {
        Package server = ServerTypes.load(new ResourceSetImpl());
        assertThat(SemanticTypeModelValidator.check(server)).as("findings of the model's rules").isEmpty();
        assertThat(XmlaTypesValidator.check(server)).as("findings of the server's rules").isEmpty();
        Diagnostic diagnostic = Diagnostician.INSTANCE.validate(server);
        assertThat(diagnostic.getChildren()).as("findings of the EMF check, OCL included").isEmpty();
    }

    /**
     * A model file types a column and a class by two types of the set, written as {@code href="<uri>#<uuid>"}; read in
     * a fresh resource set where the set is only mapped, the typings reach the types of the set in the jar.
     */
    @Test
    void aModelFileRefersToTwoTypesOfTheSetByUriAndUuid(@TempDir Path dir) throws Exception {
        ResourceSet writing = new ResourceSetImpl();
        Package server = ServerTypes.load(writing);
        Path file = dir.resolve("harbours.xmi");
        Resource model = new XMIResourceImpl(URI.createFileURI(file.toString()));
        writing.getResources().add(model);
        Model harbours = Models.create("harbours");
        model.getContents().add(harbours);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class port = CoreFactory.eINSTANCE.createClass();
        port.setName("port");
        harbours.getOwnedElement().add(port);
        Attribute town = CoreFactory.eINSTANCE.createAttribute();
        town.setName("town");
        port.getFeature().add(town);
        SemanticType city = ServerTypes.type(ServerTypes.attributes(server), "City");
        SemanticType geography = ServerTypes.type(ServerTypes.dimensions(server), "Geography");
        Typings.create(harbours, "towns", city, town);
        Typings.create(harbours, "ports", geography, port);
        model.save(Map.of());
        String cityId = server.eResource().getURIFragment(city);
        String geographyId = server.eResource().getURIFragment(geography);
        assertThat(Files.readString(file)).as("the references by the URI of the set and the UUIDs").contains(
                "href=\"" + ServerTypes.SET_URI + "#" + cityId + "\"",
                "href=\"" + ServerTypes.SET_URI + "#" + geographyId + "\"");

        ResourceSet reading = new ResourceSetImpl();
        ServerTypes.map(reading);
        Resource again = new XMIResourceImpl(URI.createFileURI(file.toString()));
        reading.getResources().add(again);
        again.load(Map.of());
        Model read = (Model) again.getContents().get(0);
        Typing towns = (Typing) read.getOwnedElement().get(1);
        Typing ports = (Typing) read.getOwnedElement().get(2);
        Resource set = reading.getResource(URI.createURI(ServerTypes.SET_URI), false);
        assertThat(towns.getType()).as("the town is a City of the set").isSameAs(set.getEObject(cityId));
        assertThat(ports.getType()).as("the port is a Geography of the set").isSameAs(set.getEObject(geographyId));
        assertThat(SemanticTypes.libraryOf(towns.getType()).map(TypeLibrary::getUri)).as("its library")
                .contains(ServerTypes.ATTRIBUTE_URI);
    }

    /** The UUIDs of {@code root} and of everything it holds, in the order of its contents. */
    private static List<String> ids(EObject root) {
        List<String> ids = new ArrayList<>();
        ids.add(root.eResource().getURIFragment(root));
        root.eAllContents().forEachRemaining(e -> ids.add(e.eResource().getURIFragment(e)));
        return ids;
    }
}
