/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import java.util.List;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CallbackKind;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityListener;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;
import org.eclipse.daanse.cwm.model.daanse.orm.LifecycleCallback;
import org.eclipse.daanse.cwm.model.daanse.orm.PersistenceUnitDefaults;

/**
 * Creates entity listeners and lifecycle callbacks. A listener's name is its class name, a
 * callback's name the name of its operation.
 */
public final class Listeners {

    private Listeners() {
    }

    /** Defines {@code cls} as entity listener in {@code em}. */
    public static EntityListener create(EntityMappings em, Class cls) {
        EntityListener l = CWMORMFactory.eINSTANCE.createEntityListener();
        l.setName(cls.getName());
        l.setCwmClass(cls);
        em.getEntityListener().add(l);
        return l;
    }

    /** A callback of the listener: {@code operation} of the listener class runs on {@code kind}. */
    public static LifecycleCallback callback(EntityListener listener, CallbackKind kind, Operation operation) {
        LifecycleCallback c = callback(kind, operation);
        listener.getLifecycleCallback().add(c);
        return c;
    }

    /** A callback of the type: {@code operation} of its own class runs on {@code kind}. */
    public static LifecycleCallback callback(IdentifiableType type, CallbackKind kind, Operation operation) {
        LifecycleCallback c = callback(kind, operation);
        type.getLifecycleCallback().add(c);
        return c;
    }

    /** Lets {@code type} use the listeners, in invocation order. */
    public static void use(IdentifiableType type, EntityListener... listeners) {
        type.getListener().addAll(List.of(listeners));
    }

    /** Makes the listeners default listeners of the persistence unit, in invocation order. */
    public static void defaults(PersistenceUnitDefaults defaults, EntityListener... listeners) {
        defaults.getListener().addAll(List.of(listeners));
    }

    private static LifecycleCallback callback(CallbackKind kind, Operation operation) {
        LifecycleCallback c = CWMORMFactory.eINSTANCE.createLifecycleCallback();
        c.setName(operation.getName());
        c.setKind(kind);
        c.setCwmOperation(operation);
        return c;
    }
}
