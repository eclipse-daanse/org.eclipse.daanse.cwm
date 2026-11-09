# org.eclipse.daanse.cwm.model.daanse.dcat

W3C DCAT 3 (Data Catalog Vocabulary, REC 2024) on CWM: a catalog publishes model elements that
already exist in CWM as datasets, without copying them. `Dataset.subject` points at any
`core::ModelElement` — a `relational::Schema`, a table, a class. A `Catalog` is a `Dataset` and a
`core::Package`: it lists its `resource`s (datasets, dataset series, data services, catalogs) and
owns its `CatalogRecord`s. A `Distribution` is an access form of a dataset (download and access URLs,
media type, checksum), a `DataService` an API (`endpointUrl`, `servesDataset`, optionally the deployed
`softwaredeployment::DataProvider`). Agents are `businessinformation::ResponsibleParty`s, named with
their role in an `Attribution`; `dcterms:title` is the inherited `name`. Controlled vocabularies are
IRI strings with the suffix `Iri` (constants in `DcatVocabularies`); a theme is an IRI and/or a
`businessnomenclature::Concept`. 13 OCL invariants check the rules of DCAT 3.

## Relations

- Builds on CWM `core`, `businessinformation` (agents, descriptions), `businessnomenclature` (themes)
  and `softwaredeployment` (data providers).
- References are one way; the DCAT 3 inverses (`dataset`, `inCatalog`, `seriesMember`, `next`,
  `dataService`, `servedDistribution`, ...) are hidden opposite role names. With
  `InverseReferences.install(resourceSet)` the helpers answer them from an index:
  `Datasets.datasetsFor(element)`, `Catalogs.catalogsListing(dataset)`,
  `Datasets.seriesMembersInOrder(series)`, `Distributions.distributionsServedBy(service)`.

## Example

A catalog publishes the class `EMPLOYEE` as the dataset `employees`, with a CSV distribution and an
API (ids left out):

```xml
<ownedElement xsi:type="core:Package" name="HR">
  <ownedElement xsi:type="core:Class" name="EMPLOYEE"/>
</ownedElement>
<ownedElement xsi:type="dcat:Catalog" name="open-data" homepage="https://data.example.org" resource="_employees _hr-api">
  <ownedElement xsi:type="businessinformation:ResponsibleParty" name="statistics-office"/>
  <ownedElement xsi:type="dcat:Dataset" name="employees" description="All employees with a running contract" subject="_EMPLOYEE" accrualPeriodicityIri="http://publications.europa.eu/resource/authority/frequency/DAILY">
    <themeIri>http://publications.europa.eu/resource/authority/data-theme/GOVE</themeIri>
    <attribution agent="_statistics-office"/>
    <distribution name="employees-csv" mediaType="text/csv">
      <downloadUrl>https://data.example.org/employees.csv</downloadUrl>
      <checksum algorithm="SHA256" checksumValue="9f86d081884c7d65"/>
    </distribution>
  </ownedElement>
  <ownedElement xsi:type="dcat:DataService" name="hr-api" servesDataset="_employees">
    <endpointUrl>https://api.example.org/hr</endpointUrl>
  </ownedElement>
</ownedElement>
```

The attribution has no `role` in the XMI: `PUBLISHER` is the default.
