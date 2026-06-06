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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnMetaData;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.jdbc.db.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.jdbc.db.record.schema.PrimaryKeyRecord;

/**
 * Pure, stateless bridge from individual CWM elements ({@link Column},
 * {@link PrimaryKey}) to the jdbc.db {@code ColumnDefinition} /
 * {@code ColumnMetaData} / {@code PrimaryKey} records that
 * {@link Dialect#ddlGenerator()} consumes. Every method takes an explicit
 * {@link TableReference} — schema/catalog derivation and DDL emission live in
 * the {@link DdlGenerator}. SQL type-code mapping is delegated to
 * {@link org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes}.
 */
public final class CwmSchemaMapper {

    private CwmSchemaMapper() {
    }

    public static List<ColumnDefinition> columnDefinitions(TableReference table, NamedColumnSet cwmTable) {
        List<ColumnDefinition> out = new ArrayList<>();
        for (Feature f : cwmTable.getFeature()) {
            if (!(f instanceof Column col)) {
                continue;
            }
            ColumnReference colRef = new ColumnReference(Optional.of(table), col.getName());
            out.add(new ColumnDefinitionRecord(colRef, columnMetaData(col)));
        }
        return out;
    }

    public static org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey primaryKey(TableReference table, PrimaryKey cwmPk) {
        List<ColumnReference> cols = new ArrayList<>();
        for (StructuralFeature sf : cwmPk.getFeature()) {
            cols.add(new ColumnReference(Optional.of(table), sf.getName()));
        }
        return new PrimaryKeyRecord(table, cols, Optional.ofNullable(cwmPk.getName()));
    }

    public static ColumnMetaData columnMetaData(Column col) {
        Classifier type = col.getType();
        SQLDataType sqlType = type instanceof SQLDataType sdt ? sdt : null;

        JDBCType jdbcType = SqlSimpleTypes.toJdbcType(sqlType);
        String typeName;
        if (sqlType != null && sqlType.getName() != null && !sqlType.getName().isBlank()) {
            typeName = sqlType.getName();
        } else if (jdbcType != null) {
            typeName = jdbcType.getName();
        } else {
            typeName = "";
        }

        OptionalInt size = OptionalInt.empty();
        OptionalInt scale = OptionalInt.empty();
        if (sqlType instanceof SQLSimpleType simple) {
            long maxLen = simple.getCharacterMaximumLength();
            long numPrec = simple.getNumericPrecision();
            long numScale = simple.getNumericScale();
            if (maxLen > 0) {
                size = OptionalInt.of((int) maxLen);
            }
            if (numPrec > 0) {
                size = OptionalInt.of((int) numPrec);
            }
            if (numScale != 0) {
                scale = OptionalInt.of((int) numScale);
            }
        }
        long colPrec = col.getPrecision();
        long colScale = col.getScale();
        long colLen = col.getLength();
        if (colLen > 0) {
            size = OptionalInt.of((int) colLen);
        }
        if (colPrec > 0) {
            size = OptionalInt.of((int) colPrec);
        }
        if (colScale != 0) {
            scale = OptionalInt.of((int) colScale);
        }

        Optional<String> columnDefault = Optional.ofNullable(col.getInitialValue()).map(e -> e.getBody())
                .filter(b -> b != null && !b.isBlank());

        return new ColumnMetaDataRecord(jdbcType, typeName, size, scale, OptionalInt.empty(),
                toJdbcNullability(col.getIsNullable()), OptionalInt.empty(), Optional.empty(), columnDefault,
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    /** Map a CWM {@link NullableType} to the jdbc.db {@link ColumnMetaData.Nullability}. */
    private static ColumnMetaData.Nullability toJdbcNullability(NullableType n) {
        if (n == null) {
            return ColumnMetaData.Nullability.UNKNOWN;
        }
        return switch (n) {
        case COLUMN_NO_NULLS -> ColumnMetaData.Nullability.NO_NULLS;
        case COLUMN_NULLABLE -> ColumnMetaData.Nullability.NULLABLE;
        case COLUMN_NULLABLE_UNKNOWN -> ColumnMetaData.Nullability.UNKNOWN;
        };
    }
}
