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
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Stereotypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiffer;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.PartyDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.PrivilegeChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiffer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Compares two catalogs schema by schema and party by party, each matched by name, in both
 * directions. The schemas go to the {@link SchemaDiffer}; the tolerances of the settings apply here
 * as there, through the same {@link Comparison}.
 */
@Component(service = CatalogDiffer.class)
public final class CatalogDifferImpl implements CatalogDiffer {

    private final SchemaDiffer schemaDiffer;

    /** Constructor injection — also usable directly from plain code and tests. */
    @Activate
    public CatalogDifferImpl(@Reference SchemaDiffer schemaDiffer) {
        this.schemaDiffer = Objects.requireNonNull(schemaDiffer, "schemaDiffer");
    }

    @Override
    public CatalogDiff diff(Catalog left, Catalog right, DiffSettings settings) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(settings, "settings");
        Comparison comparison = new Comparison(settings);

        List<Schema> schemasOnlyLeft = new ArrayList<>();
        List<SchemaDiff> schemas = new ArrayList<>();
        Map<Object, Schema> rightSchemas = byName(Catalogs.schemas(right), s -> comparison.name(s.getName()));
        for (Schema schema : Catalogs.schemas(left)) {
            Schema other = rightSchemas.remove(Arrays.asList(comparison.name(schema.getName())));
            if (other == null) {
                schemasOnlyLeft.add(schema);
                continue;
            }
            SchemaDiff diff = schemaDiffer.diff(schema, other, settings);
            if (!diff.isEmpty()) {
                schemas.add(diff);
            }
        }
        List<Schema> schemasOnlyRight = new ArrayList<>(rightSchemas.values());

        List<ResponsibleParty> partiesOnlyLeft = new ArrayList<>();
        List<PartyDiff> parties = new ArrayList<>();
        Map<Object, ResponsibleParty> rightParties = byName(principals(right), p -> comparison.name(p.getName()));
        for (ResponsibleParty party : principals(left)) {
            ResponsibleParty other = rightParties.remove(Arrays.asList(comparison.name(party.getName())));
            if (other == null) {
                partiesOnlyLeft.add(party);
                continue;
            }
            PartyDiff diff = party(party, other, comparison);
            if (!diff.isEmpty()) {
                parties.add(diff);
            }
        }
        List<ResponsibleParty> partiesOnlyRight = comparison.countsExtraParties()
                ? new ArrayList<>(rightParties.values())
                : List.of();

        return new CatalogDiff(left, right, schemasOnlyLeft, schemasOnlyRight, schemas, partiesOnlyLeft,
                partiesOnlyRight, parties);
    }

    /**
     * The database principals of {@code catalog}: the parties anywhere in it that carry the
     * {@code databaseRole} or {@code databaseUser} stereotype or hold a privilege.
     */
    private static List<ResponsibleParty> principals(Catalog catalog) {
        return Selections.stream(catalog, Selection.all(), ResponsibleParty.class)
                .filter(p -> PrivilegeStereotypes.isDatabaseRole(p) || PrivilegeStereotypes.isDatabaseUser(p)
                        || p.getOwnedElement().stream().anyMatch(Privilege.class::isInstance))
                .toList();
    }

    private static PartyDiff party(ResponsibleParty left, ResponsibleParty right, Comparison comparison) {
        TreeSet<String> leftStereotypes = stereotypes(left);
        TreeSet<String> rightStereotypes = stereotypes(right);
        List<String> stereotypesOnlyLeft = leftStereotypes.stream().filter(s -> !rightStereotypes.contains(s)).toList();
        List<String> stereotypesOnlyRight = rightStereotypes.stream().filter(s -> !leftStereotypes.contains(s))
                .toList();

        List<Privilege> privilegesOnlyLeft = new ArrayList<>();
        List<PrivilegeChange> privilegesChanged = new ArrayList<>();
        Map<Object, Privilege> rightPrivileges = byName(privileges(right), p -> grants(p, comparison));
        for (Privilege privilege : privileges(left)) {
            Privilege other = rightPrivileges.remove(Arrays.asList(grants(privilege, comparison)));
            if (other == null) {
                privilegesOnlyLeft.add(privilege);
                continue;
            }
            EnumSet<PrivilegeChange.Aspect> aspects = EnumSet.noneOf(PrivilegeChange.Aspect.class);
            if (Boolean.TRUE.equals(privilege.getGrantable()) != Boolean.TRUE.equals(other.getGrantable())) {
                aspects.add(PrivilegeChange.Aspect.GRANTABLE);
            }
            if (!Objects.equals(comparison.grantor(privilege), comparison.grantor(other))) {
                aspects.add(PrivilegeChange.Aspect.GRANTOR);
            }
            if (!aspects.isEmpty()) {
                privilegesChanged.add(new PrivilegeChange(privilege, other, aspects));
            }
        }
        return new PartyDiff(left, right, stereotypesOnlyLeft, stereotypesOnlyRight, privilegesOnlyLeft,
                new ArrayList<>(rightPrivileges.values()), privilegesChanged);
    }

    private static TreeSet<String> stereotypes(ResponsibleParty party) {
        TreeSet<String> names = new TreeSet<>();
        Stereotypes.of(party).stream().map(Stereotype::getName).filter(Objects::nonNull).forEach(names::add);
        return names;
    }

    private static List<Privilege> privileges(ResponsibleParty party) {
        return party.getOwnedElement().stream().filter(Privilege.class::isInstance).map(Privilege.class::cast)
                .toList();
    }

    /**
     * What a privilege grants on what, as compared: a table privilege by action, table and columns,
     * a routine privilege by the routine's schema and signature, a membership by the role.
     */
    private static Object grants(Privilege privilege, Comparison comparison) {
        return switch (privilege) {
        case TablePrivilege tp -> {
            TreeSet<String> columns = new TreeSet<>();
            tp.getColumn().forEach(c -> columns.add(comparison.name(c.getName())));
            yield Arrays.asList("table", tp.getAction(), comparison.table(tp.getTable()), columns);
        }
        case ProcedurePrivilege pp -> Arrays.asList("routine",
                pp.getProcedure() == null || !(pp.getProcedure().getNamespace() instanceof Schema schema) ? null
                        : comparison.name(schema.getName()),
                pp.getProcedure() == null ? null : comparison.signature(pp.getProcedure()));
        case RolePrivilege rp -> Arrays.asList("member", rp.getRole() == null ? null
                : comparison.name(rp.getRole().getName()));
        default -> Arrays.asList(privilege.eClass().getName(), privilege);
        };
    }

    /** {@code elements} by key, each key wrapped in a list so a {@code null} key is one as well; the first of a key wins. */
    private static <E> Map<Object, E> byName(List<E> elements, Function<E, Object> key) {
        Map<Object, E> map = new LinkedHashMap<>();
        for (E element : elements) {
            map.putIfAbsent(Arrays.asList(key.apply(element)), element);
        }
        return map;
    }
}
