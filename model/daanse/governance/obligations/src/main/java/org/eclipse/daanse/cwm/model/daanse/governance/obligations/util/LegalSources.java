/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.ObligationsFactory;

/**
 * Creates legal sources and finds the sources of a duty: a source names the obligations, holds and
 * deletion classes it gives in {@code modelElement}, so the sources of a duty are the documents
 * that name it.
 */
public final class LegalSources {

    /** The type of a source that is a law or a regulation. */
    public static final String STATUTE = "statute";
    /** The type of a source that is a contract. */
    public static final String CONTRACT = "contract";
    /** The type of a source that is an internal policy. */
    public static final String POLICY = "policy";

    private LegalSources() {
    }

    /** A source in the {@code ownedElement} of {@code owner}: found at {@code reference}, of {@code type}. */
    public static LegalSource create(Namespace owner, String name, String reference, String type) {
        LegalSource source = ObligationsFactory.eINSTANCE.createLegalSource();
        source.setName(Objects.requireNonNull(name, "name"));
        source.setReference(Objects.requireNonNull(reference, "reference"));
        source.setType(Objects.requireNonNull(type, "type"));
        Objects.requireNonNull(owner, "owner").getOwnedElement().add(source);
        return source;
    }

    /** The part of the source that gives the duty, and where the source applies. */
    public static LegalSource provision(LegalSource source, String provision, String jurisdiction) {
        source.setProvision(provision);
        source.setJurisdiction(jurisdiction);
        return source;
    }

    /** When the source applies: ISO-8601 dates or instants, {@code to} {@code null} while in force. */
    public static LegalSource validity(LegalSource source, String from, String to) {
        source.setValidFrom(from);
        source.setValidTo(to);
        return source;
    }

    /** Lets the source name the duties, holds or classes it gives. */
    public static LegalSource cite(LegalSource source, ModelElement... duties) {
        for (ModelElement duty : duties) {
            source.getModelElement().add(Objects.requireNonNull(duty, "duty"));
        }
        return source;
    }

    /** The sources that name {@code duty}. */
    public static List<LegalSource> of(ModelElement duty) {
        return InverseReferences.referencing(duty, BusinessinformationPackage.Literals.DOCUMENT__MODEL_ELEMENT,
                LegalSource.class).toList();
    }

    /** Whether the source applies at {@code at}: from {@code validFrom} (inclusive) to {@code validTo} (exclusive). */
    public static boolean inForce(LegalSource source, Instant at) {
        Instant from = instant(source.getValidFrom());
        Instant to = instant(source.getValidTo());
        return (from == null || !at.isBefore(from)) && (to == null || at.isBefore(to));
    }

    /** A date or instant literal as an instant, a date at its start in UTC; {@code null} when unset or no time. */
    public static Instant instant(String literal) {
        if (literal == null) {
            return null;
        }
        try {
            return LocalDate.parse(literal).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(literal).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
