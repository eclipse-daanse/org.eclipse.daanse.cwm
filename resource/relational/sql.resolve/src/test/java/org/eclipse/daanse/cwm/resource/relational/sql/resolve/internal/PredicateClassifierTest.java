/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.sql.resolve.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.PredicateKind;
import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;

/** The predicates of a parsed statement, mapped back to the columns of the model by name. */
class PredicateClassifierTest {

    /**
     * Names match without case in a Turkish default locale too, where {@code "I"} lower-cases to a
     * dotless {@code "ı"}: {@code EMAIL} and {@code customer.EMAIL} are the column {@code email}.
     */
    @Test
    void namesMatchWithoutCase_inATurkishLocaleToo() throws Exception {
        Table customer = RelationalFactory.eINSTANCE.createTable();
        customer.setName("customer");
        Column email = RelationalFactory.eINSTANCE.createColumn();
        email.setName("email");
        customer.getFeature().add(email);
        Column id = RelationalFactory.eINSTANCE.createColumn();
        id.setName("ID");
        customer.getFeature().add(id);
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            Map<Column, EnumSet<PredicateKind>> kinds = PredicateClassifier.classify(
                    CCJSqlParserUtil.parse("select 1 from customer where EMAIL = 'b' and customer.id > 3"),
                    List.of(email, id));

            assertThat(kinds).containsEntry(email, EnumSet.of(PredicateKind.EQUALITY)).containsEntry(id,
                    EnumSet.of(PredicateKind.RANGE));
        } finally {
            Locale.setDefault(before);
        }
    }
}
