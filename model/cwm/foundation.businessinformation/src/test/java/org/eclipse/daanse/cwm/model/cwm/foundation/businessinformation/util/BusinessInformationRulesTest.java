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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.junit.jupiter.api.Test;

class BusinessInformationRulesTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    /** {@code m.customer} with the attributes {@code name} and {@code email}, each described. */
    private final Model m = Models.create("m");
    private final Class customer = type(m, "customer");
    private final Attribute name = attribute(customer, "name");
    private final Attribute email = attribute(customer, "email");
    private final Description emailRemark = Descriptions.describe(email, "remarks", "en", "Where the invoice goes");
    private final Description both = describeBoth();

    @Test
    void comment_matchesDescriptions_ofEveryType() {
        assertThat(BusinessInformationRules.COMMENTS.test(emailRemark)).isTrue();
        assertThat(BusinessInformationRules.COMMENTS.test(email)).isFalse();
        assertThat(BusinessInformationRules.COMMENTS.matchesAllOf(BusinessinformationPackage.Literals.DESCRIPTION))
                .isTrue();
        assertThat(BusinessInformationRules.VOCABULARY.parse("is:comment").describe()).isEqualTo("is:comment");
        assertThat(Selection.parse(BusinessInformationRules.VOCABULARY, "!is:comment")
                .excludesKind(BusinessinformationPackage.Literals.DESCRIPTION)).isTrue();
    }

    @Test
    void descriptionOfNothing_goes_oneThatStillDescribesSomething_stays() {
        Selected<Model> cut = Selections.copy(m, Selection.parse("!name:email"),
                BusinessInformationRules.CONSEQUENCES);

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("m.customer.email", "Attribute", "name:email"),
                new LeftOut(QualifiedNames.of(emailRemark), "Description",
                        "description of nothing (modelElement: m.customer.email)"));
        Description kept = QualifiedNames.find(cut.model(), QualifiedNames.of(both), Description.class)
                .orElseThrow();
        assertThat(kept.getModelElement()).extracting(ModelElement::getName).containsExactly("name");
    }

    @Test
    void withoutComments_theDescriptionsGo() {
        Selected<Model> cut = Selections.copy(m, Selection.parse(BusinessInformationRules.VOCABULARY, "!is:comment"),
                BusinessInformationRules.CONSEQUENCES);

        assertThat(cut.leftOut()).extracting(LeftOut::kind).containsOnly("Description").hasSize(2);
        assertThat(Selections.list(cut.model(), Selection.all(), Attribute.class)).hasSize(2);
    }

    @Test
    void consequences_ownRuleFirst_coreLast() {
        assertThat(BusinessInformationRules.CONSEQUENCES).map(ConsequenceRule::describe).containsExactly(
                "description of nothing", "dependency on a removed supplier", "required reference");
    }

    private Description describeBoth() {
        Description description = BusinessinformationFactory.eINSTANCE.createDescription();
        description.setName("names_and_emails");
        description.setBody("How a customer is reached");
        description.getModelElement().add(name);
        description.getModelElement().add(email);
        customer.getOwnedElement().add(description);
        return description;
    }

    private static Class type(Model owner, String name) {
        Class type = CF.createClass();
        type.setName(name);
        owner.getOwnedElement().add(type);
        return type;
    }

    private static Attribute attribute(Class owner, String name) {
        Attribute attribute = CF.createAttribute();
        attribute.setName(name);
        owner.getFeature().add(attribute);
        return attribute;
    }
}
