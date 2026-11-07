/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingFactory;

/**
 * Creates historisation rules: in the {@code ownedElement} of {@code owner}, named,
 * constraining the given elements, its {@code body} set to the rule's text; the modifiers name the
 * features and the classifier that hold the history and keep the body in step.
 *
 * <p>A rule on classifiers acts on their rows ({@link #rows}): new versions with their validity, a
 * history table, snapshots. A rule on attributes acts on their values ({@link #values}): overwrite,
 * a new version on change, the previous or the current value in a column of its own.</p>
 */
public final class HistorisationRules {

    private HistorisationRules() {
    }

    /** How the rows of the governed classifiers keep their history. */
    public static HistorisationRule rows(Namespace owner, String name, HistorisationKind kind,
            ModelElement... classifiers) {
        HistorisationRule rule = create(kind);
        rule.setScope(DataScope.ROW);
        return refreshed(LifecycleRules.attach(owner, name, rule, classifiers));
    }

    /** What a change of the governed attributes does to the history. */
    public static HistorisationRule values(Namespace owner, String name, HistorisationKind kind,
            ModelElement... attributes) {
        return refreshed(LifecycleRules.attach(owner, name, create(kind), attributes));
    }

    private static HistorisationRule create(HistorisationKind kind) {
        HistorisationRule rule = RecordingFactory.eINSTANCE.createHistorisationRule();
        rule.setKind(Objects.requireNonNull(kind, "kind"));
        return rule;
    }

    /** Names the features that hold the validity of a version; {@code validTo} may be {@code null}. */
    public static HistorisationRule validity(HistorisationRule rule, StructuralFeature validFrom,
            StructuralFeature validTo) {
        rule.setValidFrom(Objects.requireNonNull(validFrom, "validFrom"));
        rule.setValidTo(validTo);
        return refreshed(rule);
    }

    /** Names the feature that marks the current version. */
    public static HistorisationRule currentFlag(HistorisationRule rule, StructuralFeature currentFlag) {
        rule.setCurrentFlag(Objects.requireNonNull(currentFlag, "currentFlag"));
        return refreshed(rule);
    }

    /** Names the feature that holds the previous value of the governed attribute. */
    public static HistorisationRule previousValue(HistorisationRule rule, StructuralFeature previousValue) {
        rule.setPreviousValue(Objects.requireNonNull(previousValue, "previousValue"));
        return refreshed(rule);
    }

    /** Names the feature that holds the current value of the governed attribute in every version. */
    public static HistorisationRule currentValue(HistorisationRule rule, StructuralFeature currentValue) {
        rule.setCurrentValue(Objects.requireNonNull(currentValue, "currentValue"));
        return refreshed(rule);
    }

    /** Names the classifier that holds the history rows. */
    public static HistorisationRule history(HistorisationRule rule, Classifier history) {
        rule.setHistory(Objects.requireNonNull(history, "history"));
        return refreshed(rule);
    }

    /** The historisation rule of {@code scope} that applies to {@code element}: the nearest one along its path. */
    public static Optional<HistorisationRule> effective(ModelElement element, DataScope scope) {
        return LifecycleRules.nearest(element, HistorisationRule.class, scope);
    }

    /**
     * The text of a historisation rule in the language {@value LifecycleRules#LANGUAGE}, e.g.
     * {@code rows: newVersion; valid from validFrom to validTo; current isCurrent}.
     */
    public static String render(HistorisationRule rule) {
        Objects.requireNonNull(rule, "rule");
        List<String> parts = new ArrayList<>();
        parts.add(rule.getScope().getLiteral() + "s: " + rule.getKind().getLiteral());
        if (rule.getValidFrom() != null) {
            parts.add("valid from " + rule.getValidFrom().getName()
                    + (rule.getValidTo() != null ? " to " + rule.getValidTo().getName() : ""));
        }
        if (rule.getCurrentFlag() != null) {
            parts.add("current " + rule.getCurrentFlag().getName());
        }
        if (rule.getPreviousValue() != null) {
            parts.add("previous in " + rule.getPreviousValue().getName());
        }
        if (rule.getCurrentValue() != null) {
            parts.add("current value in " + rule.getCurrentValue().getName());
        }
        if (rule.getHistory() != null) {
            parts.add("history in " + rule.getHistory().getName());
        }
        return String.join("; ", parts);
    }

    /** Sets the {@code body} of a historisation rule to its {@linkplain #render text}. */
    public static HistorisationRule refreshed(HistorisationRule rule) {
        LifecycleRules.setBody(rule, render(rule));
        return rule;
    }
}
