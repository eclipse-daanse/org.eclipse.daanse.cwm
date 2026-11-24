/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.dcat.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Cube;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.OlapFactory;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.DataObjectSet;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.Transformation;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationFactory;
import org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationTask;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Contact;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResourceLocator;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.Component;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.DataManager;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.DataProvider;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.Machine;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.ProviderConnection;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.SoftwareSystem;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.SoftwaredeploymentFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.SoftwaredeploymentPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.dcat.AgentRole;
import org.eclipse.daanse.cwm.model.daanse.dcat.Catalog;
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Dataset;
import org.eclipse.daanse.cwm.model.daanse.dcat.DatasetSeries;
import org.eclipse.daanse.cwm.model.daanse.dcat.DcatFactory;
import org.eclipse.daanse.cwm.model.daanse.dcat.Distribution;
import org.eclipse.daanse.cwm.model.daanse.dcat.PeriodOfTime;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Catalogs;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DataServices;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Datasets;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatVocabularies;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Distributions;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Portals;
import org.junit.jupiter.api.Test;

/**
 * The catalog model at work, one setting per test: an open-data portal, the internal catalog of a
 * warehouse, a derived data product, a live feed, one dataset through two services, a series over
 * the years with versions, a portal that takes a catalog over, the agents of a dataset, and an
 * OLAP server as a service. Each builds the model with the elements a user would build and asks
 * it what a user would ask, through the helpers of the module.
 */
class DcatAcrossCwmTest {

    private static final DcatFactory DCAT = DcatFactory.eINSTANCE;
    private static final SoftwaredeploymentFactory DEPLOYMENT = SoftwaredeploymentFactory.eINSTANCE;

    @Test
    void anOpenDataPortalPublishesAFileAndAnApi() {
        MunicipalUtility u = new MunicipalUtility();

        // The portal owns its access service and its catalog; the service speaks a metadata profile.
        assertThat(Portals.accessService(u.portal)).contains(u.portalApi);
        assertThat(u.portalApi.getConformsToIri()).containsExactly(DcatVocabularies.PROFILE_DCAT_AP_DE);
        assertThat(DataServices.protocolOf(u.portalApi)).as("a profile is not a protocol").isEmpty();
        assertThat(Portals.catalogs(u.portal)).containsExactly(u.openData);
        assertThat(Portals.portalOf(u.openData)).contains(u.portal);

        // The catalog lists the dataset and the API that serves it.
        assertThat(Catalogs.datasets(u.openData)).containsExactly(u.pools);
        assertThat(Catalogs.services(u.openData)).containsExactly(u.ogcAssets);

        // The dataset as a file to download ...
        Distribution csv = Distributions.withMediaType(u.pools, "text/csv").findFirst().orElseThrow();
        assertThat(csv.getDownloadUrl()).containsExactly("https://opendata.utility.example/files/pools.csv");
        assertThat(csv.getAccessService()).isNull();
        // ... and as a collection of an OGC API Features service.
        Distribution geojson = Distributions.servedBy(u.pools, u.ogcAssets).orElseThrow();
        assertThat(geojson.getMediaType()).isEqualTo("application/geo+json");
        assertThat(DataServices.protocolOf(u.ogcAssets)).contains(DcatVocabularies.PROTOCOL_OGC_API_FEATURES);
        assertThat(DataServices.pathOf(geojson)).contains("collections/pools");

        // The installation of the portal: a data manager on a machine whose data package is the portal.
        Model operations = Elements.model("operations");
        DataManager portalServer = dataManager(operations, "web-01", "portal-server", "catalog-software");
        portalServer.getDataPackage().add(u.portal);
        Checks.indexed(u.model, u.region, operations);
        assertThat(InverseReferences.referencing(u.portal,
                SoftwaredeploymentPackage.Literals.DATA_MANAGER__DATA_PACKAGE, DataManager.class))
                .containsExactly(portalServer);

        assertThat(Checks.findings(u.model, u.region)).isEmpty();
    }

    @Test
    void theInternalCatalogOfAWarehouse() {
        // The warehouse as CWM has it: a relational catalog with the schema sales.
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog dwh =
                org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs.create("dwh");
        Schema sales = Schemas.create(dwh, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "order_id", SQLSimpleTypes.ownedIn(dwh, "BIGINT"));
        Columns.create(orders, "amount", SQLSimpleTypes.ownedIn(dwh, "DECIMAL"));
        Table customers = Tables.create(sales, "customers");
        Columns.create(customers, "customer_id", SQLSimpleTypes.ownedIn(dwh, "BIGINT"));

        // The operations view: the database server holds the catalog dwh, the JDBC driver reaches it.
        Model operations = Elements.model("operations");
        DataManager database = dataManager(operations, "dwh-host", "dwh-postgres", "postgresql");
        database.getDataPackage().add(dwh);
        DataProvider jdbc = DEPLOYMENT.createDataProvider();
        deploy(operations, "dwh-host", jdbc, "dwh-jdbc", "jdbc-driver");
        ProviderConnection connection = DEPLOYMENT.createProviderConnection();
        connection.setName("dwh-connection");
        connection.setDataManager(database);
        connection.setIsReadOnly(true);
        jdbc.getResourceConnection().add(connection);

        // The internal catalog: no portal, nothing public; the database is the serving service.
        Model governance = Elements.model("data-governance");
        Catalog internal = Elements.catalog(governance, "warehouse-catalog");
        DataService dwhDb = Elements.service(internal, "dwh-db", "jdbc:postgresql://dwh.internal.example:5432/dwh",
                DcatVocabularies.PROTOCOL_JDBC);
        dwhDb.setProvider(jdbc);
        Dataset salesSchema = Elements.dataset(internal, "sales", sales);
        Dataset ordersDataset = Elements.dataset(internal, "orders", orders);
        Dataset customersDataset = Elements.dataset(internal, "customers", customers);
        Elements.servedAt(salesSchema, dwhDb, "sales-schema", "sales");
        Elements.servedAt(ordersDataset, dwhDb, "orders-table", "sales.orders");
        Elements.servedAt(customersDataset, dwhDb, "customers-table", "sales.customers");
        for (Dataset dataset : List.of(salesSchema, ordersDataset, customersDataset)) {
            dataset.setAccessRightsIri(DcatVocabularies.ACCESS_RIGHT_NON_PUBLIC);
            internal.getResource().add(dataset);
        }
        internal.getResource().add(dwhDb);
        Checks.indexed(dwh, operations, governance);

        // As which dataset is the table orders catalogued, and where is it read?
        assertThat(Datasets.datasetsFor(orders)).containsExactly(ordersDataset);
        Distribution ordersTable = Distributions.servedBy(ordersDataset, dwhDb).orElseThrow();
        assertThat(DataServices.pathOf(ordersTable)).contains("sales.orders");
        assertThat(DataServices.protocolOf(dwhDb)).contains(DcatVocabularies.PROTOCOL_JDBC);
        // Which database holds it: from the service through the deployed driver to the data manager.
        assertThat(dwhDb.getProvider().getResourceConnection()).singleElement()
                .extracting(ProviderConnection::getDataManager).isSameAs(database);
        assertThat(database.getDataPackage()).containsExactly(dwh);
        // Nothing of it is on a portal.
        assertThat(Portals.portalOf(internal)).isEmpty();
        assertThat(Portals.portalsListing(ordersDataset)).isEmpty();
        assertThat(Catalogs.datasets(internal)).allSatisfy(
                d -> assertThat(d.getAccessRightsIri()).isEqualTo(DcatVocabularies.ACCESS_RIGHT_NON_PUBLIC));

        assertThat(Checks.findings(governance)).isEmpty();
    }

    @Test
    void aDerivedDataProduct() {
        Model leisure = Elements.model("leisure");
        Package visits = Elements.pkg(leisure, "visits");
        Class turnstileEvent = Elements.cls(visits, "TurnstileEvent");
        Class dailyVisits = Elements.cls(visits, "DailyVisits");
        Class monthlyVisits = Elements.cls(visits, "MonthlyVisits");
        Package etl = Elements.pkg(leisure, "etl");
        Transformation countVisits = transformation(etl, "count-visits",
                "count the turnstile passages per pool and day", turnstileEvent, dailyVisits);
        Transformation sumVisits = transformation(etl, "sum-visits", "sum the daily visits per pool and month",
                dailyVisits, monthlyVisits);
        TransformationTask monthlyRun = TransformationFactory.eINSTANCE.createTransformationTask();
        monthlyRun.setName("monthly-aggregation");
        monthlyRun.getTransformation().add(sumVisits);
        etl.getOwnedElement().add(monthlyRun);

        // The raw events are not published; the two products are derived from them, step by step.
        Dataset events = Elements.dataset(visits, "turnstile-events", turnstileEvent);
        events.setAccessRightsIri(DcatVocabularies.ACCESS_RIGHT_NON_PUBLIC);
        Dataset daily = Elements.dataset(visits, "pool-visits-daily", dailyVisits);
        daily.getSource().add(events);
        daily.getWasGeneratedBy().add(countVisits);
        Dataset monthly = Elements.dataset(visits, "pool-visits-monthly", monthlyVisits);
        monthly.getSource().add(daily);
        monthly.getWasGeneratedBy().add(monthlyRun);
        Checks.indexed(leisure);

        // Where does the monthly product come from?
        assertThat(Datasets.sourcesOf(monthly)).containsExactly(daily);
        assertThat(Datasets.allSourcesOf(monthly)).containsExactly(daily, events);
        // How was it made: by the task that runs the transformation from the daily visits.
        TransformationTask task = (TransformationTask) monthly.getWasGeneratedBy().get(0);
        assertThat(task.getTransformation()).containsExactly(sumVisits);
        assertThat(sumVisits.getSource()).flatExtracting(DataObjectSet::getElement).containsExactly(dailyVisits);
        // What is made of the raw events?
        assertThat(Datasets.derivedDatasets(events)).containsExactly(daily);
        assertThat(Checks.findings(leisure)).isEmpty();

        // A product that names how it was made but not from what is reported.
        Dataset weekly = Elements.dataset(visits, "pool-visits-weekly", dailyVisits);
        weekly.getWasGeneratedBy().add(countVisits);
        assertThat(Checks.findings(leisure)).containsExactly("GeneratedHasSource pool-visits-weekly");
    }

    @Test
    void aLiveFeedFromAnMqttBroker() {
        Model sensors = Elements.model("sensors");
        Package telemetry = Elements.pkg(sensors, "telemetry");
        Class waterTemperature = Elements.cls(telemetry, "WaterTemperature");
        Class poolTelemetry = Elements.cls(telemetry, "PoolTelemetry");
        Catalog live = Elements.catalog(sensors, "live-data");
        // A version of MQTT names the protocol as well as the family IRI.
        DataService broker = Elements.service(live, "sensor-broker", "mqtts://broker.utility.example:8883",
                "http://docs.oasis-open.org/mqtt/mqtt/v5.0/");

        Dataset temperatures = Elements.dataset(live, "pool-water-temperature", waterTemperature);
        temperatures.setTemporalResolution("PT1M");
        Distribution feed = Elements.servedAt(temperatures, broker, "water-temperature-feed",
                "leisure/pools/+/water-temperature");
        Distribution history = Elements.distribution(temperatures, "water-temperature-history",
                "https://opendata.utility.example/dataset/pool-water-temperature");
        history.getDownloadUrl().add("https://opendata.utility.example/files/pool-water-temperature-2025.csv");
        Dataset everything = Elements.dataset(live, "pool-telemetry", poolTelemetry);
        Distribution all = Elements.servedAt(everything, broker, "pool-telemetry-feed", "leisure/pools/%23");

        assertThat(DataServices.protocolOf(broker)).contains(DcatVocabularies.PROTOCOL_MQTT);
        assertThat(DataServices.topicOf(feed)).contains("leisure/pools/+/water-temperature");
        assertThat(DataServices.topicOf(all)).as("the multi-level wildcard, escaped in the URL")
                .contains("leisure/pools/#");
        assertThat(DataServices.topicOf(history)).as("a file has no topic").isEmpty();
        assertThat(Checks.findings(sensors)).isEmpty();
    }

    @Test
    void oneDatasetThroughTwoServices() {
        Model mobility = Elements.model("mobility");
        Package assets = Elements.pkg(mobility, "assets");
        Class chargingStation = Elements.cls(assets, "ChargingStation");
        Catalog catalog = Elements.catalog(mobility, "mobility-data");
        DataService ogc = Elements.service(catalog, "ogc-mobility", "https://geo.utility.example/ogc/mobility",
                DcatVocabularies.PROTOCOL_OGC_API_FEATURES);
        DataService odata = Elements.service(catalog, "odata-mobility", "https://data.utility.example/odata/mobility",
                "http://docs.oasis-open.org/odata/odata/v4.01/");
        Dataset stations = Elements.dataset(catalog, "charging-stations", chargingStation);
        Elements.servedAt(stations, ogc, "charging-stations-geojson", "collections/charging-stations")
                .setMediaType("application/geo+json");
        Elements.servedAt(stations, odata, "charging-stations-odata", "ChargingStations")
                .setMediaType("application/json");
        Checks.indexed(mobility);

        // Two services, two distributions: per service its protocol and the path of the dataset in it.
        Map<String, String> pathByProtocol = new LinkedHashMap<>();
        for (DataService service : Datasets.servicesServing(stations)) {
            Distribution access = Distributions.servedBy(stations, service).orElseThrow();
            pathByProtocol.put(DataServices.protocolOf(service).orElseThrow(),
                    DataServices.pathOf(access).orElseThrow());
        }
        assertThat(pathByProtocol).containsExactly(
                Map.entry(DcatVocabularies.PROTOCOL_ODATA, "ChargingStations"),
                Map.entry(DcatVocabularies.PROTOCOL_OGC_API_FEATURES, "collections/charging-stations"));
        assertThat(Checks.findings(mobility)).isEmpty();

        // A service that is meant to serve the dataset but has no distribution of it is reported.
        DataService wfs = Elements.service(catalog, "wfs-mobility", "https://geo.utility.example/wfs/mobility",
                "http://www.opengis.net/def/serviceType/ogc/wfs/");
        wfs.getServesDataset().add(stations);
        assertThat(Checks.findings(mobility)).containsExactly("ServedDatasetHasDistribution wfs-mobility");
    }

    @Test
    void aSeriesOverTheYearsAndVersions() {
        Model leisure = Elements.model("leisure");
        Package lab = Elements.pkg(leisure, "lab");
        Class sample = Elements.cls(lab, "WaterSample");
        Catalog catalog = Elements.catalog(leisure, "open-data");
        DatasetSeries waterQuality = Elements.series(catalog, "pool-water-quality");
        Dataset previous = null;
        for (int year = 2024; year <= 2026; year++) {
            Dataset member = Elements.dataset(catalog, "pool-water-quality-" + year, sample);
            member.getInSeries().add(waterQuality);
            member.getTemporal().add(period(year + "-01-01", year + "-12-31"));
            Elements.distribution(member, "pool-water-quality-" + year + "-csv",
                    "https://opendata.utility.example/dataset/pool-water-quality-" + year)
                    .getDownloadUrl().add("https://opendata.utility.example/files/pool-water-quality-" + year + ".csv");
            member.setPrev(previous);
            if (previous == null) {
                waterQuality.setFirst(member);
            }
            waterQuality.setLast(member);
            catalog.getResource().add(member);
            previous = member;
        }
        catalog.getResource().add(waterQuality);

        // Versions of one dataset: the opening hours of 2025 and 2026, the latter the current one.
        Dataset openingHours = Elements.dataset(catalog, "opening-hours");
        Dataset hours2025 = Elements.dataset(catalog, "opening-hours-2025");
        hours2025.setVersion("2025");
        Dataset hours2026 = Elements.dataset(catalog, "opening-hours-2026");
        hours2026.setVersion("2026");
        hours2026.setPreviousVersion(hours2025);
        openingHours.getHasVersion().add(hours2025);
        openingHours.getHasVersion().add(hours2026);
        openingHours.setHasCurrentVersion(hours2026);
        Checks.indexed(leisure);

        assertThat(Datasets.seriesMembersInOrder(waterQuality)).extracting(Dataset::getName).containsExactly(
                "pool-water-quality-2024", "pool-water-quality-2025", "pool-water-quality-2026");
        assertThat(Datasets.next(waterQuality.getFirst())).map(r -> r.getName()).contains("pool-water-quality-2025");
        assertThat(Datasets.nextVersions(hours2025)).containsExactly(hours2026);
        assertThat(openingHours.getHasCurrentVersion()).isSameAs(hours2026);
        assertThat(Checks.findings(leisure)).isEmpty();
    }

    @Test
    void aPortalTakesACatalogOver() {
        MunicipalUtility u = new MunicipalUtility();
        // The regional portal takes the catalog of the utility over: it imports it and serves it.
        u.regionalPortal.getImportedElement().add(u.openData);
        u.regionalApi.getServesDataset().add(u.openData);
        Checks.indexed(u.model, u.region);

        // The regional portal shows its own catalog and the one it takes over ...
        assertThat(Portals.catalogs(u.regionalPortal)).containsExactly(u.regionalData, u.openData);
        assertThat(Portals.ownCatalogs(u.regionalPortal)).containsExactly(u.regionalData);
        assertThat(Portals.takenOverCatalogs(u.regionalPortal)).containsExactly(u.openData);
        // ... while the home of the catalog stays with the portal of the utility.
        assertThat(Portals.portalOf(u.openData)).contains(u.portal);
        assertThat(u.openData.getNamespace()).isSameAs(u.portal);
        // Which portals show the catalog, and which the dataset it lists?
        assertThat(Portals.portalsShowing(u.openData)).containsExactly(u.portal, u.regionalPortal);
        assertThat(Portals.portalsListing(u.pools)).containsExactly(u.portal, u.regionalPortal);
        assertThat(Checks.findings(u.model, u.region)).isEmpty();

        // The access service of the regional portal has to serve the catalog the portal owns.
        u.regionalApi.getServesDataset().remove(u.regionalData);
        assertThat(Checks.findings(u.region)).containsExactly("AccessServiceServesCatalogs region-open-data");
    }

    @Test
    void theAgentsOfADataset() {
        MunicipalUtility u = new MunicipalUtility();

        // The organization publishes and holds the rights: one party in two roles, one URI.
        ResponsibleParty publisher = Datasets.publisherOf(u.pools).orElseThrow();
        assertThat(publisher).isSameAs(u.utility);
        assertThat(Datasets.rightsHoldersOf(u.pools)).containsExactly(publisher);
        assertThat(Datasets.uriOf(publisher)).contains("https://www.utility.example");
        assertThat(Datasets.emailOf(publisher)).isEmpty();
        // The contact point is a party of its own inside the organization, with page and e-mail.
        ResponsibleParty contactPoint = Datasets.contactPointsOf(u.pools).get(0);
        assertThat(Datasets.uriOf(contactPoint)).contains("https://www.utility.example/contact");
        assertThat(Datasets.emailOf(contactPoint)).contains("service@utility.example");
        assertThat(Datasets.organizationOf(contactPoint)).contains(publisher);
        assertThat(Checks.findings(u.model)).isEmpty();

        // A second address on the organization is reported; the first one stays its URI.
        Contact press = BusinessinformationFactory.eINSTANCE.createContact();
        press.setName("press");
        ResourceLocator pressPage = BusinessinformationFactory.eINSTANCE.createResourceLocator();
        pressPage.setName("press-page");
        pressPage.setUrl("https://www.utility.example/press");
        press.getUrl().add(pressPage);
        u.utility.getOwnedElement().add(press);
        u.utility.getOwnedElement().add(pressPage);
        u.utility.getContact().add(press);
        assertThat(Checks.findings(u.model)).containsExactly("AgentHasOneLocator publisher",
                "AgentHasOneLocator rights-holder");
        assertThat(Datasets.uriOf(publisher)).contains("https://www.utility.example");
    }

    @Test
    void anOlapServerAsAService() {
        Model analytics = Elements.model("analytics");
        org.eclipse.daanse.cwm.model.cwm.analysis.olap.Schema leisureAnalytics = OlapFactory.eINSTANCE.createSchema();
        leisureAnalytics.setName("leisure-analytics");
        analytics.getOwnedElement().add(leisureAnalytics);
        Cube visits = OlapFactory.eINSTANCE.createCube();
        visits.setName("Visits");
        visits.setIsVirtual(true);
        leisureAnalytics.getCube().add(visits);

        // XMLA has no public specification IRI: the module coins one.
        Catalog catalog = Elements.catalog(analytics, "analytics-catalog");
        DataService olap = Elements.service(catalog, "olap-server", "https://olap.utility.example/xmla",
                DcatVocabularies.PROTOCOL_XMLA);
        Dataset visitsCube = Elements.dataset(catalog, "visits-cube", visits);
        visitsCube.setAccessRightsIri(DcatVocabularies.ACCESS_RIGHT_RESTRICTED);
        Distribution xmla = Elements.servedAt(visitsCube, olap, "visits-cube-xmla", "leisure-analytics/Visits");

        assertThat(DataServices.protocolOf(olap)).contains(DcatVocabularies.PROTOCOL_XMLA);
        assertThat(DcatVocabularies.PROTOCOL_XMLA).startsWith(DcatVocabularies.DAANSE_PROTOCOL);
        assertThat(DataServices.pathOf(xmla)).contains("leisure-analytics/Visits");
        assertThat(Checks.findings(analytics)).isEmpty();

        // A service is one protocol: an OLAP server that also claims OData is reported.
        olap.getConformsToIri().add(DcatVocabularies.PROTOCOL_ODATA);
        assertThat(Checks.findings(analytics)).containsExactly("OneProtocol olap-server");
    }

    // ------------------------------------------------------------------

    /**
     * A data manager deployed on the machine {@code machineName} of {@code operations}, as an
     * installation of the component {@code componentName}.
     */
    private static DataManager dataManager(Namespace operations, String machineName, String name,
            String componentName) {
        DataManager manager = DEPLOYMENT.createDataManager();
        deploy(operations, machineName, manager, name, componentName);
        return manager;
    }

    /**
     * Deploys {@code deployed} on the machine {@code machineName} of {@code operations} (created at
     * first use), as an installation of the component {@code componentName} of a software system
     * of that name.
     */
    private static void deploy(Namespace operations, String machineName, DataManager deployed, String name,
            String componentName) {
        Machine machine = operations.getOwnedElement().stream().filter(Machine.class::isInstance)
                .map(Machine.class::cast).filter(m -> machineName.equals(m.getName())).findFirst()
                .orElseGet(() -> {
                    Machine created = DEPLOYMENT.createMachine();
                    created.setName(machineName);
                    created.getHostName().add(machineName + ".internal.example");
                    operations.getOwnedElement().add(created);
                    return created;
                });
        SoftwareSystem system = DEPLOYMENT.createSoftwareSystem();
        system.setName(componentName);
        operations.getOwnedElement().add(system);
        Component component = DEPLOYMENT.createComponent();
        component.setName(componentName);
        system.getOwnedElement().add(component);
        deployed.setName(name);
        deployed.setComponent(component);
        deployed.setPathname("/opt/" + componentName);
        deployed.setIsCaseSensitive(true);
        machine.getDeployedComponent().add(deployed);
    }

    /**
     * The transformation {@code name} in {@code etl} that makes {@code target} from {@code source};
     * {@code description} says what it does.
     */
    private static Transformation transformation(Package etl, String name, String description, ModelElement source,
            ModelElement target) {
        TransformationFactory factory = TransformationFactory.eINSTANCE;
        Transformation transformation = factory.createTransformation();
        transformation.setName(name);
        transformation.setFunctionDescription(description);
        transformation.setIsPrimary(true);
        ProcedureExpression function = CoreFactory.eINSTANCE.createProcedureExpression();
        function.setBody(description);
        function.setLanguage("text");
        transformation.setFunction(function);
        etl.getOwnedElement().add(transformation);
        DataObjectSet from = factory.createDataObjectSet();
        from.setName(name + "-source");
        from.getElement().add(source);
        DataObjectSet to = factory.createDataObjectSet();
        to.setName(name + "-target");
        to.getElement().add(target);
        transformation.getOwnedElement().add(from);
        transformation.getOwnedElement().add(to);
        transformation.getSource().add(from);
        transformation.getTarget().add(to);
        return transformation;
    }

    private static PeriodOfTime period(String start, String end) {
        PeriodOfTime period = DCAT.createPeriodOfTime();
        period.setStartDate(start);
        period.setEndDate(end);
        return period;
    }
}
