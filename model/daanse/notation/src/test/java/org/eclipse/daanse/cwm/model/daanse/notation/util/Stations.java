/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;

/**
 * A network of weather stations in Thuringia (Jena, Erfurt, Weimar, Gera) whose loggers write their readings as text:
 * model {@code weather} with the SI; a relational schema {@code thuringia} with the table {@code readings} (station,
 * observed and day as text, rain as text, dry as a flag, logged as a count of seconds, sealed as a timestamp with a
 * zone, postcode); a package {@code design} with the logical class {@code Reading} typed by data types that name the
 * value (LocalDateTime, Instant, Decimal, Text); a package {@code notes} for the notations.
 */
final class Stations {

    final Model model = model("weather");
    final SystemOfUnits si = SI.create(model);
    final Catalog catalog = owned(model, "loggers", RelationalFactory.eINSTANCE.createCatalog());
    final Schema thuringia = owned(catalog, "thuringia", RelationalFactory.eINSTANCE.createSchema());
    final SQLSimpleType varchar = sqlType(thuringia, "VARCHAR", 12);
    final SQLSimpleType bigint = sqlType(thuringia, "BIGINT", -5);
    final SQLSimpleType integer = sqlType(thuringia, "INTEGER", 4);
    final SQLSimpleType date = sqlType(thuringia, "DATE", 91);
    final SQLSimpleType timestampTz = sqlType(thuringia, "TIMESTAMP WITH TIME ZONE", 2014);
    final Table readings = owned(thuringia, "readings", RelationalFactory.eINSTANCE.createTable());
    final Column station = column(readings, "station", varchar);
    final Column observed = column(readings, "observed", varchar);
    final Column day = column(readings, "day", varchar);
    final Column dayKey = column(readings, "day_key", integer);
    final Column rain = column(readings, "rain", varchar);
    final Column dry = column(readings, "dry", varchar);
    final Column logged = column(readings, "logged", bigint);
    final Column sealed = column(readings, "sealed", timestampTz);
    final Column measuredOn = column(readings, "measured_on", date);
    final Column postcode = column(readings, "postcode", varchar);
    final Package design = owned(model, "design", CoreFactory.eINSTANCE.createPackage());
    final DataType localDateTime = owned(design, "LocalDateTime", CoreFactory.eINSTANCE.createDataType());
    final DataType instant = owned(design, "Instant", CoreFactory.eINSTANCE.createDataType());
    final DataType decimal = owned(design, "Decimal", CoreFactory.eINSTANCE.createDataType());
    final DataType text = owned(design, "Text", CoreFactory.eINSTANCE.createDataType());
    final Class reading = owned(design, "Reading", CoreFactory.eINSTANCE.createClass());
    final Attribute takenAt = attribute(reading, "takenAt", localDateTime);
    final Attribute receivedAt = attribute(reading, "receivedAt", instant);
    final Attribute amount = attribute(reading, "amount", decimal);
    final Attribute label = attribute(reading, "label", text);
    final Package notes = owned(model, "notes", CoreFactory.eINSTANCE.createPackage());

    Unit unit(String name) {
        return SI.unit(si, name);
    }

    static Model model(String name) {
        Model m = CoreFactory.eINSTANCE.createModel();
        m.setName(name);
        return m;
    }

    static <T extends ModelElement> T owned(Namespace owner, String name, T element) {
        element.setName(name);
        owner.getOwnedElement().add(element);
        return element;
    }

    static SQLSimpleType sqlType(Namespace owner, String name, long typeNumber) {
        SQLSimpleType t = owned(owner, name, RelationalFactory.eINSTANCE.createSQLSimpleType());
        t.setTypeNumber(typeNumber);
        return t;
    }

    static Column column(Table table, String name, Classifier type) {
        Column c = RelationalFactory.eINSTANCE.createColumn();
        c.setName(name);
        c.setType(type);
        table.getFeature().add(c);
        return c;
    }

    static Attribute attribute(Class owner, String name, Classifier type) {
        Attribute a = CoreFactory.eINSTANCE.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }

    static Parameter parameter(Namespace owner, String name, Classifier type) {
        Parameter p = BehavioralFactory.eINSTANCE.createParameter();
        p.setKind(ParameterDirectionKind.PDK_IN);
        p.setType(type);
        return owned(owner, name, p);
    }
}
