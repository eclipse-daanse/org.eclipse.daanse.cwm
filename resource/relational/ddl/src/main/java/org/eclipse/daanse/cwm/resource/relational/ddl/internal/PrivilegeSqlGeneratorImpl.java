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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.osgi.service.component.annotations.Component;

/**
 * Default {@link PrivilegeSqlGenerator}: maps the CWM {@code privilege} model
 * onto the dialect's grant rendering ({@code dialect.ddlGenerator()}). Table
 * targets are derived via the structural {@link DdlGeneratorImpl}
 * ({@code tableReference} — schema and TABLE/VIEW type from the namespace
 * chain); procedure targets take their schema from {@code getNamespace()} and
 * the FUNCTION/PROCEDURE keyword from {@code Procedure.getType()}.
 */
public final class PrivilegeSqlGeneratorImpl implements PrivilegeSqlGenerator {

    private final Dialect dialect;
    private final DdlGeneratorImpl structural;

    public PrivilegeSqlGeneratorImpl(Dialect dialect) {
        if (dialect == null) {
            throw new IllegalArgumentException("dialect must not be null");
        }
        this.dialect = dialect;
        this.structural = new DdlGeneratorImpl(dialect);
    }

    /** Default {@code PrivilegeSqlGeneratorFactory}. */
    @Component(service = org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGeneratorFactory.class)
    public static final class Factory
            implements org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGeneratorFactory {

        @Override
        public PrivilegeSqlGenerator create(Dialect dialect) {
            return new PrivilegeSqlGeneratorImpl(dialect);
        }
    }

    @Override
    public List<String> grantStatements(List<? extends Privilege> privileges) {
        List<String> out = new ArrayList<>();
        for (Privilege privilege : sorted(privileges)) {
            out.add(grantStatement(privilege));
        }
        return List.copyOf(out);
    }

    @Override
    public List<String> revokeStatements(List<? extends Privilege> privileges) {
        List<Privilege> ordered = sorted(privileges);
        List<String> out = new ArrayList<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            out.add(revokeStatement(ordered.get(i)));
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
            return dialect.ddlGenerator().grantExecute(schemaName(procedure), procedure.getName(),
                    isFunction(procedure), grantee, grantable);
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
            return dialect.ddlGenerator().revokeExecute(schemaName(procedure), procedure.getName(),
                    isFunction(procedure), grantee);
        }
        if (privilege instanceof RolePrivilege rp) {
            return dialect.ddlGenerator().revokeRole(rp.getRole().getName(), grantee);
        }
        throw new IllegalArgumentException("unsupported privilege type: " + privilege.eClass().getName());
    }

    @Override
    public List<String> createRoleStatements(Collection<? extends ResponsibleParty> roles) {
        return roles.stream().map(ResponsibleParty::getName).sorted()
                .map(dialect.ddlGenerator()::createRole).toList();
    }

    @Override
    public List<String> dropRoleStatements(Collection<? extends ResponsibleParty> roles) {
        return roles.stream().map(ResponsibleParty::getName).sorted(Comparator.reverseOrder())
                .map(dialect.ddlGenerator()::dropRole).toList();
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

    private static String schemaName(Procedure procedure) {
        ModelElement namespace = procedure.getNamespace();
        return namespace == null ? null : namespace.getName();
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
