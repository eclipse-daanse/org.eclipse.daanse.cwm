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
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritable;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritables;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.osgi.service.component.annotations.Component;

/**
 * Default {@link PrivilegeSqlGenerator}: maps the CWM {@code privilege} model
 * onto the dialect's grant rendering ({@code dialect.ddlGenerator()}). Table
 * targets are derived via the structural {@link DdlGeneratorImpl}
 * ({@code tableReference} — schema and TABLE/VIEW type from the namespace
 * chain, the schema written as {@link DdlSettings#includeSchema()} says);
 * procedure targets take their schema from {@code getNamespace()}, likewise,
 * and the FUNCTION/PROCEDURE keyword from {@code Procedure.getType()}.
 *
 * <p>
 * A routine is named by its name alone, as every dialect takes it. Where the
 * model holds overloads of it — another procedure of the same name in the same
 * schema — the name alone is ambiguous, and only a dialect that has overloads
 * can hold them: there the types of its parameters follow the name,
 * {@code "s"."f"(integer, OUT text)}, as PostgreSQL wants them. The return
 * value is no parameter there; an {@code OUT} or {@code INOUT} parameter says
 * so.
 * </p>
 */
public final class PrivilegeSqlGeneratorImpl implements PrivilegeSqlGenerator {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PrivilegeSqlGeneratorImpl.class);

    private final Dialect dialect;
    private final DdlSettings settings;
    private final DdlGeneratorImpl structural;

    public PrivilegeSqlGeneratorImpl(Dialect dialect) {
        this(dialect, DdlSettings.defaults());
    }

    public PrivilegeSqlGeneratorImpl(Dialect dialect, DdlSettings settings) {
        if (dialect == null) {
            throw new IllegalArgumentException("dialect must not be null");
        }
        this.dialect = dialect;
        this.settings = settings == null ? DdlSettings.defaults() : settings;
        this.structural = new DdlGeneratorImpl(dialect, this.settings);
    }

    /** Default {@code PrivilegeSqlGeneratorFactory}. */
    @Component(service = org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGeneratorFactory.class)
    public static final class Factory
            implements org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGeneratorFactory {

        @Override
        public PrivilegeSqlGenerator create(Dialect dialect) {
            return new PrivilegeSqlGeneratorImpl(dialect);
        }

        @Override
        public PrivilegeSqlGenerator create(Dialect dialect, DdlSettings settings) {
            return new PrivilegeSqlGeneratorImpl(dialect, settings);
        }
    }

    @Override
    public List<DdlStatement> grantStatements(List<? extends Privilege> privileges) {
        List<DdlStatement> out = new ArrayList<>();
        for (Privilege privilege : sorted(privileges)) {
            out.add(new DdlStatement(privilege, grantStatement(privilege)));
        }
        return List.copyOf(out);
    }

    @Override
    public List<DdlStatement> revokeStatements(List<? extends Privilege> privileges) {
        List<Privilege> ordered = sorted(privileges);
        List<DdlStatement> out = new ArrayList<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            out.add(new DdlStatement(ordered.get(i), revokeStatement(ordered.get(i))));
        }
        return List.copyOf(out);
    }

    @Override
    public String grantStatement(Privilege privilege) {
        String grantee = granteeName(privilege);
        boolean grantable = Boolean.TRUE.equals(privilege.getGrantable());
        if (privilege instanceof TablePrivilege tp) {
            return dialect.ddlGenerator().grantTablePrivilege(tp.getAction().getLiteral(),
                    structural.tableReference(tp.getTable()), columnNames(tp), grantee, grantable);
        }
        if (privilege instanceof ProcedurePrivilege pp) {
            Procedure procedure = pp.getProcedure();
            return withSignature(dialect.ddlGenerator().grantExecute(schemaName(procedure), procedure.getName(),
                    isFunction(procedure), grantee, grantable), procedure);
        }
        if (privilege instanceof RolePrivilege rp) {
            return dialect.ddlGenerator().grantRole(rp.getRole().getName(), grantee, grantable);
        }
        throw new IllegalArgumentException("unsupported privilege type: " + privilege.eClass().getName());
    }

    @Override
    public String revokeStatement(Privilege privilege) {
        String grantee = granteeName(privilege);
        if (privilege instanceof TablePrivilege tp) {
            return dialect.ddlGenerator().revokeTablePrivilege(tp.getAction().getLiteral(),
                    structural.tableReference(tp.getTable()), columnNames(tp), grantee);
        }
        if (privilege instanceof ProcedurePrivilege pp) {
            Procedure procedure = pp.getProcedure();
            return withSignature(dialect.ddlGenerator().revokeExecute(schemaName(procedure), procedure.getName(),
                    isFunction(procedure), grantee), procedure);
        }
        if (privilege instanceof RolePrivilege rp) {
            return dialect.ddlGenerator().revokeRole(rp.getRole().getName(), grantee);
        }
        throw new IllegalArgumentException("unsupported privilege type: " + privilege.eClass().getName());
    }

    @Override
    public List<DdlStatement> createRoleStatements(Collection<? extends ResponsibleParty> roles) {
        return roles.stream().sorted(Comparator.comparing(ResponsibleParty::getName))
                .map(role -> new DdlStatement(role, dialect.ddlGenerator().createRole(role.getName()))).toList();
    }

    @Override
    public List<DdlStatement> dropRoleStatements(Collection<? extends ResponsibleParty> roles) {
        return roles.stream().sorted(Comparator.comparing(ResponsibleParty::getName).reversed())
                .map(role -> new DdlStatement(role, dialect.ddlGenerator().dropRole(role.getName()))).toList();
    }

    @Override
    public List<DdlStatement> createUserStatements(Collection<? extends ResponsibleParty> users) {
        if (!usersWritten(users, Unwritables.forCreate(settings))) {
            return List.of();
        }
        List<DdlStatement> out = new ArrayList<>();
        users.stream().sorted(Comparator.comparing(ResponsibleParty::getName))
                .forEach(user -> dialect.ddlGenerator().createUser(user.getName(), settings.ifNotExists())
                        .ifPresent(sql -> out.add(new DdlStatement(user, sql))));
        return List.copyOf(out);
    }

    @Override
    public List<DdlStatement> dropUserStatements(Collection<? extends ResponsibleParty> users) {
        if (!usersWritten(users, Unwritables.forDrop(settings))) {
            return List.of();
        }
        List<DdlStatement> out = new ArrayList<>();
        users.stream().sorted(Comparator.comparing(ResponsibleParty::getName).reversed())
                .forEach(user -> dialect.ddlGenerator().dropUser(user.getName(), settings.ifNotExists())
                        .ifPresent(sql -> out.add(new DdlStatement(user, sql))));
        return List.copyOf(out);
    }

    /**
     * Whether the dialect writes users; where not, each of {@code users} is a case the dialect cannot
     * write, and the run ends with them ({@link Unwritables#finish()}).
     */
    private boolean usersWritten(Collection<? extends ResponsibleParty> users, Unwritables found) {
        if (dialect.ddlGenerator().supportsUsers()) {
            return true;
        }
        users.stream().map(ResponsibleParty::getName).sorted()
                .forEach(name -> found.add(new Unwritable(dialect.name(), "user " + name, "a user", "left out")));
        found.finish();
        return false;
    }

    /** Sortierte Spalteneinschraenkung; leer = ganze Tabelle. */
    private static List<String> columnNames(TablePrivilege privilege) {
        return privilege.getColumn().stream()
                .map(c -> String.valueOf(c.getName())).sorted().toList();
    }

    private static String granteeName(Privilege privilege) {
        Namespace grantee = privilege.getNamespace();
        if (grantee == null || grantee.getName() == null) {
            throw new IllegalArgumentException(
                    "privilege '" + privilege.getName() + "' has no owning grantee (namespace)");
        }
        return grantee.getName();
    }

    /** The schema a routine is named with: its own with {@link DdlSettings#includeSchema()}, else none. */
    private String schemaName(Procedure procedure) {
        ModelElement namespace = procedure.getNamespace();
        return settings.includeSchema() && namespace != null ? namespace.getName() : null;
    }

    /**
     * {@code statement} with the parameter types after the routine's name,
     * where the model holds overloads of {@code procedure}; otherwise as it is.
     */
    private String withSignature(String statement, Procedure procedure) {
        Namespace owner = procedure.getNamespace();
        boolean overloaded = owner != null && owner.getOwnedElement().stream()
                .anyMatch(e -> e != procedure && e instanceof Procedure other
                        && java.util.Objects.equals(other.getName(), procedure.getName()));
        if (!overloaded) {
            return statement;
        }
        List<String> types = new ArrayList<>();
        for (Parameter parameter : procedure.getParameter()) {
            if (parameter.getKind() == ParameterDirectionKind.PDK_RETURN) {
                continue;
            }
            if (parameter.getType() == null || parameter.getType().getName() == null
                    || parameter.getType().getName().isBlank()) {
                LOGGER.warn("db={} — routine {} has overloads, but parameter {} has no type: named without its"
                        + " parameters", dialect.name(), procedure.getName(), parameter.getName());
                return statement;
            }
            String mode = switch (parameter.getKind()) {
            case PDK_OUT -> "OUT ";
            case PDK_INOUT -> "INOUT ";
            default -> "";
            };
            types.add(mode + parameter.getType().getName());
        }
        String routine = dialect.ddlGenerator().qualifiedRoutine(schemaName(procedure), procedure.getName());
        int at = statement.indexOf(routine);
        if (at < 0) {
            return statement;
        }
        int end = at + routine.length();
        return statement.substring(0, end) + "(" + String.join(", ", types) + ")" + statement.substring(end);
    }

    private static boolean isFunction(Procedure procedure) {
        return procedure.getType() == ProcedureType.FUNCTION;
    }

    /** Deterministic order: grantee, privilege kind, target, action. */
    private static List<Privilege> sorted(List<? extends Privilege> privileges) {
        List<Privilege> ordered = new ArrayList<>(privileges);
        ordered.sort(Comparator
                .comparing(PrivilegeSqlGeneratorImpl::granteeName)
                .thenComparing(p -> p.eClass().getName())
                .thenComparing(PrivilegeSqlGeneratorImpl::targetName)
                .thenComparing(PrivilegeSqlGeneratorImpl::actionName));
        return ordered;
    }

    private static String targetName(Privilege privilege) {
        if (privilege instanceof TablePrivilege tp && tp.getTable() != null) {
            return String.valueOf(tp.getTable().getName());
        }
        if (privilege instanceof ProcedurePrivilege pp && pp.getProcedure() != null) {
            return String.valueOf(pp.getProcedure().getName());
        }
        if (privilege instanceof RolePrivilege rp && rp.getRole() != null) {
            return String.valueOf(rp.getRole().getName());
        }
        return "";
    }

    private static String actionName(Privilege privilege) {
        return privilege instanceof TablePrivilege tp ? tp.getAction().getLiteral() : "";
    }
}
