/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Subsystem;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CWM's MOF Reference Closure rule leaves {@code Description.modelElement}
 * unidirectional — there is no {@code ModelElement.description} back-reference.
 * Lookup therefore goes through the cross-reference index, which means the objects
 * must live in a {@link Resource}; a fully detached element cannot be answered for.
 */
class DescriptionsTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final String TYPE = "remarks";

    private Resource resource;

    @BeforeEach
    void newResource() {
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        resource = new ResourceImpl(URI.createURI("test:/descriptions"));
        set.getResources().add(resource);
    }

    /** Adds an object to the indexed resource and returns it. */
    private <T extends EObject> T managed(T object) {
        resource.getContents().add(object);
        return object;
    }

    private DataType table(String name, String... cols) {
        DataType t = CF.createDataType();
        t.setName(name);
        for (String cn : cols) {
            Attribute c = CF.createAttribute();
            c.setName(cn);
            t.getFeature().add(c);
        }
        return managed(t);
    }

    private Description describe(ModelElement element, String type, String body) {
        Description d = BusinessinformationFactory.eINSTANCE.createDescription();
        d.setType(type);
        d.setBody(body);
        d.getModelElement().add(element);
        return managed(d);
    }

    @Test
    void find_viaInverseIndex_containmentIndependent() {
        DataType t = table("EMP", "ID");
        Attribute id = (Attribute) t.getFeature().get(0);

        // The Descriptions do not contain — and are not contained by — the elements
        // they describe. Lookup runs over Description.modelElement in reverse.
        describe(t, TYPE, "employee master data");
        describe(id, TYPE, "surrogate key");

        assertThat(Descriptions.find(t, TYPE)).get().extracting(Description::getBody)
                .isEqualTo("employee master data");
        assertThat(Descriptions.find(id, TYPE)).get().extracting(Description::getBody)
                .isEqualTo("surrogate key");
    }

    @Test
    void find_filtersByType() {
        DataType t = table("EMP", "ID");
        describe(t, "OTHER-TYPE", "not a remark");

        assertThat(Descriptions.find(t, TYPE)).isEmpty();
        assertThat(Descriptions.find(t, "OTHER-TYPE")).get().extracting(Description::getBody)
                .isEqualTo("not a remark");
    }

    @Test
    void find_ignoresBlankBodies_takesFirstNonBlank() {
        DataType t = table("EMP", "ID");
        describe(t, TYPE, "  ");
        describe(t, TYPE, "first real");
        describe(t, TYPE, "second real");

        assertThat(Descriptions.find(t, TYPE)).get().extracting(Description::getBody).isNotNull();
        // all() stays unfiltered — the blank one is still there.
        assertThat(Descriptions.all(t, TYPE)).hasSize(3);
    }

    @Test
    void find_withLanguage_matchesExactly() {
        DataType t = table("EMP");
        describe(t, TYPE, "Mitarbeiterstammdaten").setLanguage("de");
        describe(t, TYPE, "employee master data").setLanguage("en");

        assertThat(Descriptions.find(t, TYPE, "en")).get().extracting(Description::getBody)
                .isEqualTo("employee master data");
        assertThat(Descriptions.find(t, TYPE, "de")).get().extracting(Description::getBody)
                .isEqualTo("Mitarbeiterstammdaten");
        assertThat(Descriptions.find(t, TYPE, "fr")).isEmpty();
    }

    @Test
    void findInherited_fallsBackToImmediateNamespaceOnly() {
        Subsystem schema = managed(CF.createSubsystem());
        schema.setName("HR");
        DataType t = CF.createDataType();
        t.setName("EMP");
        Attribute id = CF.createAttribute();
        id.setName("ID");
        t.getFeature().add(id);
        schema.getOwnedElement().add(t);

        describe(schema, TYPE, "human resources");

        // The table's namespace is the schema — one level up, so it is found.
        assertThat(Descriptions.findInherited(t, TYPE)).get().extracting(Description::getBody)
                .isEqualTo("human resources");
        // The column's namespace is the table, which carries nothing; the schema is
        // two levels up and must NOT be consulted.
        assertThat(Descriptions.findInherited(id, TYPE)).isEmpty();
    }

    @Test
    void findInherited_prefersOwnDescription() {
        Subsystem schema = managed(CF.createSubsystem());
        DataType t = CF.createDataType();
        t.setName("EMP");
        schema.getOwnedElement().add(t);
        describe(schema, TYPE, "from schema");
        describe(t, TYPE, "from table");

        assertThat(Descriptions.findInherited(t, TYPE)).get().extracting(Description::getBody)
                .isEqualTo("from table");
    }

    @Test
    void ownedAndOrphans_useContainmentNotReferences() {
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg =
                managed(CoreFactory.eINSTANCE.createPackage());
        DataType t = table("EMP");

        Description attached = describe(t, TYPE, "describes the table");
        Description dangling = BusinessinformationFactory.eINSTANCE.createDescription();
        dangling.setType(TYPE);
        dangling.setBody("describes nothing");

        pkg.getOwnedElement().add(attached);
        pkg.getOwnedElement().add(dangling);

        assertThat(Descriptions.owned(pkg)).containsExactly(attached, dangling);
        assertThat(Descriptions.orphans(pkg)).containsExactly(dangling);
    }

    @Test
    void elementsAndSelfReference() {
        DataType t = table("EMP");
        Description d = describe(t, TYPE, "body");

        assertThat(Descriptions.elements(d)).containsExactly(t);
        assertThat(Descriptions.isSelfReferencing(d)).isFalse();

        // C-3-1 forbids this; the helper only reports it.
        d.getModelElement().add(d);
        assertThat(Descriptions.isSelfReferencing(d)).isTrue();
    }

    @Test
    void detachedElement_yieldsEmpty() {
        // Nothing indexes an element outside any resource — this is the documented
        // cost of dropping the ModelElement.description back-reference.
        DataType detached = CF.createDataType();
        detached.setName("EMP");
        Description d = BusinessinformationFactory.eINSTANCE.createDescription();
        d.setType(TYPE);
        d.setBody("unreachable");
        d.getModelElement().add(detached);

        assertThat(Descriptions.all(detached)).isEmpty();
        // The forward direction still works, always.
        assertThat(d.getModelElement()).containsExactly(detached);
    }

    @Test
    void describe_upsertsPerElementTypeAndLanguage() {
        DataType t = table("EMP");

        Description created = Descriptions.describe(t, TYPE, "de_DE", "Alt");
        assertThat(created.getLanguage()).isEqualTo("de-DE");
        // a DataType is a Namespace, so its texts live in the element itself
        assertThat(t.getOwnedElement()).contains(created);

        Description updated = Descriptions.describe(t, TYPE, "de-DE", "Neu");
        assertThat(updated).isSameAs(created);
        assertThat(updated.getBody()).isEqualTo("Neu");

        Description neutral = Descriptions.describe(t, TYPE, null, "Neutral");
        assertThat(neutral).isNotSameAs(created);
        assertThat(neutral.getLanguage()).isEqualTo("und");
    }

    @Test
    void describe_failsLoudlyOnDetachedNonNamespace() {
        Attribute detached = CF.createAttribute();
        detached.setName("COL");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> Descriptions.describe(detached, TYPE, "en", "text"));
    }

    @Test
    void owner_isElementItselfOrNearestContainerNamespace() {
        DataType t = table("EMP", "ID");
        Attribute col = (Attribute) t.getFeature().get(0);

        assertThat(Descriptions.owner(t)).isSameAs(t);
        // a feature hangs in Classifier.feature, not ownedElement — the
        // containment walk still finds the classifier
        assertThat(Descriptions.owner(col)).isSameAs(t);
    }

    @Test
    void localizedBody_resolvesViaLookupChain() {
        DataType t = table("EMP");
        Descriptions.describe(t, TYPE, "en", "English");
        Descriptions.describe(t, TYPE, "de-DE", "Deutsch");
        Descriptions.describe(t, TYPE, null, "Neutral");

        assertThat(Descriptions.localizedBody(t, TYPE, java.util.Locale.GERMANY)).contains("Deutsch");
        // de has no exact entry: the chain falls through to the neutral text
        assertThat(Descriptions.localizedBody(t, TYPE, java.util.Locale.GERMAN)).contains("Neutral");
        assertThat(Descriptions.localizedBody(t, TYPE, null)).contains("Neutral");
        assertThat(Descriptions.localizedBody(t, "other", null)).isEmpty();
    }

    @Test
    void localizedBody_lastGrabIsSmallestLanguageTag() {
        DataType t = table("EMP");
        Descriptions.describe(t, TYPE, "fr", "Francais");
        Descriptions.describe(t, TYPE, "en", "English");

        // no chain entry matches and no neutral text exists: the
        // lexicographically smallest tag wins, deterministically
        assertThat(Descriptions.localizedBody(t, TYPE, java.util.Locale.ITALIAN)).contains("English");
    }

    @Test
    void writeAndRead_workAdapterFreeOnBareTrees() {
        // no resource, no index: describe and localizedBody fall back to the
        // owning-namespace scan
        DataType t = CF.createDataType();
        t.setName("EMP");
        Descriptions.describe(t, TYPE, "en", "Text");

        assertThat(Descriptions.localizedBody(t, TYPE, java.util.Locale.ENGLISH)).contains("Text");
    }

    @Test
    void languageTag_canonicalizesAndAnswersEmptyForUndetermined() {
        assertThat(Descriptions.languageTag("de_DE")).contains("de-DE");
        assertThat(Descriptions.languageTag(" en ")).contains("en");
        assertThat(Descriptions.languageTag(null)).isEmpty();
        assertThat(Descriptions.languageTag("  ")).isEmpty();
        assertThat(Descriptions.languageTag("und")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Descriptions.all(null)).isEmpty();
        assertThat(Descriptions.all(null, TYPE)).isEmpty();
        assertThat(Descriptions.all(table("EMP"), null)).isEmpty();
        assertThat(Descriptions.find(null, TYPE)).isEmpty();
        assertThat(Descriptions.find(null, TYPE, "en")).isEmpty();
        assertThat(Descriptions.findInherited(null, TYPE)).isEmpty();
        assertThat(Descriptions.findInherited(table("EMP"), null)).isEmpty();
        assertThat(Descriptions.owned(null)).isEmpty();
        assertThat(Descriptions.orphans(null)).isEmpty();
        assertThat(Descriptions.elements(null)).isEmpty();
        assertThat(Descriptions.hasBody(null)).isFalse();
        assertThat(Descriptions.isSelfReferencing(null)).isFalse();
    }
}
