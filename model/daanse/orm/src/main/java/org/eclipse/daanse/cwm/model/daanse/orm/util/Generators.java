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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.GeneratedValue;
import org.eclipse.daanse.cwm.model.daanse.orm.GenerationType;
import org.eclipse.daanse.cwm.model.daanse.orm.Generator;
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.SequenceGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.TableGenerator;

/**
 * Creates id generators — always at the root, their names are unit-wide — and the generated values
 * of id mappings.
 */
public final class Generators {

    private Generators() {
    }

    /** A sequence generator for the database sequence {@code sequenceName} in {@code schema}. */
    public static SequenceGenerator sequence(EntityMappings em, String name, Schema schema, String sequenceName) {
        SequenceGenerator g = CWMORMFactory.eINSTANCE.createSequenceGenerator();
        g.setName(name);
        g.setCwmSchema(schema);
        g.setSequenceName(sequenceName);
        em.getGenerator().add(g);
        return g;
    }

    /** A table generator over the pk and value columns of the generator table. */
    public static TableGenerator table(EntityMappings em, String name, Column pkColumn, Column valueColumn,
            String pkColumnValue) {
        TableGenerator g = CWMORMFactory.eINSTANCE.createTableGenerator();
        g.setName(name);
        g.setCwmPkColumn(pkColumn);
        g.setCwmValueColumn(valueColumn);
        g.setPkColumnValue(pkColumnValue);
        em.getGenerator().add(g);
        return g;
    }

    /**
     * The generated value of {@code id}: {@code generator} for SEQUENCE and TABLE, {@code null}
     * otherwise. The name is the generator name, or the strategy without generator.
     */
    public static GeneratedValue generated(Id id, GenerationType strategy, Generator generator) {
        GeneratedValue gv = CWMORMFactory.eINSTANCE.createGeneratedValue();
        gv.setStrategy(strategy);
        gv.setGenerator(generator);
        gv.setName(generator != null ? generator.getName() : strategy.getLiteral());
        id.setGeneratedValue(gv);
        return gv;
    }
}
