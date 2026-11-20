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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The three tables of the types of an Analysis Services server, beside this class: {@code attribute-types.tsv},
 * {@code dimension-types.tsv}, {@code account-types.tsv}. Read once; each row by its column names.
 */
final class ServerTypeTables {

    /** A row of {@code attribute-types.tsv}. */
    record AttributeRow(String name, long number, int levelType, Integer levelTypeSpec, int propertyContentType,
            Integer propertyContentTypeSpec, String otherSpelling, String group) {
    }

    /** A row of {@code dimension-types.tsv}. */
    record DimensionRow(String name, long number, int dimensionType) {
    }

    /** A row of {@code account-types.tsv}. */
    record AccountRow(String name, long number) {
    }

    static final List<AttributeRow> ATTRIBUTES = rows("attribute-types.tsv").stream()
            .map(r -> new AttributeRow(r.get("name"), Long.parseLong(r.get("number")), code(r.get("level_type")),
                    optionalCode(r.get("level_type_spec")), code(r.get("property_content_type")),
                    optionalCode(r.get("property_content_type_spec")), emptyAsNull(r.get("other_spelling")),
                    r.get("group")))
            .toList();

    static final List<DimensionRow> DIMENSIONS = rows("dimension-types.tsv").stream()
            .map(r -> new DimensionRow(r.get("name"), Long.parseLong(r.get("number")), code(r.get("dimension_type"))))
            .toList();

    static final List<AccountRow> ACCOUNTS = rows("account-types.tsv").stream()
            .map(r -> new AccountRow(r.get("name"), Long.parseLong(r.get("number")))).toList();

    private ServerTypeTables() {
    }

    /** The attribute type of the table spelled {@code spelling}, by its name or its other spelling. */
    static AttributeRow attribute(String spelling) {
        for (AttributeRow row : ATTRIBUTES) {
            if (row.name().equals(spelling) || spelling.equals(row.otherSpelling())) {
                return row;
            }
        }
        return null;
    }

    /** The dimension type of the table named {@code name}. */
    static DimensionRow dimension(String name) {
        return DIMENSIONS.stream().filter(row -> row.name().equals(name)).findFirst().orElse(null);
    }

    /** The account type of the table named {@code name}. */
    static AccountRow account(String name) {
        return ACCOUNTS.stream().filter(row -> row.name().equals(name)).findFirst().orElse(null);
    }

    /** A code written in hexadecimal ({@code 0x...}) or in decimal. */
    static int code(String text) {
        return text.startsWith("0x") ? Integer.parseInt(text.substring(2), 16) : Integer.parseInt(text);
    }

    private static Integer optionalCode(String text) {
        return text == null || text.isEmpty() ? null : code(text);
    }

    private static String emptyAsNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    /** The rows of a table beside this class, without comment lines, each by its column names. */
    private static List<Map<String, String>> rows(String table) {
        try (InputStream in = ServerTypeTables.class.getResourceAsStream(table)) {
            if (in == null) {
                throw new IllegalStateException("table not found: " + table);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
            String[] header = lines.get(0).split("\t", -1);
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] cells = line.split("\t", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i], i < cells.length ? cells[i] : "");
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
