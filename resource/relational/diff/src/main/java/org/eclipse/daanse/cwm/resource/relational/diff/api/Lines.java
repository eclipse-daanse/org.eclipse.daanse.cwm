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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.sql.JDBCType;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;

/**
 * The words of the readable lines of a diff: {@code <kind> <qualified name>: <difference>}, the
 * difference {@code only left}, {@code only right} or {@code <aspect> <value> left, <value> right}.
 * Left is the first model of the comparison (the old one of a migration), right the second.
 */
final class Lines {

    static final String ONLY_LEFT = "only left";
    static final String ONLY_RIGHT = "only right";

    private Lines() {
    }

    /** {@code <kind> <qualified name>: <difference>}. */
    static String line(ModelElement element, String difference) {
        return kind(element) + " " + name(element) + ": " + difference;
    }

    /** {@code <aspect> <left> left, <right> right}. */
    static String differs(String aspect, Object left, Object right) {
        return aspect + " " + value(left) + " left, " + value(right) + " right";
    }

    /**
     * The qualified name of {@code element}, an element without a name of its own under the name
     * RelationalNames gives it.
     */
    static String name(ModelElement element) {
        if (element == null) {
            return "?";
        }
        if (element.getName() != null && !element.getName().isBlank()) {
            return QualifiedNames.of(element) + (element instanceof Procedure p ? signature(p) : "");
        }
        String given = RelationalNames.of(element);
        String owner = QualifiedNames.of(element.eContainer());
        return (owner.isEmpty() ? "" : owner + ".") + (given == null ? "?" : given);
    }

    /** The name of {@code element} alone: its own, or the one RelationalNames gives it. */
    static String simpleName(ModelElement element) {
        String name = RelationalNames.of(element);
        return name == null ? "?" : name;
    }

    /** The kind of {@code element} as the lines name it. */
    static String kind(ModelElement element) {
        return switch (element) {
        case Schema s -> "schema";
        case Table t -> "table";
        case View v -> "view";
        case Column c -> "column";
        case PrimaryKey pk -> "primary key";
        case UniqueConstraint uc -> "unique constraint";
        case CheckConstraint ck -> "check constraint";
        case ForeignKey fk -> "foreign key";
        case SQLIndex i -> "index";
        case Trigger t -> "trigger";
        case Procedure p -> p.getType() == null ? "routine" : p.getType().getLiteral();
        case ResponsibleParty party -> PrivilegeStereotypes.isDatabaseRole(party) ? "role"
                : PrivilegeStereotypes.isDatabaseUser(party) ? "user" : "party";
        case null -> "element";
        default -> element.eClass().getName();
        };
    }

    /** {@code (type, …)}: the types of the parameters of a routine. */
    static String signature(Procedure procedure) {
        return procedure.getParameter().stream().map(Parameter::getType).map(Lines::type)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    /** A type by its name, with its JDBC type where the name is another: {@code int4 (INTEGER)}. */
    static String type(Classifier type) {
        if (type == null) {
            return "none";
        }
        String name = type.getName();
        if (type instanceof SQLDataType sql) {
            JDBCType code = SQLSimpleTypes.toJdbcType(sql);
            if (code != JDBCType.OTHER && (name == null || !name.trim().equalsIgnoreCase(code.getName()))) {
                return (name == null ? "" : name + " ") + "(" + code.getName() + ")";
            }
        }
        return name == null ? "?" : name;
    }

    /** The size of a column as DDL writes it: {@code (100)}, {@code (10, 2)}, or none. */
    static String size(Column column) {
        SQLSimpleType type = column.getType() instanceof SQLSimpleType s ? s : null;
        JDBCType code = type == null ? JDBCType.OTHER : SQLSimpleTypes.toJdbcType(type);
        if (code == JDBCType.DECIMAL || code == JDBCType.NUMERIC) {
            long precision = Math.max(column.getPrecision(), type.getNumericPrecision());
            long scale = column.getScale() != 0 ? column.getScale() : type.getNumericScale();
            return "(" + precision + ", " + scale + ")";
        }
        long length = Math.max(column.getLength(), type == null ? 0 : type.getCharacterMaximumLength());
        return length > 0 ? "(" + length + ")" : "none";
    }

    static String nullability(Column column) {
        return column.getIsNullable() == NullableType.COLUMN_NO_NULLS ? "not null" : "null";
    }

    static String columns(List<? extends StructuralFeature> features) {
        return features.stream().map(StructuralFeature::getName).collect(Collectors.joining(", ", "(", ")"));
    }

    /** What a privilege grants: {@code SELECT on shop.sales.orders (qty)}, {@code EXECUTE on …}, {@code member of …}. */
    static String privilege(Privilege privilege) {
        return switch (privilege) {
        case TablePrivilege tp -> tp.getAction() + " on " + name(tp.getTable())
                + (tp.getColumn().isEmpty() ? "" : " " + columns(tp.getColumn()));
        case ProcedurePrivilege pp -> "EXECUTE on " + name(pp.getProcedure());
        case RolePrivilege rp -> "member of " + (rp.getRole() == null ? "?" : rp.getRole().getName());
        default -> privilege.eClass().getName();
        };
    }

    private static String value(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof String s) {
            return s.isEmpty() ? "none" : s;
        }
        if (value instanceof Boolean b) {
            return b ? "yes" : "no";
        }
        return value.toString();
    }

    /** {@code "text"}, or {@code none} for {@code null}. */
    static String quoted(String text) {
        return text == null ? null : "\"" + text + "\"";
    }
}
