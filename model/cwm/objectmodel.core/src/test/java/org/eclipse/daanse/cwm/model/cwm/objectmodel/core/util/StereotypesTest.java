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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

class StereotypesTest {

    @Test
    void of_andHas_readTheStereotypesThatExtendAnElement() {
        Shop model = new Shop();
        Stereotype entity = stereotype(model, "entity");
        Stereotype audited = stereotype(model, "audited");
        entity.getExtendedElement().add(model.orders);
        audited.getExtendedElement().add(model.orders);
        audited.getExtendedElement().add(model.customer);

        assertThat(Stereotypes.of(model.orders)).containsExactly(audited, entity);
        assertThat(Stereotypes.of(model.customer)).containsExactly(audited);
        assertThat(Stereotypes.of(model.product)).isEmpty();
        assertThat(Stereotypes.has(model.orders, "entity")).isTrue();
        assertThat(Stereotypes.has(model.customer, "entity")).isFalse();
    }

    @Test
    void withInstalledIndex_answersTheSame() {
        Shop model = new Shop();
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        Resource resource = new ResourceImpl(URI.createURI("test:/shop"));
        set.getResources().add(resource);
        resource.getContents().add(model.shop);
        Stereotype entity = stereotype(model, "entity");
        entity.getExtendedElement().add(model.orders);

        assertThat(InverseReferences.isIndexed(model.orders)).isTrue();
        assertThat(Stereotypes.ofStream(model.orders)).containsExactly(entity);
        assertThat(Stereotypes.has(model.orders, "entity")).isTrue();
    }

    @Test
    void nullSafe() {
        assertThat(Stereotypes.of(null)).isEmpty();
        assertThat(Stereotypes.has(null, "entity")).isFalse();
        assertThat(Stereotypes.has(new Shop().orders, null)).isFalse();
    }

    private static Stereotype stereotype(Shop model, String name) {
        Stereotype stereotype = Shop.CF.createStereotype();
        stereotype.setName(name);
        stereotype.setBaseClass("Class");
        model.shop.getOwnedElement().add(stereotype);
        return stereotype;
    }
}
