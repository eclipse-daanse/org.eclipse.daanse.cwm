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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * What becomes of a reference from an element a {@link Selections#copy} keeps
 * to an element it leaves out. The copy asks its rules about every such
 * reference, in order; the first rule that answers decides, and when none
 * answers the reference is unset.
 *
 * <p>
 * The copy asks in two phases (see {@link Selections#copy}): while it grows,
 * only {@link Consequence#RETAIN} has an effect; while it is pruned, every
 * answer but {@code RETAIN} has. A rule need not know the phase, but it is
 * shown the elements of the original model in the first and those of the copy
 * in the second: it should look at kinds, references and values, not compare
 * with elements it holds. While pruning, the container of an element left out
 * is asked too, through the containment reference; there {@code REMOVE}
 * leaves the container out as well, any other answer lets the element go.
 * </p>
 *
 * <p>
 * The model modules add rules of their own (a foreign key without its
 * referenced key, a privilege without its table); they put them in front of
 * {@link #CORE}, so their more specific answer comes first.
 * </p>
 */
@FunctionalInterface
public interface ConsequenceRule {

    /** The rules every model has: {@link #brokenDependencies()}, then {@link #requiredReferences()}. */
    List<ConsequenceRule> CORE = List.of(brokenDependencies(), requiredReferences());

    /** The answer of a rule. */
    enum Consequence {

        /** The referring element is left out as well, with everything it contains. */
        REMOVE,

        /** The reference no longer names the left-out element; the referring element stays. */
        UNSET,

        /**
         * The element referred to stays in the copy after all, with its contents
         * that no exclude rule reaches and with its owners as containers: the
         * referring element needs it. This answer counts only for an element left
         * out because no include rule reaches it. An element an exclude rule
         * reaches is never brought back; for it, and for an element a
         * consequence removed, {@code RETAIN} counts as no answer and the next
         * rule decides.
         */
        RETAIN
    }

    /** One reference from an element the copy keeps to an element it leaves out. */
    interface Loss {

        /** The element that refers. */
        EObject referrer();

        /** The reference through which it refers. */
        EReference reference();

        /** The element left out. */
        EObject target();

        /**
         * Whether an exclude rule reaches the target, so that it is left out
         * for good; {@code false} when it is left out only because no include
         * rule reaches it, or because a consequence removed it.
         */
        boolean targetExcluded();

        /** The values of the reference that stay in the copy, as far as known so far. */
        List<EObject> remaining();

        /** Whether the copy leaves {@code object} out, as far as known so far. */
        boolean isRemoved(EObject object);
    }

    /** The consequence for {@code loss}, or empty when this rule has nothing to say about it. */
    Optional<Consequence> decide(Loss loss);

    /**
     * How the rule reads in the report of a removal. A plain lambda reads as
     * {@code custom consequence}; {@link #of} names it.
     */
    default String describe() {
        return "custom consequence";
    }

    /**
     * The rules of {@code lists} in order, each once, with the rules of
     * {@link #CORE} moved to the end. A model module lists its own rules
     * first, then the lists of the modules it builds on.
     */
    @SafeVarargs
    static List<ConsequenceRule> combine(List<? extends ConsequenceRule>... lists) {
        List<ConsequenceRule> combined = new ArrayList<>();
        for (List<? extends ConsequenceRule> list : lists) {
            for (ConsequenceRule rule : list) {
                if (!CORE.contains(rule) && !combined.contains(rule)) {
                    combined.add(Objects.requireNonNull(rule, "rule"));
                }
            }
        }
        combined.addAll(CORE);
        return List.copyOf(combined);
    }

    /** A rule from a function, reading as {@code description} in reports. */
    static ConsequenceRule of(String description, Function<? super Loss, Optional<Consequence>> decide) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(decide, "decide");
        return new ConsequenceRule() {

            @Override
            public Optional<Consequence> decide(Loss loss) {
                return decide.apply(loss);
            }

            @Override
            public String describe() {
                return description;
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    /**
     * An element whose required reference (lower bound one or more) would
     * keep fewer values than the bound asks for is removed; it would not be a
     * valid element of its kind any more. Below the bound means
     * {@link Consequence#REMOVE}, otherwise no answer.
     */
    static ConsequenceRule requiredReferences() {
        return of("required reference", loss -> {
            int lowerBound = loss.reference().getLowerBound();
            return lowerBound > 0 && loss.remaining().size() < lowerBound
                    ? Optional.of(Consequence.REMOVE)
                    : Optional.empty();
        });
    }

    /**
     * A dependency that loses a supplier is removed, even when other
     * suppliers remain: it no longer says what it said. Its clients stay and
     * just no longer name it. In CWM the kind of a dependency is free text and
     * does not tell whether a client can exist without its supplier; where a
     * kind does, {@link #dependentClients(String)} says so.
     */
    static ConsequenceRule brokenDependencies() {
        return of("dependency on a removed supplier",
                loss -> loss.reference() == CorePackage.Literals.DEPENDENCY__SUPPLIER
                        && loss.referrer() instanceof Dependency
                                ? Optional.of(Consequence.REMOVE)
                                : Optional.empty());
    }

    /**
     * A dependency of {@code kind} that loses a supplier is removed, and so
     * are its clients: for this kind, a client cannot exist without what it
     * depends on. A dependency left out for another reason, while its
     * suppliers stay, removes no client; the client just no longer names it.
     * Not part of {@link #CORE}; a model module or a caller that knows the
     * meaning of {@code kind} puts it in front.
     */
    static ConsequenceRule dependentClients(String kind) {
        Objects.requireNonNull(kind, "kind");
        return of("dependency of kind " + kind + " on a removed supplier", loss -> {
            if (loss.reference() == CorePackage.Literals.DEPENDENCY__SUPPLIER
                    && loss.referrer() instanceof Dependency dependency && kind.equals(dependency.getKind())) {
                return Optional.of(Consequence.REMOVE);
            }
            if (loss.reference() == CorePackage.Literals.MODEL_ELEMENT__CLIENT_DEPENDENCY
                    && loss.target() instanceof Dependency dependency && kind.equals(dependency.getKind())
                    && dependency.getSupplier().stream().anyMatch(loss::isRemoved)) {
                return Optional.of(Consequence.REMOVE);
            }
            return Optional.empty();
        });
    }

    /** Every loss through {@code reference} has the consequence {@code consequence}. */
    static ConsequenceRule on(EReference reference, Consequence consequence) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(consequence, "consequence");
        String owner = reference.getEContainingClass() == null ? "" : reference.getEContainingClass().getName() + ".";
        return of(owner + reference.getName() + " " + consequence.name().toLowerCase(Locale.ROOT),
                loss -> loss.reference() == reference ? Optional.of(consequence) : Optional.empty());
    }
}
