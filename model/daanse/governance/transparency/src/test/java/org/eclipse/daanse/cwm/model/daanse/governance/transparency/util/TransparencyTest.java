/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.transparency.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Policies;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.PublicationMode;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyAssessment;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyClassRating;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** Ratings of a city's open data, their retention and their de-identification. */
class TransparencyTest {

    private final Model city = Models.create("city");
    private final Package governance = Packages.create(city, "governance");
    private final ResponsibleParty office = party("transparency-office", "transparency officer of the city");
    private final Catalog data = Catalogs.create(city, "data");
    private final Table contracts = Tables.create(Schemas.create(data, "public"), "contracts");
    private final Column partner = Columns.create(contracts, "partner", SQLSimpleTypes.ownedIn(data, "VARCHAR"));
    private final TransparencyAssessment assessment = TransparencyRatings.assess(governance, "assessment-2026",
            "2026-01-01", office);
    private final TransparencyClassRating rating = TransparencyRatings.rate(assessment, "contracts", contracts,
            PublicationMode.PROACTIVE_MANDATORY);

    @Test
    void ratingsOfAClassAndItsFields() {
        TransparencyRatings.rate(rating, "partner", partner, PublicationMode.ON_REQUEST);
        assertThat(assessment.getDataClasses()).containsExactly(rating);
        assertThat(rating.getDataFields()).singleElement()
                .satisfies(f -> assertThat(f.getDataField()).isSameAs(partner));
        assertThat(TransparencyRatings.proactive(PublicationMode.PROACTIVE_OPTIONAL)).isTrue();
        assertThat(TransparencyRatings.proactive(PublicationMode.ON_REQUEST)).isFalse();
    }

    @Test
    void t1_publishedDataIsKeptTenYears() {
        assertThat(rules(Severity.WARNING)).containsExactly("T1");
        LegalSource thuertg = TransparencyLifecycle.thuertg(governance);
        RetentionObligation kept = TransparencyLifecycle.retention(assessment, rating, 5, "Europe/Berlin", thuertg);
        assertThat(rules(Severity.WARNING)).containsExactly("T1");
        kept.setMinimum("P10Y");
        assertThat(rules(Severity.WARNING)).isEmpty();
        assertThat(kept.getNamespace()).isSameAs(assessment);
        assertThat(LegalSources.of(kept)).containsExactly(thuertg);
        assertThat(thuertg.getProvision()).isEqualTo("§ 7 (8)");
        rating.setPublicationMode(PublicationMode.ON_REQUEST);
        kept.setMinimum("P1Y");
        assertThat(rules(Severity.WARNING)).isEmpty();
    }

    @Test
    void t2_thePolicyOfARatingGovernsItsClass() {
        TransparencyLifecycle.retention(assessment, rating, 10, "Europe/Berlin",
                TransparencyLifecycle.thuertg(governance));
        DeidentificationPolicy policy = Policies.create(governance, "published", Outcome.ANONYMOUS, contracts);
        TransparencyRatings.policy(rating, policy);
        assertThat(TransparencyModelValidator.check(city)).isEmpty();
        Table other = Tables.create(Schemas.create(data, "internal"), "notes");
        policy.getConstrainedElement().clear();
        policy.getConstrainedElement().add(other);
        assertThat(TransparencyModelValidator.validate(city)).singleElement().satisfies(e -> assertThat(e)
                .isEqualTo("T2 rating 'contracts' names the policy 'published', which does not govern "
                        + "city.data.public.contracts."));
    }

    @Test
    void t3_anElementThatDoesNotResolve() {
        TransparencyLifecycle.retention(assessment, rating, 10, "Europe/Berlin",
                TransparencyLifecycle.thuertg(governance));
        Table proxy = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        ((InternalEObject) proxy).eSetProxyURI(URI.createURI("other.xmi#//elsewhere"));
        rating.setDataClass(proxy);
        assertThat(rules(Severity.WARNING)).containsExactly("T3");
    }

    private List<String> rules(Severity severity) {
        return TransparencyModelValidator.check(city).stream().filter(f -> f.severity() == severity)
                .map(Finding::rule).toList();
    }

    private ResponsibleParty party(String name, String responsibility) {
        ResponsibleParty party = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        party.setName(name);
        party.setResponsibility(responsibility);
        governance.getOwnedElement().add(party);
        return party;
    }

    /** Lines of the XMI {@link #inXmi()} writes: an assessment, its ratings and the retention of the rated class. */
    private static final String XMI = """
            <ownedElement xsi:type="transparency:TransparencyAssessment" name="assessment-2026" \
            validFrom="2026-01-01" contactResponsible="_transparency-office">
            <ownedElement xsi:type="obligations:RetentionObligation" name="contracts-kept" \
            constrainedElement="_contracts" scope="row" minimum="P10Y">
            <body body="keep at least P10Y from the time of the value, end of year in Europe/Berlin" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:ValueTime" alignment="endOfYear" zone="Europe/Berlin"/>
            <dataClasses name="contracts" dataClass="_contracts" publicationMode="PROACTIVE_MANDATORY">
            <dataFields name="partner" dataField="_contracts.partner" publicationMode="ON_REQUEST"/>
            <ownedElement xsi:type="obligations:LegalSource" name="thuertg-7-8" reference="ThuerTG" \
            type="statute" modelElement="_contracts-kept" jurisdiction="DE-TH" provision="§ 7 (8)"/>
            <ownedElement xsi:type="relational:Table" name="contracts" constraint="_contracts-kept">
            """;

    @Test
    void inXmi() throws IOException {
        TransparencyRatings.rate(rating, "partner", partner, PublicationMode.ON_REQUEST);
        TransparencyLifecycle.retention(assessment, rating, 10, "Europe/Berlin",
                TransparencyLifecycle.thuertg(governance));
        assertThat(TransparencyModelValidator.check(city)).isEmpty();

        String xmi = xmi(city, "transparency.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** {@code root} as XMI with short ids, written to {@code target/<file>} as well. */
    private static String xmi(EObject root, String file) throws IOException {
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
