/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;

/**
 * A small model to de-identify: model {@code clinic} › package {@code data} › class {@code Patient}
 * ({@code insuranceNo}, {@code name}, {@code birthDate}, {@code zip}, {@code sex}, {@code diagnosis},
 * {@code income}), class {@code Pseudonym} ({@code insuranceNo}, {@code pseudonym}), class
 * {@code Visit} ({@code visitedOn}); the rules in the package {@code governance}, with the parties
 * {@code trustCentre} and {@code research}.
 */
final class Clinic {

    private static final CoreFactory F = CoreFactory.eINSTANCE;

    final Model model = F.createModel();
    final Package data = pkg("data");
    final Package governance = pkg("governance");
    final DataType string = dataType("String");
    final DataType date = dataType("Date");
    final DataType decimal = dataType("Decimal");
    final Class patient = cls("Patient");
    final Attribute insuranceNo = attribute(patient, "insuranceNo", string);
    final Attribute name = attribute(patient, "name", string);
    final Attribute birthDate = attribute(patient, "birthDate", date);
    final Attribute zip = attribute(patient, "zip", string);
    final Attribute sex = attribute(patient, "sex", string);
    final Attribute diagnosis = attribute(patient, "diagnosis", string);
    final Attribute income = attribute(patient, "income", decimal);
    final Class pseudonym = cls("Pseudonym");
    final Attribute pseudonymNo = attribute(pseudonym, "insuranceNo", string);
    final Attribute pseudonymValue = attribute(pseudonym, "pseudonym", string);
    final Class visit = cls("Visit");
    final Attribute visitedOn = attribute(visit, "visitedOn", date);
    final ResponsibleParty trustCentre = party("trustCentre", "trusted third party holding the pseudonym keys");
    final ResponsibleParty research = party("research", "research partner receiving the data");

    Clinic() {
        model.setName("clinic");
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

    private ResponsibleParty party(String name, String responsibility) {
        ResponsibleParty p = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        p.setName(name);
        p.setResponsibility(responsibility);
        governance.getOwnedElement().add(p);
        return p;
    }

    private static Attribute attribute(Class owner, String name, DataType type) {
        Attribute a = F.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }
}
