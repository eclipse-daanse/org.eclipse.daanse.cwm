/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.daanse.dcat.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Email;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResourceLocator;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Contacts;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.ResponsibleParties;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.dcat.AgentRole;
import org.eclipse.daanse.cwm.model.daanse.dcat.Attribution;
import org.eclipse.daanse.cwm.model.daanse.dcat.CatalogedResource;
import org.eclipse.daanse.cwm.model.daanse.dcat.DataService;
import org.eclipse.daanse.cwm.model.daanse.dcat.Dataset;
import org.eclipse.daanse.cwm.model.daanse.dcat.DatasetSeries;
import org.eclipse.daanse.cwm.model.daanse.dcat.DcatPackage;

/**
 * Accessors around {@link Dataset} and {@link CatalogedResource}: series, versions, sources and the
 * agents of a resource with their URI and e-mail address. Reverse lookups (hidden roles dataset,
 * seriesMember, dataService, next, derivedDataset) need
 * {@link InverseReferences#install(org.eclipse.emf.common.notify.Notifier)} on the ResourceSet
 * — without it they return empty.
 *
 * <p>An agent is a {@code businessinformation::ResponsibleParty}. Its URI is the url of its one
 * {@code ResourceLocator}, reached over {@code contact} and {@code Contact.url}, for every role it
 * has: as publisher the URI of the organization, as contact point the contact page. A contact point
 * that needs another address than its organization is a party of its own, owned by the
 * organization's party ({@link #organizationOf}).</p>
 */
public final class Datasets {

    private Datasets() {
    }

    /** Hidden role dataset — "as which datasets is this CWM element published?". */
    public static List<Dataset> datasetsFor(ModelElement element) {
        return InverseReferences
                .referencing(element, DcatPackage.Literals.DATASET__SUBJECT, Dataset.class)
                .toList();
    }

    /** dcat:seriesMember (hidden role) — the datasets pointing at {@code series} via inSeries. */
    public static List<Dataset> seriesMembers(DatasetSeries series) {
        return InverseReferences
                .referencing(series, DcatPackage.Literals.DATASET__IN_SERIES, Dataset.class)
                .toList();
    }

    /**
     * Series members in series order: starts at the series' dcat:first and follows the hidden
     * role next (inverse of dcat:prev). Members not linked into the chain are appended in
     * {@link #seriesMembers(DatasetSeries)} order; cycles are cut at the first revisit.
     */
    public static List<Dataset> seriesMembersInOrder(DatasetSeries series) {
        Set<Dataset> remaining = new LinkedHashSet<>(seriesMembers(series));
        List<Dataset> ordered = new ArrayList<>();
        CatalogedResource cursor = series.getFirst();
        while (cursor instanceof Dataset d && remaining.remove(d)) {
            ordered.add(d);
            cursor = InverseReferences
                    .referencing(d, DcatPackage.Literals.CATALOGED_RESOURCE__PREV, Dataset.class)
                    .findFirst().orElse(null);
        }
        ordered.addAll(remaining);
        return ordered;
    }

    /** dcat:next (hidden role) — the resource whose dcat:prev is {@code resource}. */
    public static Optional<CatalogedResource> next(CatalogedResource resource) {
        return InverseReferences
                .referencing(resource, DcatPackage.Literals.CATALOGED_RESOURCE__PREV, CatalogedResource.class)
                .findFirst();
    }

    /** dcat:nextVersion (hidden role) — resources whose previousVersion is {@code resource}. */
    public static List<CatalogedResource> nextVersions(CatalogedResource resource) {
        return InverseReferences
                .referencing(resource, DcatPackage.Literals.CATALOGED_RESOURCE__PREVIOUS_VERSION,
                        CatalogedResource.class)
                .toList();
    }

    /** The attributions of {@code resource} in {@code role}. */
    public static Stream<Attribution> attributionsByRole(CatalogedResource resource, AgentRole role) {
        return resource.getAttribution().stream().filter(a -> a.getRole() == role);
    }

    /** dcterms:publisher — the agent of the (at most one) PUBLISHER attribution. */
    public static Optional<ResponsibleParty> publisherOf(CatalogedResource resource) {
        return attributionsByRole(resource, AgentRole.PUBLISHER).map(Attribution::getAgent).findFirst();
    }

    /** dcat:contactPoint — the agents of all CONTACT_POINT attributions. */
    public static List<ResponsibleParty> contactPointsOf(CatalogedResource resource) {
        return attributionsByRole(resource, AgentRole.CONTACT_POINT).map(Attribution::getAgent).toList();
    }

    /** dcterms:rightsHolder — the agents of all RIGHTS_HOLDER attributions. */
    public static List<ResponsibleParty> rightsHoldersOf(CatalogedResource resource) {
        return attributionsByRole(resource, AgentRole.RIGHTS_HOLDER).map(Attribution::getAgent).toList();
    }

    /**
     * The URI of {@code party}: the url of its first ResourceLocator over its contacts, in contact
     * order. A party has one (rule AgentHasOneLocator); with more, the first counts. Empty when it
     * has none.
     */
    public static Optional<String> uriOf(ResponsibleParty party) {
        return ResponsibleParties.contactStream(party)
                .flatMap(Contacts::resourceLocatorStream)
                .map(ResourceLocator::getUrl)
                .filter(url -> url != null && !url.isEmpty())
                .findFirst();
    }

    /** The e-mail address of {@code party}: the address of its first Email over its contacts, in contact order. */
    public static Optional<String> emailOf(ResponsibleParty party) {
        return ResponsibleParties.contactStream(party)
                .flatMap(Contacts::emailStream)
                .map(Email::getEmailAddress)
                .filter(address -> address != null && !address.isEmpty())
                .findFirst();
    }

    /**
     * The organization {@code party} belongs to: the party that owns it, as the organization owns
     * its contact point; empty for a party owned by anything else.
     */
    public static Optional<ResponsibleParty> organizationOf(ResponsibleParty party) {
        return party.getNamespace() instanceof ResponsibleParty organization
                ? Optional.of(organization)
                : Optional.empty();
    }

    /** dcterms:source — the datasets {@code dataset} is derived from. */
    public static List<Dataset> sourcesOf(Dataset dataset) {
        return List.copyOf(dataset.getSource());
    }

    /**
     * The sources of {@code dataset} and their sources, all the way to the raw data: nearest first,
     * each once, without {@code dataset} itself — a cycle (rule SourceNotCircular) ends where it
     * closes.
     */
    public static List<Dataset> allSourcesOf(Dataset dataset) {
        Set<Dataset> seen = new LinkedHashSet<>();
        Deque<Dataset> open = new ArrayDeque<>(dataset.getSource());
        while (!open.isEmpty()) {
            Dataset next = open.removeFirst();
            if (next != dataset && seen.add(next)) {
                open.addAll(next.getSource());
            }
        }
        return List.copyOf(seen);
    }

    /** Hidden role derivedDataset — the datasets naming {@code dataset} in their dcterms:source. */
    public static List<Dataset> derivedDatasets(Dataset dataset) {
        return InverseReferences
                .referencing(dataset, DcatPackage.Literals.DATASET__SOURCE, Dataset.class)
                .toList();
    }

    /** Hidden role dataService — the services declaring dcat:servesDataset {@code dataset}. */
    public static List<DataService> servicesServing(Dataset dataset) {
        return InverseReferences
                .referencing(dataset, DcatPackage.Literals.DATA_SERVICE__SERVES_DATASET, DataService.class)
                .toList();
    }
}
