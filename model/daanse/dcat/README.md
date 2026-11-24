# org.eclipse.daanse.cwm.model.daanse.dcat

W3C DCAT 3 (Data Catalog Vocabulary, REC 2024) on CWM: a catalog publishes model elements that
already exist in CWM as datasets, without copying them, and a portal is the place that provides
catalogs. The model documents every class, feature and rule in English and German and states its
rules as 23 OCL invariants. The helpers in `util` answer what a reader of a catalog asks: which
portal shows a dataset, through which service and at which path it is read, where it comes from,
whom to ask about it.

One example runs through this page: a municipal utility runs a leisure centre and publishes its
pools as open data. The same model is built by
[`MunicipalUtility`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/MunicipalUtility.java);
every XMI and Java snippet below is checked by
[`DcatExampleTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/DcatExampleTest.java).
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java)
shows the model in other settings, one test each.

## The concepts

### Portal, catalog, dataset, distribution, data service

| Concept | What it is | In the example |
| --- | --- | --- |
| `Portal` | The place that provides catalogs, such as the open-data site of a city. A `core::Package` without features of its own. DCAT itself has no portal; its unit is the catalog. | `utility-open-data` |
| `Catalog` | A curated collection of metadata about resources. As in DCAT it is a `Dataset`, and it is a `core::Package` as well. | `open-data` |
| `Dataset` | Data published as a unit. `subject` points at the CWM elements it publishes: a class, a table, a schema. | `pools`, which publishes the class `Pool` |
| `Distribution` | One access form of a dataset: a file to download, or the view of the dataset in one service. It lives in its dataset. | `pools-csv`, `pools-geojson` |
| `DataService` | A service with one root address (`endpointUrl`) that implements one protocol (`conformsToIri`) and serves datasets (`servesDataset`). | `ogc-assets`, an OGC API Features service |

```text
utility-open-data         Portal
 ├─ portal-api            DataService: the access service of the portal, speaks DCAT-AP.de
 └─ open-data             Catalog, lists pools and ogc-assets
     ├─ pools             Dataset: publishes the class Pool, derived from pool-register
     │   ├─ pools-geojson Distribution in ogc-assets at the path collections/pools
     │   └─ pools-csv     Distribution: a file to download
     └─ ogc-assets        DataService: OGC API Features at https://geo.utility.example/ogc/assets
```

### Who owns what, and what is only listed

Every element of CWM has exactly one owner, its home. A portal owns its catalogs and exactly one
data service, its access service: `endpointUrl` is the address of the portal's catalog interface,
`conformsToIri` the metadata profile it speaks (DCAT-AP.de, for example) and `servesDataset` the
catalogs. A catalog owns the datasets and services that have no other home; a dataset owns its
distributions. Which resources a catalog lists is a separate matter, the reference
`Catalog.resource`: a dataset can be listed by several catalogs, and a dataset that lives next to
the model it describes can be listed all the same. In the same way a portal can show a catalog it
does not own: it takes the catalog over from another portal (harvesting) and holds it in
`importedElement`, while the catalog keeps its home.

### Published dataset and source dataset

A published dataset is often an extract, an aggregation or a cleaned copy of data that is not
public. The source is a dataset of its own, with `accessRightsIri` non-public and with
distributions in the services that hold it, and the published dataset names it in `source`
(dcterms:source). `wasGeneratedBy` says how a dataset was made (a `transformation::Transformation`,
a `TransformationTask`, a run), `source` says from what.

### Protocol, path and topic

A data service has one root address, `endpointUrl`, and names the protocol it implements in
`conformsToIri`; `DcatVocabularies` holds the IRIs of the known protocols. A distribution accessed
through a service names it in `accessService` and has an access URL that starts with the root. The
part behind the root is the path of the dataset in the service: `collections/pools` in an OGC API
Features service, an entity set in OData, `schema.table` behind a JDBC URL. For an MQTT broker the
path is the topic. The same dataset in two services is two distributions, and a service at a
second root is a second service.

### Agents, their URI and their contact data

Publisher, creator, contact point and rights holder are `businessinformation::ResponsibleParty`s,
each named with its role in an `Attribution` of the resource; one party can hold several roles.
A party has one `ResourceLocator` (`contact`, then `Contact.url`), and its `url` is the party's
URI for every purpose: the address of the organization as publisher, the contact page as contact
point. The e-mail address is in `Contact.email`. A contact point that needs another address than
its organization is a party of its own, owned by the party of the organization.

### What does not belong here

DCAT describes what a service offers, not how a client talks to it. Page sizes, paging and
parameter names belong to the interface description of a service (`endpointDescription`, for
example an OpenAPI document); credentials belong to the deployment; map styles belong to the
presentation of the data. None of them is a feature of this model.

## A portal, its catalog and a dataset

The portal of the utility as XMI (ids left out; a reference names the short id of its target):

```xml
<ownedElement xsi:type="dcat:Portal" name="utility-open-data">
  <ownedElement xsi:type="dcat:DataService" name="portal-api" endpointUrl="https://opendata.utility.example/api" servesDataset="_open-data">
    <conformsToIri>http://dcat-ap.de/def/dcatde/3.0/</conformsToIri>
  </ownedElement>
  <ownedElement xsi:type="dcat:Catalog" name="open-data" homepage="https://opendata.utility.example" resource="_pools _ogc-assets">
    <ownedElement xsi:type="dcat:Dataset" name="pools" description="Indoor and outdoor pools of the leisure centre with location and water area" accessRightsIri="http://publications.europa.eu/resource/authority/access-right/PUBLIC" subject="_Pool" source="_pool-register">
      <themeIri>http://publications.europa.eu/resource/authority/data-theme/EDUC</themeIri>
      <attribution name="publisher" agent="_municipal-utility"/>
      <attribution name="rights-holder" agent="_municipal-utility" role="RIGHTS_HOLDER"/>
      <attribution name="contact-point" agent="_municipal-utility.customer-service" role="CONTACT_POINT"/>
      <distribution name="pools-geojson" mediaType="application/geo+json" accessService="_ogc-assets">
        <accessUrl>https://geo.utility.example/ogc/assets/collections/pools</accessUrl>
      </distribution>
      <distribution name="pools-csv" mediaType="text/csv">
        <accessUrl>https://opendata.utility.example/dataset/pools</accessUrl>
        <downloadUrl>https://opendata.utility.example/files/pools.csv</downloadUrl>
      </distribution>
    </ownedElement>
    <ownedElement xsi:type="dcat:DataService" name="ogc-assets" endpointUrl="https://geo.utility.example/ogc/assets" servesDataset="_pools">
      <conformsToIri>http://www.opengis.net/spec/ogcapi-features-1/</conformsToIri>
    </ownedElement>
  </ownedElement>
</ownedElement>
```

An attribution without `role` is a `PUBLISHER`, the default. What the helpers read from it:

```java
Portals.accessService(portal);   // portal-api
Portals.catalogs(portal);        // [open-data]
Portals.portalOf(openData);      // utility-open-data
Catalogs.datasets(openData);     // [pools]
Catalogs.services(openData);     // [ogc-assets]
```

More examples: `anOpenDataPortalPublishesAFileAndAnApi` and `theInternalCatalogOfAWarehouse` (a
catalog without a portal, nothing public, the database as the serving service) in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## Protocol and path

Per service its protocol, per distribution its path in the service:

```java
DataServices.protocolOf(ogcAssets);                // http://www.opengis.net/spec/ogcapi-features-1/
Distribution geojson = Distributions.servedBy(pools, ogcAssets).orElseThrow();
DataServices.pathOf(geojson);                      // collections/pools
DataServices.protocolOf(assetsDb);                 // DcatVocabularies.PROTOCOL_JDBC
DataServices.pathOf(poolRegisterDb);               // leisure.pool
DataServices.protocolOf(portalApi);                // empty: DCAT-AP.de is a profile, not a protocol
```

The known protocols are constants of `DcatVocabularies`. A protocol IRI names a family of
specifications: an IRI that begins with it, such as a version or a conformance class
(`http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core`), names the same protocol. A service
names at most one of them. Where a protocol has no public specification IRI, this module coins one
below its own namespace, `DAANSE_PROTOCOL` =
`https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat/protocol/`.

| Constant | IRI | Protocol |
| --- | --- | --- |
| `PROTOCOL_OGC_API_FEATURES` | `http://www.opengis.net/spec/ogcapi-features-1/` | OGC API - Features |
| `PROTOCOL_OGC_SENSORTHINGS` | `http://www.opengis.net/spec/iot_sensing/` | OGC SensorThings API |
| `PROTOCOL_ODATA` | `http://docs.oasis-open.org/odata/` | OASIS OData |
| `PROTOCOL_GEOJSON` | `https://www.rfc-editor.org/rfc/rfc7946` | GeoJSON (RFC 7946) |
| `PROTOCOL_GRAPHQL` | `https://spec.graphql.org/` | GraphQL |
| `PROTOCOL_OPENAPI` | `https://spec.openapis.org/oas/` | an HTTP API described by OpenAPI |
| `PROTOCOL_MQTT` | `http://docs.oasis-open.org/mqtt/` | OASIS MQTT |
| `PROTOCOL_XMLA` | `DAANSE_PROTOCOL` + `xmla` | XML for Analysis, coined here |
| `PROTOCOL_QGIS_PROJECT` | `DAANSE_PROTOCOL` + `qgis-project` | a QGIS project file, coined here |
| `PROTOCOL_JDBC` | `DAANSE_PROTOCOL` + `jdbc` | a relational database reached through JDBC, coined here |

`PROFILE_DCAT_AP_DE` (`http://dcat-ap.de/def/dcatde/3.0/`) is the profile a portal's access
service speaks, not a protocol.

More examples: `oneDatasetThroughTwoServices` (one dataset in OGC API Features and in OData, path
and protocol per service) and `anOlapServerAsAService` (XMLA) in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## Live feeds: the topic

A broker of MQTT is a data service; a feed is the distribution of a dataset in it, and its topic
is the path of the access URL behind the broker's endpoint URL:

```java
DataService broker = DcatFactory.eINSTANCE.createDataService();
broker.setName("sensor-broker");
broker.setEndpointUrl("mqtts://broker.utility.example:8883");
broker.getConformsToIri().add(DcatVocabularies.PROTOCOL_MQTT);
Distribution feed = DcatFactory.eINSTANCE.createDistribution();
feed.setName("water-temperature-feed");
feed.setAccessService(broker);
feed.getAccessUrl().add("mqtts://broker.utility.example:8883/leisure/pools/+/water-temperature");

DataServices.topicOf(feed);      // leisure/pools/+/water-temperature
```

Percent escapes in the path are decoded, so the multi-level wildcard `#` is written `%23` in the
access URL; `+` stays the single-level wildcard. A distribution of any other service has no topic.

More examples: `aLiveFeedFromAnMqttBroker` in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## A published dataset and its source

The register of the pools is not public. It lives in the package of the class it publishes,
served by the database of the utility:

```xml
<ownedElement xsi:type="core:Package" name="assets">
  <ownedElement xsi:type="core:Class" name="Pool"/>
  <ownedElement xsi:type="dcat:DataService" name="assets-db" endpointUrl="jdbc:postgresql://db.utility.example/assets" servesDataset="_pool-register">
    <conformsToIri>https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat/protocol/jdbc</conformsToIri>
  </ownedElement>
  <ownedElement xsi:type="dcat:Dataset" name="pool-register" description="Register of the pools of the leisure centre with their technical data" accessRightsIri="http://publications.europa.eu/resource/authority/access-right/NON_PUBLIC" subject="_Pool">
    <distribution name="pool-register-db" accessService="_assets-db">
      <accessUrl>jdbc:postgresql://db.utility.example/assets/leisure.pool</accessUrl>
    </distribution>
  </ownedElement>
</ownedElement>
```

The published dataset `pools` names it in `source` (`source="_pool-register"` above):

```java
Datasets.sourcesOf(pools);                 // [pool-register]
Datasets.allSourcesOf(pools);              // [pool-register], then their sources, nearest first
Datasets.derivedDatasets(poolRegister);    // [pools]
```

More examples: `aDerivedDataProduct` (two steps from raw events to a monthly product, each made by
a transformation) in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## Agents

The utility and its customer service; contact, locator and e-mail belong to their party:

```xml
<ownedElement xsi:type="businessinformation:ResponsibleParty" name="municipal-utility" responsibility="Municipal utility, operator of the leisure centre" contact="_municipal-utility.contact">
  <ownedElement xsi:type="businessinformation:Contact" name="contact" url="_municipal-utility.uri" responsibleParty="_municipal-utility"/>
  <ownedElement xsi:type="businessinformation:ResourceLocator" name="uri" url="https://www.utility.example" contact="_municipal-utility.contact"/>
  <ownedElement xsi:type="businessinformation:ResponsibleParty" name="customer-service" responsibility="Customer service of the municipal utility" contact="_municipal-utility.customer-service.contact">
    <ownedElement xsi:type="businessinformation:Contact" name="contact" email="_municipal-utility.customer-service.email" url="_municipal-utility.customer-service.uri" responsibleParty="_municipal-utility.customer-service"/>
    <ownedElement xsi:type="businessinformation:ResourceLocator" name="uri" url="https://www.utility.example/contact" contact="_municipal-utility.customer-service.contact"/>
    <ownedElement xsi:type="businessinformation:Email" name="email" emailAddress="service@utility.example" emailType="work" contact="_municipal-utility.customer-service.contact"/>
  </ownedElement>
</ownedElement>
```

```java
ResponsibleParty publisher = Datasets.publisherOf(pools).orElseThrow();  // municipal-utility
Datasets.uriOf(publisher);                                               // https://www.utility.example
Datasets.rightsHoldersOf(pools);                                         // [municipal-utility]
ResponsibleParty contactPoint = Datasets.contactPointsOf(pools).get(0);  // customer-service
Datasets.uriOf(contactPoint);                                            // https://www.utility.example/contact
Datasets.emailOf(contactPoint);                                          // service@utility.example
Datasets.organizationOf(contactPoint);                                   // municipal-utility
```

Without a `CONTACT_POINT` attribution a resource has no contact point; the publisher does not stand
in for it. More examples: `theAgentsOfADataset` in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## A catalog taken over

A regional portal owns its catalog `regional-data` and takes `open-data` over: it imports the
catalog and its access service serves it.

```java
regionalPortal.getImportedElement().add(openData);
regionalApi.getServesDataset().add(openData);

Portals.catalogs(regionalPortal);            // [regional-data, open-data]
Portals.ownCatalogs(regionalPortal);         // [regional-data]
Portals.takenOverCatalogs(regionalPortal);   // [open-data]
Portals.portalOf(openData);                  // utility-open-data: the home does not move
Portals.portalsShowing(openData);            // [utility-open-data, region-open-data]
Portals.portalsListing(pools);               // [utility-open-data, region-open-data]
```

More examples: `aPortalTakesACatalogOver` in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## Series and versions

The water quality of the pools, one dataset per year: each member names the series in `inSeries`
and its predecessor in `prev`, the series names its `first` and `last` member.

```java
Datasets.seriesMembersInOrder(waterQuality);   // [pool-water-quality-2024, pool-water-quality-2025]
Datasets.next(quality2024);                    // pool-water-quality-2025
Datasets.nextVersions(hours2025);              // [opening-hours-2026]
```

A version names the one before it in `previousVersion`; the version-independent dataset lists its
versions in `hasVersion` and the current one in `hasCurrentVersion`. More examples:
`aSeriesOverTheYearsAndVersions` in
[`DcatAcrossCwmTest`](src/test/java/org/eclipse/daanse/cwm/model/daanse/dcat/examples/DcatAcrossCwmTest.java).

## The way back

References are one way. The DCAT inverses (`inCatalog`, `seriesMember`, `dataService`,
`servedDistribution`, ...) and the other directions back are hidden role names, answered from an
index once `InverseReferences.install(resourceSet)` has run (without it, from a scan of the model):

```java
InverseReferences.install(resourceSet);
Datasets.datasetsFor(pool);                        // [pool-register, pools]: as which datasets Pool is published
Catalogs.catalogsListing(pools);                   // [open-data]
Datasets.servicesServing(pools);                   // [ogc-assets]
Distributions.distributionsServedBy(ogcAssets);    // [pools-geojson]
Datasets.derivedDatasets(poolRegister);            // [pools]
```

## Checking a model

The rules are OCL invariants of the Ecore model. Outside Eclipse the classic OCL delegate is
registered once; then the EMF check evaluates them:

```java
OCLDelegateDomain.initialize(null);
DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());

poolsGeojson.getAccessUrl().add("https://opendata.utility.example/dataset/pools");
Diagnostic result = Diagnostician.INSTANCE.validate(model);
// The 'AccessUrlUnderEndpoint' constraint is violated on ... (the distribution pools-geojson)
```

The EMF check reports every violated rule as an error. The level of a rule, `error` or `warning`,
is in its metadata: the annotation `https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat/constraints`
of its class holds `<rule>.level`, `<rule>.description` and `<rule>.description.de`. A warning
names a model that is readable but probably not meant that way.

## Reference

### Classes

| Class | Is a | Own features |
| --- | --- | --- |
| `DcatElement` (abstract) | `core::ModelElement` | `description`, `issued`, `modified`, `licenseIri`, `accessRightsIri`, `rights`, `policyIri`, `conformsToIri` |
| `CatalogedResource` (abstract) | `DcatElement` | `identifier`, `otherIdentifier`, `language`, `typeIri`, `statusIri`, `version`, `versionNotes`, `keyword`, `landingPage`, `themeIri`, `theme`, `attribution`, `qualifiedRelation`, `relation`, `hasPart`, `isReferencedBy`, `previousVersion`, `hasVersion`, `hasCurrentVersion`, `replaces`, `first`, `last`, `prev` |
| `Dataset` | `CatalogedResource` | `subject`, `distribution`, `accrualPeriodicityIri`, `inSeries`, `spatial`, `spatialResolutionInMeters`, `temporal`, `temporalResolution`, `wasGeneratedBy`, `source` |
| `DatasetSeries` | `Dataset` | none |
| `Catalog` | `Dataset`, `core::Package` | `homepage`, `themeTaxonomy`, `themeTaxonomyIri`, `resource`, `record` |
| `Portal` | `core::Package` | none: catalogs and the access service in `ownedElement`, catalogs taken over in `importedElement` |
| `DataService` | `CatalogedResource` | `endpointUrl` (exactly one), `endpointDescription`, `servesDataset`, `provider` |
| `Distribution` | `DcatElement` | `accessUrl`, `downloadUrl`, `mediaType`, `formatIri`, `compressFormat`, `packageFormat`, `byteSize`, `spatialResolutionInMeters`, `temporalResolution`, `checksum`, `accessService` (at most one) |
| `CatalogRecord` | `DcatElement` | `primaryTopic` |
| `Attribution` | `core::ModelElement` | `agent`, `role` (`AgentRole`), `roleIri` |
| `Relationship` | `core::ModelElement` | `relation`, `roleIri`, `role` |
| `PeriodOfTime` | `core::Element` | `startDate`, `endDate` |
| `Location` | `core::Element` | `iri`, `geometryWkt`, `bboxWkt`, `centroidWkt` |
| `Checksum` | `core::Element` | `algorithm` (`ChecksumAlgorithm`), `checksumValue` |

`dcterms:title` is the inherited `name`. Controlled vocabularies are IRI strings with the suffix
`Iri`; a theme is an IRI and/or a `businessnomenclature::Concept`. The hidden roles read back
most often:

| Reference | Hidden role back |
| --- | --- |
| `Dataset.subject` | `dataset` |
| `Dataset.source` | `derivedDataset` |
| `Dataset.inSeries` | `seriesMember` |
| `Dataset.wasGeneratedBy` | `generated` |
| `Catalog.resource` | `inCatalog` |
| `CatalogedResource.prev`, `previousVersion` | `next`, `nextVersion` |
| `DataService.servesDataset` | `dataService` |
| `Distribution.accessService` | `servedDistribution` |
| `Attribution.agent` | `attribution` |

### Rules

| Class | Rule | Level | Sentence |
| --- | --- | --- | --- |
| `CatalogedResource` | `SinglePublisher` | error | A resource has at most one publisher attribution. |
| `Dataset` | `SpatialResolutionPositive` | error | The spatial resolution, when given, is positive. |
| `Dataset` | `SourceNotCircular` | error | A dataset is not its own source, neither directly nor over other datasets. |
| `Dataset` | `GeneratedHasSource` | warning | A dataset with `wasGeneratedBy` names the datasets it was generated from in `source`. |
| `Dataset` | `DistributionServiceServesDataset` | warning | The access service of every distribution of a dataset names the dataset in `servesDataset`. |
| `DatasetSeries` | `SeriesHasNoDistribution` | warning | A dataset series should not carry distributions itself. |
| `Portal` | `OneAccessService` | error | A portal owns exactly one data service: its access service. |
| `Portal` | `AccessServiceServesCatalogs` | error | The access service of a portal serves every catalog the portal owns, and nothing but catalogs the portal owns or takes over. |
| `Portal` | `TakenOverNotOwned` | error | A portal does not take over what it owns: a catalog of another portal is in `importedElement` only, never also in `ownedElement`. |
| `Catalog` | `RecordTopicIsListed` | error | Every catalog record describes a resource the catalog lists or owns. |
| `Catalog` | `NotOwnMember` | error | A catalog does not list itself as a member. |
| `DataService` | `EndpointUrlNotEmpty` | error | The endpoint URL, when set, is a non-empty string. |
| `DataService` | `OneProtocol` | error | A data service names at most one of the known protocols in `conformsToIri`. |
| `DataService` | `ServedDatasetHasDistribution` | warning | Every dataset a service serves, catalogs excepted, has a distribution with this service as `accessService`. |
| `Distribution` | `ByteSizeNonNegative` | error | The byte size is not negative. |
| `Distribution` | `SpatialResolutionPositive` | error | The spatial resolution, when given, is positive. |
| `Distribution` | `AccessUrlUnderEndpoint` | error | Every access URL of a distribution with an access service starts with the endpoint URL of that service. |
| `Attribution` | `OtherRoleNeedsIri` | error | The open role `OTHER` requires a refining role IRI. |
| `Attribution` | `AgentHasOneLocator` | warning | The agent of an attribution has at most one `ResourceLocator` over all its contacts; with more, the first counts. |
| `Relationship` | `RelationshipHasRole` | error | A qualified relationship names at least one role (IRI or concept). |
| `PeriodOfTime` | `StartBeforeEnd` | error | When both bounds are given, the period starts no later than it ends. |
| `Location` | `LocationNotEmpty` | error | A location carries at least one of gazetteer IRI, geometry, bounding box or centroid. |
| `Checksum` | `ChecksumValueNotEmpty` | error | The checksum value is a non-empty hex string. |

A catalog needs no distribution in the service that serves it: the service gives the catalog as
a whole at its endpoint URL.

### Helpers

| Class | Answers |
| --- | --- |
| `Portals` | `portalOf(catalog)`, `accessService(portal)`, `catalogs(portal)` with the catalogs taken over, `ownCatalogs(portal)`, `takenOverCatalogs(portal)`, `portalsShowing(catalog)`, `portalsListing(resource)` |
| `Catalogs` | `datasets`, `services` and `catalogs` a catalog lists, `catalogsListing(resource)`, `recordFor(catalog, resource)` |
| `Datasets` | `datasetsFor(element)`, `sourcesOf`, `allSourcesOf`, `derivedDatasets`, series and versions (`seriesMembers`, `seriesMembersInOrder`, `next`, `nextVersions`), `servicesServing`; the agents `publisherOf`, `contactPointsOf`, `rightsHoldersOf`, `attributionsByRole` and of a party `uriOf`, `emailOf`, `organizationOf` |
| `Distributions` | `datasetOf(distribution)`, `servedBy(dataset, service)`, `distributionsServedBy(service)`, `withMediaType(dataset, mediaType)` |
| `DataServices` | `protocolOf(service)`, `pathOf(distribution)`, `topicOf(distribution)` |
| `DcatVocabularies` | the IRIs of EU authority tables, IANA media types, protocols and the DCAT-AP.de profile; `protocolNamedBy(iri)` |

## Relations

- Builds on CWM `core`, `businessinformation` (agents, descriptions), `businessnomenclature`
  (themes) and `softwaredeployment` (the deployed component behind a service; a
  `DataManager.dataPackage` can be a portal). `wasGeneratedBy` points at `transformation` and
  `warehouseoperation` elements without a dependency on them.
- The examples publish elements of `relational`, `olap` and `transformation`; these modules are
  test dependencies only.
