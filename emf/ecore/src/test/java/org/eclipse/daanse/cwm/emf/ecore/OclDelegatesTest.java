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
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.cls;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.constraint;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.dataType;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.method;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.operation;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.parameter;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The invariants and OCL operation bodies the converter writes, evaluated by classic Eclipse OCL
 * as EMF validation and invocation delegates on dynamic instances of the generated classes: an
 * account that must not be overdrawn, its doubled balance, and a label that a subclass overrides.
 * A Java invariant of the same class does not disturb the dynamic validation: EMF evaluates
 * invariant operations without OCL body only in generated code.
 */
class OclDelegatesTest {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;

    private static Class account;
    private static Class savings;
    private static Attribute balance;
    private static Operation doubled;
    private static Operation label;
    private static Result result;

    @BeforeAll
    static void convert() {
        // the standalone registrations of classic OCL: delegates for the LPG evaluator and the
        // mapping of the virtual Ecore/OCL URI onto it
        OCLDelegateDomain.initialize(null);
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());

        Package bank = CORE.createPackage();
        bank.setName("bank");
        DataType integer = dataType("Integer");
        DataType string = dataType("String");
        account = cls("Account");
        savings = cls("Savings");
        balance = CORE.createAttribute();
        balance.setName("balance");
        balance.setType(integer);
        doubled = operation("doubled", integer);
        label = operation("label", string, parameter("prefix", string, ParameterDirectionKind.PDK_IN));
        account.getFeature().addAll(List.of(balance, doubled, label,
                method("doubled", doubled, "OCL", "self.balance * 2"),
                method("label", label, "OCL", "prefix.concat('account')")));
        savings.getFeature().add(method("label", label, "OCL", "prefix.concat('savings')"));
        Generalization g = RelationshipsFactory.eINSTANCE.createGeneralization();
        g.setName("Savings is an Account");
        g.setChild(savings);
        g.setParent(account);
        bank.getOwnedElement().addAll(List.of(integer, string, account, savings, g,
                constraint("NotOverdrawn", "OCL", "self.balance >= 0", account),
                constraint("BalanceKnown", "OCL", "not self.balance.oclIsUndefined()", balance),
                constraint("InJava", "Java", "getBalance() != null", account)));

        result = CwmToEcore.convert(List.of(bank));
        assertThat(result.notes()).isEmpty();
    }

    @Test
    void invariantsHoldOrAreViolatedOnInstancesOfTheClassAndItsSubclasses() {
        EObject ok = account(10);
        EObject overdrawn = account(-1);
        EObject unknown = EcoreUtil.create(eClass(account));
        EObject overdrawnSavings = EcoreUtil.create(eClass(savings));
        overdrawnSavings.eSet(eClass(account).getEStructuralFeature("balance"), -5L);

        assertThat(Diagnostician.INSTANCE.validate(ok).getSeverity()).isEqualTo(Diagnostic.OK);
        assertThat(violated(overdrawn)).containsExactly("NotOverdrawn");
        assertThat(violated(unknown)).contains("BalanceKnown");
        assertThat(violated(overdrawnSavings)).as("inherited by the subclass").containsExactly("NotOverdrawn");
        assertThat(eClass(account).getEOperations()).as("the Java invariant is there, but needs generated code")
                .anyMatch(o -> o.getName().equals("InJava") && EcoreUtil.isInvariant(o));
    }

    @Test
    void oclBodiesAreEvaluatedAndTheSubclassBodyOverrides() {
        EObject plain = account(21);
        EObject saving = EcoreUtil.create(eClass(savings));
        EOperation eDoubled = result.get(doubled, EOperation.class);
        EOperation eLabel = result.get(label, EOperation.class);

        assertThat(invoke(plain, eDoubled)).isInstanceOfSatisfying(Number.class,
                n -> assertThat(n.longValue()).isEqualTo(42L));
        assertThat(invoke(plain, eLabel, "my ")).isEqualTo("my account");
        assertThat(invoke(saving, eLabel, "my ")).isEqualTo("my savings");
    }

    @Test
    void fromTheWrittenCorpusFilesInvariantsBodiesAndDerivationsAreEvaluated() {
        ResourceSet rs = CwmPackages.newResourceSet();
        EPackage store = expected(rs, "behaviour", "store.ecore");
        EPackage bank = expected(rs, "constraints", "bank.ecore");
        EPackage mapping = expected(rs, "ecore-forms", "mapping.ecore");

        EClass product = (EClass) store.getEClassifier("Product");
        EObject p = EcoreUtil.create(product);
        p.eSet(product.getEStructuralFeature("quantity"), 21L);
        EOperation price = ((EClass) store.getEClassifier("Priced")).getEOperations().get(0);
        assertThat(invoke(p, price)).as("the interface operation, overridden in Product")
                .isInstanceOfSatisfying(Number.class, n -> assertThat(n.longValue()).isEqualTo(42L));
        EObject book = EcoreUtil.create((EClass) store.getEClassifier("Book"));
        assertThat(invoke(book, product.getEOperations().stream().filter(o -> o.getName().equals("describe"))
                .findFirst().orElseThrow())).isEqualTo("book");

        EClass account = (EClass) bank.getEClassifier("Account");
        EObject overdrawn = EcoreUtil.create(account);
        overdrawn.eSet(account.getEStructuralFeature("balance"), -1L);
        assertThat(violated(overdrawn)).contains("NotOverdrawn");

        EClass m = (EClass) mapping.getEClassifier("Mapping");
        EObject o = EcoreUtil.create(m);
        o.eSet(m.getEStructuralFeature("mid"), "a");
        assertThat(o.eGet(m.getEStructuralFeature("shout"))).as("OCL derivation").isEqualTo("a!");
    }

    private static EPackage expected(ResourceSet rs, String corpusCase, String file) {
        return (EPackage) CwmPackages.load(rs, CorpusCase.dir().resolve("expected").resolve(corpusCase).resolve(file))
                .getContents().get(0);
    }

    private static List<String> violated(EObject o) {
        Diagnostic d = Diagnostician.INSTANCE.validate(o);
        return d.getChildren().stream().filter(c -> c.getSeverity() >= Diagnostic.WARNING)
                .map(c -> constraintName(c.getMessage())).toList();
    }

    /** The constraint name from EMF's message "The 'NAME' constraint is violated on '…'". */
    private static String constraintName(String message) {
        int start = message.indexOf('\'') + 1;
        return message.substring(start, message.indexOf('\'', start));
    }

    private static Object invoke(EObject target, EOperation op, Object... args) {
        try {
            return target.eInvoke(op, ECollections.asEList(args));
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private static EObject account(long value) {
        EObject o = EcoreUtil.create(eClass(account));
        o.eSet(eClass(account).getEStructuralFeature("balance"), value);
        return o;
    }

    private static EClass eClass(Class c) {
        return result.get(c, EClass.class);
    }
}
