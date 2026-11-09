/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.r;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
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
 * Checks the R model against the checklist in
 * {@code model/CWM-MODELLING-GUIDE.md}, section 8.
 */
class RModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";

    private final RFactory r = RFactory.eINSTANCE;
    private final DatatypesFactory dt = DatatypesFactory.eINSTANCE;

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------- Model

    @Test
    void packageDeclaresHiddenOpposites() {
        assertThat(RPackage.eINSTANCE.getEAnnotation(
                "http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
    }

    @Test
    void everyReferenceIsOneWayAndNamesItsOppositeRole() {
        int hidden = 0;
        for (var classifier : RPackage.eINSTANCE.getEClassifiers()) {
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

    /** The numeric values are the codes R carries in the serialization stream. */
    @Test
    void sexpTypeCodesMatchTheSerializationStream() {
        assertThat(SexpType.RNIL.getValue()).isZero();
        assertThat(SexpType.RLOGICAL.getValue()).isEqualTo(10);
        assertThat(SexpType.RINTEGER.getValue()).isEqualTo(13);
        assertThat(SexpType.RREAL.getValue()).isEqualTo(14);
        assertThat(SexpType.RCHARACTER.getValue()).isEqualTo(16);
        assertThat(SexpType.RLIST.getValue()).isEqualTo(19);
        assertThat(SexpType.ROBJECT.getValue()).isEqualTo(25);
        // 11 and 12 were assigned to internal factors and have been withdrawn.
        assertThat(SexpType.get(11)).isNull();
        assertThat(SexpType.get(12)).isNull();
    }

    // ------------------------------------------------------------ Structures

    /**
     * A data.frame is a generic list of equally long columns; column order
     * lives in the inherited, ordered Classifier::feature.
     */
    @Test
    void dataFrameIsAListOfEquallyLongColumns() {
        RDataFrameType df = r.createRDataFrameType();
        df.setName("iris");
        df.setSexpType(SexpType.RLIST);
        df.setRowNamesKind(RowNamesKind.RAUTOMATIC);
        df.setRowCount(150);
        df.getRClass().add("data.frame");
        df.setIsObject(true);

        RComponent laenge = component("Sepal.Length", 0, vector(SexpType.RREAL, 150));
        RComponent art = component("Species", 1, factor(3));
        df.getFeature().add(laenge);
        df.getFeature().add(art);

        assertThat(df.getFeature()).containsExactly(laenge, art);
        assertThat(df.getRowCount()).isEqualTo(150L);
        assertThat(df.isSetRowCount()).isTrue();

        RDataFrameType leer = r.createRDataFrameType();
        leer.setName("leer");
        leer.setSexpType(SexpType.RLIST);
        leer.setRowNamesKind(RowNamesKind.RAUTOMATIC);
        assertThat(leer.isSetRowCount()).isFalse();
    }

    /** A factor is an integer vector with levels and class="factor". */
    @Test
    void factorIsAnIntegerVectorWithLevels() {
        RFactorType f = factor(3);

        assertThat(f.getSexpType()).isEqualTo(SexpType.RINTEGER);
        assertThat(f.getRClass()).containsExactly("factor");
        assertThat(f.getLevelSet().getLiteral()).hasSize(3);
        // The opposite end on Enumeration is deliberately not a feature (guide 2.5).
        assertThat(RPackage.eINSTANCE.getRFactorType_LevelSet().getEOpposite()).isNull();
    }

    // ------------------------------------------------------------ Round trip

    @Test
    void savesAndReloadsWithoutUnresolvedProxies() throws Exception {
        RdsFile rds = r.createRdsFile();
        rds.setName("iris.rds");
        rds.setSerializationFormat(SerializationFormat.RXDR);
        rds.setSerializationVersion(3);
        rds.setNativeEncoding("UTF-8");
        rds.setCompression(CompressionKind.RGZIP);
        rds.setWriterVersion("4.4.1");

        RDataFrameType df = r.createRDataFrameType();
        df.setName("iris");
        df.setSexpType(SexpType.RLIST);
        df.setRowNamesKind(RowNamesKind.RAUTOMATIC);
        df.getRClass().add("data.frame");

        RFactorType art = factor(3);
        RComponent spalte = component("Species", 0, art);
        df.getFeature().add(spalte);

        rds.getOwnedElement().add(df);
        rds.getOwnedElement().add(art);
        rds.getOwnedElement().add(art.getLevelSet());
        rds.setRootType(df);

        ResourceSet set = newResourceSet();
        Resource res = set.createResource(
                URI.createFileURI(tempDir.resolve("iris.xmi").toFile().getAbsolutePath()));
        res.getContents().add(rds);
        res.save(Map.of());

        ResourceSet reloadSet = newResourceSet();
        Resource reloaded = reloadSet.getResource(res.getURI(), true);
        EcoreUtil.resolveAll(reloadSet);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(reloadSet)).isEmpty();

        RdsFile back = (RdsFile) reloaded.getContents().get(0);
        assertThat(back.getSerializationVersion()).isEqualTo(3L);
        assertThat(back.getNativeEncoding()).isEqualTo("UTF-8");
        assertThat(back.getRootType()).isInstanceOf(RDataFrameType.class);
        RFactorType backFactor = (RFactorType) ((RComponent) ((RDataFrameType) back.getRootType())
                .getFeature().get(0)).getType();
        assertThat(backFactor.getLevelSet().getLiteral()).hasSize(3);
    }

    /** An RData file carries named bindings, an RDS file does not. */
    @Test
    void rdataCarriesNamedBindings() {
        RDataFile rda = r.createRDataFile();
        rda.setName("workspace.RData");
        rda.setSerializationFormat(SerializationFormat.RXDR);
        rda.setSerializationVersion(2);
        rda.setCompression(CompressionKind.RGZIP);

        RVectorType v = vector(SexpType.RREAL, 1);
        RBinding sichtbar = binding("x", v, false);
        RBinding verborgen = binding(".Random.seed", v, true);
        rda.getOwnedElement().add(v);
        rda.getOwnedElement().add(sichtbar);
        rda.getOwnedElement().add(verborgen);

        assertThat(rda.getOwnedElement()).contains(sichtbar, verborgen);
        assertThat(verborgen.isIsHidden()).isTrue();
        assertThat(RPackage.eINSTANCE.getRdsFile().getEStructuralFeature("rootType")).isNotNull();
    }

    @Test
    void backwardNavigationNeedsTheCrossReferenceIndex() {
        RVectorType v = vector(SexpType.RREAL, 3);
        v.setName("werte");
        RdsFile rds = r.createRdsFile();
        rds.setName("werte.rds");
        rds.setSerializationFormat(SerializationFormat.RXDR);
        rds.setSerializationVersion(3);
        rds.setCompression(CompressionKind.RNONE);
        rds.getOwnedElement().add(v);
        rds.setRootType(v);

        ResourceSet set = newResourceSet();
        Resource res = set.createResource(URI.createFileURI("mem:/r.xmi"));
        res.getContents().add(rds);

        EReference rootType = RPackage.eINSTANCE.getRdsFile_RootType();
        assertThat(referencing(set, v, rootType)).isEmpty();

        InverseReferences.install(set);
        assertThat(referencing(set, v, rootType)).containsExactly(rds);
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

    private RVectorType vector(SexpType type, int length) {
        RVectorType v = r.createRVectorType();
        v.setName(type.getName());
        v.setSexpType(type);
        v.setLength(length);
        v.setHasNames(false);
        v.setAllowsNA(true);
        v.setIsObject(false);
        return v;
    }

    private RFactorType factor(int levelCount) {
        Enumeration levels = dt.createEnumeration();
        levels.setName("levels");
        levels.setIsOrdered(false);
        for (int i = 0; i < levelCount; i++) {
            EnumerationLiteral lit = dt.createEnumerationLiteral();
            lit.setName("stufe" + i);
            levels.getLiteral().add(lit);
        }
        RFactorType f = r.createRFactorType();
        f.setName("factor");
        f.setSexpType(SexpType.RINTEGER);
        f.setHasNames(false);
        f.setAllowsNA(true);
        f.setIsOrdered(false);
        f.setIsObject(true);
        f.getRClass().add("factor");
        f.setLevelSet(levels);
        return f;
    }

    private RComponent component(String name, int position, RType type) {
        RComponent c = r.createRComponent();
        c.setName(name);
        c.setPosition(position);
        c.setIsNamed(true);
        c.setType(type);
        return c;
    }

    private RBinding binding(String symbol, RType type, boolean hidden) {
        RBinding b = r.createRBinding();
        b.setName(symbol);
        b.setType(type);
        b.setIsHidden(hidden);
        return b;
    }
}
