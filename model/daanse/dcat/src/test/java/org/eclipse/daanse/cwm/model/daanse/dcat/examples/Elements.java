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

import java.util.Locale;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Contact;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Email;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResourceLocator;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.dcat.AgentRole;
import org.eclipse.daanse.cwm.model.daanse.dcat.Attribution;
import org.eclipse.daanse.cwm.model.daanse.dcat.Catalog;
import org.eclipse.daanse.cwm.model.daanse.dcat.CatalogedResource;
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Dataset;
import org.eclipse.daanse.cwm.model.daanse.dcat.DatasetSeries;
import org.eclipse.daanse.cwm.model.daanse.dcat.DcatFactory;
import org.eclipse.daanse.cwm.model.daanse.dcat.Distribution;
import org.eclipse.daanse.cwm.model.daanse.dcat.Portal;

/**
 * The elements the examples of this module are built of: each created by the factory of its
 * package, named, and owned where the model keeps it — a dataset, a service or a catalog by a
 * namespace, a distribution by its dataset, a contact and its locator and e-mail by their party.
 */
public final class Elements {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;
    private static final DcatFactory DCAT = DcatFactory.eINSTANCE;
    private static final BusinessinformationFactory BUSINESS = BusinessinformationFactory.eINSTANCE;

    private Elements() {
    }

    /** A model, the root of an example. */
    public static Model model(String name) {
        Model model = CORE.createModel();
        model.setName(Objects.requireNonNull(name, "name"));
        return model;
    }

    /** A package in the {@code ownedElement} of {@code parent}. */
    public static Package pkg(Namespace parent, String name) {
        return owned(parent, name, CORE.createPackage());
    }

    /** A class in the {@code ownedElement} of {@code parent}. */
    public static Class cls(Namespace parent, String name) {
        return owned(parent, name, CORE.createClass());
    }

    /** A portal in the {@code ownedElement} of {@code parent}. */
    public static Portal portal(Namespace parent, String name) {
        return owned(parent, name, DCAT.createPortal());
    }

    /** A catalog in the {@code ownedElement} of {@code parent}. */
    public static Catalog catalog(Namespace parent, String name) {
        return owned(parent, name, DCAT.createCatalog());
    }

    /** A dataset in the {@code ownedElement} of {@code parent} that publishes {@code subjects}. */
    public static Dataset dataset(Namespace parent, String name, ModelElement... subjects) {
        Dataset dataset = owned(parent, name, DCAT.createDataset());
        for (ModelElement subject : subjects) {
            dataset.getSubject().add(subject);
        }
        return dataset;
    }

    /** A dataset series in the {@code ownedElement} of {@code parent}. */
    public static DatasetSeries series(Namespace parent, String name) {
        return owned(parent, name, DCAT.createDatasetSeries());
    }

    /**
     * A data service in the {@code ownedElement} of {@code parent} with its endpoint URL and the
     * IRI of the protocol it implements or the profile it speaks.
     */
    public static DataService service(Namespace parent, String name, String endpointUrl, String conformsToIri) {
        DataService service = owned(parent, name, DCAT.createDataService());
        service.setEndpointUrl(endpointUrl);
        service.getConformsToIri().add(conformsToIri);
        return service;
    }

    /** A distribution of {@code dataset} reached at {@code accessUrl}, without a service. */
    public static Distribution distribution(Dataset dataset, String name, String accessUrl) {
        Distribution distribution = DCAT.createDistribution();
        distribution.setName(name);
        distribution.getAccessUrl().add(accessUrl);
        dataset.getDistribution().add(distribution);
        return distribution;
    }

    /**
     * The access form of {@code dataset} in {@code service}: a distribution whose access URL is the
     * endpoint URL of the service followed by {@code path}; the service serves the dataset.
     */
    public static Distribution servedAt(Dataset dataset, DataService service, String name, String path) {
        Distribution distribution = distribution(dataset, name, service.getEndpointUrl() + "/" + path);
        distribution.setAccessService(service);
        service.getServesDataset().add(dataset);
        return distribution;
    }

    /**
     * {@code party} is responsible for {@code resource} in {@code role}; the attribution is named
     * after its role ({@code rights-holder}), as every model element has a name.
     */
    public static Attribution attribute(CatalogedResource resource, ResponsibleParty party, AgentRole role) {
        Attribution attribution = DCAT.createAttribution();
        attribution.setName(role.getLiteral().toLowerCase(Locale.ROOT).replace('_', '-'));
        attribution.setAgent(party);
        attribution.setRole(role);
        resource.getAttribution().add(attribution);
        return attribution;
    }

    /**
     * A responsible party in the {@code ownedElement} of {@code parent} with one contact whose
     * resource locator is {@code uri}: the URI of the party. Contact and locator belong to the party.
     */
    public static ResponsibleParty party(Namespace parent, String name, String responsibility, String uri) {
        ResponsibleParty party = owned(parent, name, BUSINESS.createResponsibleParty());
        party.setResponsibility(responsibility);
        Contact contact = owned(party, "contact", BUSINESS.createContact());
        party.getContact().add(contact);
        ResourceLocator locator = owned(party, "uri", BUSINESS.createResourceLocator());
        locator.setUrl(uri);
        contact.getUrl().add(locator);
        return party;
    }

    /** The e-mail address {@code address} on the first contact of {@code party}. */
    public static Email email(ResponsibleParty party, String address) {
        Email email = owned(party, "email", BUSINESS.createEmail());
        email.setEmailAddress(address);
        email.setEmailType("work");
        party.getContact().get(0).getEmail().add(email);
        return email;
    }

    private static <T extends ModelElement> T owned(Namespace parent, String name, T element) {
        element.setName(Objects.requireNonNull(name, "name"));
        parent.getOwnedElement().add(element);
        return element;
    }
}
