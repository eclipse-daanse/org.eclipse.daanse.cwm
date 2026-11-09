/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.attribute;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.cls;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.constraint;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.dataType;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.enumeration;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.pkg;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.tagged;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;

/**
 * Rules the corpus does not show, on models built in memory: a model outside any resource,
 * options the corpus cases do not use, and failures no corpus file holds. Every other rule is
 * shown by a corpus case ({@link CorpusTest}).
 */
class ConversionRulesTest {

    @Test
    void aModelOutsideAnyResourceConvertsWithNamespaceUrisFromItsPath() {
        Package root = pkg("Root");
        Package shop = pkg(pkg(root, "Classes"), "shop");
        cls(shop, "Customer");
        Package enums = pkg(shop, "enumerations");
        enumeration(enums, "Status", "OPEN", "CLOSED");

        Result r = CwmToEcore.convert(List.of(root));

        assertThat(r.ePackages()).extracting(EPackage::getName).containsExactly("shop");
        EPackage ep = r.ePackages().get(0);
        assertThat(ep.getNsURI()).isEqualTo(Options.DEFAULT_NS_URI_BASE + "/root/classes/shop");
        assertThat(ep.getESubpackages()).extracting(EPackage::getNsURI)
                .containsExactly(Options.DEFAULT_NS_URI_BASE + "/root/classes/shop/enumerations");
        assertThat(r.trace()).containsKeys(shop, enums).doesNotContainKeys(root);
    }

    @Test
    void packagesOptionSelectsByQualifiedName() {
        Package root = pkg("Root");
        cls(pkg(root, "a"), "A");
        cls(pkg(root, "b"), "B");

        Result r = CwmToEcore.convert(List.of(root), Options.defaults().withPackages(List.of("Root.b")));

        assertThat(r.ePackages()).extracting(EPackage::getName).containsExactly("b");
    }

    @Test
    void reusedEcoreDataTypesStayUntouched() {
        Package root = pkg("Root");
        DataType string = dataType(pkg(root, "datatypes"), "String");
        Class c = cls(pkg(root, "model"), "C");
        Attribute a = attribute(c, "name", string);

        Result r = CwmToEcore.convert(List.of(root), Options.defaults().withReuseEcoreDataTypes(true));

        assertThat(r.ePackages()).extracting(EPackage::getName).containsExactly("model");
        assertThat(r.get(a, EAttribute.class).getEType()).isSameAs(EcorePackage.Literals.ESTRING);
        assertThat(EcorePackage.Literals.ESTRING.getEAnnotation(CwmAnnotations.ELEMENT)).isNull();
    }

    @Test
    void anotherOclDelegateUriCanBeChosen() {
        Package p = pkg("p");
        DataType integer = dataType(p, "Integer");
        Class account = cls(p, "Account");
        attribute(account, "balance", integer);
        constraint(p, "Positive", "OCL", "self.balance > 0", account);
        String pivot = "http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot";

        Result r = CwmToEcore.convert(List.of(p), Options.defaults().withOclDelegateUri(pivot));

        EClass ec = r.get(account, EClass.class);
        assertThat(EcoreUtil.getAnnotation(ec, pivot, "Positive")).isEqualTo("self.balance > 0");
        assertThat(EcoreUtil.getValidationDelegates(ec.getEPackage())).containsExactly(pivot);
    }

    @Test
    void withoutXmiIdTheTraceHoldsTheUriFragment() {
        Package p = pkg("lib");
        Class c = cls(p, "Book");
        Attribute title = attribute(c, "title", dataType(p, "String"));
        XMIResourceImpl res = new XMIResourceImpl(URI.createURI("lib.xmi"));
        res.getContents().add(p);
        res.setID(c, "Class_lib.Book");

        Result r = CwmToEcore.convert(List.of(p));

        assertThat(CwmAnnotations.id(r.get(c, EClass.class))).isEqualTo("Class_lib.Book");
        EAttribute eTitle = r.get(title, EAttribute.class);
        assertThat(CwmAnnotations.id(eTitle)).isEqualTo(res.getURIFragment(title));
        assertThat(CwmAnnotations.path(eTitle)).isEqualTo("lib.Book.title");
    }

    @Test
    void aFeatureOfAnotherKindIsNotConverted() {
        Package p = pkg("p");
        Class c = cls(p, "C");
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setName("code");
        column.setType(dataType(p, "String"));
        c.getFeature().add(column);

        Result r = CwmToEcore.convert(List.of(p));

        assertThat(r.notes()).extracting(Note::rule).containsExactly("FEATURE");
        assertThat(r.get(c, EClass.class).getEStructuralFeatures()).isEmpty();
    }

    @Test
    void aControlTagWithAValueOfTheWrongTypeIsReported() {
        Package p = pkg("p");
        Class c = cls(p, "C");
        tagged(attribute(c, "name", dataType(p, "String")), "daanse.ecore.lowerBound", "one");

        Result r = CwmToEcore.convert(List.of(p));

        assertThat(r.notes()).extracting(Note::toString)
                .containsExactly("WARNING TAG p.C.name: tag daanse.ecore.lowerBound: 'one' is no EInt");
    }

    @Test
    void aClassifierOfAnUnregisteredMetamodelIsAnError() {
        Package p = pkg("p");
        tagged(cls(p, "Missing"), CwmAnnotations.TAG_E_CLASSIFIER, "http://example.org/none#//Missing");

        Result r = CwmToEcore.convert(List.of(p));

        assertThat(r.errors()).extracting(Note::rule).containsExactly("TYPE");
    }

    @Test
    void aSecondDerivationOfAFeatureIsReported() {
        Package p = pkg("p");
        Class c = cls(p, "C");
        DataType text = dataType(p, "String");
        attribute(c, "mid", text);
        Attribute derived = tagged(attribute(c, "upper", text), CwmAnnotations.TAG_DERIVED, "true");
        constraint(p, "first", "Java", "return getMid();", derived);
        constraint(p, "second", "Java", "return null;", derived);

        Result r = CwmToEcore.convert(List.of(p));

        EAttribute upper = r.get(derived, EAttribute.class);
        assertThat(EcoreUtil.getAnnotation(upper, "http://www.eclipse.org/emf/2002/GenModel", "get"))
                .isEqualTo("return getMid();");
        assertThat(r.notes()).extracting(Note::rule).containsExactly("CONSTRAINT");
    }

    @Test
    void theBasePackageIsNotTakenFromTheFurtherGenModelProperties() {
        Package p = pkg("p");
        cls(p, "C");
        Options options = Options.defaults().withGenModelAnnotations(Map.of("basePackage", "org.other", "x", "y"));

        Result r = CwmToEcore.convert(List.of(p), options);

        assertThat(options.genModelAnnotations()).isEqualTo(Map.of("x", "y"));
        assertThat(r.notes()).extracting(Note::toString).containsExactly(
                "WARNING GENMODEL basePackage: not taken from the GenModel annotations: the base package has a"
                        + " setting of its own");
        assertThat(EcoreUtil.getAnnotation(r.ePackages().get(0), "http://www.eclipse.org/emf/2002/GenModel",
                "basePackage")).isNull();
    }

    @Test
    void javaNames() {
        assertThat(ConversionState.javaName("byte[]")).isEqualTo("byteArray");
        assertThat(ConversionState.javaName("first name")).isEqualTo("firstName");
        assertThat(ConversionState.javaName("2nd")).isEqualTo("_2nd");
        assertThat(ConversionState.javaName("café")).as("letters of any script stay").isEqualTo("café");
        assertThat(ConversionState.javaName("a-b.c")).isEqualTo("aBC");
        assertThat(ConversionState.javaName("")).isEqualTo("_");
        assertThat(ConversionState.javaName(null)).isEqualTo("_");
    }
}
