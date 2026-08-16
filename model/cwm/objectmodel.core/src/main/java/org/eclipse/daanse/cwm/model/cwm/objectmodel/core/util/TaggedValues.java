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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;

/**
 * Helpers around the {@code ModelElement.taggedValue} containment, the CWM
 * name/value annotation mechanism.
 *
 * <p>CWM constraint C-3-4 demands at most one tagged value per tag on an
 * element; {@link #set(ModelElement, String, String)} therefore upserts —
 * setting an existing tag replaces its value instead of adding a duplicate.
 * {@code tag} and {@code value} are mandatory in CWM, so a {@code null} value
 * is stored as the empty string.
 */
public final class TaggedValues {

    private TaggedValues() {
    }

    /** All tagged values of {@code element}, in declaration order. */
    public static List<TaggedValue> all(ModelElement element) {
        return element == null ? List.of() : element.getTaggedValue();
    }

    /** The value stored under {@code tag}, if any. */
    public static Optional<String> value(ModelElement element, String tag) {
        return find(element, tag).map(TaggedValue::getValue);
    }

    /** The tagged value stored under {@code tag}, if any. */
    public static Optional<TaggedValue> find(ModelElement element, String tag) {
        if (element == null || tag == null) {
            return Optional.empty();
        }
        return element.getTaggedValue().stream()
                .filter(tv -> tag.equals(tv.getTag()))
                .findFirst();
    }

    /**
     * Upserts the tagged value {@code tag} = {@code value} on {@code element}
     * and returns it. A {@code null} value is stored as {@code ""} (both ends
     * are mandatory in CWM); an existing tag is updated in place, keeping
     * C-3-4 (unique tags per element) true by construction.
     */
    public static TaggedValue set(ModelElement element, String tag, String value) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(tag, "tag");
        String v = value == null ? "" : value;
        Optional<TaggedValue> existing = find(element, tag);
        if (existing.isPresent()) {
            existing.get().setValue(v);
            return existing.get();
        }
        TaggedValue tv = CoreFactory.eINSTANCE.createTaggedValue();
        tv.setTag(tag);
        tv.setValue(v);
        element.getTaggedValue().add(tv);
        return tv;
    }
}
