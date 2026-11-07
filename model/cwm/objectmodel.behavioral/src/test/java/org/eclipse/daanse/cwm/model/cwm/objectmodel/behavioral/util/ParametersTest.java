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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.DataTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.junit.jupiter.api.Test;

class ParametersTest {

    @Test
    void create_inNamespace_keepsOrder() {
        Model m = Models.create("m");
        DataType date = DataTypes.create(m, "java.time.LocalDate");
        Parameter from = Parameters.create(m, "fromDate", date);
        Parameter to = Parameters.create(m, "toDate", date);

        assertThat(from.getKind()).isEqualTo(ParameterDirectionKind.PDK_IN);
        assertThat(from.getType()).isSameAs(date);
        assertThat(Parameters.declared(m)).containsExactly(from, to);
        assertThat(Parameters.find(m, "toDate")).contains(to);
    }

    @Test
    void create_onOperation_usesParameterFeature() {
        Model m = Models.create("m");
        DataType text = DataTypes.create(m, "String");
        Operation op = BehavioralFactory.eINSTANCE.createOperation();
        Parameter p = Parameters.create(op, "x", text);

        assertThat(op.getParameter()).containsExactly(p);
        assertThat(p.getBehavioralFeature()).isSameAs(op);
    }
}
