/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.foundation.typemapping.TypeSystem;
import org.eclipse.daanse.cwm.model.cwm.foundation.typemapping.TypemappingFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.typemapping.TypemappingPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes.Sql99;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * The SQL types of the SQL standard as a standard set other model files import: a {@code typemapping::TypeSystem}
 * {@value #NAME} that owns one {@code relational::SQLSimpleType} per type, each as {@link SQLSimpleTypes} makes it.
 * A column of a model file may refer to its type in the set; a catalog may as well own its types
 * ({@link SQLSimpleTypes#ownedIn}). Both stay.
 *
 * <p>The types are the 23 SQL-99 types the CWM specification lists (section 19.5) and {@code BIGINT}, which came with
 * SQL:2003: 24 types, none of one product. CWM gives no type numbers; the numbers are those of
 * {@code java.sql.Types}, as its section 19.6 suggests. The set is the file {@value #SET_FILE} beside this class under
 * the URI {@value #SET_URI}: {@link #standardSet()} builds it, {@link #load(ResourceSet)} reads it from the jar, and a
 * model file refers to a type as {@code href="<SET_URI>#<id>"}, the id the UUID stated beside the type here.</p>
 */
public final class StandardSqlTypes {

    /** The name of the type system. */
    public static final String NAME = "SQL";

    /** The version of the type system: the editions of the standard its types come from. */
    public static final String VERSION = "SQL:1999, BIGINT of SQL:2003";

    /** The URI of the standard set, under which a model file refers to its types. */
    public static final String SET_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.cwm.resource.relational/sql-types.xmi";

    /** The file of the standard set, beside this class in the jar. */
    public static final String SET_FILE = "sql-types.xmi";

    private StandardSqlTypes() {
    }

    /**
     * The standard set, built in memory with its ids, the type system its root: the SQL-99 types in the order of the
     * CWM specification, as {@link SQLSimpleTypes} makes them, then {@code BIGINT}.
     */
    public static XMIResource standardSet() {
        XMIResource set = StandardSets.create(SET_URI);
        TypeSystem sql = TypemappingFactory.eINSTANCE.createTypeSystem();
        sql.setName(NAME);
        sql.setVersion(VERSION);
        set.getContents().add(sql);
        StandardSets.id(sql, "_Ldvh4-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.bitType(), "_Ldvh0eUIEfGl4IEUgfF4xA");
        add(sql, Sql99.bitVaryingType(), "_Ldvh0uUIEfGl4IEUgfF4xA");
        add(sql, Sql99.binaryLargeObjectType(), "_Ldvh0-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.characterType(), "_Ldvhy-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.characterVaryingType(), "_LdvhzOUIEfGl4IEUgfF4xA");
        add(sql, Sql99.characterLargeObjectType(), "_LdvhzeUIEfGl4IEUgfF4xA");
        add(sql, Sql99.nationalCharacterType(), "_LdvhzuUIEfGl4IEUgfF4xA");
        add(sql, Sql99.nationalCharacterVaryingType(), "_Ldvhz-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.nationalCharacterLargeObjectType(), "_Ldvh0OUIEfGl4IEUgfF4xA");
        add(sql, Sql99.numericType(), "_Ldvh1OUIEfGl4IEUgfF4xA");
        add(sql, Sql99.decimalType(), "_Ldvh1eUIEfGl4IEUgfF4xA");
        add(sql, Sql99.integerType(), "_Ldvh1-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.smallintType(), "_Ldvh1uUIEfGl4IEUgfF4xA");
        add(sql, Sql99.floatType(), "_Ldvh2eUIEfGl4IEUgfF4xA");
        add(sql, Sql99.realType(), "_Ldvh2uUIEfGl4IEUgfF4xA");
        add(sql, Sql99.doublePrecisionType(), "_Ldvh2-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.booleanType(), "_Ldvh3OUIEfGl4IEUgfF4xA");
        add(sql, Sql99.dateType(), "_Ldvh3eUIEfGl4IEUgfF4xA");
        add(sql, Sql99.timeType(), "_Ldvh3uUIEfGl4IEUgfF4xA");
        add(sql, Sql99.timeWithTimezoneType(), "_Ldvh3-UIEfGl4IEUgfF4xA");
        add(sql, Sql99.timestampType(), "_Ldvh4OUIEfGl4IEUgfF4xA");
        add(sql, Sql99.timestampWithTimezoneType(), "_Ldvh4eUIEfGl4IEUgfF4xA");
        add(sql, Sql99.intervalType(), "_Ldvh4uUIEfGl4IEUgfF4xA");
        add(sql, SQLSimpleTypes.bigintType(), "_Ldvh2OUIEfGl4IEUgfF4xA");
        return set;
    }

    /**
     * Maps the standard set in {@code resourceSet} to its file in the jar: a model file there that refers to
     * its types reads them from the jar ({@link StandardSets#map}).
     */
    public static void map(ResourceSet resourceSet) {
        StandardSets.map(resourceSet, SET_URI, StandardSqlTypes.class.getResource(SET_FILE), CorePackage.eINSTANCE,
                TypemappingPackage.eINSTANCE, RelationalPackage.eINSTANCE);
    }

    /**
     * The type system of the standard set in {@code resourceSet}, mapped there and read from the jar where it is not
     * read yet.
     */
    public static TypeSystem load(ResourceSet resourceSet) {
        map(resourceSet);
        return StandardSets.load(resourceSet, SET_URI, TypeSystem.class);
    }

    /**
     * The type of {@code types} that {@code name} names, an alias of {@link SQLSimpleTypes#byName} too
     * ({@code VARCHAR} is {@code CHARACTER VARYING}).
     */
    public static Optional<SQLSimpleType> type(TypeSystem types, String name) {
        Objects.requireNonNull(types, "types");
        return SQLSimpleTypes.byName(name).map(SQLSimpleType::getName)
                .flatMap(canonical -> Namespaces.findOwnedByName(types, SQLSimpleType.class, canonical));
    }

    private static void add(TypeSystem sql, SQLSimpleType type, String id) {
        sql.getOwnedElement().add(type);
        StandardSets.id(type, id);
    }
}
