/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.dcat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatVocabularies;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** The example of the README: a catalog that publishes a CWM class as a dataset. */
class DcatExampleTest {

    private static final DcatFactory DF = DcatFactory.eINSTANCE;

    /** Lines of the XMI {@link #aCatalogPublishesAClassAsDataset()} writes. */
    private static final String XMI = """
            <ownedElement xsi:type="core:Package" name="HR">
            <ownedElement xsi:type="core:Class" name="EMPLOYEE"/>
            <ownedElement xsi:type="dcat:Catalog" name="open-data" homepage="https://data.example.org" \
            resource="_employees _hr-api">
            <ownedElement xsi:type="businessinformation:ResponsibleParty" name="statistics-office"/>
            <ownedElement xsi:type="dcat:Dataset" name="employees" description="All employees with a running \
            contract" subject="_EMPLOYEE" \
            accrualPeriodicityIri="http://publications.europa.eu/resource/authority/frequency/DAILY">
            <themeIri>http://publications.europa.eu/resource/authority/data-theme/GOVE</themeIri>
            <attribution agent="_statistics-office"/>
            <distribution name="employees-csv" mediaType="text/csv">
            <downloadUrl>https://data.example.org/employees.csv</downloadUrl>
            <checksum algorithm="SHA256" checksumValue="9f86d081884c7d65"/>
            <ownedElement xsi:type="dcat:DataService" name="hr-api" servesDataset="_employees">
            <endpointUrl>https://api.example.org/hr</endpointUrl>
            """;

    @Test
    void aCatalogPublishesAClassAsDataset() throws IOException {
        Model model = CoreFactory.eINSTANCE.createModel();
        model.setName("hr");
        Package hr = CoreFactory.eINSTANCE.createPackage();
        hr.setName("HR");
        model.getOwnedElement().add(hr);
        Class employee = CoreFactory.eINSTANCE.createClass();
        employee.setName("EMPLOYEE");
        hr.getOwnedElement().add(employee);

        Catalog catalog = DF.createCatalog();
        catalog.setName("open-data");
        catalog.setHomepage("https://data.example.org");
        model.getOwnedElement().add(catalog);
        ResponsibleParty office = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        office.setName("statistics-office");
        catalog.getOwnedElement().add(office);

        Dataset employees = DF.createDataset();
        employees.setName("employees");
        employees.setDescription("All employees with a running contract");
        employees.getSubject().add(employee);
        employees.getThemeIri().add(DcatVocabularies.themeIri("GOVE"));
        employees.setAccrualPeriodicityIri(DcatVocabularies.frequencyIri("DAILY"));
        Attribution publisher = DF.createAttribution();
        publisher.setAgent(office);
        publisher.setRole(AgentRole.PUBLISHER);
        employees.getAttribution().add(publisher);
        Distribution csv = DF.createDistribution();
        csv.setName("employees-csv");
        csv.getDownloadUrl().add("https://data.example.org/employees.csv");
        csv.setMediaType("text/csv");
        Checksum sum = DF.createChecksum();
        sum.setAlgorithm(ChecksumAlgorithm.SHA256);
        sum.setChecksumValue("9f86d081884c7d65");
        csv.setChecksum(sum);
        employees.getDistribution().add(csv);
        catalog.getOwnedElement().add(employees);
        catalog.getResource().add(employees);

        DataService api = DF.createDataService();
        api.setName("hr-api");
        api.getEndpointUrl().add("https://api.example.org/hr");
        api.getServesDataset().add(employees);
        catalog.getOwnedElement().add(api);
        catalog.getResource().add(api);

        String xmi = xmi(model, "catalog.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** {@code root} as XMI with short ids and without the ids, written to {@code target/<file>} as well. */
    private static String xmi(Model root, String file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI(file));
        r.getContents().add(root);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target", file), xmi, StandardCharsets.UTF_8);
        return xmi;
    }
}
