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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection.Verdict;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegePackage;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CatalogPartLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.ObjectKey;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.api.PrivilegeLoader;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.api.schema.ObjectPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.RoleMembership;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.osgi.service.component.annotations.Component;

/**
 * Maps a {@link MetaInfo} snapshot onto {@link ResponsibleParty} instances in the
 * catalog, owning their privileges ({@code ownedElement} — the grantee is the
 * namespace): table rows → {@code TablePrivilege}, EXECUTE routine rows →
 * {@code ProcedurePrivilege}, membership edges → {@code RolePrivilege}
 * ({@code grantable} = WITH GRANT/ADMIN OPTION); column rows aggregate into one
 * column-restricted {@code TablePrivilege} per (grantee, action, table).
 * Principals come first (roles without privileges appear) and are marked via
 * {@code PrivilegeStereotypes}; {@code KIND_UNKNOWN} (MySQL), unknown grantees
 * and PUBLIC stay unmarked. Rows without grantee or privilege, rows outside the
 * common core (dialect actions, DENY, schema privileges, self-memberships) and
 * rows whose target the catalog lacks are skipped.
 *
 * <p>
 * A party the selection excludes is not read, nor are its privileges, nor the
 * memberships in it; as grantor it is left out. Whatever else the selection
 * leaves out, the {@code CwmLoader} cuts afterwards with {@link #consequences()}.
 * </p>
 *
 * <p>
 * A row names a routine by its schema and its specific name, which tells the
 * routines of one name in a schema (overloads) apart: in PostgreSQL the name
 * and the oid. The loader keeps a routine's specific name where it differs
 * from its name ({@link RelationalNames#SPECIFIC_NAME_TAG}), so a row reaches
 * exactly the overload it is on. A row that names no routine by its specific
 * name reaches the routine of its name where the schema holds one; where it
 * holds several, the row is left out with a warning rather than given to an
 * arbitrary overload or to all of them.
 * </p>
 */
@Component(service = { PrivilegeLoader.class, CatalogPartLoader.class })
public final class PrivilegeLoaderImpl implements PrivilegeLoader {

    private static final Logger LOGGER = System.getLogger(PrivilegeLoaderImpl.class.getName());

    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;

    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;

    private static final Set<String> ROUTINE_KINDS = Set.of("FUNCTION", "PROCEDURE", "ROUTINE");

    private static final Comparator<String> NULLS_FIRST = Comparator.nullsFirst(Comparator.naturalOrder());

    private static final Comparator<ObjectKey> BY_KEY = Comparator.comparing(ObjectKey::schema, NULLS_FIRST)
            .thenComparing(ObjectKey::name, NULLS_FIRST);

    @Override
    public List<ConsequenceRule> consequences() {
        return PrivilegeRules.CONSEQUENCES;
    }

    @Override
    public void load(MetaInfo info, Catalog catalog, Selection selection) {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        if (selection == null) {
            throw new IllegalArgumentException("selection must not be null");
        }
        if (selection.excludesKind(BusinessinformationPackage.Literals.RESPONSIBLE_PARTY)) {
            // the parties own all the rest
            return;
        }
        StructureInfo si = info.structureInfo();
        Parties parties = new Parties(catalog, selection);
        Targets targets = new Targets(catalog);

        // Principals first: roles without privileges appear, and the stereotype markers
        // (databaseRole/databaseUser) are set before the privilege rows bring their grantees.
        si.principals().stream().filter(p -> hasText(p.name()))
                .sorted(Comparator.comparing(DatabasePrincipal::name))
                .forEach(p -> parties.classify(p.name(), p.kind()));

        Set<TableGrant> tableLevel = new HashSet<>();
        if (!selection.excludesKind(PrivilegePackage.Literals.TABLE_PRIVILEGE)) {
            tablePrivileges(si, parties, targets, tableLevel);
            columnPrivileges(si, parties, targets, tableLevel);
        }
        if (!selection.excludesKind(PrivilegePackage.Literals.PROCEDURE_PRIVILEGE)) {
            routinePrivileges(si, parties, targets);
        }
        if (!selection.excludesKind(PrivilegePackage.Literals.ROLE_PRIVILEGE)) {
            memberships(si, parties);
        }
        parties.finish();
    }

    private static void tablePrivileges(StructureInfo si, Parties parties, Targets targets,
            Set<TableGrant> tableLevel) {
        List<org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege> rows = si.tablePrivileges().stream()
                .filter(tp -> tp.table() != null && tp.table().name() != null && hasText(tp.grantee())
                        && hasText(tp.privilege()))
                .sorted(Comparator
                        .comparing((org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege tp) -> ObjectKey
                                .of(tp.table()), BY_KEY)
                        .thenComparing(org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege::privilege)
                        .thenComparing(org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege::grantee))
                .toList();
        for (org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege tp : rows) {
            TablePrivilegeAction action = tableAction(tp.privilege());
            ObjectKey table = ObjectKey.of(tp.table());
            NamedColumnSet target = targets.columnSets.get(table);
            if (action == null || target == null) {
                continue;
            }
            ResponsibleParty grantee = parties.party(tp.grantee());
            if (grantee == null) {
                continue;
            }
            TablePrivilege privilege = PF.createTablePrivilege();
            privilege.setAction(action);
            privilege.setTable(target);
            privilege.setName(action.getLiteral() + " ON " + table);
            applyCommon(privilege, tp.grantor(), tp.isGrantable(), parties);
            grantee.getOwnedElement().add(privilege);
            tableLevel.add(new TableGrant(tp.grantee(), action, table));
        }
    }

    /**
     * Column privileges: one row per column, condensed into one column-restricted
     * {@code TablePrivilege} per (grantee, action, table). PostgreSQL also expands a table
     * grant per column; such duplicates of a table privilege are dropped.
     */
    private static void columnPrivileges(StructureInfo si, Parties parties, Targets targets,
            Set<TableGrant> tableLevel) {
        List<ColumnPrivilege> rows = si.columnPrivileges().stream()
                .filter(cp -> cp.column() != null && cp.column().name() != null
                        && cp.column().table().map(TableReference::name).isPresent() && hasText(cp.grantee())
                        && hasText(cp.privilege()))
                .sorted(Comparator.comparing((ColumnPrivilege cp) -> ObjectKey.of(cp.column().table().get()), BY_KEY)
                        .thenComparing(ColumnPrivilege::privilege)
                        .thenComparing(ColumnPrivilege::grantee)
                        .thenComparing(cp -> cp.column().name()))
                .toList();
        Map<TableGrant, TablePrivilege> restricted = new LinkedHashMap<>();
        for (ColumnPrivilege cp : rows) {
            TablePrivilegeAction action = tableAction(cp.privilege());
            ObjectKey table = ObjectKey.of(cp.column().table().get());
            NamedColumnSet target = targets.columnSets.get(table);
            if (action == null || target == null) {
                continue;
            }
            Column column = columnByName(target, cp.column().name());
            TableGrant grant = new TableGrant(cp.grantee(), action, table);
            if (column == null || tableLevel.contains(grant)) {
                continue;
            }
            ResponsibleParty grantee = parties.party(cp.grantee());
            if (grantee == null) {
                continue;
            }
            TablePrivilege privilege = restricted.computeIfAbsent(grant, k -> {
                TablePrivilege p = PF.createTablePrivilege();
                p.setAction(action);
                p.setTable(target);
                applyCommon(p, cp.grantor(), cp.isGrantable(), parties);
                grantee.getOwnedElement().add(p);
                return p;
            });
            if (!privilege.getColumn().contains(column)) {
                privilege.getColumn().add(column);
            }
        }
        for (Map.Entry<TableGrant, TablePrivilege> entry : restricted.entrySet()) {
            TablePrivilege privilege = entry.getValue();
            String columns = String.join(", ", privilege.getColumn().stream().map(Column::getName).toList());
            privilege.setName(privilege.getAction().getLiteral() + " (" + columns + ") ON " + entry.getKey().table());
        }
    }

    private static void routinePrivileges(StructureInfo si, Parties parties, Targets targets) {
        List<ObjectPrivilege> rows = si.objectPrivileges().stream()
                .filter(op -> hasText(op.objectName()) && hasText(op.grantee()) && hasText(op.privilege())
                        && isRoutine(op.objectKind()) && isExecute(op.privilege()))
                .sorted(Comparator.comparing((ObjectPrivilege op) -> routineKey(op), BY_KEY)
                        .thenComparing(PrivilegeLoaderImpl::specificName).thenComparing(ObjectPrivilege::grantee))
                .toList();
        for (ObjectPrivilege row : rows) {
            Optional<Procedure> routine = targets.routine(row);
            if (routine.isEmpty()) {
                continue;
            }
            ResponsibleParty grantee = parties.party(row.grantee());
            if (grantee != null) {
                execute(grantee, routine.get(), row, parties);
            }
        }
    }

    private static void execute(ResponsibleParty grantee, Procedure routine, ObjectPrivilege row, Parties parties) {
        ProcedurePrivilege privilege = PF.createProcedurePrivilege();
        privilege.setProcedure(routine);
        privilege.setName("EXECUTE ON " + ObjectKey.of(routine));
        applyCommon(privilege, row.grantor(), row.isGrantable(), parties);
        grantee.getOwnedElement().add(privilege);
    }

    private static void memberships(StructureInfo si, Parties parties) {
        List<RoleMembership> rows = si.roleMemberships().stream()
                .filter(rm -> hasText(rm.grantee()) && hasText(rm.role()) && !rm.grantee().equals(rm.role()))
                .sorted(Comparator.comparing(RoleMembership::grantee).thenComparing(RoleMembership::role))
                .toList();
        for (RoleMembership rm : rows) {
            ResponsibleParty role = parties.party(rm.role());
            ResponsibleParty grantee = parties.party(rm.grantee());
            if (role == null || grantee == null) {
                continue;
            }
            RolePrivilege privilege = PF.createRolePrivilege();
            privilege.setRole(role);
            privilege.setName("MEMBER OF " + rm.role());
            applyCommon(privilege, rm.grantor(), rm.adminOption(), parties);
            grantee.getOwnedElement().add(privilege);
        }
    }

    private static void applyCommon(Privilege p, Optional<String> grantor, Optional<String> isGrantable,
            Parties parties) {
        if (grantor != null) {
            grantor.filter(PrivilegeLoaderImpl::hasText).map(parties::party).ifPresent(p::setGrantor);
        }
        if (isGrantable != null) {
            isGrantable.map(String::strip).ifPresent(s -> {
                if ("YES".equalsIgnoreCase(s)) {
                    p.setGrantable(Boolean.TRUE);
                } else if ("NO".equalsIgnoreCase(s)) {
                    p.setGrantable(Boolean.FALSE);
                }
            });
        }
    }

    /** Map a raw table-privilege spelling onto the common core; {@code null} = skip the row. */
    private static TablePrivilegeAction tableAction(String rawPrivilege) {
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
        return "EXECUTE".equalsIgnoreCase(rawPrivilege.strip());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ObjectKey routineKey(ObjectPrivilege op) {
        return new ObjectKey(op.schemaName().orElse(null), op.objectName());
    }

    /** The specific name a row names its routine by; its name where it gives none. */
    private static String specificName(ObjectPrivilege op) {
        return hasText(op.specificName()) ? op.specificName() : op.objectName();
    }

    private static Column columnByName(NamedColumnSet columnSet, String name) {
        for (var feature : columnSet.getFeature()) {
            if (feature instanceof Column column && name.equals(column.getName())) {
                return column;
            }
        }
        return null;
    }

    /** A table privilege granted to a grantee, as the rows of tables and of columns name it. */
    private record TableGrant(String grantee, TablePrivilegeAction action, ObjectKey table) {
    }

    /** The tables, views and routines of the catalog, by the schema and name the rows give. */
    private static final class Targets {

        private final Map<ObjectKey, NamedColumnSet> columnSets = new HashMap<>();
        /** All routines of a name: overloads stay apart. */
        private final Map<ObjectKey, List<Procedure>> routines = new HashMap<>();
        /** The routines by schema and specific name; more than one where a model left them alike. */
        private final Map<ObjectKey, List<Procedure>> bySpecificName = new HashMap<>();

        Targets(Catalog catalog) {
            for (ModelElement se : catalog.getOwnedElement()) {
                if (se instanceof Schema schema) {
                    for (ModelElement te : schema.getOwnedElement()) {
                        if (te instanceof NamedColumnSet ncs) {
                            columnSets.put(ObjectKey.of(ncs), ncs);
                        } else if (te instanceof Procedure proc) {
                            routines.computeIfAbsent(ObjectKey.of(proc), k -> new ArrayList<>()).add(proc);
                            bySpecificName.computeIfAbsent(new ObjectKey(schema.getName(),
                                    RelationalNames.specificName(proc)), k -> new ArrayList<>()).add(proc);
                        }
                    }
                }
            }
        }

        /**
         * The routine {@code row} is on: the one of its schema and specific name, otherwise the one
         * routine of its name; empty where the catalog holds none, or several the row does not tell
         * apart.
         */
        Optional<Procedure> routine(ObjectPrivilege row) {
            ObjectKey named = routineKey(row);
            List<Procedure> exact = bySpecificName.getOrDefault(new ObjectKey(named.schema(), specificName(row)),
                    List.of());
            if (exact.size() == 1) {
                return Optional.of(exact.get(0));
            }
            List<Procedure> ofName = routines.getOrDefault(named, List.of());
            if (ofName.size() == 1) {
                return Optional.of(ofName.get(0));
            }
            if (!ofName.isEmpty()) {
                LOGGER.log(Level.WARNING, "EXECUTE of {0} on {1} ({2}): {3} routines have that name and none of them"
                        + " that specific name; left out", row.grantee(), named, specificName(row), ofName.size());
            }
            return Optional.empty();
        }
    }

    /**
     * The grantees and grantors, one party per name, owned by the catalog. A party is asked
     * about once it is in its place, with its marker: what the selection excludes is taken out
     * again, and its name answers {@code null} from then on.
     */
    private static final class Parties {

        private final Catalog catalog;
        private final Selection selection;
        private final Verdict catalogVerdict;
        private final Map<String, ResponsibleParty> kept = new LinkedHashMap<>();
        private final Set<String> excluded = new HashSet<>();
        private final Map<String, Stereotype> stereotypes = new LinkedHashMap<>();

        Parties(Catalog catalog, Selection selection) {
            this.catalog = catalog;
            this.selection = selection;
            this.catalogVerdict = selection.verdictOf(catalog);
        }

        /** The party {@code name}; {@code null} when the selection excludes it. */
        ResponsibleParty party(String name) {
            if (excluded.contains(name)) {
                return null;
            }
            ResponsibleParty party = kept.get(name);
            return party != null ? party : judged(name, create(name));
        }

        /**
         * Brings in {@code name} with the marker of its kind. {@code KIND_UNKNOWN} (MySQL: users
         * and roles are structurally indistinguishable) brings the party in without a marker. The
         * first row of a name decides.
         */
        void classify(String name, String kind) {
            if (excluded.contains(name) || kept.containsKey(name)) {
                return;
            }
            ResponsibleParty party = create(name);
            boolean user = DatabasePrincipal.KIND_USER.equals(kind);
            boolean role = DatabasePrincipal.KIND_ROLE.equals(kind);
            if (user || role) {
                party.setResponsibility(user ? RESPONSIBILITY_DATABASE_USER : RESPONSIBILITY_DATABASE_ROLE);
                stereotypes.computeIfAbsent(
                        user ? PrivilegeStereotypes.DATABASE_USER : PrivilegeStereotypes.DATABASE_ROLE, n -> {
                            Stereotype stereotype = PrivilegeStereotypes.create(n);
                            catalog.getOwnedElement().add(stereotype);
                            return stereotype;
                        }).getExtendedElement().add(party);
            }
            judged(name, party);
        }

        /** Puts the markers in front of the parties and drops a marker that marks none. */
        void finish() {
            EList<ModelElement> owned = catalog.getOwnedElement();
            int first = kept.values().stream().mapToInt(owned::indexOf).filter(i -> i >= 0).min().orElse(owned.size());
            for (Stereotype stereotype : stereotypes.values()) {
                if (stereotype.getExtendedElement().isEmpty()) {
                    EcoreUtil.remove(stereotype);
                } else if (owned.indexOf(stereotype) > first) {
                    owned.move(first++, stereotype);
                }
            }
        }

        private ResponsibleParty create(String name) {
            ResponsibleParty party = BF.createResponsibleParty();
            party.setName(name);
            party.setResponsibility("PUBLIC".equalsIgnoreCase(name) ? RESPONSIBILITY_PUBLIC
                    : RESPONSIBILITY_DATABASE_ROLE);
            catalog.getOwnedElement().add(party);
            return party;
        }

        private ResponsibleParty judged(String name, ResponsibleParty party) {
            if (selection.below(catalogVerdict, party).excluded()) {
                stereotypes.values().forEach(s -> s.getExtendedElement().remove(party));
                EcoreUtil.remove(party);
                excluded.add(name);
                return null;
            }
            kept.put(name, party);
            return party;
        }
    }
}
