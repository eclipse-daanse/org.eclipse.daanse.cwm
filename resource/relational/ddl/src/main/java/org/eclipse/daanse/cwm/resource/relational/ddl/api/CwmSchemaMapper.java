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
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.ParameterMode;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineParameter;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineSignature;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerScope;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;

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

    /**
     * The primary key record of {@code cwmPk} on {@code table}, named
     * {@code name}; give the name {@link RelationalNames#of} gives, so a later
     * drop finds the key.
     */
    public static org.eclipse.daanse.sql.model.schema.PrimaryKey primaryKey(TableReference table, PrimaryKey cwmPk,
            String name) {
        List<ColumnReference> cols = new ArrayList<>();
        for (StructuralFeature sf : cwmPk.getFeature()) {
            cols.add(new ColumnReference(Optional.of(table), sf.getName()));
        }
        return new PrimaryKeyRecord(table, cols, Optional.ofNullable(name));
    }

    public static ColumnMetaData columnMetaData(Column col) {
        Optional<String> columnDefault = Optional.ofNullable(col.getInitialValue()).map(e -> e.getBody())
                .filter(b -> b != null && !b.isBlank());
        return metaData(col.getType(), col.getLength(), col.getPrecision(), col.getScale(), col.getIsNullable(),
                columnDefault);
    }

    /**
     * The metadata of {@code type} alone, as {@link #columnMetaData} gives it for a column of the type
     * without a size of its own: its JDBC type, its name, and the size and scale the type gives.
     */
    public static ColumnMetaData typeMetaData(Classifier type) {
        return metaData(type, 0, 0, 0, null, Optional.empty());
    }

    private static ColumnMetaData metaData(Classifier type, long colLen, long colPrec, long colScale,
            NullableType nullable, Optional<String> columnDefault) {
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
        if (colLen > 0) {
            size = OptionalInt.of((int) colLen);
        }
        if (colPrec > 0) {
            size = OptionalInt.of((int) colPrec);
        }
        if (colScale != 0) {
            scale = OptionalInt.of((int) colScale);
        }

        return new ColumnMetaDataRecord(jdbcType, typeName, size, scale, OptionalInt.empty(),
                toJdbcNullability(nullable), OptionalInt.empty(), Optional.empty(), columnDefault,
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
     * CREATE statements for one trigger on {@code table}, named as
     * {@link RelationalNames#of} names it: for dialects with separate trigger
     * procedures its own procedure, {@link #triggerFunctionName}, plus the
     * trigger calling it, otherwise the trigger with its inline body. Empty
     * when the trigger has no body.
     */
    public static List<String> createTrigger(Dialect dialect, TableReference table, Trigger trigger) {
        return createTrigger(dialect, table, trigger, RelationalNames.of(trigger));
    }

    /** As {@link #createTrigger(Dialect, TableReference, Trigger)}, the trigger named {@code name}. */
    public static List<String> createTrigger(Dialect dialect, TableReference table, Trigger trigger, String name) {
        String body = triggerBody(trigger);
        if (body == null) {
            return List.of();
        }
        return createTrigger(dialect, table, name, body, triggerTiming(trigger), triggerScope(trigger),
                triggerWhen(trigger), triggerEvent(trigger));
    }

    /**
     * Whether the dialect writes {@code trigger}, a row trigger in the model, as a statement trigger:
     * it writes the same statements for it as for a statement trigger, so it writes no orientation,
     * and a trigger without one fires once per statement, {@code FOR EACH STATEMENT} being the
     * default of SQL. SQL Server, whose triggers fire once per statement, is such a dialect.
     * {@code false} for a statement trigger and for a trigger without body.
     */
    public static boolean rowTriggerWrittenPerStatement(Dialect dialect, TableReference table, Trigger trigger,
            String name) {
        String body = triggerBody(trigger);
        if (body == null || triggerScope(trigger) != TriggerScope.ROW) {
            return false;
        }
        TriggerTiming timing = triggerTiming(trigger);
        String when = triggerWhen(trigger);
        TriggerEvent event = triggerEvent(trigger);
        return createTrigger(dialect, table, name, body, timing, TriggerScope.ROW, when, event)
                .equals(createTrigger(dialect, table, name, body, timing, TriggerScope.STATEMENT, when, event));
    }

    /**
     * Whether the dialect writes {@code trigger}, a {@code BEFORE} trigger in the model, as an
     * {@code AFTER} trigger: it writes the same statements for it as for one that fires after the
     * statement. SQL Server, which has no {@code BEFORE} triggers, is such a dialect. {@code false} for
     * an {@code AFTER} trigger and for a trigger without body.
     */
    public static boolean beforeTriggerWrittenAfter(Dialect dialect, TableReference table, Trigger trigger,
            String name) {
        String body = triggerBody(trigger);
        if (body == null || triggerTiming(trigger) != TriggerTiming.BEFORE) {
            return false;
        }
        TriggerScope scope = triggerScope(trigger);
        String when = triggerWhen(trigger);
        TriggerEvent event = triggerEvent(trigger);
        return createTrigger(dialect, table, name, body, TriggerTiming.BEFORE, scope, when, event)
                .equals(createTrigger(dialect, table, name, body, TriggerTiming.AFTER, scope, when, event));
    }

    /**
     * Whether the dialect leaves out the {@code WHEN} condition of {@code trigger}: it writes the same
     * statements for it as for the trigger without one, so the trigger fires for every row or
     * statement. {@code false} for a trigger without condition and for a trigger without body.
     */
    public static boolean triggerConditionLeftOut(Dialect dialect, TableReference table, Trigger trigger,
            String name) {
        String body = triggerBody(trigger);
        String when = triggerWhen(trigger);
        if (body == null || when == null) {
            return false;
        }
        TriggerTiming timing = triggerTiming(trigger);
        TriggerScope scope = triggerScope(trigger);
        TriggerEvent event = triggerEvent(trigger);
        return createTrigger(dialect, table, name, body, timing, scope, when, event)
                .equals(createTrigger(dialect, table, name, body, timing, scope, null, event));
    }

    /** The statements of a trigger named {@code name}, with the timing, orientation, condition and event given. */
    private static List<String> createTrigger(Dialect dialect, TableReference table, String name, String body,
            TriggerTiming timing, TriggerScope scope, String when, TriggerEvent event) {
        String schemaName = table.schema().map(s -> s.name()).orElse(null);
        List<String> out = new ArrayList<>();
        Optional<String> proc = dialect.ddlGenerator().createTriggerProcedure(triggerFunctionName(name), schemaName,
                body);
        if (proc.isPresent()) {
            out.add(proc.get());
            out.add(dialect.ddlGenerator().createTriggerUsingProcedure(name, schemaName, timing, event, table, scope,
                    when, triggerFunctionName(name)));
        } else {
            out.add(dialect.ddlGenerator().createTrigger(name, timing, event, table, scope, when, body));
        }
        return out;
    }

    /**
     * DROP statements for one trigger on {@code table}, the reverse of
     * {@link #createTrigger(Dialect, TableReference, Trigger)}: the trigger,
     * then its own procedure where the dialect has one. Empty when the trigger
     * has no body, as nothing was created for it.
     */
    public static List<String> dropTrigger(Dialect dialect, TableReference table, Trigger trigger) {
        return dropTrigger(dialect, table, trigger, RelationalNames.of(trigger), true);
    }

    /**
     * As {@link #dropTrigger(Dialect, TableReference, Trigger)}, the trigger
     * named {@code name}, with {@code IF EXISTS} where the dialect has it and
     * {@code ifExists} asks for it.
     */
    public static List<String> dropTrigger(Dialect dialect, TableReference table, Trigger trigger, String name,
            boolean ifExists) {
        if (triggerBody(trigger) == null) {
            return List.of();
        }
        String schemaName = table.schema().map(s -> s.name()).orElse(null);
        List<String> out = new ArrayList<>(dialect.ddlGenerator().dropTriggerOnTable(name, table, ifExists));
        if (dialect.ddlGenerator().createTriggerProcedure(triggerFunctionName(name), schemaName,
                triggerBody(trigger)).isPresent()) {
            dialect.ddlGenerator().dropProcedure(triggerFunctionName(name), schemaName, ifExists).ifPresent(out::add);
        }
        return out;
    }

    /**
     * The procedure a trigger named {@code triggerName} calls, where the
     * dialect keeps the body of a trigger in a procedure of its own:
     * {@link RelationalNames#triggerProcedure(String)}.
     */
    public static String triggerFunctionName(String triggerName) {
        return RelationalNames.triggerProcedure(triggerName);
    }

    /**
     * The procedure {@code trigger} calls: {@link #triggerFunctionName(String)} of
     * its {@link RelationalNames name}.
     */
    public static String triggerFunctionName(Trigger trigger) {
        return triggerFunctionName(RelationalNames.of(trigger));
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

    /** BEFORE or AFTER, the only timings CWM knows. */
    public static TriggerTiming triggerTiming(Trigger t) {
        return t.getConditionTiming() == ConditionTimingType.AFTER ? TriggerTiming.AFTER : TriggerTiming.BEFORE;
    }

    /** INSERT, UPDATE or DELETE. */
    public static TriggerEvent triggerEvent(Trigger t) {
        EventManipulationType event = t.getEventManipulation();
        return switch (event == null ? EventManipulationType.INSERT : event) {
        case INSERT -> TriggerEvent.INSERT;
        case UPDATE -> TriggerEvent.UPDATE;
        case DELETE -> TriggerEvent.DELETE;
        };
    }

    /** ROW or STATEMENT. */
    public static TriggerScope triggerScope(Trigger t) {
        return t.getActionOrientation() == ActionOrientationType.ROW ? TriggerScope.ROW : TriggerScope.STATEMENT;
    }

    /**
     * The language of the body of {@code routine}, in lower case as the dialects name the languages
     * they write ({@code sql}, {@code plpgsql}, {@code plsql}, {@code tsql}): that of its expression,
     * {@code sql} where it names none. Empty for a routine without body.
     */
    public static Optional<String> routineLanguage(Procedure routine) {
        if (routine.getBody() == null || routine.getBody().getBody() == null
                || routine.getBody().getBody().isBlank()) {
            return Optional.empty();
        }
        String language = routine.getBody().getLanguage();
        return Optional.of(language == null || language.isBlank() ? "sql" : language.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The type of a parameter of a routine, written by the dialect as it writes the type of a column
     * ({@code nativeType}), but by the name of the type, with the size the type gives:
     * {@code VARCHAR(100)}, {@code text}, {@code int4}. A database tells overloads apart, and grants
     * and drops one of them, by the types of their parameters as they were created; PostgreSQL's
     * {@code text}, which its driver reports as {@code VARCHAR}, names another routine than
     * {@code varchar}. Empty for a parameter without type.
     */
    public static Optional<String> parameterType(Dialect dialect, Parameter parameter) {
        if (!(parameter.getType() instanceof Classifier type) || type.getName() == null || type.getName().isBlank()) {
            return Optional.empty();
        }
        ColumnMetaData meta = typeMetaData(type);
        return Optional.of(dialect.ddlGenerator().nativeType(new ColumnMetaDataRecord(JDBCType.OTHER,
                type.getName().trim(), meta.columnSize(), meta.decimalDigits(), OptionalInt.empty(),
                ColumnMetaData.Nullability.UNKNOWN, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN)));
    }

    /**
     * {@code routine} as the dialect writes it: in {@code schemaName} ({@code null} for none), with its
     * parameters but the one that returns the result, in their order, each with its direction and its
     * type ({@link #parameterType}), the type of that one for a function, the language of its body
     * ({@link #routineLanguage}) and the body as it is. Empty for a routine without body or name, and
     * where a parameter has no name or no type.
     */
    public static Optional<RoutineDefinition> routineDefinition(Dialect dialect, Procedure routine,
            String schemaName) {
        Optional<String> language = routineLanguage(routine);
        if (language.isEmpty() || routine.getName() == null || routine.getName().isBlank()) {
            return Optional.empty();
        }
        List<RoutineParameter> parameters = new ArrayList<>();
        String returnType = null;
        for (Parameter parameter : routine.getParameter()) {
            Optional<String> type = parameterType(dialect, parameter);
            if (type.isEmpty()) {
                return Optional.empty();
            }
            if (parameter.getKind() == ParameterDirectionKind.PDK_RETURN) {
                returnType = type.get();
                continue;
            }
            if (parameter.getName() == null || parameter.getName().isBlank()) {
                return Optional.empty();
            }
            parameters.add(new RoutineParameter(parameter.getName(), switch (parameter.getKind()) {
            case PDK_OUT -> ParameterMode.OUT;
            case PDK_INOUT -> ParameterMode.INOUT;
            default -> ParameterMode.IN;
            }, type.get()));
        }
        return Optional.of(new RoutineDefinition(schemaName, routine.getName(), parameters,
                routine.getType() == ProcedureType.FUNCTION ? returnType : null, language.get(),
                routine.getBody().getBody()));
    }

    /**
     * The signature {@code routine} is dropped by: its name in {@code schemaName} and the types of
     * the parameters that pass a value into it ({@code IN}, {@code INOUT}), as a database tells
     * overloads apart by them ({@link #parameterType}).
     */
    public static RoutineSignature routineSignature(Dialect dialect, Procedure routine, String schemaName) {
        List<String> types = new ArrayList<>();
        for (Parameter parameter : routine.getParameter()) {
            ParameterDirectionKind kind = parameter.getKind();
            if (kind == ParameterDirectionKind.PDK_RETURN || kind == ParameterDirectionKind.PDK_OUT) {
                continue;
            }
            Optional<String> type = parameterType(dialect, parameter);
            if (type.isEmpty()) {
                return new RoutineSignature(schemaName, routine.getName(), null);
            }
            types.add(type.get());
        }
        return new RoutineSignature(schemaName, routine.getName(), types);
    }
}
