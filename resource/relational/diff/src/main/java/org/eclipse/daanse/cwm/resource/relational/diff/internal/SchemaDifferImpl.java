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
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeMarkers;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ColumnChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ColumnRename;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CommentChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ConstraintRename;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.IndexRename;
import org.eclipse.daanse.cwm.resource.relational.diff.api.PredecessorLinks;
import org.eclipse.daanse.cwm.resource.relational.diff.api.PrimaryKeyChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableMerge;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableRename;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableSplit;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ViewBodyChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiffer;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.Collections;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;

/**
 * Compares two CWM relational schemas and produces a {@link SchemaDiff}.
 *
 * <p>Element identity is resolved per new element from up to three sources,
 * individually switchable via {@link DiffSettings} and consulted in this
 * order (first hit wins): predecessor Dependency, {@code renamedFrom}
 * TaggedValue marker, name equality. Left-over adds/drops may then be folded
 * by the conservative shape heuristic. In {@link DiffSettings.Scope#PARTIAL}
 * the old schema is a stub: added elements outside the stub set are
 * suppressed.</p>
 *
 * <p>Tables only: a clean split (one old table claimed by several new ones)
 * or merge (several old tables claimed by one new one) is detected from
 * predecessor Dependency links ahead of the ordinary pairing and surfaced as
 * {@link TableSplit}/{@link TableMerge} instead of an unrelated add and drop.
 * Structure only — no row data is moved; see {@code ChangePlanner#warnings}.</p>
 *
 * <p>Structural comparisons:
 * <ul>
 *   <li>Columns: type name, length/precision/scale, nullability, default value.</li>
 *   <li>Primary key: column-name list (order-sensitive).</li>
 *   <li>Unique/check/foreign keys, indexes: paired like tables and columns;
 *       same shape and a new name is a rename, a changed shape is drop + add.
 *       A primary key whose columns stay and whose name changes is a rename.</li>
 *   <li>Triggers: paired likewise; any change (including the name) is drop + add.</li>
 *   <li>Views: same-named views with different bodies are surfaced as
 *       {@link ViewBodyChange}.</li>
 *   <li>Comments: the body of the {@link DiffSettings#commentType()}
 *       Description on tables and columns, including those of added tables
 *       and columns.</li>
 * </ul>
 */
@Component(service = SchemaDiffer.class)
public final class SchemaDifferImpl implements SchemaDiffer {

    private static final System.Logger LOG = System.getLogger(SchemaDifferImpl.class.getName());

    @Override
    public SchemaDiff diff(Schema oldSchema, Schema newSchema) {
        return diff(oldSchema, newSchema, DiffSettings.defaults());
    }

    @Override
    public SchemaDiff diff(Schema oldSchema, Schema newSchema, DiffSettings settings) {
        if (oldSchema == null || newSchema == null) {
            throw new IllegalArgumentException("schemas must not be null");
        }
        Objects.requireNonNull(settings, "settings");

        List<Table> allOldTables = Schemas.tables(oldSchema);
        List<Table> allNewTables = Schemas.tables(newSchema);
        SplitMergeResult splitMerge = detectSplitsAndMerges(allOldTables, allNewTables, settings);
        List<Table> oldsForPairing = allOldTables.stream()
                .filter(t -> !splitMerge.consumedOld().contains(t)).toList();
        List<Table> newsForPairing = allNewTables.stream()
                .filter(t -> !splitMerge.consumedNew().contains(t)).toList();

        Pairing<Table> tables = pair(oldsForPairing, newsForPairing, settings);
        if (settings.useRenameHeuristic()) {
            foldTableRenameHeuristic(tables);
        }

        List<Table> tablesAdded = new ArrayList<>(tables.added);
        List<Table> tablesDropped = new ArrayList<>(tables.dropped);
        if (settings.scope() == DiffSettings.Scope.PARTIAL) {
            tablesAdded.clear();
        }

        List<TableRename> tablesRenamed = new ArrayList<>();
        List<TableDiff> tablesChanged = new ArrayList<>();
        List<CommentChange> comments = new ArrayList<>();
        for (Pair<Table> p : tables.paired) {
            if (!Objects.equals(p.oldE().getName(), p.newE().getName())) {
                tablesRenamed.add(new TableRename(p.oldE(), p.newE()));
            }
            compareComment(p.newE(), p.oldE(), p.newE(), settings, comments);
            TableDiff td = diffTable(oldSchema, newSchema, p.oldE(), p.newE(), settings, comments);
            if (!td.isEmpty()) {
                tablesChanged.add(td);
            }
        }

        Pairing<View> views = pair(Schemas.views(oldSchema), Schemas.views(newSchema), settings);
        List<View> viewsAdded = new ArrayList<>(views.added);
        List<View> viewsDropped = new ArrayList<>(views.dropped);
        if (settings.scope() == DiffSettings.Scope.PARTIAL) {
            viewsAdded.clear();
        }
        List<ViewBodyChange> viewsChanged = new ArrayList<>();
        for (Pair<View> p : views.paired) {
            if (!Objects.equals(Views.queryBody(p.oldE()).orElse(null),
                    Views.queryBody(p.newE()).orElse(null))) {
                viewsChanged.add(new ViewBodyChange(p.oldE(), p.newE()));
            }
        }

        for (Table t : tablesAdded) {
            compareComment(t, null, t, settings, comments);
            ColumnSets.columns(t).forEach(c -> compareComment(t, null, c, settings, comments));
        }
        for (TableSplit s : splitMerge.splits()) {
            for (Table t : s.newTables()) {
                compareComment(t, null, t, settings, comments);
                ColumnSets.columns(t).forEach(c -> compareComment(t, null, c, settings, comments));
            }
        }
        for (TableMerge m : splitMerge.merges()) {
            compareComment(m.newTable(), null, m.newTable(), settings, comments);
            ColumnSets.columns(m.newTable()).forEach(c -> compareComment(m.newTable(), null, c, settings, comments));
        }

        return new SchemaDiff(oldSchema, newSchema,
                tablesAdded, tablesDropped, viewsAdded, viewsDropped,
                tablesChanged, viewsChanged, tablesRenamed,
                splitMerge.splits(), splitMerge.merges(), comments);
    }

    // pairing

    private record Pair<E>(E oldE, E newE) {
    }

    private static final class Pairing<E> {
        final List<Pair<E>> paired = new ArrayList<>();
        final List<E> added = new ArrayList<>();
        final List<E> dropped = new ArrayList<>();
    }

    private record SplitMergeResult(List<TableSplit> splits, List<TableMerge> merges,
            Set<Table> consumedOld, Set<Table> consumedNew) {
    }

    /**
     * Detects clean table splits and merges from predecessor Dependency links
     * only (name equality and the {@code renamedFrom} marker cannot express
     * n:1/1:n, so they are not consulted here). A merge is a new table
     * claiming two or more old tables, none of which any other new table also
     * claims; a split is an old table claimed by two or more new tables, each
     * of which claims no other predecessor. Anything more tangled (e.g. a new
     * table's predecessor is itself split among others) is left for the
     * ordinary {@link #pair} claim logic to fall back on.
     */
    private static SplitMergeResult detectSplitsAndMerges(List<Table> olds, List<Table> news,
            DiffSettings settings) {
        List<TableSplit> splits = new ArrayList<>();
        List<TableMerge> merges = new ArrayList<>();
        Set<Table> consumedOld = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Table> consumedNew = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!settings.useDependencyLinks()) {
            return new SplitMergeResult(splits, merges, consumedOld, consumedNew);
        }

        Set<Table> oldSet = Collections.newSetFromMap(new IdentityHashMap<>());
        oldSet.addAll(olds);

        Map<Table, List<Table>> predsByNew = new LinkedHashMap<>();
        for (Table n : news) {
            List<Table> preds = new ArrayList<>();
            for (ModelElement pred : PredecessorLinks.predecessors(n)) {
                if (pred instanceof Table t && oldSet.contains(t) && !preds.contains(t)) {
                    preds.add(t);
                }
            }
            if (!preds.isEmpty()) {
                predsByNew.put(n, preds);
            }
        }
        Map<Table, List<Table>> newsByOld = new LinkedHashMap<>();
        predsByNew.forEach((n, preds) -> preds.forEach(
                o -> newsByOld.computeIfAbsent(o, k -> new ArrayList<>()).add(n)));

        for (Map.Entry<Table, List<Table>> e : predsByNew.entrySet()) {
            Table n = e.getKey();
            List<Table> preds = e.getValue();
            if (preds.size() > 1 && preds.stream().allMatch(o -> newsByOld.get(o).size() == 1)) {
                merges.add(new TableMerge(preds, n));
                consumedNew.add(n);
                consumedOld.addAll(preds);
            }
        }
        for (Map.Entry<Table, List<Table>> e : newsByOld.entrySet()) {
            Table o = e.getKey();
            if (consumedOld.contains(o)) {
                continue;
            }
            List<Table> claimants = e.getValue();
            if (claimants.size() > 1 && claimants.stream().allMatch(n -> predsByNew.get(n).size() == 1)) {
                splits.add(new TableSplit(o, claimants));
                consumedOld.add(o);
                consumedNew.addAll(claimants);
            }
        }
        return new SplitMergeResult(splits, merges, consumedOld, consumedNew);
    }

    /**
     * Resolves the old counterpart for every new element: predecessor
     * Dependency, then marker, then name — first source that answers wins.
     */
    private static <E extends ModelElement> Pairing<E> pair(List<E> olds, List<E> news,
            DiffSettings settings) {
        Map<String, E> oldByName = byName(olds);
        Set<E> oldSet = Collections.newSetFromMap(new IdentityHashMap<>());
        oldSet.addAll(olds);
        Set<E> claimed = Collections.newSetFromMap(new IdentityHashMap<>());

        Pairing<E> out = new Pairing<>();
        for (E n : news) {
            if (isBlank(n.getName())) {
                continue;
            }
            E old = resolveOld(n, oldSet, oldByName, settings);
            if (old != null && !claimed.contains(old)) {
                claimed.add(old);
                out.paired.add(new Pair<>(old, n));
            } else {
                if (old != null) {
                    LOG.log(System.Logger.Level.WARNING,
                            "predecessor of {0} already claimed - treating as added", n.getName());
                }
                out.added.add(n);
            }
        }
        for (E o : olds) {
            if (!isBlank(o.getName()) && !claimed.contains(o)) {
                out.dropped.add(o);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <E extends ModelElement> E resolveOld(E n, Set<E> oldSet,
            Map<String, E> oldByName, DiffSettings settings) {
        if (settings.useDependencyLinks()) {
            for (ModelElement pred : PredecessorLinks.predecessors(n)) {
                if (oldSet.contains(pred)) {
                    return (E) pred;
                }
            }
        }
        if (settings.useTagMarkers()) {
            Optional<String> marker = ChangeMarkers.renamedFrom(n);
            if (marker.isPresent()) {
                E byMarker = oldByName.get(marker.get());
                if (byMarker != null) {
                    return byMarker;
                }
                LOG.log(System.Logger.Level.WARNING,
                        "renamedFrom={0} on {1} has no counterpart in the old schema",
                        marker.get(), n.getName());
            }
        }
        return oldByName.get(n.getName());
    }

    /**
     * Conservative fallback: exactly one dropped + one added table with the
     * same column shape is a rename. Mutates the pairing.
     */
    private static void foldTableRenameHeuristic(Pairing<Table> tables) {
        if (tables.added.size() == 1 && tables.dropped.size() == 1
                && haveSameColumnShape(tables.dropped.get(0), tables.added.get(0))) {
            tables.paired.add(new Pair<>(tables.dropped.get(0), tables.added.get(0)));
            tables.added.clear();
            tables.dropped.clear();
        }
    }

    /**
     * Two tables match for rename purposes when they have the same column
     * names (in any order) and each pair has matching type/nullability — i.e.
     * the column set is preserved across the rename.
     */
    private static boolean haveSameColumnShape(Table a, Table b) {
        List<Column> ac = ColumnSets.columns(a);
        List<Column> bc = ColumnSets.columns(b);
        if (ac.size() != bc.size()) return false;
        Map<String, Column> bByName = bc.stream()
                .collect(LinkedHashMap::new, (m, c) -> m.put(c.getName(), c), Map::putAll);
        for (Column ca : ac) {
            Column cb = bByName.get(ca.getName());
            if (cb == null) return false;
            if (!compareColumn(ca, cb).isEmpty()) return false;
        }
        return true;
    }

    //per-table diff

    private static TableDiff diffTable(Schema oldSchema, Schema newSchema, Table oldTable, Table newTable,
            DiffSettings settings, List<CommentChange> comments) {
        Pairing<Column> cols = pair(ColumnSets.columns(oldTable), ColumnSets.columns(newTable), settings);

        List<Column> columnsAdded = new ArrayList<>(cols.added);
        List<Column> columnsDropped = new ArrayList<>(cols.dropped);
        if (settings.scope() == DiffSettings.Scope.PARTIAL) {
            columnsAdded.clear();
        }

        // Conservative rename fallback: exactly one dropped + one added column
        // with identical declaration is a rename.
        if (settings.useRenameHeuristic()
                && columnsAdded.size() == 1 && columnsDropped.size() == 1
                && compareColumn(columnsDropped.get(0), columnsAdded.get(0)).isEmpty()) {
            cols.paired.add(new Pair<>(columnsDropped.get(0), columnsAdded.get(0)));
            columnsAdded.clear();
            columnsDropped.clear();
        }

        List<ColumnRename> columnsRenamed = new ArrayList<>();
        List<ColumnChange> columnsChanged = new ArrayList<>();
        for (Pair<Column> p : cols.paired) {
            if (!Objects.equals(p.oldE().getName(), p.newE().getName())) {
                columnsRenamed.add(new ColumnRename(p.oldE(), p.newE()));
            }
            EnumSet<ColumnChange.Aspect> aspects = compareColumn(p.oldE(), p.newE());
            if (!aspects.isEmpty()) {
                columnsChanged.add(new ColumnChange(p.oldE(), p.newE(), aspects));
            }
            compareComment(newTable, p.oldE(), p.newE(), settings, comments);
        }
        columnsAdded.forEach(c -> compareComment(newTable, null, c, settings, comments));

        List<ConstraintRename> constraintsRenamed = new ArrayList<>();
        PrimaryKeyChange pkChange = comparePk(oldTable, newTable, constraintsRenamed);

        List<UniqueConstraint> ucsAdded = new ArrayList<>();
        List<UniqueConstraint> ucsDropped = new ArrayList<>();
        List<Pair<UniqueConstraint>> ucsRenamed = new ArrayList<>();
        diffKeyed(filterNonPk(Tables.uniqueConstraints(oldTable)),
                filterNonPk(Tables.uniqueConstraints(newTable)),
                settings, SchemaDifferImpl::uniqueShape, ucsAdded, ucsDropped, ucsRenamed);

        List<CheckConstraint> checksAdded = new ArrayList<>();
        List<CheckConstraint> checksDropped = new ArrayList<>();
        List<Pair<CheckConstraint>> checksRenamed = new ArrayList<>();
        diffKeyed(checkConstraintsOf(oldTable), checkConstraintsOf(newTable),
                settings, SchemaDifferImpl::checkShape, checksAdded, checksDropped, checksRenamed);

        List<ForeignKey> fksAdded = new ArrayList<>();
        List<ForeignKey> fksDropped = new ArrayList<>();
        List<Pair<ForeignKey>> fksRenamed = new ArrayList<>();
        diffKeyed(Tables.foreignKeys(oldTable), Tables.foreignKeys(newTable),
                settings, SchemaDifferImpl::foreignKeyShape, fksAdded, fksDropped, fksRenamed);

        List<SQLIndex> indexesAdded = new ArrayList<>();
        List<SQLIndex> indexesDropped = new ArrayList<>();
        List<Pair<SQLIndex>> indexPairs = new ArrayList<>();
        diffKeyed(indexesOf(oldSchema, oldTable), indexesOf(newSchema, newTable),
                settings, SchemaDifferImpl::indexShape, indexesAdded, indexesDropped, indexPairs);

        List<Trigger> triggersAdded = new ArrayList<>();
        List<Trigger> triggersDropped = new ArrayList<>();
        List<Pair<Trigger>> triggerPairs = new ArrayList<>();
        diffKeyed(oldTable.getTrigger(), newTable.getTrigger(), settings, SchemaDifferImpl::triggerShape,
                triggersAdded, triggersDropped, triggerPairs);
        // no portable trigger rename: re-create under the new name
        triggerPairs.forEach(r -> {
            triggersDropped.add(r.oldE());
            triggersAdded.add(r.newE());
        });

        ucsRenamed.forEach(r -> constraintsRenamed.add(new ConstraintRename(r.oldE(), r.newE())));
        checksRenamed.forEach(r -> constraintsRenamed.add(new ConstraintRename(r.oldE(), r.newE())));
        fksRenamed.forEach(r -> constraintsRenamed.add(new ConstraintRename(r.oldE(), r.newE())));
        List<IndexRename> indexesRenamed = new ArrayList<>();
        indexPairs.forEach(r -> indexesRenamed.add(new IndexRename(r.oldE(), r.newE())));

        return new TableDiff(oldTable, newTable,
                columnsAdded, columnsDropped, columnsChanged, columnsRenamed,
                pkChange,
                ucsAdded, ucsDropped,
                checksAdded, checksDropped,
                fksAdded, fksDropped,
                indexesAdded, indexesDropped,
                indexesRenamed, constraintsRenamed,
                triggersAdded, triggersDropped);
    }

    // field comparisons

    /**
     * Records a {@link CommentChange} when the comment of {@code newE} differs
     * from that of {@code oldE}; a {@code null} {@code oldE} is a new element,
     * which only counts when it carries a comment.
     */
    private static void compareComment(Table table, ModelElement oldE, ModelElement newE, DiffSettings settings,
            List<CommentChange> out) {
        if (settings.commentType() == null) {
            return;
        }
        String oldC = oldE == null ? null : comment(oldE, settings.commentType());
        String newC = comment(newE, settings.commentType());
        if (!Objects.equals(oldC, newC)) {
            out.add(new CommentChange(table, newE, oldC, newC));
        }
    }

    private static String comment(ModelElement e, String type) {
        return Descriptions.find(e, type).map(Description::getBody).orElse(null);
    }

    static EnumSet<ColumnChange.Aspect> compareColumn(Column oldC, Column newC) {
        EnumSet<ColumnChange.Aspect> out = EnumSet.noneOf(ColumnChange.Aspect.class);
        String oldType = typeName(oldC);
        String newType = typeName(newC);
        if (!Objects.equals(normalize(oldType), normalize(newType))) {
            out.add(ColumnChange.Aspect.TYPE);
        }
        long oldLen = oldC.getLength();
        long newLen = newC.getLength();
        long oldPrec = oldC.getPrecision();
        long newPrec = newC.getPrecision();
        long oldScale = oldC.getScale();
        long newScale = newC.getScale();
        // Pull size from the SQLSimpleType too — that's where the loader puts it.
        SqlTypeFacts oldFacts = simpleTypeFacts(oldC);
        SqlTypeFacts newFacts = simpleTypeFacts(newC);
        long oldEffMax = Math.max(oldLen, oldFacts.charMax);
        long newEffMax = Math.max(newLen, newFacts.charMax);
        long oldEffPrec = Math.max(oldPrec, oldFacts.numPrec);
        long newEffPrec = Math.max(newPrec, newFacts.numPrec);
        long oldEffScale = oldScale != 0 ? oldScale : oldFacts.numScale;
        long newEffScale = newScale != 0 ? newScale : newFacts.numScale;
        if (oldEffMax != newEffMax || oldEffPrec != newEffPrec || oldEffScale != newEffScale) {
            out.add(ColumnChange.Aspect.SIZE);
        }
        if (nullable(oldC) != nullable(newC)) {
            out.add(ColumnChange.Aspect.NULLABILITY);
        }
        if (!Objects.equals(defaultBody(oldC), defaultBody(newC))) {
            out.add(ColumnChange.Aspect.DEFAULT);
        }
        return out;
    }

    private record SqlTypeFacts(long charMax, long numPrec, long numScale) {
    }

    private static SqlTypeFacts simpleTypeFacts(Column c) {
        if (c.getType() instanceof SQLSimpleType s) {
            return new SqlTypeFacts(s.getCharacterMaximumLength(),
                    s.getNumericPrecision(), s.getNumericScale());
        }
        return new SqlTypeFacts(0, 0, 0);
    }

    private static String typeName(Column c) {
        if (c.getType() != null && c.getType().getName() != null) return c.getType().getName();
        return null;
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    private static boolean nullable(Column c) {
        return c.getIsNullable() != NullableType.COLUMN_NO_NULLS;
    }

    private static String defaultBody(Column c) {
        return Optional.ofNullable(c.getInitialValue()).map(e -> e.getBody()).orElse(null);
    }

    /**
     * A primary key whose columns stayed but whose name changed is a rename
     * (collected into {@code renamed}); a changed column list is a rebuild.
     */
    private static PrimaryKeyChange comparePk(Table oldT, Table newT, List<ConstraintRename> renamed) {
        PrimaryKey oldPk = Tables.findPrimaryKey(oldT).orElse(null);
        PrimaryKey newPk = Tables.findPrimaryKey(newT).orElse(null);
        if (oldPk == null && newPk == null) return null;
        if (oldPk == null || newPk == null) return new PrimaryKeyChange(oldPk, newPk);
        if (!pkColumnNames(oldPk).equals(pkColumnNames(newPk))) {
            return new PrimaryKeyChange(oldPk, newPk);
        }
        if (!isBlank(oldPk.getName()) && !isBlank(newPk.getName())
                && !Objects.equals(oldPk.getName(), newPk.getName())) {
            renamed.add(new ConstraintRename(oldPk, newPk));
        }
        return null;
    }

    private static List<String> pkColumnNames(PrimaryKey pk) {
        List<String> out = new ArrayList<>();
        for (var sf : pk.getFeature()) {
            out.add(sf.getName());
        }
        return out;
    }

    // keyed (index / constraint) diff

    /**
     * Pairs indexes or constraints through the same identity sources as tables
     * and columns (Dependency, marker, name). A pair whose {@code shape} differs
     * cannot be renamed and becomes drop + add; a pair with the same shape and
     * a different name is a rename. With the heuristic on, a leftover dropped
     * and added element of identical, unambiguous shape are folded into a
     * rename as well.
     */
    private static <E extends ModelElement> void diffKeyed(List<E> olds, List<E> news, DiffSettings settings,
            Function<E, Object> shape, List<E> added, List<E> dropped, List<Pair<E>> renamed) {
        Pairing<E> pairing = pair(olds, news, settings);
        List<E> adds = new ArrayList<>(pairing.added);
        List<E> drops = new ArrayList<>(pairing.dropped);
        for (Pair<E> p : pairing.paired) {
            if (!Objects.equals(shape.apply(p.oldE()), shape.apply(p.newE()))) {
                drops.add(p.oldE());
                adds.add(p.newE());
            } else if (!Objects.equals(p.oldE().getName(), p.newE().getName())) {
                renamed.add(p);
            }
        }
        if (settings.useRenameHeuristic()) {
            for (E d : List.copyOf(drops)) {
                Object s = shape.apply(d);
                List<E> sameShapeAdds = adds.stream().filter(a -> Objects.equals(shape.apply(a), s)).toList();
                long sameShapeDrops = drops.stream().filter(o -> Objects.equals(shape.apply(o), s)).count();
                if (sameShapeAdds.size() == 1 && sameShapeDrops == 1) {
                    renamed.add(new Pair<>(d, sameShapeAdds.get(0)));
                    drops.remove(d);
                    adds.remove(sameShapeAdds.get(0));
                }
            }
        }
        added.addAll(adds);
        dropped.addAll(drops);
    }

    private static Object indexShape(SQLIndex i) {
        List<String> cols = new ArrayList<>();
        for (var ifc : i.getIndexedFeature()) {
            cols.add(ifc.getFeature() == null ? null : ifc.getFeature().getName());
        }
        return List.of(cols, i.isIsUnique());
    }

    private static Object uniqueShape(UniqueConstraint uc) {
        return UniqueConstraints.columns(uc).stream().map(Column::getName).toList();
    }

    private static Object checkShape(CheckConstraint ck) {
        String body = ck.getBody() == null ? null : ck.getBody().getBody();
        return body == null ? "" : normalize(body);
    }

    private static Object triggerShape(Trigger t) {
        return Arrays.asList(
                t.getConditionTiming() == null ? null : t.getConditionTiming().getName(),
                t.getEventManipulation() == null ? null : t.getEventManipulation().getName(),
                t.getActionOrientation() == null ? null : t.getActionOrientation().getName(),
                t.getActionCondition() == null ? null : t.getActionCondition().getBody(),
                t.getActionStatement() == null ? null : t.getActionStatement().getBody());
    }

    private static Object foreignKeyShape(ForeignKey fk) {
        String target = ForeignKeys.targetTable(fk).map(Table::getName).orElse(null);
        List<String> refCols = fk.getUniqueKey() == null ? List.of()
                : fk.getUniqueKey().getFeature().stream().map(f -> f.getName()).toList();
        return Arrays.asList(ForeignKeys.columns(fk).stream().map(Column::getName).toList(), target, refCols);
    }

    private static <E extends ModelElement> Map<String, E> byName(List<E> list) {
        Map<String, E> out = new LinkedHashMap<>();
        for (E e : list) {
            String n = e.getName();
            if (n == null || n.isBlank()) continue;
            out.putIfAbsent(n, e);
        }
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static List<UniqueConstraint> filterNonPk(List<UniqueConstraint> in) {
        List<UniqueConstraint> out = new ArrayList<>();
        for (UniqueConstraint uc : in) {
            if (!(uc instanceof PrimaryKey)) out.add(uc);
        }
        return out;
    }

    private static List<CheckConstraint> checkConstraintsOf(Table table) {
        List<CheckConstraint> out = new ArrayList<>();
        Namespaces.ownedElementStream(table, CheckConstraint.class).forEach(out::add);
        for (Column col : ColumnSets.columns(table)) {
            col.getConstraint().stream()
                    .filter(CheckConstraint.class::isInstance)
                    .map(CheckConstraint.class::cast)
                    .filter(c -> !out.contains(c))
                    .forEach(out::add);
        }
        return out;
    }

    private static List<SQLIndex> indexesOf(Schema schema, Table table) {
        List<SQLIndex> out = new ArrayList<>();
        Namespaces.ownedElementStream(schema, SQLIndex.class)
                .filter(i -> i.getSpannedClass() == table)
                .forEach(out::add);
        return out;
    }
}
