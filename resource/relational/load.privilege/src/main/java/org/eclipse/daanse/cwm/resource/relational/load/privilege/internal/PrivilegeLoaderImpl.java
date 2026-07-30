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
package org.eclipse.daanse.cwm.resource.relational.load.privilege.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.api.PrivilegeLoadResult;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.api.PrivilegeLoader;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.api.schema.ObjectPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.RoleMembership;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.osgi.service.component.annotations.Component;

/**
 * Maps a {@link MetaInfo} snapshot onto interned {@link ResponsibleParty}
 * instances owning their privileges ({@code ownedElement} — the grantee is the
 * namespace): table rows → {@code TablePrivilege}, EXECUTE routine rows →
 * {@code ProcedurePrivilege}, membership edges → {@code RolePrivilege}
 * ({@code grantable} = WITH GRANT/ADMIN OPTION); column rows aggregate into one
 * column-restricted {@code TablePrivilege} per (grantee, action, table).
 * Principals are interned first (roles without privileges appear) and marked
 * via {@code PrivilegeStereotypes}; {@code KIND_UNKNOWN} (MySQL), unknown
 * grantees and PUBLIC stay unmarked. Everything returned is unattached — the
 * caller places it via {@code ownedElement}. Rows outside the common core
 * (dialect actions, DENY, schema privileges, self-memberships, unresolved
 * targets) are skipped.
 */
@Component(service = PrivilegeLoader.class)
public final class PrivilegeLoaderImpl implements PrivilegeLoader {

    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;

    /** The human-readable {@code responsibility} of roles (and the default for unknown grantees). */
    public static final String RESPONSIBILITY_DATABASE_ROLE = "database role";

    /** The human-readable {@code responsibility} of login-capable users. */
    public static final String RESPONSIBILITY_DATABASE_USER = "database user";

    /** The human-readable {@code responsibility} of the PUBLIC pseudo grantee. */
    public static final String RESPONSIBILITY_PUBLIC = "public";

    private static final Set<String> ROUTINE_KINDS = Set.of("FUNCTION", "PROCEDURE", "ROUTINE");

    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;

    @Override
    public PrivilegeLoadResult load(MetaInfo info, Catalog cwmCatalog) {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }
        if (cwmCatalog == null) {
            throw new IllegalArgumentException("cwmCatalog must not be null");
        }
        StructureInfo si = info.structureInfo();
        Interning interning = new Interning(cwmCatalog);

        // Principals zuerst: so erscheinen auch Rollen ohne Privilegien, und die
        // Stereotype-Marker (databaseRole/databaseUser) sind gesetzt, bevor die
        // Privilegien-Zeilen Grantees internieren.
        List<DatabasePrincipal> principals = new ArrayList<>(si.principals());
        principals.sort(Comparator.comparing(DatabasePrincipal::name));
        for (DatabasePrincipal principal : principals) {
            if (principal.name() == null || principal.name().isBlank()) {
                continue;
            }
            interning.classify(principal.name(), principal.kind());
        }

        List<org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege> tablePrivileges =
                new ArrayList<>(si.tablePrivileges());
        tablePrivileges.sort(Comparator
                .comparing((org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege tp) -> schemaName(tp.table()),
                        Comparator.nullsFirst(String::compareTo))
                .thenComparing(tp -> tp.table().name())
                .thenComparing(org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege::privilege)
                .thenComparing(org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege::grantee));
        Set<String> tableLevel = new HashSet<>();
        for (org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege tp : tablePrivileges) {
            TablePrivilegeAction action = tableAction(tp.privilege());
            if (action == null) {
                continue;
            }
            NamedColumnSet target = interning.columnSet(tp.table());
            if (target == null) {
                continue;
            }
            TablePrivilege privilege = PF.createTablePrivilege();
            privilege.setAction(action);
            privilege.setTable(target);
            privilege.setName(action.getLiteral() + " ON " + qualified(schemaName(tp.table()), tp.table().name()));
            applyCommon(privilege, tp.grantor(), tp.isGrantable(), interning);
            interning.party(tp.grantee()).getOwnedElement().add(privilege);
            tableLevel.add(privilegeKey(tp.grantee(), action, fqnOf(tp.table())));
        }

        // Spaltenprivilegien: eine Zeile je Spalte im Katalog — verdichtet zu einem
        // spalteneingeschraenkten TablePrivilege je (Grantee, Aktion, Tabelle).
        // PG expandiert Tabellen-Grants zusaetzlich je Spalte — solche Duplikate eines
        // vorhandenen Tabellen-Privilegs werden unterdrueckt.
        List<ColumnPrivilege> columnPrivileges = new ArrayList<>(si.columnPrivileges());
        columnPrivileges.sort(Comparator
                .comparing((ColumnPrivilege cp) -> cp.column().table().map(t -> fqnOf(t)).orElse(""))
                .thenComparing(cp -> cp.privilege() == null ? "" : cp.privilege())
                .thenComparing(ColumnPrivilege::grantee)
                .thenComparing(cp -> cp.column().name()));
        Map<String, TablePrivilege> restricted = new LinkedHashMap<>();
        for (ColumnPrivilege cp : columnPrivileges) {
            TablePrivilegeAction action = tableAction(cp.privilege());
            TableReference tableRef = cp.column().table().orElse(null);
            if (action == null || tableRef == null) {
                continue;
            }
            NamedColumnSet target = interning.columnSet(tableRef);
            if (target == null) {
                continue;
            }
            Column column = columnByName(target, cp.column().name());
            if (column == null) {
                continue;
            }
            String key = privilegeKey(cp.grantee(), action, fqnOf(tableRef));
            if (tableLevel.contains(key)) {
                continue;
            }
            TablePrivilege privilege = restricted.computeIfAbsent(key, k -> {
                TablePrivilege p = PF.createTablePrivilege();
                p.setAction(action);
                p.setTable(target);
                applyCommon(p, cp.grantor(), cp.isGrantable(), interning);
                interning.party(cp.grantee()).getOwnedElement().add(p);
                return p;
            });
            if (!privilege.getColumn().contains(column)) {
                privilege.getColumn().add(column);
            }
        }
        for (TablePrivilege privilege : restricted.values()) {
            String columns = String.join(", ", privilege.getColumn().stream().map(Column::getName).toList());
            privilege.setName(privilege.getAction().getLiteral() + " (" + columns + ") ON "
                    + qualified(schemaOf(privilege.getTable()), privilege.getTable().getName()));
        }

        List<ObjectPrivilege> objectPrivileges = new ArrayList<>(si.objectPrivileges());
        objectPrivileges.sort(Comparator
                .comparing((ObjectPrivilege op) -> op.schemaName().orElse(""))
                .thenComparing(ObjectPrivilege::objectName)
                .thenComparing(ObjectPrivilege::privilege)
                .thenComparing(ObjectPrivilege::grantee));
        for (ObjectPrivilege op : objectPrivileges) {
            if (!isRoutine(op.objectKind()) || !isExecute(op.privilege())) {
                continue;
            }
            Procedure target = interning.procedure(op.schemaName().orElse(null), op.objectName());
            if (target == null) {
                continue;
            }
            ProcedurePrivilege privilege = PF.createProcedurePrivilege();
            privilege.setProcedure(target);
            privilege.setName("EXECUTE ON " + qualified(op.schemaName().orElse(null), op.objectName()));
            applyCommon(privilege, op.grantor(), op.isGrantable(), interning);
            interning.party(op.grantee()).getOwnedElement().add(privilege);
        }

        List<RoleMembership> memberships = new ArrayList<>(si.roleMemberships());
        memberships.sort(Comparator.comparing(RoleMembership::grantee).thenComparing(RoleMembership::role));
        for (RoleMembership rm : memberships) {
            if (rm.grantee() == null || rm.grantee().isBlank() || rm.role() == null || rm.role().isBlank()
                    || rm.grantee().equals(rm.role())) {
                continue;
            }
            RolePrivilege privilege = PF.createRolePrivilege();
            privilege.setRole(interning.party(rm.role()));
            privilege.setName("MEMBER OF " + rm.role());
            applyCommon(privilege, rm.grantor(), rm.adminOption(), interning);
            interning.party(rm.grantee()).getOwnedElement().add(privilege);
        }

        return new PrivilegeLoadResult(interning.parties(), interning.stereotypes());
    }

    private static void applyCommon(Privilege p, Optional<String> grantor, Optional<String> isGrantable,
            Interning interning) {
        grantor.filter(s -> !s.isBlank()).ifPresent(s -> p.setGrantor(interning.party(s)));
        isGrantable.map(String::strip).ifPresent(s -> {
            if ("YES".equalsIgnoreCase(s)) {
                p.setGrantable(Boolean.TRUE);
            } else if ("NO".equalsIgnoreCase(s)) {
                p.setGrantable(Boolean.FALSE);
            }
        });
    }

    /** Map a raw table-privilege spelling onto the common core; {@code null} = skip the row. */
    private static TablePrivilegeAction tableAction(String rawPrivilege) {
        if (rawPrivilege == null) {
            return null;
        }
        return switch (rawPrivilege.strip().toUpperCase(Locale.ROOT)) {
        case "SELECT" -> TablePrivilegeAction.SELECT;
        case "INSERT" -> TablePrivilegeAction.INSERT;
        case "UPDATE" -> TablePrivilegeAction.UPDATE;
        case "DELETE" -> TablePrivilegeAction.DELETE;
        case "REFERENCES" -> TablePrivilegeAction.REFERENCES;
        default -> null;
        };
    }

    private static boolean isRoutine(String rawObjectKind) {
        return rawObjectKind != null && ROUTINE_KINDS.contains(rawObjectKind.strip().toUpperCase(Locale.ROOT));
    }

    private static boolean isExecute(String rawPrivilege) {
        return rawPrivilege != null && "EXECUTE".equalsIgnoreCase(rawPrivilege.strip());
    }

    private static String schemaName(TableReference tr) {
        return tr.schema().map(SchemaReference::name).orElse(null);
    }

    private static String fqnOf(TableReference tr) {
        return qualified(schemaName(tr), tr.name());
    }

    private static String privilegeKey(String grantee, TablePrivilegeAction action, String tableFqn) {
        return grantee + "\u0000" + action.getLiteral() + "\u0000" + tableFqn;
    }

    private static String schemaOf(NamedColumnSet columnSet) {
        return columnSet.getNamespace() == null ? null : columnSet.getNamespace().getName();
    }

    private static Column columnByName(NamedColumnSet columnSet, String name) {
        if (name == null) {
            return null;
        }
        for (var feature : columnSet.getFeature()) {
            if (feature instanceof Column column && name.equals(column.getName())) {
                return column;
            }
        }
        return null;
    }

    private static String qualified(String schema, String object) {
        return schema == null || schema.isBlank() ? object : schema + "." + object;
    }

    /**
     * Interns grantees/grantors (by name, as unattached ResponsibleParty) and
     * resolves targets against the CWM catalog.
     */
    private static final class Interning {
        private final Map<String, ResponsibleParty> parties = new LinkedHashMap<>();
        private final Map<String, Stereotype> stereotypes = new LinkedHashMap<>();
        private final Map<String, NamedColumnSet> columnSetsByFqn = new LinkedHashMap<>();
        private final Map<String, Procedure> proceduresByFqn = new LinkedHashMap<>();

        Interning(Catalog cwmCatalog) {
            for (ModelElement se : cwmCatalog.getOwnedElement()) {
                if (se instanceof Schema schema) {
                    for (ModelElement te : schema.getOwnedElement()) {
                        if (te instanceof NamedColumnSet ncs) {
                            columnSetsByFqn.put(fqn(schema.getName(), ncs.getName()), ncs);
                        } else if (te instanceof Procedure proc) {
                            proceduresByFqn.put(fqn(schema.getName(), proc.getName()), proc);
                        }
                    }
                }
            }
        }

        ResponsibleParty party(String name) {
            return parties.computeIfAbsent(name, n -> {
                ResponsibleParty party = BF.createResponsibleParty();
                party.setName(n);
                party.setResponsibility("PUBLIC".equalsIgnoreCase(n) ? RESPONSIBILITY_PUBLIC
                        : RESPONSIBILITY_DATABASE_ROLE);
                return party;
            });
        }

        /**
         * Interns {@code name} and marks it with the matching stereotype.
         * {@code KIND_UNKNOWN} (MySQL: users and roles are structurally
         * indistinguishable) interns the party without any marker.
         */
        void classify(String name, String kind) {
            ResponsibleParty party = party(name);
            boolean user = DatabasePrincipal.KIND_USER.equals(kind);
            boolean role = DatabasePrincipal.KIND_ROLE.equals(kind);
            if (!user && !role) {
                return;
            }
            party.setResponsibility(user ? RESPONSIBILITY_DATABASE_USER : RESPONSIBILITY_DATABASE_ROLE);
            Stereotype stereotype = stereotypes.computeIfAbsent(
                    user ? PrivilegeStereotypes.DATABASE_USER : PrivilegeStereotypes.DATABASE_ROLE,
                    PrivilegeStereotypes::create);
            if (!stereotype.getExtendedElement().contains(party)) {
                stereotype.getExtendedElement().add(party);
            }
        }

        List<ResponsibleParty> parties() {
            return List.copyOf(parties.values());
        }

        List<Stereotype> stereotypes() {
            return List.copyOf(stereotypes.values());
        }

        NamedColumnSet columnSet(TableReference tr) {
            return columnSetsByFqn.get(fqn(schemaName(tr), tr.name()));
        }

        Procedure procedure(String schema, String name) {
            return proceduresByFqn.get(fqn(schema, name));
        }

        private static String fqn(String schema, String object) {
            return (schema == null ? "\0" : schema) + "." + object;
        }
    }
}
