/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the JSON model against the checklist in
 * {@code model/CWM-MODELLING-GUIDE.md}, section 8.
 */
class JsonModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";

    private final JsonFactory json = JsonFactory.eINSTANCE;

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------- Model

    @Test
    void packageDeclaresHiddenOpposites() {
        assertThat(JsonPackage.eINSTANCE.getEAnnotation(
                "http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
    }

    @Test
    void everyReferenceIsOneWayAndNamesItsOppositeRole() {
        int hidden = 0;
        for (var classifier : JsonPackage.eINSTANCE.getEClassifiers()) {
            if (!(classifier instanceof EClass ec)) {
                continue;
            }
            for (EReference ref : ec.getEReferences()) {
                assertThat(ref.getEOpposite())
                        .as("%s.%s carries an eOpposite", ec.getName(), ref.getName())
                        .isNull();
                if (ref.isContainment()) {
                    continue;
                }
                var a = ref.getEAnnotation(EMOF);
                assertThat(a)
                        .as("%s.%s without Property.oppositeRoleName", ec.getName(), ref.getName())
                        .isNotNull();
                assertThat(ref.getEAnnotation(EMOF + "#Property.oppositeRoleName")
                        .getDetails().get("body"))
                        .isEqualTo(a.getDetails().get("Property.oppositeRoleName"));
                hidden++;
            }
        }
        assertThat(hidden).isEqualTo(3);
    }

    // ------------------------------------------------------------ Structures

    /**
     * An object is an unordered collection of pairs; the JSON Schema default
     * is that further pairs are allowed.
     */
    @Test
    void objectTypeDefaultsToOpenAndOptional() {
        ObjectType o = json.createObjectType();
        o.setName("Kunde");

        assertThat(o.isAllowsAdditionalProperties())
                .as("additionalProperties is true when not stated")
                .isTrue();

        Property nummer = property("nummer", true);
        Property notiz = property("notiz", false);
        o.getFeature().add(nummer);
        o.getFeature().add(notiz);

        assertThat(o.getFeature()).containsExactly(nummer, notiz);
        assertThat(nummer.isIsRequired()).isTrue();
        assertThat(notiz.isIsRequired()).isFalse();
    }

    /**
     * The order of array elements is significant - prefixItems runs through
     * the inherited, ordered Classifier::feature.
     */
    @Test
    void tupleKeepsItsPositions() {
        ArrayType koordinate = json.createArrayType();
        koordinate.setName("Koordinate");
        koordinate.setAllowsAdditionalItems(false);
        koordinate.setMinItems(2);
        koordinate.setMaxItems(3);

        ItemDef laenge = item("laengengrad", 0);
        ItemDef breite = item("breitengrad", 1);
        ItemDef hoehe = item("hoehe", 2);
        koordinate.getFeature().add(laenge);
        koordinate.getFeature().add(breite);
        koordinate.getFeature().add(hoehe);

        assertThat(koordinate.getFeature()).containsExactly(laenge, breite, hoehe);
        assertThat(JsonPackage.eINSTANCE.getArrayType().getEStructuralFeature("itemType")
                .isOrdered()).isTrue();
        assertThat(koordinate.isSetMaxItems()).isTrue();

        ArrayType offen = json.createArrayType();
        offen.setName("Liste");
        assertThat(offen.isSetMaxItems()).isFalse();
        assertThat(offen.isAllowsAdditionalItems()).isTrue();
    }

    /**
     * Numeric bounds are held as strings: RFC 8259 fixes neither precision
     * nor range.
     */
    @Test
    void numericBoundsKeepTheirWrittenForm() {
        PrimitiveType p = json.createPrimitiveType();
        p.setName("Betrag");
        p.setValueKind(JsonValueKind.JSON_NUMBER);
        p.setMinimum("0");
        p.setMaximum("99999999999999999999.999");

        assertThat(p.getMaximum()).isEqualTo("99999999999999999999.999");
        assertThat(JsonPackage.eINSTANCE.getPrimitiveType_Maximum().getEType().getName())
                .isEqualTo("String");
    }

    /** JSON Lines is the record-oriented shape and sits next to record. */
    @Test
    void documentKnowsTheRecordOrientedForm() {
        Document d = json.createDocument();
        d.setName("ereignisse.jsonl");
        d.setForm(DocumentForm.JSON_LINES);
        d.setMediaType("application/jsonl");

        assertThat(d.getCharset()).as("default per RFC 8259").isEqualTo("UTF-8");
        assertThat(d.getForm()).isEqualTo(DocumentForm.JSON_LINES);
    }

    // ------------------------------------------------------------ Round trip

    @Test
    void savesAndReloadsWithoutUnresolvedProxies() throws Exception {
        Schema schema = json.createSchema();
        schema.setName("bestellung");
        schema.setDialect("https://json-schema.org/draft/2020-12/schema");
        schema.setIdentifier("https://www.daanse.org/schema/bestellung");

        PrimitiveType text = json.createPrimitiveType();
        text.setName("Text");
        text.setValueKind(JsonValueKind.JSON_STRING);
        text.getAllowedValue().add("\"offen\"");
        text.getAllowedValue().add("\"bezahlt\"");

        ObjectType bestellung = json.createObjectType();
        bestellung.setName("Bestellung");
        Property status = property("status", true);
        status.setType(text);
        bestellung.getFeature().add(status);

        SchemaReference ref = json.createSchemaReference();
        ref.setName("kundeRef");
        ref.setPointer("#/$defs/kunde");
        ref.setIsDynamic(false);
        ref.setTarget(bestellung);

        CompositeType entweder = json.createCompositeType();
        entweder.setName("BestellungOderVerweis");
        entweder.setComposition(CompositionKind.JSON_ONE_OF);
        entweder.getMember().add(bestellung);
        entweder.getMember().add(ref);

        schema.getOwnedElement().add(text);
        schema.getOwnedElement().add(bestellung);
        schema.getOwnedElement().add(ref);
        schema.getOwnedElement().add(entweder);

        ResourceSet set = newResourceSet();
        Resource res = set.createResource(
                URI.createFileURI(tempDir.resolve("bestellung.xmi").toFile().getAbsolutePath()));
        res.getContents().add(schema);
        res.save(Map.of());

        ResourceSet reloadSet = newResourceSet();
        Resource reloaded = reloadSet.getResource(res.getURI(), true);
        EcoreUtil.resolveAll(reloadSet);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(reloadSet)).isEmpty();

        Schema back = (Schema) reloaded.getContents().get(0);
        assertThat(back.getDialect()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        CompositeType backComposite = (CompositeType) back.getOwnedElement().get(3);
        assertThat(backComposite.getComposition()).isEqualTo(CompositionKind.JSON_ONE_OF);
        assertThat(backComposite.getMember()).hasSize(2);
        PrimitiveType backText = (PrimitiveType) back.getOwnedElement().get(0);
        assertThat(backText.getAllowedValue()).containsExactly("\"offen\"", "\"bezahlt\"");
    }

    @Test
    void backwardNavigationNeedsTheCrossReferenceIndex() {
        ObjectType ziel = json.createObjectType();
        ziel.setName("Kunde");
        SchemaReference ref = json.createSchemaReference();
        ref.setName("kundeRef");
        ref.setPointer("#/$defs/kunde");
        ref.setTarget(ziel);

        Schema schema = json.createSchema();
        schema.setName("s");
        schema.getOwnedElement().add(ziel);
        schema.getOwnedElement().add(ref);

        ResourceSet set = newResourceSet();
        Resource res = set.createResource(URI.createFileURI("mem:/json.xmi"));
        res.getContents().add(schema);

        EReference target = JsonPackage.eINSTANCE.getSchemaReference_Target();
        assertThat(referencing(set, ziel, target)).isEmpty();

        InverseReferences.install(set);
        assertThat(referencing(set, ziel, target)).containsExactly(ref);
    }

    // --------------------------------------------------------------- Helpers

    private static List<EObject> referencing(ResourceSet set, EObject target, EReference ref) {
        ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(set);
        if (adapter == null) {
            return List.of();
        }
        return adapter.getInverseReferences(target, true).stream()
                .filter(s -> s.getEStructuralFeature() == ref)
                .map(org.eclipse.emf.ecore.EStructuralFeature.Setting::getEObject)
                .toList();
    }

    private static ResourceSet newResourceSet() {
        ResourceSet set = new ResourceSetImpl();
        set.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        return set;
    }

    private Property property(String name, boolean required) {
        Property p = json.createProperty();
        p.setName(name);
        p.setIsRequired(required);
        return p;
    }

    private ItemDef item(String name, int position) {
        ItemDef i = json.createItemDef();
        i.setName(name);
        i.setPosition(position);
        return i;
    }
}
