/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.dcat.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;

/**
 * What the examples ask of a whole model: the rules it breaks, read from the EMF check with the
 * OCL invariants of the model evaluated by the classic OCL delegate, and one resource set over
 * several models with the inverse index installed, so that the hidden roles answer.
 */
public final class Checks {

    /** The message EMF gives a violated constraint: {@code The 'X' constraint is violated on '...'}. */
    private static final Pattern VIOLATED = Pattern.compile("The '([A-Za-z0-9]+)' constraint is violated on .*",
            Pattern.DOTALL);

    static {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    private Checks() {
    }

    /**
     * What the EMF check of {@code roots} reports, one line per diagnostic: the name of the rule and
     * the name of the element for a violated constraint ({@code AccessUrlUnderEndpoint pools-geojson}),
     * the message for anything else. Empty for a model that keeps every rule.
     */
    public static List<String> findings(EObject... roots) {
        List<String> lines = new ArrayList<>();
        for (EObject root : roots) {
            collect(Diagnostician.INSTANCE.validate(root), lines);
        }
        return lines;
    }

    private static void collect(Diagnostic diagnostic, List<String> lines) {
        if (!diagnostic.getChildren().isEmpty()) {
            diagnostic.getChildren().forEach(child -> collect(child, lines));
            return;
        }
        if (diagnostic.getSeverity() == Diagnostic.OK) {
            return;
        }
        Matcher violated = VIOLATED.matcher(diagnostic.getMessage());
        Object first = diagnostic.getData().isEmpty() ? null : diagnostic.getData().get(0);
        lines.add(violated.matches() && first instanceof EObject element
                ? violated.group(1) + " " + nameOf(element)
                : diagnostic.getMessage());
    }

    private static String nameOf(EObject element) {
        EStructuralFeature name = element.eClass().getEStructuralFeature("name");
        Object value = name == null ? null : element.eGet(name);
        return value == null ? element.eClass().getName() : value.toString();
    }

    /** One resource set holding each of {@code roots} in a resource of its own, with the inverse index installed. */
    public static ResourceSet indexed(EObject... roots) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        InverseReferences.install(resourceSet);
        int n = 0;
        for (EObject root : roots) {
            Resource resource = resourceSet.createResource(URI.createURI("model-" + n++ + ".xmi"));
            resource.getContents().add(root);
        }
        return resourceSet;
    }
}
