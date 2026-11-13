/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.junit.jupiter.api.Test;

class PrivilegeStereotypesTest {

    @Test
    void rolesAndUsers_areToldByTheirMarker() {
        ShopWithGrants m = new ShopWithGrants();
        ResponsibleParty steward = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        steward.setName("steward");
        m.shop.getOwnedElement().add(steward);

        assertThat(PrivilegeStereotypes.isDatabaseRole(m.reader)).isTrue();
        assertThat(PrivilegeStereotypes.isDatabaseRole(m.alice)).isFalse();
        assertThat(PrivilegeStereotypes.isDatabaseUser(m.alice)).isTrue();
        assertThat(PrivilegeStereotypes.isDatabaseRole(steward)).isFalse();
        assertThat(PrivilegeStereotypes.databaseRoles(m.shop)).containsExactly(m.reader, m.writer);
        assertThat(PrivilegeStereotypes.databaseUsers(m.shop)).containsExactly(m.alice);
    }

    @Test
    void aPartyFindsItsMarkerOnlyWhenBothAreInOneModel() {
        ResponsibleParty loose = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        Stereotype marker = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
        marker.getExtendedElement().add(loose);

        assertThat(PrivilegeStereotypes.isDatabaseRole(loose)).isFalse();
        assertThat(marker.getBaseClass()).isEqualTo(PrivilegeStereotypes.BASE_CLASS);
        assertThat(PrivilegeStereotypes.isDatabaseRole(null)).isFalse();
        assertThat(PrivilegeStereotypes.databaseRoles(null)).isEmpty();
    }
}
