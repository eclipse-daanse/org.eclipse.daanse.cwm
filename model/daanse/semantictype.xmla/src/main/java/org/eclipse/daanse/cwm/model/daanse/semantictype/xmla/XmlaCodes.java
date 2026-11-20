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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.daanse.semantictype.base.ExternalType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.xmla.ServerTypeTables.AccountRow;
import org.eclipse.daanse.cwm.model.daanse.semantictype.xmla.ServerTypeTables.AttributeRow;
import org.eclipse.daanse.cwm.model.daanse.semantictype.xmla.ServerTypeTables.DimensionRow;

/**
 * The XMLA codes of semantic types, as an Analysis Services server reports them. The library a type of the server
 * lies in chooses the codes: a type of the attribute list has a LEVEL_TYPE (MDSCHEMA_LEVELS) and a
 * PROPERTY_CONTENT_TYPE (MDSCHEMA_PROPERTIES), a type of the dimension list a DIMENSION_TYPE (MDSCHEMA_DIMENSIONS), a
 * type of the account list no code. The codes are read from the tables beside {@link ServerTypes}.
 *
 * <p>A type of another library takes, in each list, the first type of the server it reaches: its same types first,
 * then the external types that name a list of the server, then its broader types, each of them in turn the same way.
 * So one type of one's own may give a level the code of an attribute type and a dimension the code of a dimension
 * type. A type that reaches none of a list, and an element without a type, has the codes of a regular level,
 * property or dimension. Where the specification [MS-SSAS] names another code than the server reports, the server's
 * is the one given ({@code FormattingSubtotal}, {@code PersonFullName}).</p>
 */
public final class XmlaCodes {

    /** LEVEL_TYPE of a level whose attribute is not typed: regular. */
    public static final int UNTYPED_LEVEL_TYPE = 0;

    /** PROPERTY_CONTENT_TYPE of a property whose related attribute is not typed: regular. */
    public static final int UNTYPED_PROPERTY_CONTENT_TYPE = 0;

    /** DIMENSION_TYPE of a dimension that is not typed: other. */
    public static final int UNTYPED_DIMENSION_TYPE = 3;

    /** DIMENSION_TYPE of the measures dimension, which is structural and has no dimension type. */
    public static final int MEASURE_DIMENSION_TYPE = 2;

    /**
     * A code as the server reports it, and the code the specification [MS-SSAS] names for the type, {@code null}
     * where it names none.
     *
     * @param value     the code the server reports
     * @param specified the code the specification names, or {@code null}
     */
    public record Code(int value, Integer specified) {
    }

    private XmlaCodes() {
    }

    /**
     * The LEVEL_TYPE of a level whose attribute is of {@code type}: that of the server's attribute type it is or
     * reaches; {@value #UNTYPED_LEVEL_TYPE} for none.
     */
    public static int levelType(SemanticType type) {
        return serverName(type, ServerTypes.ATTRIBUTE_URI).flatMap(XmlaCodes::levelTypeCode).map(Code::value)
                .orElse(UNTYPED_LEVEL_TYPE);
    }

    /**
     * The PROPERTY_CONTENT_TYPE of a property whose related attribute is of {@code type}: that of the server's
     * attribute type it is or reaches; {@value #UNTYPED_PROPERTY_CONTENT_TYPE} for none.
     */
    public static int propertyContentType(SemanticType type) {
        return serverName(type, ServerTypes.ATTRIBUTE_URI).flatMap(XmlaCodes::propertyContentTypeCode)
                .map(Code::value).orElse(UNTYPED_PROPERTY_CONTENT_TYPE);
    }

    /**
     * The DIMENSION_TYPE of a dimension of {@code type}: that of the server's dimension type it is or reaches;
     * {@value #UNTYPED_DIMENSION_TYPE} for none.
     */
    public static int dimensionType(SemanticType type) {
        return serverName(type, ServerTypes.DIMENSION_URI).flatMap(XmlaCodes::dimensionTypeCode).map(Code::value)
                .orElse(UNTYPED_DIMENSION_TYPE);
    }

    /** The LEVEL_TYPE of the server's attribute type named {@code serverName}. */
    public static Optional<Code> levelTypeCode(String serverName) {
        return Optional.ofNullable(ServerTypeTables.attribute(serverName))
                .map(row -> new Code(row.levelType(), row.levelTypeSpec()));
    }

    /** The PROPERTY_CONTENT_TYPE of the server's attribute type named {@code serverName}. */
    public static Optional<Code> propertyContentTypeCode(String serverName) {
        return Optional.ofNullable(ServerTypeTables.attribute(serverName))
                .map(row -> new Code(row.propertyContentType(), row.propertyContentTypeSpec()));
    }

    /** The DIMENSION_TYPE of the server's dimension type named {@code serverName}. */
    public static Optional<Code> dimensionTypeCode(String serverName) {
        return Optional.ofNullable(ServerTypeTables.dimension(serverName))
                .map(row -> new Code(row.dimensionType(), row.dimensionType()));
    }

    /**
     * The name there of the first type of the server's list {@code list} (one of {@link ServerTypes#LISTS}) that
     * {@code type} is or reaches. Empty for {@code null} and for a type that reaches none of that list.
     */
    public static Optional<String> serverName(SemanticType type, String list) {
        return Optional.ofNullable(reached(type).get(list));
    }

    /** The server's lists {@code type} is a type of or reaches, in the order it reaches them. */
    public static Set<String> lists(SemanticType type) {
        return reached(type).keySet();
    }

    /** The first type of each of the server's lists that {@code type} is or reaches, by list, nearest first. */
    private static Map<String, String> reached(SemanticType type) {
        Map<String, String> reached = new LinkedHashMap<>();
        if (type == null) {
            return reached;
        }
        Set<SemanticType> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<SemanticType> next = new ArrayDeque<>();
        next.add(type);
        while (!next.isEmpty() && reached.size() < ServerTypes.LISTS.size()) {
            SemanticType t = next.removeFirst();
            if (t == null || t.eIsProxy() || !seen.add(t)) {
                continue;
            }
            server(t, reached);
            for (SemanticType same : t.getSameAs()) {
                server(same, reached);
            }
            for (ExternalType external : t.getSameAsExternal()) {
                external(external).ifPresent(name -> reached.putIfAbsent(external.getLibraryUri(), name));
            }
            next.addAll(t.getSameAs());
            next.addAll(t.getBroader());
        }
        return reached;
    }

    /** Notes {@code type} where it is a type of a list of the server that is not reached yet. */
    private static void server(SemanticType type, Map<String, String> reached) {
        ServerTypes.listOf(type).filter(list -> known(list, ServerTypes.serverName(type)))
                .ifPresent(list -> reached.putIfAbsent(list, ServerTypes.serverName(type)));
    }

    /** The name of the server's type an external type names in a list of the server, by name or number. */
    static Optional<String> external(ExternalType external) {
        String list = external.getLibraryUri();
        if (!ServerTypes.LISTS.contains(list)) {
            return Optional.empty();
        }
        if (external.getSourceName() != null) {
            String name = ServerTypes.ATTRIBUTE_URI.equals(list)
                    ? ServerTypes.nameOf(external.getSourceName()).orElse(external.getSourceName())
                    : external.getSourceName();
            return known(list, name) ? Optional.of(name) : Optional.empty();
        }
        if (!external.isSetSourceNumber()) {
            return Optional.empty();
        }
        long number = external.getSourceNumber();
        if (ServerTypes.ATTRIBUTE_URI.equals(list)) {
            return ServerTypeTables.ATTRIBUTES.stream().filter(row -> row.number() == number).map(AttributeRow::name)
                    .findFirst();
        }
        if (ServerTypes.DIMENSION_URI.equals(list)) {
            return ServerTypeTables.DIMENSIONS.stream().filter(row -> row.number() == number).map(DimensionRow::name)
                    .findFirst();
        }
        return ServerTypeTables.ACCOUNTS.stream().filter(row -> row.number() == number).map(AccountRow::name)
                .findFirst();
    }

    /** Whether the table of the list {@code list} has a type named {@code name}. */
    private static boolean known(String list, String name) {
        if (name == null) {
            return false;
        }
        if (ServerTypes.ATTRIBUTE_URI.equals(list)) {
            return ServerTypeTables.attribute(name) != null;
        }
        if (ServerTypes.DIMENSION_URI.equals(list)) {
            return ServerTypeTables.dimension(name) != null;
        }
        return ServerTypes.ACCOUNT_URI.equals(list) && ServerTypeTables.account(name) != null;
    }
}
