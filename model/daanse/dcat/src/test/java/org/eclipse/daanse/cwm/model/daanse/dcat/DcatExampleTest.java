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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.daanse.dcat.examples.Checks;
import org.eclipse.daanse.cwm.model.daanse.dcat.examples.Elements;
import org.eclipse.daanse.cwm.model.daanse.dcat.examples.MunicipalUtility;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Catalogs;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DataServices;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Datasets;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatVocabularies;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Distributions;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Portals;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.Test;

/**
 * The snippets of the README, one test each, on the model of the municipal utility that runs
 * through it. An XMI snippet is read from the README and every line of it is found in the XMI the
 * model is written to; a Java snippet is repeated here call for call, its comments as assertions.
 */
class DcatExampleTest {

    private final MunicipalUtility u = new MunicipalUtility();

    @Test
    void aPortalItsCatalogAndADataset() throws IOException {
        assertThat(xmi(u.model, "utility.xmi"))
                .contains(lines(readmeBlock("A portal, its catalog and a dataset", "xml")));

        assertThat(Portals.accessService(u.portal)).contains(u.portalApi);
        assertThat(Portals.catalogs(u.portal)).containsExactly(u.openData);
        assertThat(Portals.portalOf(u.openData)).contains(u.portal);
        assertThat(Catalogs.datasets(u.openData)).containsExactly(u.pools);
        assertThat(Catalogs.services(u.openData)).containsExactly(u.ogcAssets);
        assertThat(u.pools.getAttribution().get(0).getRole()).isEqualTo(AgentRole.PUBLISHER);
    }

    @Test
    void protocolAndPath() {
        assertThat(DataServices.protocolOf(u.ogcAssets)).contains("http://www.opengis.net/spec/ogcapi-features-1/");
        Distribution geojson = Distributions.servedBy(u.pools, u.ogcAssets).orElseThrow();
        assertThat(DataServices.pathOf(geojson)).contains("collections/pools");
        assertThat(DataServices.protocolOf(u.assetsDb)).contains(DcatVocabularies.PROTOCOL_JDBC);
        assertThat(DataServices.pathOf(u.poolRegisterDb)).contains("leisure.pool");
        assertThat(DataServices.protocolOf(u.portalApi)).isEmpty();

        assertThat(DcatVocabularies.protocolNamedBy("http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core"))
                .contains(DcatVocabularies.PROTOCOL_OGC_API_FEATURES);
        assertThat(DcatVocabularies.PROTOCOLS).containsExactly(DcatVocabularies.PROTOCOL_OGC_API_FEATURES,
                DcatVocabularies.PROTOCOL_OGC_SENSORTHINGS, DcatVocabularies.PROTOCOL_ODATA,
                DcatVocabularies.PROTOCOL_GEOJSON, DcatVocabularies.PROTOCOL_GRAPHQL, DcatVocabularies.PROTOCOL_OPENAPI,
                DcatVocabularies.PROTOCOL_MQTT, DcatVocabularies.PROTOCOL_XMLA, DcatVocabularies.PROTOCOL_QGIS_PROJECT,
                DcatVocabularies.PROTOCOL_JDBC);
        String table = readmeSection("Protocol and path");
        for (String protocol : DcatVocabularies.PROTOCOLS) {
            String own = protocol.startsWith(DcatVocabularies.DAANSE_PROTOCOL)
                    ? "`DAANSE_PROTOCOL` + `" + protocol.substring(DcatVocabularies.DAANSE_PROTOCOL.length()) + "`"
                    : "`" + protocol + "`";
            assertThat(table).as("the README lists %s", protocol).contains("| " + own + " |");
        }
        assertThat(table).contains("`" + DcatVocabularies.DAANSE_PROTOCOL + "`")
                .contains("(`" + DcatVocabularies.PROFILE_DCAT_AP_DE + "`)");
    }

    @Test
    void aLiveFeedHasATopic() {
        DataService broker = DcatFactory.eINSTANCE.createDataService();
        broker.setName("sensor-broker");
        broker.setEndpointUrl("mqtts://broker.utility.example:8883");
        broker.getConformsToIri().add(DcatVocabularies.PROTOCOL_MQTT);
        Distribution feed = DcatFactory.eINSTANCE.createDistribution();
        feed.setName("water-temperature-feed");
        feed.setAccessService(broker);
        feed.getAccessUrl().add("mqtts://broker.utility.example:8883/leisure/pools/+/water-temperature");

        assertThat(DataServices.topicOf(feed)).contains("leisure/pools/+/water-temperature");
        assertThat(DataServices.topicOf(u.poolsGeojson)).isEmpty();
    }

    @Test
    void aPublishedDatasetAndItsSource() throws IOException {
        assertThat(xmi(u.model, "utility.xmi"))
                .contains(lines(readmeBlock("A published dataset and its source", "xml")))
                .contains("source=\"_pool-register\"");
        Checks.indexed(u.model);

        assertThat(Datasets.sourcesOf(u.pools)).containsExactly(u.poolRegister);
        assertThat(Datasets.allSourcesOf(u.pools)).containsExactly(u.poolRegister);
        assertThat(Datasets.derivedDatasets(u.poolRegister)).containsExactly(u.pools);
    }

    @Test
    void agents() throws IOException {
        assertThat(xmi(u.model, "utility.xmi")).contains(lines(readmeBlock("Agents", "xml")));

        ResponsibleParty publisher = Datasets.publisherOf(u.pools).orElseThrow();
        assertThat(publisher.getName()).isEqualTo("municipal-utility");
        assertThat(Datasets.uriOf(publisher)).contains("https://www.utility.example");
        assertThat(Datasets.rightsHoldersOf(u.pools)).containsExactly(publisher);
        ResponsibleParty contactPoint = Datasets.contactPointsOf(u.pools).get(0);
        assertThat(contactPoint.getName()).isEqualTo("customer-service");
        assertThat(Datasets.uriOf(contactPoint)).contains("https://www.utility.example/contact");
        assertThat(Datasets.emailOf(contactPoint)).contains("service@utility.example");
        assertThat(Datasets.organizationOf(contactPoint)).contains(publisher);
    }

    @Test
    void aCatalogTakenOver() {
        Portal regionalPortal = u.regionalPortal;
        DataService regionalApi = u.regionalApi;
        Catalog openData = u.openData;
        regionalPortal.getImportedElement().add(openData);
        regionalApi.getServesDataset().add(openData);
        Checks.indexed(u.model, u.region);

        assertThat(Portals.catalogs(regionalPortal)).containsExactly(u.regionalData, openData);
        assertThat(Portals.ownCatalogs(regionalPortal)).containsExactly(u.regionalData);
        assertThat(Portals.takenOverCatalogs(regionalPortal)).containsExactly(openData);
        assertThat(Portals.portalOf(openData)).contains(u.portal);
        assertThat(Portals.portalsShowing(openData)).containsExactly(u.portal, regionalPortal);
        assertThat(Portals.portalsListing(u.pools)).containsExactly(u.portal, regionalPortal);
        assertThat(regionalPortal.getName()).isEqualTo("region-open-data");
        assertThat(Checks.findings(u.model, u.region)).isEmpty();
    }

    @Test
    void seriesAndVersions() {
        DatasetSeries waterQuality = Elements.series(u.openData, "pool-water-quality");
        Dataset quality2024 = Elements.dataset(u.openData, "pool-water-quality-2024");
        Dataset quality2025 = Elements.dataset(u.openData, "pool-water-quality-2025");
        quality2024.getInSeries().add(waterQuality);
        quality2025.getInSeries().add(waterQuality);
        quality2025.setPrev(quality2024);
        waterQuality.setFirst(quality2024);
        waterQuality.setLast(quality2025);
        Dataset hours2025 = Elements.dataset(u.openData, "opening-hours-2025");
        Dataset hours2026 = Elements.dataset(u.openData, "opening-hours-2026");
        hours2026.setPreviousVersion(hours2025);
        Checks.indexed(u.model);

        assertThat(Datasets.seriesMembersInOrder(waterQuality)).containsExactly(quality2024, quality2025);
        assertThat(Datasets.next(quality2024)).contains(quality2025);
        assertThat(Datasets.nextVersions(hours2025)).containsExactly(hours2026);
    }

    @Test
    void theWayBack() {
        ResourceSet resourceSet = Checks.indexed(u.model);
        InverseReferences.install(resourceSet);

        assertThat(Datasets.datasetsFor(u.pool)).containsExactly(u.poolRegister, u.pools);
        assertThat(Catalogs.catalogsListing(u.pools)).containsExactly(u.openData);
        assertThat(Datasets.servicesServing(u.pools)).containsExactly(u.ogcAssets);
        assertThat(Distributions.distributionsServedBy(u.ogcAssets)).containsExactly(u.poolsGeojson);
        assertThat(Datasets.derivedDatasets(u.poolRegister)).containsExactly(u.pools);
    }

    @Test
    void checkingAModel() {
        OCLDelegateDomain.initialize(null);
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
        assertThat(Diagnostician.INSTANCE.validate(u.model).getSeverity()).isEqualTo(Diagnostic.OK);

        u.poolsGeojson.getAccessUrl().add("https://opendata.utility.example/dataset/pools");
        Diagnostic result = Diagnostician.INSTANCE.validate(u.model);
        assertThat(result.getSeverity()).isEqualTo(Diagnostic.ERROR);
        assertThat(result.getChildren()).singleElement().satisfies(d -> {
            assertThat(d.getMessage()).startsWith("The 'AccessUrlUnderEndpoint' constraint is violated on");
            assertThat(d.getData()).first().isSameAs(u.poolsGeojson);
        });
    }

    // ------------------------------------------------------------------

    /** The lines of a README snippet, without their indentation and without empty lines. */
    private static String[] lines(String block) {
        return block.lines().map(String::trim).filter(l -> !l.isEmpty()).toArray(String[]::new);
    }

    /** The text of the README section {@code ## heading}, up to the next section. */
    private static String readmeSection(String heading) {
        String readme;
        try {
            readme = Files.readString(Path.of(System.getProperty("user.dir"), "README.md"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        int start = readme.indexOf("\n## " + heading + "\n");
        assertThat(start).as("README section %s", heading).isNotNegative();
        int end = readme.indexOf("\n## ", start + 1);
        return readme.substring(start, end < 0 ? readme.length() : end);
    }

    /** The first fenced block of {@code language} in the README section {@code ## heading}. */
    private static String readmeBlock(String heading, String language) {
        String section = readmeSection(heading);
        String fence = "```" + language + "\n";
        int start = section.indexOf(fence);
        assertThat(start).as("a %s block in README section %s", language, heading).isNotNegative();
        int end = section.indexOf("\n```", start + fence.length());
        return section.substring(start + fence.length(), end);
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
