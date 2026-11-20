/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.xmla;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.TypeLibraries;
import org.eclipse.daanse.cwm.model.daanse.semantictype.xmla.ServerTypeTables.AccountRow;
import org.eclipse.daanse.cwm.model.daanse.semantictype.xmla.ServerTypeTables.AttributeRow;
import org.eclipse.daanse.cwm.model.daanse.semantictype.xmla.ServerTypeTables.DimensionRow;
import org.eclipse.emf.ecore.EObject;

/**
 * The types of an Analysis Services server as libraries of semantic types: {@link #create(Namespace)} builds them
 * into a namespace, as {@code units} builds the SI.
 *
 * <p>The server keeps three lists of types, and each is a library of its own under {@value #URI}: the 240 attribute
 * types ({@value #ATTRIBUTE_URI}), the 15 dimension types ({@value #DIMENSION_URI}) and the 7 account types
 * ({@value #ACCOUNT_URI}), each type with its name there (the spelling of the schema) and its number there (its
 * place in the schema's enumeration), read from the tables beside this class. A name in two lists is two types in
 * two libraries: the attribute type Channel and the dimension type Channel. A fourth library ({@value #GROUPS_URI})
 * holds the groups of the attribute types (Time, Geography, Person ...): every attribute type is narrower than its
 * group, and a group of the fiscal, reporting, manufacturing, ISO 8601 or day-flag calendar is narrower than Time.
 * The groups are not the server's: they gather the types by what they describe, so that a reader finds all time
 * types or all geographic types at once.</p>
 *
 * <p>A model holds the libraries it uses, as it holds the SI, or names the types of libraries a program delivers
 * beside it, recognised by their URIs ({@link #LIBRARIES}). An account type of a model's own lies in a library of its
 * own and is narrower than or the same as one of the seven.</p>
 */
public final class ServerTypes {

    /** The URI under which the libraries of the server's types lie. */
    public static final String URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla";

    /** The URI of the library of the server's attribute types. */
    public static final String ATTRIBUTE_URI = URI + "/attribute";

    /** The URI of the library of the server's dimension types. */
    public static final String DIMENSION_URI = URI + "/dimension";

    /** The URI of the library of the server's account types. */
    public static final String ACCOUNT_URI = URI + "/account";

    /** The URI of the library of the groups of the server's attribute types. */
    public static final String GROUPS_URI = URI + "/groups";

    /** The URIs of the server's three lists: attribute, dimension and account types. */
    public static final List<String> LISTS = List.of(ATTRIBUTE_URI, DIMENSION_URI, ACCOUNT_URI);

    /** The URIs of the four libraries this module delivers. */
    public static final Set<String> LIBRARIES = Set.of(ATTRIBUTE_URI, DIMENSION_URI, ACCOUNT_URI, GROUPS_URI);

    /** The name of the package that holds the libraries. */
    public static final String NAME = "AnalysisServices";

    /** The system the types come from. */
    public static final String SYSTEM = "Analysis Services";

    /** The name of the attribute type and of the dimension type that an untyped element is. */
    public static final String REGULAR = "Regular";

    /** The attribute types the history rules of a model say: valid from, valid to, current flag or status. */
    public static final Set<String> HISTORY = Set.of("ScdStartDate", "ScdEndDate", "ScdStatus");

    /** The groups that are narrower than another group. */
    private static final Map<String, String> GROUP_BROADER = Map.of("TimeFiscal", "Time", "TimeReporting", "Time",
            "TimeManufacturing", "Time", "TimeIso8601", "Time", "TimeDayFlag", "Time", "GeographyShape", "Geography");

    private ServerTypes() {
    }

    /** Builds a package {@value #NAME} with the four libraries into {@code owner} and returns it. */
    public static Package create(Namespace owner) {
        Package server = Packages.create(owner, NAME);
        TypeLibrary attributes = list(server, "AttributeTypes", ATTRIBUTE_URI);
        TypeLibrary dimensions = list(server, "DimensionTypes", DIMENSION_URI);
        TypeLibrary accounts = list(server, "AccountTypes", ACCOUNT_URI);
        TypeLibrary groups = TypeLibraries.create(server, "AttributeGroups", GROUPS_URI);
        Map<String, SemanticType> groupTypes = new LinkedHashMap<>();
        for (AttributeRow row : ServerTypeTables.ATTRIBUTES) {
            groupTypes.computeIfAbsent(row.group(), name -> SemanticTypes.create(groups, name));
        }
        GROUP_BROADER.forEach((narrower, broader) -> SemanticTypes.broader(groupTypes.get(narrower),
                groupTypes.get(broader)));
        for (AttributeRow row : ServerTypeTables.ATTRIBUTES) {
            SemanticTypes.broader(SemanticTypes.source(SemanticTypes.create(attributes, row.name()), row.name(),
                    row.number()), groupTypes.get(row.group()));
        }
        for (DimensionRow row : ServerTypeTables.DIMENSIONS) {
            SemanticTypes.source(SemanticTypes.create(dimensions, row.name()), row.name(), row.number());
        }
        for (AccountRow row : ServerTypeTables.ACCOUNTS) {
            SemanticTypes.source(SemanticTypes.create(accounts, row.name()), row.name(), row.number());
        }
        return server;
    }

    /** The library of the server's attribute types in {@code scope} and below. */
    public static TypeLibrary attributes(EObject scope) {
        return library(scope, ATTRIBUTE_URI);
    }

    /** The library of the server's dimension types in {@code scope} and below. */
    public static TypeLibrary dimensions(EObject scope) {
        return library(scope, DIMENSION_URI);
    }

    /** The library of the server's account types in {@code scope} and below. */
    public static TypeLibrary accounts(EObject scope) {
        return library(scope, ACCOUNT_URI);
    }

    /** The library of the groups of the attribute types in {@code scope} and below. */
    public static TypeLibrary groups(EObject scope) {
        return library(scope, GROUPS_URI);
    }

    /**
     * The type of {@code library} named {@code spelling}; in the library of attribute types the other spelling a
     * server takes ({@code ProductSku}, {@code TenDays}, {@code Id}) finds it too.
     */
    public static SemanticType type(TypeLibrary library, String spelling) {
        Objects.requireNonNull(spelling, "spelling");
        String name = ATTRIBUTE_URI.equals(library.getUri()) ? nameOf(spelling).orElse(spelling) : spelling;
        return SemanticTypes.named(library, name).orElseThrow(() -> new IllegalArgumentException("no type "
                + spelling + " in " + library.getUri()));
    }

    /** The schema's spelling of the attribute type spelled {@code spelling}, by either spelling. */
    public static Optional<String> nameOf(String spelling) {
        Objects.requireNonNull(spelling, "spelling");
        return Optional.ofNullable(ServerTypeTables.attribute(spelling)).map(AttributeRow::name);
    }

    /** The URI of the server's list {@code type} lies in, one of {@link #LISTS}; empty for any other type. */
    public static Optional<String> listOf(SemanticType type) {
        if (type == null || type.eIsProxy()) {
            return Optional.empty();
        }
        return SemanticTypes.libraryOf(type).map(TypeLibrary::getUri).filter(LISTS::contains);
    }

    /** Whether {@code type} is a type of one of the server's lists. */
    public static boolean isServerType(SemanticType type) {
        return listOf(type).isPresent();
    }

    /** The name there of a type of the server: the stated name there, else its name. */
    static String serverName(SemanticType type) {
        return type.getSourceName() != null ? type.getSourceName() : type.getName();
    }

    private static TypeLibrary library(EObject scope, String uri) {
        return TypeLibraries.byUri(scope, uri)
                .orElseThrow(() -> new IllegalArgumentException("no library " + uri + " in the scope"));
    }

    private static TypeLibrary list(Package server, String name, String uri) {
        return TypeLibraries.source(TypeLibraries.create(server, name, uri), SYSTEM, null);
    }
}
