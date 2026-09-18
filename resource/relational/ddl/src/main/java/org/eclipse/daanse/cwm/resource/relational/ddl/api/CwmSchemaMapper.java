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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerScope;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;

/**
 * Pure, stateless bridge from individual CWM elements ({@link Column},
 * {@link PrimaryKey}) to the jdbc.db {@code ColumnDefinition} /
 * {@code ColumnMetaData} / {@code PrimaryKey} records that
 * {@link Dialect#ddlGenerator()} consumes. Every method takes an explicit
 * {@link TableReference} — schema/catalog derivation and DDL emission live in
 * the {@link DdlGenerator}. SQL type-code mapping is delegated to
 * {@link org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes}.
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

    public static org.eclipse.daanse.sql.model.schema.PrimaryKey primaryKey(TableReference table, PrimaryKey cwmPk) {
        List<ColumnReference> cols = new ArrayList<>();
        for (StructuralFeature sf : cwmPk.getFeature()) {
            cols.add(new ColumnReference(Optional.of(table), sf.getName()));
        }
        return new PrimaryKeyRecord(table, cols, Optional.ofNullable(cwmPk.getName()));
    }

    public static ColumnMetaData columnMetaData(Column col) {
        Classifier type = col.getType();
        SQLDataType sqlType = type instanceof SQLDataType sdt ? sdt : null;

        JDBCType jdbcType = SQLSimpleTypes.toJdbcType(sqlType);
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

    // triggers

    /**
     * CREATE statements for one trigger on {@code table}: for dialects with
     * separate trigger procedures the procedure {@code <trigger>_fn} plus the
     * trigger calling it, otherwise the trigger with its inline body. Empty
     * when the trigger has no body, timing or event.
     */
    public static List<String> createTrigger(Dialect dialect, TableReference table, Trigger trigger) {
        String body = triggerBody(trigger);
        TriggerTiming timing = triggerTiming(trigger);
        TriggerEvent event = triggerEvent(trigger);
        if (body == null || timing == null || event == null) {
            return List.of();
        }
        String name = triggerName(trigger, table);
        String schemaName = table.schema().map(s -> s.name()).orElse(null);
        List<String> out = new ArrayList<>();
        Optional<String> proc = dialect.ddlGenerator().createTriggerProcedure(name + "_fn", schemaName, body);
        if (proc.isPresent()) {
            out.add(proc.get());
            out.add(dialect.ddlGenerator().createTriggerUsingProcedure(name, schemaName, timing, event, table,
                    triggerScope(trigger), triggerWhen(trigger), name + "_fn"));
        } else {
            out.add(dialect.ddlGenerator().createTrigger(name, timing, event, table, triggerScope(trigger),
                    triggerWhen(trigger), body));
        }
        return out;
    }

    /** DROP statements for one trigger on {@code table}: the trigger and its {@code <trigger>_fn} procedure. */
    public static List<String> dropTrigger(Dialect dialect, TableReference table, Trigger trigger) {
        String name = triggerName(trigger, table);
        String schemaName = table.schema().map(s -> s.name()).orElse(null);
        List<String> out = new ArrayList<>(dialect.ddlGenerator().dropTriggerOnTable(name, table, true));
        dialect.ddlGenerator().dropProcedure(name + "_fn", schemaName, true).ifPresent(out::add);
        return out;
    }

    private static String triggerName(Trigger trigger, TableReference table) {
        String n = trigger.getName();
        return n == null || n.isBlank() ? "trg_" + table.name() : n;
    }

    /** The trigger body, or {@code null} when it is missing or blank. */
    public static String triggerBody(Trigger t) {
        if (t.getActionStatement() == null) {
            return null;
        }
        String body = t.getActionStatement().getBody();
        return (body == null || body.isBlank()) ? null : body;
    }

    /** The WHEN condition, or {@code null} when there is none. */
    public static String triggerWhen(Trigger t) {
        if (t.getActionCondition() == null) {
            return null;
        }
        String cond = t.getActionCondition().getBody();
        return (cond == null || cond.isBlank()) ? null : cond;
    }

    /** BEFORE / AFTER / INSTEAD OF, or {@code null} when not set. */
    public static TriggerTiming triggerTiming(Trigger t) {
        if (t.getConditionTiming() == null) {
            return null;
        }
        String name = t.getConditionTiming().getName();
        if (name == null) {
            return null;
        }
        return switch (stripEnumPrefix(name).toUpperCase()) {
        case "BEFORE" -> TriggerTiming.BEFORE;
        case "AFTER" -> TriggerTiming.AFTER;
        case "INSTEAD", "INSTEADOF" -> TriggerTiming.INSTEAD_OF;
        default -> null;
        };
    }

    /** INSERT / UPDATE / DELETE, or {@code null} when not set. */
    public static TriggerEvent triggerEvent(Trigger t) {
        if (t.getEventManipulation() == null) {
            return null;
        }
        String name = t.getEventManipulation().getName();
        if (name == null) {
            return null;
        }
        return switch (stripEnumPrefix(name).toUpperCase()) {
        case "INSERT" -> TriggerEvent.INSERT;
        case "UPDATE" -> TriggerEvent.UPDATE;
        case "DELETE" -> TriggerEvent.DELETE;
        default -> null;
        };
    }

    /** ROW or STATEMENT (the default). */
    public static TriggerScope triggerScope(Trigger t) {
        if (t.getActionOrientation() == null) {
            return TriggerScope.STATEMENT;
        }
        String name = t.getActionOrientation().getName();
        return switch (stripEnumPrefix(name == null ? "" : name).toUpperCase()) {
        case "ROW" -> TriggerScope.ROW;
        default -> TriggerScope.STATEMENT;
        };
    }

    private static String stripEnumPrefix(String literal) {
        if (literal == null) {
            return "";
        }
        int us = literal.lastIndexOf('_');
        return us >= 0 ? literal.substring(us + 1) : literal;
    }
}
