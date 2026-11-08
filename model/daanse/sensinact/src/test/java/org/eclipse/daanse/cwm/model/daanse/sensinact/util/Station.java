/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact.util;

import static org.eclipse.daanse.cwm.model.daanse.sensinact.examples.ClassModels.attr;
import static org.eclipse.daanse.cwm.model.daanse.sensinact.examples.ClassModels.cls;
import static org.eclipse.daanse.cwm.model.daanse.sensinact.examples.ClassModels.many;
import static org.eclipse.daanse.cwm.model.daanse.sensinact.examples.ClassModels.pkg;
import static org.eclipse.daanse.cwm.model.daanse.sensinact.examples.ClassModels.type;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AggregationKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.sensinact.examples.ClassModels;

/**
 * A small source model and provider for the tests of bindings, helpers and validator.
 *
 * <pre>
 * source:   Station { name: String, readings: Reading[*], location: Location }
 *           Location { lat: Double, lon: Double }
 *           Reading { value: Double, takenAt: Timestamp, state: String }, Gauge &lt; Reading { level: Double }
 *           Owner { name: String };  association StationOwner: station (Station) -- owner (Owner, navigable)
 * provider: weather › climate › temperature: Double (required), state: String (optional)
 * mapping:  m: Station, id name, climate reads readings[0], temperature = value, state = state
 * </pre>
 */
final class Station {

    final Model model = ClassModels.model("sensors");
    final Package source = pkg(model, "source");
    final DataType string = type(model, "String");
    final DataType decimal = type(model, "Double");
    final DataType timestamp = type(model, "Timestamp");
    final Class station = cls(source, "Station");
    final Class location = cls(source, "Location");
    final Class reading = cls(source, "Reading");
    final Class gauge = cls(source, "Gauge", reading);
    final Class owner = cls(source, "Owner");
    final Attribute name = attr(station, "name", string);
    final Attribute readings = many(station, "readings", reading);
    final Attribute locationRef = attr(station, "location", location);
    final Attribute lat = attr(location, "lat", decimal);
    final Attribute lon = attr(location, "lon", decimal);
    final Attribute value = attr(reading, "value", decimal);
    final Attribute takenAt = attr(reading, "takenAt", timestamp);
    final Attribute state = attr(reading, "state", string);
    final Attribute level = attr(gauge, "level", decimal);
    final Attribute ownerName = attr(owner, "name", string);
    final AssociationEnd ownerEnd;
    final AssociationEnd stationEnd;

    final Package providers = pkg(model, "providers");
    final Provider weather = Providers.create(providers, "weather");
    final Service climate = Services.create(weather, "climate");
    final Resource temperature = Resources.create(climate, "temperature", decimal);
    final Resource stateResource = Resources.optional(climate, "state", string);

    final Package mappings = pkg(model, "mappings");
    final ProviderMapping mapping;
    final ServiceMapping climateMapping;

    Station() {
        Association a = RelationshipsFactory.eINSTANCE.createAssociation();
        a.setName("StationOwner");
        stationEnd = end("station", station, false);
        ownerEnd = end("owner", owner, true);
        a.getFeature().add(stationEnd);
        a.getFeature().add(ownerEnd);
        source.getOwnedElement().add(a);

        mapping = ProviderMappings.create(mappings, "m", weather, ValueSources.path(name), station);
        climateMapping = ServiceMappings.create(mapping, climate, ValueSources.at(ValueSources.path(readings), 0));
        ResourceMappings.create(climateMapping, temperature, ValueSources.path(value));
        ResourceMappings.create(climateMapping, stateResource, ValueSources.path(state));
    }

    private static AssociationEnd end(String name, Class type, boolean navigable) {
        AssociationEnd e = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        e.setName(name);
        e.setType(type);
        e.setIsNavigable(navigable);
        e.setAggregation(AggregationKind.AK_NONE);
        return e;
    }
}
