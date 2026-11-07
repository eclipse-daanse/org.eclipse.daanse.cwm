/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.util;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Event;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;

/**
 * A small model to hang duties on: model {@code lab} › package {@code data} › class {@code Invoice}
 * ({@code issuedOn}, {@code amount}), class {@code Customer} ({@code name}, {@code contractEnd}),
 * class {@code Reading} ({@code value}); the rules in the package {@code governance}, with the
 * event {@code contractEnded} and the party {@code legal}; the sources in the package {@code law}.
 */
final class Lab {

    private static final CoreFactory F = CoreFactory.eINSTANCE;

    final Model model = F.createModel();
    final Package data = pkg("data");
    final Package governance = pkg("governance");
    final Package law = pkg("law");
    final DataType timestamp = dataType("Timestamp");
    final DataType decimal = dataType("Decimal");
    final DataType string = dataType("String");
    final Class invoice = cls("Invoice");
    final Attribute issuedOn = attribute(invoice, "issuedOn", timestamp);
    final Attribute amount = attribute(invoice, "amount", decimal);
    final Class customer = cls("Customer");
    final Attribute name = attribute(customer, "name", string);
    final Attribute contractEnd = attribute(customer, "contractEnd", timestamp);
    final Class reading = cls("Reading");
    final Attribute value = attribute(reading, "value", decimal);
    final Event contractEnded = BehavioralFactory.eINSTANCE.createEvent();
    final ResponsibleParty legal = BusinessinformationFactory.eINSTANCE.createResponsibleParty();

    Lab() {
        model.setName("lab");
        contractEnded.setName("contractEnded");
        governance.getOwnedElement().add(contractEnded);
        legal.setName("legal");
        legal.setResponsibility("legal department");
        governance.getOwnedElement().add(legal);
    }

    private Package pkg(String name) {
        Package p = F.createPackage();
        p.setName(name);
        model.getOwnedElement().add(p);
        return p;
    }

    private DataType dataType(String name) {
        DataType t = F.createDataType();
        t.setName(name);
        data.getOwnedElement().add(t);
        return t;
    }

    private Class cls(String name) {
        Class c = F.createClass();
        c.setName(name);
        data.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attribute(Class owner, String name, DataType type) {
        Attribute a = F.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }
}
