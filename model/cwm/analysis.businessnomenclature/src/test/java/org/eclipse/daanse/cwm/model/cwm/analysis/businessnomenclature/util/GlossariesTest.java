/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.Glossary;
import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.Term;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.junit.jupiter.api.Test;

class GlossariesTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    @Test
    void glossary_findsOrCreates_ownedInPackage() {
        Model pkg = CF.createModel();

        Glossary created = Glossaries.glossary(pkg, "Vertrieb", "de");
        assertThat(created.getLanguage()).isEqualTo("de");
        assertThat(pkg.getOwnedElement()).contains(created);
        assertThat(Glossaries.owned(pkg)).containsExactly(created);

        assertThat(Glossaries.glossary(pkg, "Vertrieb", null)).isSameAs(created);
    }

    @Test
    void glossary_undeterminedLanguageBecomesNeutralTag() {
        Model pkg = CF.createModel();
        // language is mandatory in CWM, undetermined input lands as und
        assertThat(Glossaries.glossary(pkg, "G", null).getLanguage()).isEqualTo("und");
    }

    @Test
    void define_findsOrCreatesTerm_updatesDefinition() {
        Model pkg = CF.createModel();
        Glossary g = Glossaries.glossary(pkg, "G", "en");

        Term t = Glossaries.define(g, "Umsatz", "Netto, ohne Steuer");
        assertThat(g.getOwnedElement()).contains(t);
        assertThat(Glossaries.terms(g)).containsExactly(t);

        Term again = Glossaries.define(g, "Umsatz", "Brutto");
        assertThat(again).isSameAs(t);
        assertThat(again.getDefinition()).isEqualTo("Brutto");

        Term untouched = Glossaries.define(g, "Umsatz", null);
        assertThat(untouched.getDefinition()).isEqualTo("Brutto");
    }

    @Test
    void termsFor_findsAnchoredTermsAcrossGlossaries() {
        Model pkg = CF.createModel();
        DataType element = CF.createDataType();
        pkg.getOwnedElement().add(element);

        Term anchored = Glossaries.define(Glossaries.glossary(pkg, "A", "en"), "Revenue", "def");
        anchored.getModelElement().add(element);
        Glossaries.define(Glossaries.glossary(pkg, "B", "en"), "Other", "def");

        assertThat(Glossaries.termsFor(pkg, element)).containsExactly(anchored);
    }

    @Test
    void languageTag_canonicalizesAndAnswersEmptyForUndetermined() {
        assertThat(Glossaries.languageTag("de_DE")).contains("de-DE");
        assertThat(Glossaries.languageTag(null)).isEmpty();
        assertThat(Glossaries.languageTag("und")).isEmpty();
    }
}
