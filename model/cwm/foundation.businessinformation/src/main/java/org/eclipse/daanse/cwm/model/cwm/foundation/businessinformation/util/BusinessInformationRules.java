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
package org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule.Consequence;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.RuleVocabulary;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;

/**
 * The {@link ElementRule}s and {@link ConsequenceRule}s of the business
 * information kinds, for a {@link org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection}.
 *
 * <p>
 * {@code is:comment} ({@link #COMMENTS}) matches every {@code Description},
 * whatever its type: the comments a database keeps on its tables and columns
 * and every other text that describes an element.
 * </p>
 *
 * <p>
 * {@link #CONSEQUENCES} adds one rule in front of {@link ConsequenceRule#CORE}:
 * a description that no longer describes any element is removed. In CWM a
 * description may describe several elements; it stays while one of them does.
 * In the Ecore mapping only the description names its elements
 * ({@code Description.modelElement}, the element end is hidden), so the
 * description of a left-out element is found through that reference.
 * </p>
 *
 * @see Selections#copy
 */
public final class BusinessInformationRules {

    /** {@code is:comment}: descriptions. */
    public static final ElementRule COMMENTS = ElementRules.kind(BusinessinformationPackage.Literals.DESCRIPTION)
            .describedAs("is:comment");

    /** The core words and {@code is:comment}. */
    public static final RuleVocabulary VOCABULARY = RuleVocabulary.core().withRule("comment", COMMENTS);

    private static final ConsequenceRule DESCRIPTIONS = ConsequenceRule.of("description of nothing",
            loss -> loss.referrer() instanceof Description
                    && loss.reference() == BusinessinformationPackage.Literals.DESCRIPTION__MODEL_ELEMENT
                    && loss.remaining().isEmpty()
                            ? Optional.of(Consequence.REMOVE)
                            : Optional.empty());

    /** A description that describes nothing any more is removed; then {@link ConsequenceRule#CORE}. */
    public static final List<ConsequenceRule> CONSEQUENCES = ConsequenceRule.combine(List.of(DESCRIPTIONS),
            ConsequenceRule.CORE);

    private BusinessInformationRules() {
    }
}
