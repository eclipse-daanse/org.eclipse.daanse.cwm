/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.liquibase.render;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.JDBCType;
import java.util.HexFormat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.NamedColumnSets;

/**
 * Shared building blocks of both writers: the fixed document frame, strict
 * whitespace (2-space indent, \n line ends — checksums hang on the text),
 * attribute escaping, deterministic changeSet ids, and the CWM column →
 * generic Liquibase type mapping.
 */
final class LiquibaseXml {

    static final String NS = "http://www.liquibase.org/xml/ns/dbchangelog";
    static final String XSD = NS + " " + NS + "/dbchangelog-4.29.xsd";

    private final StringBuilder out = new StringBuilder();
    private int changeSetNo;
    private boolean open;

    void openDocument(String logicalFilePath) {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<databaseChangeLog\n");
        out.append("    xmlns=\"").append(NS).append("\"\n");
        out.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        out.append("    xsi:schemaLocation=\"").append(XSD).append('"');
        if (logicalFilePath != null && !logicalFilePath.isBlank()) {
            out.append("\n    logicalFilePath=\"").append(escape(logicalFilePath)).append('"');
        }
        out.append(">\n");
    }

    String closeDocument() {
        out.append("</databaseChangeLog>\n");
        return out.toString();
    }

    /** Opens a changeSet with a deterministic id: running number + content hash. */
    void openChangeSet(String author, String canonicalOpDescription) {
        String id = "%04d-%s".formatted(++changeSetNo, hash8(canonicalOpDescription));
        out.append("  <changeSet id=\"").append(id).append("\" author=\"")
                .append(escape(author)).append("\">\n");
        open = true;
    }

    void closeChangeSet() {
        out.append("  </changeSet>\n");
        open = false;
    }

    /** {@code <name attr="v" …/>} on one indented line. */
    void emptyElement(String name, String... attrPairs) {
        indent();
        out.append('<').append(name);
        attributes(attrPairs);
        out.append("/>\n");
    }

    void startElement(String name, String... attrPairs) {
        indent();
        out.append('<').append(name);
        attributes(attrPairs);
        out.append(">\n");
        depth++;
    }

    void endElement(String name) {
        depth--;
        indent();
        out.append("</").append(name).append(">\n");
    }

    /** {@code <name attr…>text</name>} with escaped text content. */
    void textElement(String name, String text, String... attrPairs) {
        indent();
        out.append('<').append(name);
        attributes(attrPairs);
        out.append('>').append(escape(text)).append("</").append(name).append(">\n");
    }

    /** The deliberately empty rollback: {@code rollback} fails loudly. */
    void emptyRollback() {
        indent();
        out.append("<rollback/>\n");
    }

    private int depth;

    private void indent() {
        out.append("    ");
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private void attributes(String... attrPairs) {
        for (int i = 0; i + 1 < attrPairs.length; i += 2) {
            if (attrPairs[i + 1] != null) {
                out.append(' ').append(attrPairs[i]).append("=\"")
                        .append(escape(attrPairs[i + 1])).append('"');
            }
        }
    }

    boolean changeSetOpen() {
        return open;
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String hash8(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // CWM helpers

    static String schemaNameOf(NamedColumnSet ncs) {
        return NamedColumnSets.findSchema(ncs)
                .map(Schema::getName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }

    static boolean notNull(Column c) {
        return c.getIsNullable() == NullableType.COLUMN_NO_NULLS;
    }

    static String defaultValueOf(Column c) {
        return c.getInitialValue() == null ? null : c.getInitialValue().getBody();
    }

    /**
     * Generic Liquibase type from the CWM column — Liquibase translates it per
     * target database, which is what keeps the changelog dialect-neutral.
     */
    static String liquibaseType(Column c) {
        JDBCType jdbc = null;
        long len = c.getLength();
        long prec = c.getPrecision();
        long scale = c.getScale();
        String rawName = c.getType() == null ? null : c.getType().getName();
        if (c.getType() instanceof SQLSimpleType st) {
            try {
                jdbc = JDBCType.valueOf((int) st.getTypeNumber());
            } catch (IllegalArgumentException notAJdbcType) {
                jdbc = null;
            }
            len = Math.max(len, st.getCharacterMaximumLength());
            prec = Math.max(prec, st.getNumericPrecision());
            scale = scale != 0 ? scale : st.getNumericScale();
        }
        if (jdbc == null) {
            return rawName == null ? "VARCHAR(255)" : rawName;
        }
        return switch (jdbc) {
            case CHAR, NCHAR -> sized("CHAR", len);
            case VARCHAR, NVARCHAR, LONGVARCHAR, LONGNVARCHAR -> sized("VARCHAR", len);
            case NUMERIC, DECIMAL -> prec > 0
                    ? "DECIMAL(" + prec + (scale > 0 ? ", " + scale : "") + ")"
                    : "DECIMAL";
            case TINYINT -> "TINYINT";
            case SMALLINT -> "SMALLINT";
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case REAL -> "REAL";
            case FLOAT, DOUBLE -> "DOUBLE";
            case BOOLEAN, BIT -> "BOOLEAN";
            case DATE -> "DATE";
            case TIME, TIME_WITH_TIMEZONE -> "TIME";
            case TIMESTAMP -> "TIMESTAMP";
            case TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP WITH TIME ZONE";
            case CLOB, NCLOB -> "CLOB";
            case BLOB, VARBINARY, LONGVARBINARY, BINARY -> "BLOB";
            default -> rawName != null ? rawName : jdbc.getName();
        };
    }

    private static String sized(String base, long len) {
        return len > 0 ? base + "(" + len + ")" : base;
    }

    /** Classifies a default value into the matching Liquibase attribute. */
    static String[] defaultAttribute(String value) {
        String v = value.trim();
        if (v.equalsIgnoreCase("TRUE") || v.equalsIgnoreCase("FALSE")) {
            return new String[] { "defaultValueBoolean", v.toLowerCase() };
        }
        try {
            new java.math.BigDecimal(v);
            return new String[] { "defaultValueNumeric", v };
        } catch (NumberFormatException notNumeric) {
            // fall through
        }
        String upper = v.toUpperCase();
        if (v.endsWith(")") || upper.startsWith("CURRENT_") || upper.equals("NOW")) {
            return new String[] { "defaultValueComputed", v };
        }
        // string literal: strip enclosing quotes when present
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
            v = v.substring(1, v.length() - 1);
        }
        return new String[] { "defaultValue", v };
    }

    private LiquibaseXml() {
    }

    static LiquibaseXml document(String logicalFilePath) {
        LiquibaseXml x = new LiquibaseXml();
        x.openDocument(logicalFilePath);
        return x;
    }
}
