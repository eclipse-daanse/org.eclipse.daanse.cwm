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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;

public final class InverseReferences {

    private static final Logger LOGGER = System.getLogger(InverseReferences.class.getName());
    private static final AtomicBoolean MISSING_ADAPTER_WARNED = new AtomicBoolean(false);

    /**
     * The order every lookup comes back in: by name, then by containment path. The index behind
     * these lookups is keyed by object identity, so its raw order is the machine's — it shifts
     * with allocation history (and with it, the classpath) while the model is unchanged. Anything
     * ordered downstream of a lookup (page sections, diagram nodes, layout) would inherit that
     * shift; sorting here, at the source, is what keeps every consumer deterministic.
     */
    private static final Comparator<EObject> STABLE =
            Comparator.comparing(InverseReferences::nameOf)
                    .thenComparing(o -> {
                        String fragment = EcoreUtil.getURI(o).fragment();
                        return fragment == null ? "" : fragment;
                    });

    private static String nameOf(EObject element) {
        EStructuralFeature name = element.eClass().getEStructuralFeature("name");
        Object value = name == null ? null : element.eGet(name);
        return value == null ? "" : value.toString();
    }

    private InverseReferences() {
    }

    /** Installs the inverse index on {@code notifier}, normally the ResourceSet. Idempotent. */
    public static ECrossReferenceAdapter install(Notifier notifier) {
        if (notifier == null) {
            return null;
        }
        ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(notifier);
        if (adapter == null) {
            adapter = new ECrossReferenceAdapter();
            notifier.eAdapters().add(adapter);
        }
        return adapter;
    }

    /** Whether an inverse index is available for {@code target}. */
    public static boolean isIndexed(EObject target) {
        return target != null && ECrossReferenceAdapter.getCrossReferenceAdapter(target) != null;
    }

    /**
     * Objects of {@code type} whose {@code reference} points at {@code target}. O(1) with
     * {@link #install}, otherwise an O(model) sweep.
     */
    public static <T extends EObject> Stream<T> referencing(EObject target, EReference reference, Class<T> type) {
        if (target == null || reference == null) {
            return Stream.empty();
        }
        ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(target);
        Iterable<EStructuralFeature.Setting> settings = adapter != null
                ? adapter.getNonNavigableInverseReferences(target)
                : scan(target);
        return toStream(settings).filter(s -> s.getEStructuralFeature() == reference)
                .map(EStructuralFeature.Setting::getEObject).filter(type::isInstance).map(type::cast)
                .sorted(STABLE);
    }

    /** List-returning twin of {@link #referencing}. */
    public static <T extends EObject> List<T> referencingList(EObject target, EReference reference, Class<T> type) {
        return referencing(target, reference, type).toList();
    }

    /**
     * Name-based variant of {@link #referencing} for models loaded dynamically from
     * {@code .ecore}, where neither the generated {@code Literals} nor {@code Class}
     * can match.
     *
     * @param referenceName name of the reference pointing at {@code target}
     * @param eClassName    name of the referencing class; {@code null} accepts any
     */
    public static Stream<EObject> referencingByName(EObject target, String referenceName, String eClassName) {
        if (target == null || referenceName == null) {
            return Stream.empty();
        }
        ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(target);
        Iterable<EStructuralFeature.Setting> settings = adapter != null
                ? adapter.getNonNavigableInverseReferences(target)
                : scan(target);
        return toStream(settings).filter(s -> referenceName.equals(s.getEStructuralFeature().getName()))
                .map(EStructuralFeature.Setting::getEObject)
                .filter(o -> eClassName == null || eClassName.equals(o.eClass().getName()))
                .sorted(STABLE);
    }

    /** Adapter-free fallback: sweeps the ResourceSet, else the Resource, else the containment tree. */
    private static Iterable<EStructuralFeature.Setting> scan(EObject target) {
        Resource resource = target.eResource();
        if (resource != null) {
            // Attached to a resource but reached the fallback: the adapter was not
            // installed. Warn once, name the fix. A bare in-memory tree (resource == null)
            // is legitimate throw-away use and is not warned about.
            warnMissingAdapterOnce();
            return resource.getResourceSet() != null
                    ? EcoreUtil.UsageCrossReferencer.find(target, resource.getResourceSet())
                    : EcoreUtil.UsageCrossReferencer.find(target, resource);
        }
        return EcoreUtil.UsageCrossReferencer.find(target, EcoreUtil.getRootContainer(target));
    }

    private static void warnMissingAdapterOnce() {
        if (MISSING_ADAPTER_WARNED.compareAndSet(false, true)) {
            LOGGER.log(Level.WARNING,
                    "No ECrossReferenceAdapter installed on the ResourceSet; CWM reverse lookups "
                            + "(Descriptions/Documents/ResponsibleParties) run as O(model) scans. "
                            + "Call InverseReferences.install(resourceSet) once after loading for O(1) lookups.");
        }
    }

    private static Stream<EStructuralFeature.Setting> toStream(Iterable<EStructuralFeature.Setting> settings) {
        return java.util.stream.StreamSupport.stream(settings.spliterator(), false);
    }
}
