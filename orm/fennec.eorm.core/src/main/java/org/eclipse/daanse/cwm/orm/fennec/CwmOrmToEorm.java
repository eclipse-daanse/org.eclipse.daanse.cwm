/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Converts a CWM-ORM mapping ({@code org.eclipse.daanse.cwm.model.daanse.orm}) into the
 * {@code EntityMappings} model of Eclipse Fennec Persistence.
 *
 * <p>The mapping must satisfy the explicit profile of {@link OrmModelValidator}: every column,
 * join, table and inheritance detail is stated in the CWM model, so the EORM model states it too
 * and nothing is left to JPA or Fennec defaults. A mapping with errors is rejected
 * ({@link InvalidMappingException}) unless the conversion is lenient, which only makes sense for
 * reports.</p>
 *
 * <p>Classes and features are EORM references to Ecore ({@code Entity.class},
 * {@code accessibleObject}, {@code enumerated}, …): the {@link EcoreIndex} finds the Ecore element of
 * each CWM element by names. Fennec reads the result as a {@code .eorm} file next to the
 * {@code .ecore} ({@link EormWriter}).</p>
 */
public final class CwmOrmToEorm {

    /**
     * The outcome of a conversion.
     *
     * @param eorm  the Fennec mapping set
     * @param notes the findings: what EORM cannot hold, what Fennec does not read, validator
     *              findings in lenient mode; the rules are those of {@link EormNotes}
     */
    public record Result(org.eclipse.fennec.persistence.eorm.EntityMappings eorm, List<Note> notes) {

        /** The notes of severity {@link Note.Severity#ERROR}. */
        public List<Note> errors() {
            return notes.stream().filter(n -> n.severity() == Note.Severity.ERROR).toList();
        }
    }

    private CwmOrmToEorm() {
    }

    /**
     * Converts a mapping set that satisfies the explicit profile.
     *
     * @throws InvalidMappingException if the validator reports errors
     */
    public static Result convert(EntityMappings cwm, EcoreIndex ecore) {
        return convert(cwm, ecore, false);
    }

    /**
     * Converts a mapping set; {@code lenient} converts despite validator errors and reports them
     * as notes instead of throwing.
     */
    public static Result convert(EntityMappings cwm, EcoreIndex ecore, boolean lenient) {
        List<Note> notes = new ArrayList<>();
        List<OrmModelValidator.Finding> errors = new ArrayList<>();
        for (OrmModelValidator.Finding f : OrmModelValidator.check(cwm)) {
            boolean error = f.severity() == OrmModelValidator.Severity.ERROR;
            if (error) {
                errors.add(f);
            }
            String where = f.element() instanceof org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement me
                    ? CwmPath.of(me)
                    : CwmPath.of(cwm);
            String message = f.rule() + " " + f.message();
            notes.add(error ? Note.error(EormNotes.VALIDATION, where, message)
                    : Note.warning(EormNotes.VALIDATION, where, message));
        }
        if (!errors.isEmpty() && !lenient) {
            throw new InvalidMappingException(cwm.getName(), errors);
        }
        EormConversion conversion = new EormConversion(cwm, ecore);
        var eorm = conversion.run();
        notes.addAll(conversion.notes());
        return new Result(eorm, List.copyOf(notes));
    }

    /**
     * Rejects a mapping set that breaks the explicit profile, before anything is converted or
     * written.
     *
     * @throws InvalidMappingException with the error findings of {@link OrmModelValidator}
     */
    public static void requireExplicit(EntityMappings cwm) {
        List<OrmModelValidator.Finding> errors = OrmModelValidator.check(cwm).stream()
                .filter(f -> f.severity() == OrmModelValidator.Severity.ERROR).toList();
        if (!errors.isEmpty()) {
            throw new InvalidMappingException(cwm.getName(), errors);
        }
    }

    /** All CWM-ORM mapping sets in a resource: roots and nested ones (e.g. inside a CWM model). */
    public static List<EntityMappings> mappingsIn(Resource resource) {
        List<EntityMappings> out = new ArrayList<>();
        for (TreeIterator<EObject> it = resource.getAllContents(); it.hasNext();) {
            if (it.next() instanceof EntityMappings em) {
                out.add(em);
                it.prune();
            }
        }
        return out;
    }
}
