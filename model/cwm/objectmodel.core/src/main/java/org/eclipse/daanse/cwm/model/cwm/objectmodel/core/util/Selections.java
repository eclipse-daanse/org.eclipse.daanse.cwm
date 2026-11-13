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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule.Consequence;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule.Loss;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.Retained;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection.Verdict;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Applies a {@link Selection} to a model: lists the selected elements, or cuts
 * a copy, or the model itself, down to them. A walk asks the rules once per
 * element, carrying what the owners were found to be down the containment
 * tree; it answers as {@link Selection#selects} and
 * {@link Selection#excludedBy} do for one element.
 */
public final class Selections {

    /** The reason of an element left out because include rules exist and none reaches it. */
    public static final String NOT_INCLUDED = "not included";

    private static final String NEEDED_BY = "needed by ";
    private static final String BRANDS = "brands ";

    private Selections() {
    }

    /**
     * The selected elements of {@code type} in {@code root}'s containment tree,
     * {@code root} included, depth first in containment order. Owners that
     * stay in a copy only as containers (rule 3 of {@link Selection}) and
     * elements a copy retains for a consequence are a matter of
     * {@link #copy}; they are not selected and not listed.
     */
    public static <T extends ModelElement> Stream<T> stream(ModelElement root, Selection selection, Class<T> type) {
        return stream(root, selection, type, false);
    }

    /** List-returning twin of {@link #stream(ModelElement, Selection, Class)}. */
    public static <T extends ModelElement> List<T> list(ModelElement root, Selection selection, Class<T> type) {
        return stream(root, selection, type).toList();
    }

    /**
     * As {@link #stream(ModelElement, Selection, Class)}; with
     * {@code withImports} the walk also enters what a package imports, after
     * what it owns, as {@link Packages#available} reads a package. Each element
     * is listed once. An imported element is judged where it is owned, not where
     * it is imported.
     */
    public static <T extends ModelElement> Stream<T> stream(ModelElement root, Selection selection, Class<T> type,
            boolean withImports) {
        if (root == null || selection == null || type == null) {
            return Stream.empty();
        }
        List<T> found = new ArrayList<>();
        walk(root, selection.verdictOf(root), selection, type, withImports,
                Collections.newSetFromMap(new IdentityHashMap<>()), found);
        return found.stream();
    }

    /** List-returning twin of {@link #stream(ModelElement, Selection, Class, boolean)}. */
    public static <T extends ModelElement> List<T> list(ModelElement root, Selection selection, Class<T> type,
            boolean withImports) {
        return stream(root, selection, type, withImports).toList();
    }

    /** {@link #copy(ModelElement, Selection, List)} with the {@link ConsequenceRule#CORE core consequences}. */
    public static <T extends ModelElement> Selected<T> copy(T root, Selection selection) {
        return copy(root, selection, ConsequenceRule.CORE);
    }

    /**
     * A copy of {@code root} cut down to {@code selection}, with what it
     * leaves out and what it retains.
     *
     * <p>
     * The selection is judged on the original model. What stays are the
     * selected elements and their owners, {@code root} always included. Then
     * two phases, each only ever adding to what it has decided, so each ends:
     * </p>
     * <ol>
     * <li><b>Grow</b>, on the original. Every reference from an element that
     * stays to one left out only because no include rule reaches it is put to
     * {@code consequences}. When the first rule that answers says
     * {@link Consequence#RETAIN}, the element stays after all, with its
     * contents that no exclude rule reaches and with its owners; their
     * references are asked about in turn, until nothing is added. A
     * stereotype that brands an element that stays comes along the same way,
     * without asking: in CWM the brand is part of what the element is, and in
     * the Ecore mapping the element does not refer to its stereotype (that end
     * is hidden), so no rule would see the need.</li>
     * <li><b>Prune</b>, on the copy. Every element that does not stay is left
     * out with all it contains. Every reference from what is left to what is
     * left out is put to {@code consequences}: {@link Consequence#REMOVE}
     * leaves the referring element out too, and its loss is asked about in
     * turn, until nothing is removed; {@link Consequence#UNSET}, no answer, and
     * now also {@code RETAIN} unset the reference. The container of an element
     * left out in its own right is asked too, through the containment
     * reference: {@code REMOVE} leaves the container out as well (an index
     * that loses one of its index columns), any other answer just lets the
     * element go.</li>
     * </ol>
     *
     * <p>
     * The original is not touched: no element, no reference, no adapter. The
     * copy belongs to no resource; references to elements outside
     * {@code root} name the original ones, as {@link EcoreUtil#copy} does.
     * References that are derived or cannot be changed are not asked about.
     * </p>
     */
    public static <T extends ModelElement> Selected<T> copy(T root, Selection selection,
            List<? extends ConsequenceRule> consequences) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(consequences, "consequences");
        List<? extends ConsequenceRule> rules = List.copyOf(consequences);

        Judgement judgement = judge(root, selection, rules);

        EcoreUtil.Copier copier = new EcoreUtil.Copier();
        @SuppressWarnings("unchecked")
        T copy = (T) copier.copy(root);
        copier.copyReferences();
        Map<EObject, EObject> originals = new IdentityHashMap<>();
        copier.forEach((original, copied) -> originals.put(copied, original));

        Pruning pruning = new Pruning(copy, rules, originals::get, judgement);
        for (EObject child : root.eContents()) {
            leaveOut(child, judgement, copier::get, pruning);
        }
        pruning.settle();
        return new Selected<>(copy, pruning.leftOut, judgement.retained);
    }

    /**
     * As {@link #copy(ModelElement, Selection, List)}, but cuts {@code root}
     * itself instead of a copy: for a model its caller owns alone, such as one
     * just read from a database, where a copy would only double it. The two
     * phases are the same, and so are what stays, what is left out and what is
     * retained. What is left out is detached from the model at the end, and
     * references from what stays to it are unset. Elements outside
     * {@code root} that refer into it are not asked about and keep their
     * references; nothing outside should refer to such a model.
     *
     * @return {@code root}, cut, with what was left out and what was retained
     */
    public static <T extends ModelElement> Selected<T> cut(T root, Selection selection,
            List<? extends ConsequenceRule> consequences) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(consequences, "consequences");
        List<? extends ConsequenceRule> rules = List.copyOf(consequences);

        Judgement judgement = judge(root, selection, rules);

        Pruning pruning = new Pruning(root, rules, Function.identity(), judgement);
        for (EObject child : root.eContents()) {
            leaveOut(child, judgement, Function.identity(), pruning);
        }
        pruning.settle();
        return new Selected<>(root, pruning.leftOut, judgement.retained);
    }

    /** The verdicts on {@code root}'s tree and, grown by the consequences, what stays of it. */
    private static Judgement judge(ModelElement root, Selection selection, List<? extends ConsequenceRule> rules) {
        Judgement judgement = new Judgement(selection);
        judgement.judge(root, selection.verdictOf(root));
        judgement.kept.add(root);
        judgement.grow(root, rules);
        return judgement;
    }

    private static <T extends ModelElement> void walk(EObject node, Verdict verdict, Selection selection,
            Class<T> type, boolean withImports, Set<EObject> visited, List<T> found) {
        if (verdict.excluded() || !visited.add(node)) {
            return;
        }
        if (node instanceof ModelElement element && verdict.selected() && type.isInstance(element)) {
            found.add(type.cast(element));
        }
        for (EObject child : node.eContents()) {
            walk(child, child instanceof ModelElement element ? selection.below(verdict, element) : verdict,
                    selection, type, withImports, visited, found);
        }
        if (withImports && node instanceof Package pkg) {
            for (ModelElement imported : pkg.getImportedElement()) {
                walk(imported, selection.verdictOf(imported), selection, type, withImports, visited, found);
            }
        }
    }

    /** Leaves out what does not stay; {@code pruned} gives the object in the model that is pruned. */
    private static void leaveOut(EObject original, Judgement judgement, Function<EObject, EObject> pruned,
            Pruning pruning) {
        if (original instanceof ModelElement element && !judgement.kept.contains(element)) {
            ElementRule rule = judgement.verdicts.get(element).excludedBy();
            pruning.remove(pruned.apply(element), rule == null ? NOT_INCLUDED : rule.describe());
            return;
        }
        for (EObject child : original.eContents()) {
            leaveOut(child, judgement, pruned, pruning);
        }
    }

    /** Whether a reference is put to the consequence rules at all; a containment only while pruning. */
    private static boolean asked(EReference reference, boolean containment) {
        return (containment || !reference.isContainment()) && !reference.isContainer() && !reference.isDerived()
                && reference.isChangeable();
    }

    private static List<EObject> values(EObject holder, EReference reference) {
        Object value = holder.eGet(reference, false);
        if (reference.isMany()) {
            return ((List<?>) value).stream().map(EObject.class::cast).toList();
        }
        return value == null ? List.of() : List.of((EObject) value);
    }

    /** The first answer of {@code rules} to {@code loss}; a {@code RETAIN} only counts when {@code retainCounts}. */
    private static Optional<Answer> ask(List<? extends ConsequenceRule> rules, Loss loss, boolean retainCounts) {
        for (ConsequenceRule rule : rules) {
            Optional<Consequence> consequence = rule.decide(loss);
            if (consequence.isPresent() && (retainCounts || consequence.get() != Consequence.RETAIN)) {
                return Optional.of(new Answer(consequence.get(), rule));
            }
        }
        return Optional.empty();
    }

    private record Answer(Consequence consequence, ConsequenceRule rule) {
    }

    /** The verdicts on the original model and what stays of it; grows by {@link Consequence#RETAIN}. */
    private static final class Judgement {

        private final Selection selection;
        private final Map<ModelElement, Verdict> verdicts = new IdentityHashMap<>();
        private final Set<EObject> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<ModelElement, List<Stereotype>> brands = new IdentityHashMap<>();
        private final List<Retained> retained = new ArrayList<>();

        Judgement(Selection selection) {
            this.selection = selection;
        }

        /** Records the verdict on every model element; whether {@code node} stays or holds what stays. */
        boolean judge(EObject node, Verdict verdict) {
            if (node instanceof ModelElement element) {
                verdicts.put(element, verdict);
            }
            if (node instanceof Stereotype stereotype) {
                stereotype.getExtendedElement()
                        .forEach(e -> brands.computeIfAbsent(e, k -> new ArrayList<>()).add(stereotype));
            }
            boolean holdsKept = false;
            for (EObject child : node.eContents()) {
                Verdict below = child instanceof ModelElement element ? selection.below(verdict, element) : verdict;
                holdsKept |= judge(child, below);
            }
            boolean stays = holdsKept || node instanceof ModelElement && verdict.selected();
            if (stays && node instanceof ModelElement) {
                kept.add(node);
            }
            return stays;
        }

        void grow(ModelElement root, List<? extends ConsequenceRule> rules) {
            Deque<ModelElement> work = new ArrayDeque<>();
            work.add(root);
            for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
                if (it.next() instanceof ModelElement element && kept.contains(element)) {
                    work.add(element);
                }
            }
            while (!work.isEmpty()) {
                ModelElement element = work.removeFirst();
                withParts(element, part -> scan(part, rules, work));
                for (Stereotype stereotype : brands.getOrDefault(element, List.of())) {
                    if (!kept.contains(stereotype) && !verdicts.get(stereotype).excluded()) {
                        retain(stereotype, BRANDS + QualifiedNames.of(element), work);
                    }
                }
            }
        }

        /** {@code element} and what it holds that is no model element of its own, such as tagged values. */
        private static void withParts(EObject element, Consumer<EObject> action) {
            action.accept(element);
            for (EObject child : element.eContents()) {
                if (!(child instanceof ModelElement)) {
                    withParts(child, action);
                }
            }
        }

        /** Puts every reference from {@code referrer} to an element left out only as not included to the rules. */
        private void scan(EObject referrer, List<? extends ConsequenceRule> rules, Deque<ModelElement> work) {
            for (EReference reference : referrer.eClass().getEAllReferences()) {
                if (!asked(reference, false) || !referrer.eIsSet(reference)) {
                    continue;
                }
                for (EObject value : values(referrer, reference)) {
                    if (!(value instanceof ModelElement target) || kept.contains(target)) {
                        continue;
                    }
                    Verdict verdict = verdicts.get(target);
                    if (verdict == null || verdict.excluded()) {
                        // outside the copied tree, or left out for good
                        continue;
                    }
                    Loss loss = new GrowingLoss(referrer, reference, target);
                    Optional<Answer> answer = Selections.ask(rules, loss, true);
                    if (answer.isPresent() && answer.get().consequence() == Consequence.RETAIN) {
                        retain(target, NEEDED_BY + QualifiedNames.of(referrer) + "." + reference.getName(), work);
                    }
                }
            }
        }

        private void retain(ModelElement target, String reason, Deque<ModelElement> work) {
            retained.add(new Retained(QualifiedNames.of(target), target.eClass().getName(), reason));
            keep(target, work);
            for (TreeIterator<EObject> it = target.eAllContents(); it.hasNext();) {
                if (it.next() instanceof ModelElement part) {
                    if (verdicts.get(part).excluded()) {
                        it.prune();
                    } else {
                        keep(part, work);
                    }
                }
            }
            QualifiedNames.owners(target).takeWhile(verdicts::containsKey).forEach(owner -> keep(owner, work));
        }

        private void keep(ModelElement element, Deque<ModelElement> work) {
            if (kept.add(element)) {
                work.add(element);
            }
        }

        boolean excluded(EObject original) {
            ModelElement element = judged(original);
            return element != null && verdicts.get(element).excluded();
        }

        /** The model element of the copied tree that decides whether {@code object} stays: itself or its owner. */
        private ModelElement judged(EObject object) {
            for (EObject o = object; o != null; o = o.eContainer()) {
                if (o instanceof ModelElement element && verdicts.containsKey(element)) {
                    return element;
                }
            }
            return null;
        }

        /** A loss as the original stands while the copy grows. */
        private final class GrowingLoss implements Loss {

            private final EObject referrer;
            private final EReference reference;
            private final EObject target;

            GrowingLoss(EObject referrer, EReference reference, EObject target) {
                this.referrer = referrer;
                this.reference = reference;
                this.target = target;
            }

            @Override
            public EObject referrer() {
                return referrer;
            }

            @Override
            public EReference reference() {
                return reference;
            }

            @Override
            public EObject target() {
                return target;
            }

            @Override
            public boolean targetExcluded() {
                return false;
            }

            @Override
            public List<EObject> remaining() {
                return values(referrer, reference).stream().filter(v -> !isRemoved(v)).toList();
            }

            @Override
            public boolean isRemoved(EObject object) {
                ModelElement element = judged(object);
                return element != null && !kept.contains(element);
            }
        }
    }

    /** One reference to unset once the removals are settled. */
    private record Fix(EObject referrer, EReference reference, EObject target) {
    }

    /** Removes elements from the copy and follows the consequences until nothing more is removed. */
    private static final class Pruning {

        private final EObject root;
        private final List<? extends ConsequenceRule> consequences;
        private final Function<EObject, EObject> originals;
        private final Judgement judgement;
        private final Set<EObject> gone = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Deque<EObject> work = new ArrayDeque<>();
        private final List<EObject> removed = new ArrayList<>();
        private final List<LeftOut> leftOut = new ArrayList<>();
        private final List<Fix> fixes = new ArrayList<>();

        /** {@code originals} gives the original of an object of the pruned model, or {@code null}. */
        Pruning(EObject root, List<? extends ConsequenceRule> consequences, Function<EObject, EObject> originals,
                Judgement judgement) {
            this.root = root;
            this.consequences = consequences;
            this.originals = originals;
            this.judgement = judgement;
        }

        /** Leaves {@code object} out with all it contains, unless it is the root or already gone. */
        void remove(EObject object, String reason) {
            if (object == null || object == root || gone.contains(object)) {
                return;
            }
            leftOut.add(new LeftOut(pathOf(object), object.eClass().getName(), reason));
            removed.add(object);
            mark(object);
            for (TreeIterator<EObject> it = object.eAllContents(); it.hasNext();) {
                mark(it.next());
            }
        }

        /** Asks the consequences until nothing more is removed, then cuts the copy. */
        void settle() {
            ECrossReferenceAdapter index = new ECrossReferenceAdapter();
            root.eAdapters().add(index);
            try {
                while (!work.isEmpty()) {
                    EObject target = work.removeFirst();
                    for (EStructuralFeature.Setting setting : List.copyOf(index.getInverseReferences(target, false))) {
                        consider(setting, target);
                    }
                }
            } finally {
                root.eAdapters().remove(index);
            }
            fixes.forEach(this::unset);
            for (EObject object : removed) {
                EObject container = object.eContainer();
                if (container != null && !gone.contains(container)) {
                    EcoreUtil.remove(object);
                }
            }
        }

        private void consider(EStructuralFeature.Setting setting, EObject target) {
            if (!(setting.getEStructuralFeature() instanceof EReference reference) || !asked(reference, true)) {
                return;
            }
            EObject referrer = setting.getEObject();
            if (gone.contains(referrer)) {
                return;
            }
            // every target is gone for good now: RETAIN counts as no answer
            Loss loss = new PendingLoss(referrer, reference, target);
            Optional<Answer> answer = Selections.ask(consequences, loss, false);
            if (answer.isPresent() && answer.get().consequence() == Consequence.REMOVE && referrer != root) {
                remove(referrer, answer.get().rule().describe() + " (" + reference.getName() + ": "
                        + pathOf(target) + ")");
            } else if (!reference.isContainment()) {
                // a contained element just goes with the detachment below
                fixes.add(new Fix(referrer, reference, target));
            }
        }

        @SuppressWarnings("unchecked")
        private void unset(Fix fix) {
            if (gone.contains(fix.referrer())) {
                return;
            }
            if (fix.reference().isMany()) {
                ((List<Object>) fix.referrer().eGet(fix.reference(), false)).remove(fix.target());
            } else if (fix.referrer().eGet(fix.reference(), false) == fix.target()) {
                fix.referrer().eUnset(fix.reference());
            }
        }

        private void mark(EObject object) {
            if (gone.add(object)) {
                work.add(object);
            }
        }

        /** The qualified name in the original model, which may reach above the copied root. */
        private String pathOf(EObject copied) {
            EObject original = originals.apply(copied);
            return QualifiedNames.of(original != null ? original : copied);
        }

        /** A loss as the copy stands while it is pruned. */
        private final class PendingLoss implements Loss {

            private final EObject referrer;
            private final EReference reference;
            private final EObject target;

            PendingLoss(EObject referrer, EReference reference, EObject target) {
                this.referrer = referrer;
                this.reference = reference;
                this.target = target;
            }

            @Override
            public EObject referrer() {
                return referrer;
            }

            @Override
            public EReference reference() {
                return reference;
            }

            @Override
            public EObject target() {
                return target;
            }

            @Override
            public boolean targetExcluded() {
                EObject original = originals.apply(target);
                return original != null && judgement.excluded(original);
            }

            @Override
            public List<EObject> remaining() {
                return values(referrer, reference).stream().filter(v -> !gone.contains(v)).toList();
            }

            @Override
            public boolean isRemoved(EObject object) {
                return gone.contains(object);
            }

            @Override
            public String toString() {
                return pathOf(referrer) + "." + reference.getName() + " -> " + pathOf(target);
            }
        }
    }
}
