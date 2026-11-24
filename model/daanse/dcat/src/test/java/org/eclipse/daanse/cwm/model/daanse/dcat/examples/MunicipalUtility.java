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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.dcat.AgentRole;
import org.eclipse.daanse.cwm.model.daanse.dcat.Catalog;
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Dataset;
import org.eclipse.daanse.cwm.model.daanse.dcat.Distribution;
import org.eclipse.daanse.cwm.model.daanse.dcat.Portal;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatVocabularies;

/**
 * A municipal utility that runs a leisure centre and publishes its pools as open data — the
 * example the README follows from section to section.
 *
 * <ul>
 * <li>The class {@code Pool} of the package {@code assets} is what is published. The register of
 * the pools in the operations database is the non-public dataset {@code pool-register}, served by
 * the database service {@code assets-db} at the path {@code leisure.pool}.</li>
 * <li>The portal {@code utility-open-data} owns its access service {@code portal-api}, which speaks
 * DCAT-AP.de, and the catalog {@code open-data}. The catalog owns and lists the dataset
 * {@code pools}, derived from {@code pool-register}, and the OGC API Features service
 * {@code ogc-assets}. {@code pools} has a GeoJSON distribution in {@code ogc-assets} at
 * {@code collections/pools} and a CSV file to download.</li>
 * <li>The utility is the publisher and rights holder, with its URI; its own party
 * {@code customer-service} is the contact point, with e-mail address and contact page.</li>
 * <li>The regional portal {@code region-open-data} of a second model owns the catalog
 * {@code regional-data}; the examples about harvesting let it take {@code open-data} over.</li>
 * </ul>
 */
public final class MunicipalUtility {

    public final Model model = Elements.model("utility");
    public final ResponsibleParty utility = Elements.party(model, "municipal-utility",
            "Municipal utility, operator of the leisure centre", "https://www.utility.example");
    public final ResponsibleParty customerService = Elements.party(utility, "customer-service",
            "Customer service of the municipal utility", "https://www.utility.example/contact");

    public final Package assets = Elements.pkg(model, "assets");
    public final Class pool = Elements.cls(assets, "Pool");
    public final DataService assetsDb = Elements.service(assets, "assets-db",
            "jdbc:postgresql://db.utility.example/assets", DcatVocabularies.PROTOCOL_JDBC);
    public final Dataset poolRegister = Elements.dataset(assets, "pool-register", pool);
    public final Distribution poolRegisterDb = Elements.servedAt(poolRegister, assetsDb, "pool-register-db",
            "leisure.pool");

    public final Portal portal = Elements.portal(model, "utility-open-data");
    public final DataService portalApi = Elements.service(portal, "portal-api", "https://opendata.utility.example/api",
            DcatVocabularies.PROFILE_DCAT_AP_DE);
    public final Catalog openData = Elements.catalog(portal, "open-data");
    public final Dataset pools = Elements.dataset(openData, "pools", pool);
    public final DataService ogcAssets = Elements.service(openData, "ogc-assets",
            "https://geo.utility.example/ogc/assets", DcatVocabularies.PROTOCOL_OGC_API_FEATURES);
    public final Distribution poolsGeojson = Elements.servedAt(pools, ogcAssets, "pools-geojson",
            "collections/pools");
    public final Distribution poolsCsv = Elements.distribution(pools, "pools-csv",
            "https://opendata.utility.example/dataset/pools");

    public final Model region = Elements.model("region");
    public final Portal regionalPortal = Elements.portal(region, "region-open-data");
    public final DataService regionalApi = Elements.service(regionalPortal, "region-api",
            "https://opendata.region.example/api", DcatVocabularies.PROFILE_DCAT_AP_DE);
    public final Catalog regionalData = Elements.catalog(regionalPortal, "regional-data");

    public MunicipalUtility() {
        Elements.email(customerService, "service@utility.example");

        poolRegister.setDescription("Register of the pools of the leisure centre with their technical data");
        poolRegister.setAccessRightsIri(DcatVocabularies.ACCESS_RIGHT_NON_PUBLIC);

        portalApi.getServesDataset().add(openData);
        openData.setHomepage("https://opendata.utility.example");
        openData.getResource().add(pools);
        openData.getResource().add(ogcAssets);

        pools.setDescription("Indoor and outdoor pools of the leisure centre with location and water area");
        pools.getSource().add(poolRegister);
        pools.getThemeIri().add(DcatVocabularies.themeIri("EDUC"));
        pools.setAccessRightsIri(DcatVocabularies.ACCESS_RIGHT_PUBLIC);
        Elements.attribute(pools, utility, AgentRole.PUBLISHER);
        Elements.attribute(pools, utility, AgentRole.RIGHTS_HOLDER);
        Elements.attribute(pools, customerService, AgentRole.CONTACT_POINT);
        poolsGeojson.setMediaType("application/geo+json");
        poolsCsv.setMediaType("text/csv");
        poolsCsv.getDownloadUrl().add("https://opendata.utility.example/files/pools.csv");

        regionalApi.getServesDataset().add(regionalData);
    }
}
