/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.build;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code orm} metamodel — the plain
 * CWM object/relational mapping model of the Daanse world.
 *
 * <p>The model carries the functional scope of the Jakarta Persistence 3.1 mappings
 * (orm.xml), but deliberately does not set out to write the XML itself: no
 * ExtendedMetaData, no DocumentRoot, no derived JPA name strings and no
 * derivation OCL. The {@code cwm*} references are the single truth — class side
 * {@code core::Class}/{@code core::Attribute}/{@code relationships::AssociationEnd}/
 * {@code behavioral::Operation}, table side {@code relational::NamedColumnSet}/{@code Column}/
 * {@code ForeignKey}/{@code QueryColumnSet}/{@code Procedure}. What follows from an anchor
 * (schema, catalog, primary key, table of a join form, SQL type, nullability) has no
 * feature of its own; the derivations live in {@code OrmBindings}.</p>
 *
 * <p>Every topic has exactly one place: join columns and foreign keys in {@code Join},
 * tables with a join in {@code JoinedTable}, overrides and conversions in
 * {@code OverrideHost}, the map key in {@code MapKey}, generators, queries,
 * result-set mappings, converters and listeners only in {@code EntityMappings}. Paths and
 * class references are typed references, never strings.</p>
 *
 * <p>All concrete classes extend {@code OrmElement ⊂ core::ModelElement} (name,
 * visibility and tagged values inherited); {@code EntityMappings} and {@code ManagedType} are
 * also {@code core::Namespace}. Non-containment references are hidden
 * association ends with {@code Property.oppositeRoleName} pairs (prefix {@code orm});
 * backward navigation via InverseReferences.</p>
 *
 * <p>Construction and serialization follow {@code model/daanse/dcat/.../DcatEcoreBuilder}.</p>
 */
public final class OrmEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String BASE_NS =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.orm";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final CwmRefs cwm;

    /** Holds the target classifiers resolved in the CWM Ecores for cross-resource references. */
    public static final class CwmRefs {
        final EClass modelElement;
        final EClass namespace;
        final EClass cwmClass;
        final EClass classifier;
        final EClass attribute;
        final EClass structuralFeature;
        final EClass associationEnd;
        final EClass operation;
        final EClass column;
        final EClass namedColumnSet;
        final EClass queryColumnSet;
        final EClass schema;
        final EClass foreignKey;
        final EClass procedure;
        final EDataType stringType;
        final EDataType booleanType;
        final EDataType integerType;

        CwmRefs(EPackage core, EPackage behavioral, EPackage relationships, EPackage relational) {
            this.modelElement = eClass(core, "ModelElement");
            this.namespace = eClass(core, "Namespace");
            this.cwmClass = eClass(core, "Class");
            this.classifier = eClass(core, "Classifier");
            this.attribute = eClass(core, "Attribute");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.associationEnd = eClass(relationships, "AssociationEnd");
            this.operation = eClass(behavioral, "Operation");
            this.column = eClass(relational, "Column");
            this.namedColumnSet = eClass(relational, "NamedColumnSet");
            this.queryColumnSet = eClass(relational, "QueryColumnSet");
            this.schema = eClass(relational, "Schema");
            this.foreignKey = eClass(relational, "ForeignKey");
            this.procedure = eClass(relational, "Procedure");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.integerType = eDataType(core, "Integer");
        }
    }

    public OrmEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Loads the required CWM units and resolves the target classifiers. The
     * resource URI is set to the nsURI so that cross-references serialize as
     * {@code <nsURI>#//Class}.
     */
    public static CwmRefs loadCwmRefs(ResourceSet rs, URI coreEcoreUri, URI behavioralEcoreUri,
            URI relationshipsEcoreUri, URI relationalEcoreUri) {
        EPackage core = loadPackage(rs, coreEcoreUri);
        EPackage behavioral = loadPackage(rs, behavioralEcoreUri);
        EPackage relationships = loadPackage(rs, relationshipsEcoreUri);
        EPackage relational = loadPackage(rs, relationalEcoreUri);
        return new CwmRefs(core, behavioral, relationships, relational);
    }

    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        registerRecursive(rs, pkg);
        return pkg;
    }

    private static void registerRecursive(ResourceSet rs, EPackage pkg) {
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        for (EPackage sub : pkg.getESubpackages()) {
            registerRecursive(rs, sub);
        }
    }

    private static EClass eClass(EPackage pkg, String name) {
        EClassifier c = pkg.getEClassifier(name);
        if (!(c instanceof EClass)) {
            throw new IllegalStateException("CWM EClass not found: " + name + " in " + pkg.getName());
        }
        return (EClass) c;
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        EClassifier c = pkg.getEClassifier(name);
        if (!(c instanceof EDataType)) {
            throw new IllegalStateException("CWM EDataType not found: " + name + " in " + pkg.getName());
        }
        return (EDataType) c;
    }

    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        injectLicenseHeader(outUri);
    }

    /** Inserts the EPL-2.0 XML comment header after the XML declaration. */
    private static void injectLicenseHeader(URI outUri) throws IOException {
        String path = outUri.toFileString();
        if (path == null) {
            return;
        }
        java.nio.file.Path file = java.nio.file.Path.of(path);
        String content = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        if (content.contains("<!--\n/****")) {
            return;
        }
        String comment = "<!--\n"
                + "/*********************************************************************\n"
                + "* Copyright (c) 2026 Contributors to the Eclipse Foundation.\n"
                + "*\n"
                + "* This program and the accompanying materials are made\n"
                + "* available under the terms of the Eclipse Public License 2.0\n"
                + "* which is available at https://www.eclipse.org/legal/epl-2.0/\n"
                + "*\n"
                + "* SPDX-License-Identifier: EPL-2.0\n"
                + "**********************************************************************/\n"
                + "-->\n";
        int nl = content.indexOf('\n');
        if (nl < 0) {
            return;
        }
        String result = content.substring(0, nl + 1) + comment + content.substring(nl + 1);
        java.nio.file.Files.writeString(file, result, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Builds the metamodel from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/orm}) and writes it to {@code outFile}.
     */
    public static void generate(java.io.File moduleDir, java.io.File outFile) throws IOException {
        java.io.File cwmDir = new java.io.File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        CwmRefs refs = loadCwmRefs(rs,
                ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"),
                ecoreUri(cwmDir, "objectmodel.behavioral/model/behavioral.ecore"),
                ecoreUri(cwmDir, "objectmodel.relationships/model/relationships.ecore"),
                ecoreUri(cwmDir, "resource.relational/model/relational.ecore"));
        outFile.getParentFile().mkdirs();
        save(new OrmEcoreBuilder(refs).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    private static URI ecoreUri(java.io.File cwmDir, String path) {
        java.io.File f = new java.io.File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    public static ResourceSet newResourceSet() {
        ResourceSet rs = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    /** Creates the EPackage {@code orm}: 61 classes (15 abstract), 8 enums, no subpackages. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("orm");
        p.setNsURI(BASE_NS);
        p.setNsPrefix("orm");
        EAnnotation pa = annotate(p,
                "Plain CWM object/relational mapping model with the functional scope of the Jakarta "
                        + "Persistence 3.1 mappings (orm.xml). It deliberately does not model the XML "
                        + "itself: no ExtendedMetaData, no DocumentRoot, no derived JPA name strings "
                        + "and no derivation OCL. The cwm* references are the single truth — object "
                        + "side core::Class / core::Attribute / relationships::AssociationEnd / "
                        + "behavioral::Operation, table side relational::NamedColumnSet / Column / "
                        + "ForeignKey / QueryColumnSet / Procedure. What follows from an anchor (schema, "
                        + "catalog, primary key, the table of a join form, SQL type, nullability) has no "
                        + "feature of its own. Every topic has exactly one place: join columns and "
                        + "foreign key in Join, tables with a join in JoinedTable, overrides and "
                        + "conversions in OverrideHost, the map key in MapKey; generators, queries, "
                        + "result-set mappings, converters and listeners only in EntityMappings. Nothing "
                        + "is implicit: what JPA would default (persistent attributes, column and join "
                        + "names, inheritance strategy) is stated in the model and checked by "
                        + "OrmModelValidator. All concrete classes extend OrmElement "
                        + "(core::ModelElement); non-containment references are hidden association "
                        + "ends (Property.oppositeRoleName, prefix orm) — backward navigation via "
                        + "InverseReferences.");
        pa.getDetails().put("modelName", "CWMORM");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
        pa.getDetails().put("fileExtensions", "cwmorm");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "CWMORM");

        // Non-containment references are hidden association ends (same convention as dcat).
        EAnnotation ho = ef.createEAnnotation();
        ho.setSource(ECORE_OCL);
        ho.getDetails().put("hiddenOpposites", "true");
        p.getEAnnotations().add(ho);

        EEnum accessType = enm(p, "AccessType",
                "How the persistence provider accesses the state of an entity or embedded object.",
                lit("PROPERTY"), lit("FIELD"));
        EEnum enumType = enm(p, "EnumType",
                "How a Java enum value maps to its column: by ordinal or by name.",
                lit("ORDINAL"), lit("STRING"));
        EEnum fetchType = enm(p, "FetchType",
                "Whether the value is fetched lazily or eagerly.",
                lit("LAZY"), lit("EAGER"));
        EEnum generationType = enm(p, "GenerationType",
                "Primary-key generation strategy. SEQUENCE and TABLE name their generator, "
                        + "IDENTITY, UUID and AUTO have none.",
                lit("TABLE"), lit("SEQUENCE"), lit("IDENTITY"), lit("UUID"), lit("AUTO"));
        EEnum inheritanceType = enm(p, "InheritanceType",
                "Inheritance mapping strategy of an entity hierarchy.",
                lit("SINGLETABLE", "SINGLE_TABLE"), lit("JOINED"),
                lit("TABLEPERCLASS", "TABLE_PER_CLASS"));
        EEnum lockModeType = enm(p, "LockModeType",
                "Lock mode for a named query.",
                lit("READ"), lit("WRITE"), lit("OPTIMISTIC"),
                lit("OPTIMISTICFORCEINCREMENT", "OPTIMISTIC_FORCE_INCREMENT"),
                lit("PESSIMISTICREAD", "PESSIMISTIC_READ"),
                lit("PESSIMISTICWRITE", "PESSIMISTIC_WRITE"),
                lit("PESSIMISTICFORCEINCREMENT", "PESSIMISTIC_FORCE_INCREMENT"),
                lit("NONE"));
        EEnum cascadeKind = enm(p, "CascadeKind",
                "Cascadable operation of a relationship.",
                lit("ALL"), lit("PERSIST"), lit("MERGE"), lit("REMOVE"), lit("REFRESH"),
                lit("DETACH"));
        EEnum callbackKind = enm(p, "CallbackKind",
                "Lifecycle event of a callback method (pre/post persist, remove, update; post load).",
                lit("PREPERSIST", "PRE_PERSIST"), lit("POSTPERSIST", "POST_PERSIST"),
                lit("PREREMOVE", "PRE_REMOVE"), lit("POSTREMOVE", "POST_REMOVE"),
                lit("PREUPDATE", "PRE_UPDATE"), lit("POSTUPDATE", "POST_UPDATE"),
                lit("POSTLOAD", "POST_LOAD"));

        EClass ormElement = cls(p, "OrmElement", true,
                "Abstract local root of the mapping world: every concrete mapping class is a "
                        + "core::ModelElement (inherited name, visibility, taggedValue). Where an "
                        + "element is anchored, its name equals the name of the anchor (the class, "
                        + "attribute, end, column, foreign key, operation or path); an own meaning has "
                        + "the name only for the entity (JPA entity name), the mapping set, generators, "
                        + "queries, hints, result-set mappings, column-result aliases and entity "
                        + "graphs.");
        ormElement.getESuperTypes().add(cwm.modelElement);

        EClass managedType = cls(p, "ManagedType", true,
                "A persistently managed Java type (entity, mapped superclass or embeddable): the "
                        + "cwmClass anchor plus the attribute mappings, held in typed containments. "
                        + "The mapping is complete: every own attribute and every navigable "
                        + "association end of the class has exactly one mapping, a Transient one if "
                        + "it is not persistent.");
        managedType.getESuperTypes().add(ormElement);
        managedType.getESuperTypes().add(cwm.namespace);

        EClass identifiableType = cls(p, "IdentifiableType", true,
                "A managed type with identity: id/embeddedId/version mappings, an optional id "
                        + "class, the entity listeners it uses and its own lifecycle callbacks.");
        identifiableType.getESuperTypes().add(managedType);

        EClass attributeMapping = cls(p, "AttributeMapping", true,
                "One mapped feature of a managed type. Three branches: ValueMapping (a "
                        + "core::Attribute), RelationshipMapping (a relationships::AssociationEnd) and "
                        + "Transient (either, explicitly not persistent).");
        attributeMapping.getESuperTypes().add(ormElement);

        EClass valueMapping = cls(p, "ValueMapping", true,
                "Attribute mapping whose object side is a value: cwmAttribute is the mandatory "
                        + "core::Attribute anchor.");
        valueMapping.getESuperTypes().add(attributeMapping);

        EClass relationshipMapping = cls(p, "RelationshipMapping", true,
                "Attribute mapping whose object side is an association end: cwmAssociationEnd is "
                        + "the mandatory relationships::AssociationEnd anchor. The target entity is "
                        + "cwmAssociationEnd.type — not repeated as a string.");
        relationshipMapping.getESuperTypes().add(attributeMapping);

        EClass ownableRelationship = cls(p, "OwnableRelationship", true,
                "Bidirectionally mappable relationship (one-to-one, one-to-many, many-to-many): "
                        + "the opposite end follows from the association, so the only mapping "
                        + "information is which side owns it (owningSide).");
        ownableRelationship.getESuperTypes().add(relationshipMapping);

        EClass collectionSupport = cls(p, "CollectionSupport", true,
                "Mixin for collection-valued mappings (element collection, one-to-many, "
                        + "many-to-many): ordering, order column and map key.");

        EClass overrideHost = cls(p, "OverrideHost", true,
                "Mixin for the places that re-map features of another type: an entity (inherited "
                        + "features of mapped superclasses), an embedded or embedded-id attribute and "
                        + "an element collection (features of the embeddable) and a map key. Each "
                        + "override and conversion names its feature path as typed references.");

        EClass generator = cls(p, "Generator", true,
                "A primary-key value generator, defined once per mapping set and referenced by "
                        + "GeneratedValue.generator.");
        generator.getESuperTypes().add(ormElement);

        EClass query = cls(p, "Query", true,
                "A named query of the persistence unit (JPQL, native SQL or stored procedure) with "
                        + "its provider hints; the query name is the inherited name.");
        query.getESuperTypes().add(ormElement);

        EClass columnRef = cls(p, "ColumnRef", true,
                "A reference to one catalog column; cwmColumn is the single truth for name, type, "
                        + "length, precision, scale and nullability.");
        columnRef.getESuperTypes().add(ormElement);

        EClass column = cls(p, "Column", false,
                "A mapped column: the catalog column plus the insert/update flags, the only genuine "
                        + "mapping information. Also used for order and map-key columns — the role "
                        + "follows from the containing reference.");
        column.getESuperTypes().add(columnRef);

        EClass joinColumn = cls(p, "JoinColumn", false,
                "One column pair of a join: cwmColumn is the foreign-key column, "
                        + "cwmReferencedColumn the referenced (usually primary-key) column. Insert/"
                        + "update flags belong to the whole Join.");
        joinColumn.getESuperTypes().add(columnRef);

        EClass join = cls(p, "Join", false,
                "How two tables are joined, the one place for join columns and foreign-key policy: "
                        + "either the catalog foreign key (its feature and uniqueKey.feature columns "
                        + "are the join columns) or explicit column pairs when no constraint exists. "
                        + "Used by to-one and one-to-many relationships, the JOINED primary-key join, "
                        + "association overrides, map keys and every JoinedTable.");
        join.getESuperTypes().add(ormElement);

        EClass joinedTable = cls(p, "JoinedTable", true,
                "A further table reached over a join (secondary, collection or join table). The "
                        + "table itself is not repeated: it is the owner of the join columns (or the "
                        + "table owning the foreign key).");
        joinedTable.getESuperTypes().add(ormElement);

        EClass secondaryTable = cls(p, "SecondaryTable", false,
                "An additional table of an entity; its join is the primary-key join to the primary "
                        + "table.");
        secondaryTable.getESuperTypes().add(joinedTable);

        EClass collectionTable = cls(p, "CollectionTable", false,
                "The table holding an element collection; its join leads to the owner's table.");
        collectionTable.getESuperTypes().add(joinedTable);

        EClass joinTable = cls(p, "JoinTable", false,
                "An intersection table mapping a relationship: the join towards the owning side, "
                        + "the inverse join towards the target side.");
        joinTable.getESuperTypes().add(joinedTable);

        EClass mapKey = cls(p, "MapKey", false,
                "The key of a map-valued collection, in one place: an attribute of the target type "
                        + "(cwmAttribute) or, when no attribute is the key, the key type "
                        + "(cwmClassifier) with its column, join, enum mapping, overrides and "
                        + "conversions.");
        mapKey.getESuperTypes().add(ormElement);
        mapKey.getESuperTypes().add(overrideHost);

        EClass ordering = cls(p, "Ordering", false,
                "One ordering criterion of a collection: a feature path into the target type, "
                        + "ascending or descending; the name is the path with dots.");
        ordering.getESuperTypes().add(ormElement);

        EClass attributeOverride = cls(p, "AttributeOverride", false,
                "Overrides the column of an inherited or embedded value mapping. cwmPath is the "
                        + "feature path from the host (e.g. address, street); the name is the path "
                        + "names joined with a dot.");
        attributeOverride.getESuperTypes().add(ormElement);

        EClass associationOverride = cls(p, "AssociationOverride", false,
                "Overrides the join or join table of an inherited or embedded relationship "
                        + "mapping. cwmPath is the feature path from the host; the name is the path "
                        + "names joined with a dot.");
        associationOverride.getESuperTypes().add(ormElement);

        EClass convert = cls(p, "Convert", false,
                "Applies a converter to an attribute or disables conversion for it. An empty "
                        + "cwmPath means the attribute of the mapping itself; on an override host the "
                        + "path leads into the embedded or inherited features.");
        convert.getESuperTypes().add(ormElement);

        EClass converter = cls(p, "Converter", false,
                "Registers an attribute converter: cwmClass is the converter class in the object "
                        + "model, its name the class name.");
        converter.getESuperTypes().add(ormElement);

        EClass entityListener = cls(p, "EntityListener", false,
                "A listener class receiving lifecycle callbacks: cwmClass is the listener class in "
                        + "the object model, its operations are the callback methods.");
        entityListener.getESuperTypes().add(ormElement);

        EClass lifecycleCallback = cls(p, "LifecycleCallback", false,
                "One lifecycle callback: which event (kind) invokes which operation "
                        + "(cwmOperation, owned by the listener or the entity class).");
        lifecycleCallback.getESuperTypes().add(ormElement);

        EClass persistenceUnitMetadata = cls(p, "PersistenceUnitMetadata", false,
                "Metadata that applies to the whole persistence unit. The mapping is always "
                        + "complete; a converter to orm.xml writes metadata-complete.");
        persistenceUnitMetadata.getESuperTypes().add(ormElement);

        EClass persistenceUnitDefaults = cls(p, "PersistenceUnitDefaults", false,
                "Defaults for the persistence unit. Schema and catalog follow from the tables, the "
                        + "access type is EntityMappings.access.");
        persistenceUnitDefaults.getESuperTypes().add(ormElement);

        EClass entityMappings = cls(p, "EntityMappings", false,
                "Root of an object/relational mapping set and the only place of unit-wide "
                        + "definitions: managed types, generators, queries, result-set mappings, "
                        + "converters and entity listeners. The object package and the relational "
                        + "schema follow from the anchors of the managed types.");
        entityMappings.getESuperTypes().add(ormElement);
        entityMappings.getESuperTypes().add(cwm.namespace);

        EClass entity = cls(p, "Entity", false,
                "An entity: cwmClass (inherited) is the Java class, cwmTable its primary table. "
                        + "The JPA entity name is the inherited name; the fully qualified class name "
                        + "follows from the core package chain.");
        entity.getESuperTypes().add(identifiableType);
        entity.getESuperTypes().add(overrideHost);

        EClass mappedSuperclass = cls(p, "MappedSuperclass", false,
                "A mapped superclass: contributes mappings to subclasses, has no table of its own; "
                        + "columns it leaves open are set by every inheriting entity.");
        mappedSuperclass.getESuperTypes().add(identifiableType);

        EClass embeddable = cls(p, "Embeddable", false,
                "An embeddable type: its attribute mappings apply within the owning entity's "
                        + "table(s); columns it leaves open are set by every embedding mapping.");
        embeddable.getESuperTypes().add(managedType);

        EClass base = cls(p, "Base", true,
                "Value mapping with a column: the Column carries the catalog column and the "
                        + "insert/update flags.");
        base.getESuperTypes().add(valueMapping);

        EClass simpleBase = cls(p, "SimpleBase", true,
                "Value mapping with fetch and enum options. Temporal precision and large-object "
                        + "storage follow from the SQL type of the column.");
        simpleBase.getESuperTypes().add(base);

        EClass basic = cls(p, "Basic", false,
                "A basic value mapping. Whether the value may be null follows from the "
                        + "multiplicity of the attribute and the nullability of the column.");
        basic.getESuperTypes().add(simpleBase);

        EClass id = cls(p, "Id", false,
                "An id attribute mapping; its column belongs to the primary key of the entity "
                        + "table.");
        id.getESuperTypes().add(base);

        EClass version = cls(p, "Version", false,
                "An optimistic-locking version attribute mapping.");
        version.getESuperTypes().add(base);

        EClass embedded = cls(p, "Embedded", false,
                "Maps an attribute whose type is an embeddable; columns and joins may be "
                        + "overridden per path.");
        embedded.getESuperTypes().add(valueMapping);
        embedded.getESuperTypes().add(overrideHost);

        EClass embeddedId = cls(p, "EmbeddedId", false,
                "A composite id held in an embeddable; its columns form the primary key of the "
                        + "entity table.");
        embeddedId.getESuperTypes().add(valueMapping);
        embeddedId.getESuperTypes().add(overrideHost);

        EClass transientCls = cls(p, "Transient", false,
                "Marks an attribute or a navigable association end as not persistent — "
                        + "explicitly, because every feature of a mapped class needs a mapping.");
        transientCls.getESuperTypes().add(attributeMapping);

        EClass elementCollection = cls(p, "ElementCollection", false,
                "A collection of basic or embeddable values, stored in a collection table.");
        elementCollection.getESuperTypes().add(simpleBase);
        elementCollection.getESuperTypes().add(collectionSupport);
        elementCollection.getESuperTypes().add(overrideHost);

        EClass manyToOne = cls(p, "ManyToOne", false,
                "A many-to-one relationship mapping, always the owning side.");
        manyToOne.getESuperTypes().add(relationshipMapping);

        EClass oneToOne = cls(p, "OneToOne", false,
                "A one-to-one relationship mapping, joined by a join, a primary-key join, a join "
                        + "table or a derived id.");
        oneToOne.getESuperTypes().add(ownableRelationship);

        EClass oneToMany = cls(p, "OneToMany", false,
                "A one-to-many relationship mapping, joined by a foreign key in the target table "
                        + "or a join table.");
        oneToMany.getESuperTypes().add(ownableRelationship);
        oneToMany.getESuperTypes().add(collectionSupport);

        EClass manyToMany = cls(p, "ManyToMany", false,
                "A many-to-many relationship mapping over an intersection table.");
        manyToMany.getESuperTypes().add(ownableRelationship);
        manyToMany.getESuperTypes().add(collectionSupport);

        EClass sequenceGenerator = cls(p, "SequenceGenerator", false,
                "A sequence-based id generator. CWM 1.1 has no sequence class, so sequenceName "
                        + "stays a string; cwmSchema anchors the schema the sequence lives in.");
        sequenceGenerator.getESuperTypes().add(generator);

        EClass tableGenerator = cls(p, "TableGenerator", false,
                "A table-based id generator: pk and value columns are catalog references, the "
                        + "generator table is their owner; pkColumnValue is the row selector (a real "
                        + "value, not a name).");
        tableGenerator.getESuperTypes().add(generator);

        EClass generatedValue = cls(p, "GeneratedValue", false,
                "Id generation for an id attribute: strategy plus a typed reference to the "
                        + "generator.");
        generatedValue.getESuperTypes().add(ormElement);

        EClass queryHint = cls(p, "QueryHint", false,
                "A provider hint of a named query; the hint name is the inherited name.");
        queryHint.getESuperTypes().add(ormElement);

        EClass namedQuery = cls(p, "NamedQuery", false,
                "A named JPQL query; the JPQL text is the only query text of the model, because "
                        + "JPQL has no CWM counterpart.");
        namedQuery.getESuperTypes().add(query);

        EClass sqlResultSetMapping = cls(p, "SqlResultSetMapping", false,
                "Maps a native query result set to entities, constructor calls and scalar "
                        + "columns; the mapping name is the inherited name.");
        sqlResultSetMapping.getESuperTypes().add(ormElement);

        EClass namedNativeQuery = cls(p, "NamedNativeQuery", false,
                "A named native SQL query over a stored query of the catalog: cwmQuery is the "
                        + "relational::QueryColumnSet whose expression is the SQL and whose columns "
                        + "are the result columns.");
        namedNativeQuery.getESuperTypes().add(query);

        EClass namedStoredProcedureQuery = cls(p, "NamedStoredProcedureQuery", false,
                "A named stored-procedure query; cwmProcedure anchors the catalog procedure, whose "
                        + "parameters (order, direction, type) are the query parameters.");
        namedStoredProcedureQuery.getESuperTypes().add(query);

        EClass entityResult = cls(p, "EntityResult", false,
                "Maps result columns to an entity class.");
        entityResult.getESuperTypes().add(ormElement);

        EClass fieldResult = cls(p, "FieldResult", false,
                "Maps one result column to a feature of the result entity; the name is the "
                        + "feature name.");
        fieldResult.getESuperTypes().add(ormElement);

        EClass columnResult = cls(p, "ColumnResult", false,
                "A scalar result column or constructor argument: cwmColumn is the column of the "
                        + "stored query (its name is the alias); the name is the column name.");
        columnResult.getESuperTypes().add(ormElement);

        EClass constructorResult = cls(p, "ConstructorResult", false,
                "Maps result columns to a constructor invocation of cwmClass; column order is the "
                        + "argument order.");
        constructorResult.getESuperTypes().add(ormElement);

        EClass namedEntityGraph = cls(p, "NamedEntityGraph", false,
                "A named entity graph (fetch plan); the graph name is the inherited name.");
        namedEntityGraph.getESuperTypes().add(ormElement);

        EClass namedAttributeNode = cls(p, "NamedAttributeNode", false,
                "A node of an entity graph: the fetched feature and the subgraphs for its value "
                        + "and its map key.");
        namedAttributeNode.getESuperTypes().add(ormElement);

        EClass namedSubgraph = cls(p, "NamedSubgraph", false,
                "A subgraph of an entity graph for the type cwmClass; the subgraph name is the "
                        + "inherited, graph-local name.");
        namedSubgraph.getESuperTypes().add(ormElement);

        attr(ormElement, "description", cwm.stringType, 0, 1,
                "Free-text description of the mapping element.");

        EReference mtClass = ref(managedType, "cwmClass", cwm.cwmClass, 1, 1,
                "The mapped Java type as core::Class. The fully qualified class name follows "
                        + "from the owning core::Package chain.");
        oppositeRole(mtClass, "ormManagedType");
        attrUnsettable(managedType, "access", accessType,
                "Access strategy for the attributes of this type.");
        cont(managedType, "basic", basic, 0, -1, false,
                "Basic value mappings.");
        cont(managedType, "embedded", embedded, 0, -1, false,
                "Embedded value mappings.");
        cont(managedType, "transient", transientCls, 0, -1, false,
                "Attributes and association ends excluded from persistence.");
        cont(managedType, "elementCollection", elementCollection, 0, -1, false,
                "Element-collection mappings.");
        cont(managedType, "manyToOne", manyToOne, 0, -1, false,
                "Many-to-one relationship mappings.");
        cont(managedType, "oneToMany", oneToMany, 0, -1, false,
                "One-to-many relationship mappings.");
        cont(managedType, "oneToOne", oneToOne, 0, -1, false,
                "One-to-one relationship mappings.");
        cont(managedType, "manyToMany", manyToMany, 0, -1, false,
                "Many-to-many relationship mappings.");

        cont(identifiableType, "id", id, 0, -1, false,
                "Id attribute mappings.");
        cont(identifiableType, "embeddedId", embeddedId, 0, 1, true,
                "Composite id mapping (mutually exclusive with id mappings).");
        cont(identifiableType, "version", version, 0, -1, false,
                "Version attribute mappings.");
        EReference idClass = ref(identifiableType, "cwmIdClass", cwm.cwmClass, 0, 1,
                "The composite id class, set exactly when the type has more than one id mapping.");
        oppositeRole(idClass, "ormIdClassUser");
        EReference itListener = ref(identifiableType, "listener", entityListener, 0, -1,
                "The entity listeners of this type in invocation order (defined in EntityMappings).");
        itListener.setOrdered(true);
        oppositeRole(itListener, "ormListenerUser");
        attrUnsettableBool(identifiableType, "excludeDefaultListeners",
                "Excludes the persistence unit's default listeners for this type.");
        attrUnsettableBool(identifiableType, "excludeSuperclassListeners",
                "Excludes superclass listeners for this type.");
        cont(identifiableType, "lifecycleCallback", lifecycleCallback, 0, -1, false,
                "Lifecycle callbacks implemented by operations of the type's own class.");

        attrUnsettable(attributeMapping, "access", accessType,
                "Access strategy override for this attribute.");

        EReference vmAttr = ref(valueMapping, "cwmAttribute", cwm.attribute, 1, 1,
                "The mapped object attribute (core::Attribute) — the single truth for the "
                        + "attribute name, type and multiplicity.");
        oppositeRole(vmAttr, "ormValueMapping");

        EReference rmEnd = ref(relationshipMapping, "cwmAssociationEnd", cwm.associationEnd, 1, 1,
                "The mapped association end (relationships::AssociationEnd) — the single truth "
                        + "for name, target type and multiplicity.");
        oppositeRole(rmEnd, "ormRelationshipMapping");
        attrUnsettable(relationshipMapping, "fetch", fetchType,
                "Fetch strategy of the relationship.");
        EAttribute cascade = attr(relationshipMapping, "cascade", cascadeKind, 0, -1,
                "Cascaded operations of the relationship.");
        cascade.setOrdered(false);
        cascade.setUnique(true);
        cont(relationshipMapping, "joinTable", joinTable, 0, 1, true,
                "Maps the relationship over an intersection table.");

        EAttribute owningSide = attr(ownableRelationship, "owningSide", cwm.booleanType, 0, 1,
                "Whether this side owns the mapping; the opposite end follows from the association, "
                        + "and exactly one side of a bidirectional relationship is the owner.");
        owningSide.setDefaultValueLiteral("true");
        owningSide.setUnsettable(true);

        EReference trFeature = ref(transientCls, "cwmFeature", cwm.structuralFeature, 1, 1,
                "The attribute or association end that is not persistent.");
        oppositeRole(trFeature, "ormTransient");

        cont(collectionSupport, "orderBy", ordering, 0, -1, true,
                "Ordering criteria over attributes of the target type, most significant first.");
        cont(collectionSupport, "orderColumn", column, 0, 1, true,
                "A dedicated order column maintaining the list order.");
        cont(collectionSupport, "mapKey", mapKey, 0, 1, true,
                "The key of a map-valued collection.");

        cont(overrideHost, "attributeOverride", attributeOverride, 0, -1, false,
                "Column overrides per feature path.");
        cont(overrideHost, "associationOverride", associationOverride, 0, -1, false,
                "Join overrides per feature path.");
        cont(overrideHost, "convert", convert, 0, -1, false,
                "Converter applications per feature path.");

        EReference mkAttr = ref(mapKey, "cwmAttribute", cwm.structuralFeature, 0, 1,
                "The feature of the target type acting as key; its type is the key type.");
        oppositeRole(mkAttr, "ormMapKey");
        EReference mkClassifier = ref(mapKey, "cwmClassifier", cwm.classifier, 0, 1,
                "The key type when no feature of the target is the key.");
        oppositeRole(mkClassifier, "ormMapKeyClassifier");
        attrUnsettable(mapKey, "enumerated", enumType,
                "Enum mapping of an enum key.");
        cont(mapKey, "column", column, 0, 1, true,
                "The column holding a basic key.");
        cont(mapKey, "join", join, 0, 1, true,
                "The join to the key entity's table for an entity key.");

        EReference orPath = ref(ordering, "cwmPath", cwm.structuralFeature, 1, -1,
                "The feature path to order by: first element a feature of the target type (the "
                        + "related entity or the element embeddable), each further element a feature "
                        + "of the previous type, ending in an attribute.");
        orPath.setOrdered(true);
        oppositeRole(orPath, "ormOrdering");
        attr(ordering, "descending", cwm.booleanType, 0, 1,
                "Whether the order is descending (default ascending).");

        EReference colRef = ref(columnRef, "cwmColumn", cwm.column, 1, 1,
                "The catalog column (relational::Column) — the single truth for name, type, "
                        + "length, precision, scale and nullability.");
        oppositeRole(colRef, "ormColumn");

        attrUnsettableBool(column, "insertable",
                "Whether the column appears in provider-generated INSERT statements.");
        attrUnsettableBool(column, "updatable",
                "Whether the column appears in provider-generated UPDATE statements.");

        EReference jcRef = ref(joinColumn, "cwmReferencedColumn", cwm.column, 1, 1,
                "The referenced column in the target table.");
        oppositeRole(jcRef, "ormReferencingJoinColumn");

        EReference joinFk = ref(join, "cwmForeignKey", cwm.foreignKey, 0, 1,
                "The catalog constraint (relational::ForeignKey) the join follows; unset when no "
                        + "constraint exists, then the join columns name the column pairs.");
        oppositeRole(joinFk, "ormJoin");
        cont(join, "joinColumn", joinColumn, 0, -1, true,
                "Explicit column pairs, only when there is no foreign key.");
        attrUnsettableBool(join, "insertable",
                "Whether the join columns appear in provider-generated INSERT statements.");
        attrUnsettableBool(join, "updatable",
                "Whether the join columns appear in provider-generated UPDATE statements.");

        cont(joinedTable, "join", join, 1, 1, true,
                "The join from this table to the owner's table; its columns live in this table.");

        cont(joinTable, "inverseJoin", join, 1, 1, true,
                "The join from this table to the target's table.");

        EReference aoPath = ref(attributeOverride, "cwmPath", cwm.structuralFeature, 1, -1,
                "The overridden feature path, first element a feature of the host's class or of "
                        + "the embeddable, each further element a feature of the previous type.");
        aoPath.setOrdered(true);
        oppositeRole(aoPath, "ormAttributeOverride");
        cont(attributeOverride, "column", column, 1, 1, true,
                "The overriding column.");

        EReference asoPath = ref(associationOverride, "cwmPath", cwm.structuralFeature, 1, -1,
                "The overridden feature path, ending in an association end.");
        asoPath.setOrdered(true);
        oppositeRole(asoPath, "ormAssociationOverride");
        cont(associationOverride, "join", join, 0, 1, true,
                "The overriding join.");
        cont(associationOverride, "joinTable", joinTable, 0, 1, true,
                "The overriding join table.");

        EReference cvConverter = ref(convert, "converter", converter, 0, 1,
                "The converter applied (exclusive with disableConversion).");
        oppositeRole(cvConverter, "ormConvert");
        attrUnsettableBool(convert, "disableConversion",
                "Disables an otherwise auto-applied converter.");
        EReference cvPath = ref(convert, "cwmPath", cwm.structuralFeature, 0, -1,
                "The feature path the conversion applies to; empty for the mapped attribute "
                        + "itself.");
        cvPath.setOrdered(true);
        oppositeRole(cvPath, "ormConvertPath");

        EReference cnvClass = ref(converter, "cwmClass", cwm.cwmClass, 1, 1,
                "The converter class in the object model.");
        oppositeRole(cnvClass, "ormConverter");
        attrUnsettableBool(converter, "autoApply",
                "Whether the converter applies automatically to all matching attributes.");

        EReference elClass = ref(entityListener, "cwmClass", cwm.cwmClass, 1, 1,
                "The listener class in the object model.");
        oppositeRole(elClass, "ormEntityListener");
        cont(entityListener, "lifecycleCallback", lifecycleCallback, 0, -1, false,
                "Callbacks implemented by operations of the listener class.");

        attr(lifecycleCallback, "kind", callbackKind, 1, 1,
                "The lifecycle event invoking the operation.");
        EReference lcOp = ref(lifecycleCallback, "cwmOperation", cwm.operation, 1, 1,
                "The callback operation (behavioral::Operation) of the listener or entity class.");
        oppositeRole(lcOp, "ormLifecycleCallback");

        cont(persistenceUnitMetadata, "persistenceUnitDefaults", persistenceUnitDefaults, 0, 1, true,
                "Defaults for the persistence unit.");

        attrUnsettableBool(persistenceUnitDefaults, "delimitedIdentifiers",
                "Whether database identifiers are treated as delimited.");
        attrUnsettableBool(persistenceUnitDefaults, "cascadePersist",
                "Adds cascade-persist to all relationships of the unit.");
        EReference pudListener = ref(persistenceUnitDefaults, "listener", entityListener, 0, -1,
                "Default entity listeners of the unit in invocation order (defined in "
                        + "EntityMappings).");
        pudListener.setOrdered(true);
        oppositeRole(pudListener, "ormDefaultListenerUser");

        cont(entityMappings, "persistenceUnitMetadata", persistenceUnitMetadata, 0, 1, true,
                "Persistence-unit level metadata.");
        attrUnsettable(entityMappings, "access", accessType,
                "Default access strategy of this mapping set and its persistence unit.");
        cont(entityMappings, "generator", generator, 0, -1, false,
                "The id generators of the unit (sequence and table generators).");
        cont(entityMappings, "namedQuery", namedQuery, 0, -1, false,
                "Named JPQL queries of the unit.");
        cont(entityMappings, "namedNativeQuery", namedNativeQuery, 0, -1, false,
                "Named native queries of the unit.");
        cont(entityMappings, "namedStoredProcedureQuery", namedStoredProcedureQuery, 0, -1, false,
                "Named stored-procedure queries of the unit.");
        cont(entityMappings, "sqlResultSetMapping", sqlResultSetMapping, 0, -1, false,
                "Result-set mappings of the unit.");
        cont(entityMappings, "mappedSuperclass", mappedSuperclass, 0, -1, false,
                "Mapped superclasses of this set.");
        cont(entityMappings, "entity", entity, 0, -1, false,
                "Entities of this set.");
        cont(entityMappings, "embeddable", embeddable, 0, -1, false,
                "Embeddables of this set.");
        cont(entityMappings, "converter", converter, 0, -1, false,
                "Registered converters.");
        cont(entityMappings, "entityListener", entityListener, 0, -1, false,
                "Entity listeners, used by types and as unit defaults.");

        EReference entTable = ref(entity, "cwmTable", cwm.namedColumnSet, 0, 1,
                "The primary table (relational::NamedColumnSet; a table or updatable view). "
                        + "Schema and catalog follow from the table's namespace chain. Unset only "
                        + "for a SINGLE_TABLE subclass, which lives in the table of its root.");
        oppositeRole(entTable, "ormEntity");
        attrUnsettableBool(entity, "cacheable",
                "Whether the entity is cacheable (shared-cache mode ENABLE_SELECTIVE/"
                        + "DISABLE_SELECTIVE).");
        attrUnsettable(entity, "inheritanceStrategy", inheritanceType,
                "Inheritance strategy rooted at this entity.");
        attr(entity, "discriminatorValue", cwm.stringType, 0, 1,
                "Discriminator value of this entity in a SINGLE_TABLE/JOINED hierarchy.");
        EReference entDisc = ref(entity, "cwmDiscriminatorColumn", cwm.column, 0, 1,
                "The discriminator column of the hierarchy rooted here; its SQL type is the "
                        + "discriminator type.");
        oppositeRole(entDisc, "ormDiscriminatorColumn");
        cont(entity, "secondaryTable", secondaryTable, 0, -1, false,
                "Additional tables of the entity.");
        cont(entity, "primaryKeyJoin", join, 0, 1, true,
                "The join to the superclass table in a JOINED hierarchy.");
        cont(entity, "namedEntityGraph", namedEntityGraph, 0, -1, false,
                "Named entity graphs rooted at the entity.");

        cont(base, "column", column, 0, 1, true,
                "The mapped column with its insert/update flags; open only in a mapped superclass "
                        + "or embeddable whose users override it.");

        attrUnsettable(simpleBase, "fetch", fetchType,
                "Fetch strategy of the value.");
        attrUnsettable(simpleBase, "enumerated", enumType,
                "Enum mapping of an enum value.");

        cont(basic, "convert", convert, 0, 1, true,
                "The conversion of the value (without path).");

        cont(id, "generatedValue", generatedValue, 0, 1, true,
                "Id generation for this id attribute.");

        cont(elementCollection, "collectionTable", collectionTable, 0, 1, true,
                "The collection table.");

        cont(manyToOne, "join", join, 0, 1, true,
                "The join from the owner's table to the target's table.");
        attrUnsettableBool(manyToOne, "id",
                "Whether the relationship is part of the id (derived identity with an id class).");
        attrUnsettableBool(manyToOne, "mapsId",
                "Whether the relationship provides the id (derived identity with an embedded id).");
        EReference mtoMaps = ref(manyToOne, "cwmMapsIdAttribute", cwm.attribute, 0, 1,
                "The attribute of the embedded-id class the relationship provides; unset when it "
                        + "provides the whole id.");
        oppositeRole(mtoMaps, "ormManyToOneMapsId");

        cont(oneToOne, "join", join, 0, 1, true,
                "The join from the owner's table to the target's table.");
        cont(oneToOne, "primaryKeyJoin", join, 0, 1, true,
                "The join over the shared primary key.");
        attrUnsettableBool(oneToOne, "id",
                "Whether the relationship is part of the id (derived identity with an id class).");
        attrUnsettableBool(oneToOne, "mapsId",
                "Whether the relationship provides the id (derived identity with an embedded id).");
        EReference otoMaps = ref(oneToOne, "cwmMapsIdAttribute", cwm.attribute, 0, 1,
                "The attribute of the embedded-id class the relationship provides; unset when it "
                        + "provides the whole id.");
        oppositeRole(otoMaps, "ormOneToOneMapsId");
        attrUnsettableBool(oneToOne, "orphanRemoval",
                "Removes the target when it is dereferenced.");

        cont(oneToMany, "join", join, 0, 1, true,
                "The join from the target's table to the owner's table (unidirectional without "
                        + "a join table).");
        attrUnsettableBool(oneToMany, "orphanRemoval",
                "Removes targets when they leave the collection.");

        attrUnsettableInt(generator, "initialValue",
                "Initial value of the generator.");
        attrUnsettableInt(generator, "allocationSize",
                "Number of values allocated per round trip.");

        EReference sgSchema = ref(sequenceGenerator, "cwmSchema", cwm.schema, 0, 1,
                "The schema the database sequence lives in (CWM 1.1 has no sequence class).");
        oppositeRole(sgSchema, "ormSequenceGenerator");
        attr(sequenceGenerator, "sequenceName", cwm.stringType, 0, 1,
                "Name of the database sequence (string, because CWM 1.1 cannot model "
                        + "sequences).");

        EReference tgPk = ref(tableGenerator, "cwmPkColumn", cwm.column, 1, 1,
                "The column holding the generator names; its owner is the generator table.");
        oppositeRole(tgPk, "ormTableGeneratorPk");
        EReference tgVal = ref(tableGenerator, "cwmValueColumn", cwm.column, 1, 1,
                "The column holding the last allocated value.");
        oppositeRole(tgVal, "ormTableGeneratorValue");
        attr(tableGenerator, "pkColumnValue", cwm.stringType, 0, 1,
                "Row selector value in the pk column (a real value in the data, not a model "
                        + "name).");

        attrUnsettable(generatedValue, "strategy", generationType,
                "Generation strategy.");
        EReference gvGen = ref(generatedValue, "generator", generator, 0, 1,
                "The generator producing the values, set exactly for SEQUENCE and TABLE.");
        oppositeRole(gvGen, "ormGeneratedValue");

        cont(query, "hint", queryHint, 0, -1, false,
                "Provider hints.");

        attr(queryHint, "value", cwm.stringType, 1, 1,
                "Value of the hint.");

        attr(namedQuery, "query", cwm.stringType, 1, 1,
                "The JPQL query text.");
        attrUnsettable(namedQuery, "lockMode", lockModeType,
                "Lock mode applied when the query executes.");

        EReference nnqQuery = ref(namedNativeQuery, "cwmQuery", cwm.queryColumnSet, 1, 1,
                "The stored query of the catalog: its query expression is the SQL, its columns "
                        + "the result columns.");
        oppositeRole(nnqQuery, "ormNativeQuery");
        EReference nnqClass = ref(namedNativeQuery, "cwmResultClass", cwm.cwmClass, 0, 1,
                "The result entity class (exclusive with resultSetMapping).");
        oppositeRole(nnqClass, "ormNativeQueryResultClass");
        EReference nnqRsm = ref(namedNativeQuery, "resultSetMapping", sqlResultSetMapping, 0, 1,
                "The result-set mapping shaping the result (exclusive with cwmResultClass).");
        oppositeRole(nnqRsm, "ormNativeQueryUser");

        EReference nspqProc = ref(namedStoredProcedureQuery, "cwmProcedure", cwm.procedure, 1, 1,
                "The catalog procedure (relational::Procedure); its parameters are the query "
                        + "parameters.");
        oppositeRole(nspqProc, "ormStoredProcedureQuery");
        EReference nspqClass = ref(namedStoredProcedureQuery, "cwmResultClass", cwm.cwmClass, 0, -1,
                "Result entity classes in result order.");
        nspqClass.setOrdered(true);
        oppositeRole(nspqClass, "ormStoredProcedureQueryResultClass");
        EReference nspqRsm = ref(namedStoredProcedureQuery, "resultSetMapping", sqlResultSetMapping, 0, -1,
                "Result-set mappings in result order.");
        nspqRsm.setOrdered(true);
        oppositeRole(nspqRsm, "ormStoredProcedureQueryUser");

        cont(sqlResultSetMapping, "entityResult", entityResult, 0, -1, false,
                "Entity results.");
        cont(sqlResultSetMapping, "constructorResult", constructorResult, 0, -1, false,
                "Constructor results.");
        cont(sqlResultSetMapping, "columnResult", columnResult, 0, -1, false,
                "Scalar column results.");

        EReference erClass = ref(entityResult, "cwmClass", cwm.cwmClass, 1, 1,
                "The result entity class.");
        oppositeRole(erClass, "ormEntityResult");
        EReference erDisc = ref(entityResult, "cwmDiscriminatorColumn", cwm.column, 0, 1,
                "The result column holding the discriminator.");
        oppositeRole(erDisc, "ormEntityResultDiscriminator");
        cont(entityResult, "fieldResult", fieldResult, 0, -1, false,
                "Field results of the entity.");

        EReference frColumn = ref(fieldResult, "cwmColumn", cwm.column, 1, 1,
                "The result column: a column of the stored query of the native query (its name is "
                        + "the alias in the result set).");
        oppositeRole(frColumn, "ormFieldResultColumn");
        EReference frFeature = ref(fieldResult, "cwmFeature", cwm.structuralFeature, 1, 1,
                "The feature of the result entity the column fills.");
        oppositeRole(frFeature, "ormFieldResult");

        EReference crType = ref(columnResult, "cwmType", cwm.classifier, 0, 1,
                "The type the scalar value converts to.");
        oppositeRole(crType, "ormColumnResultType");
        EReference crCol = ref(columnResult, "cwmColumn", cwm.column, 1, 1,
                "The result column: a column of the stored query of the native query (its name is "
                        + "the alias in the result set).");
        oppositeRole(crCol, "ormColumnResult");

        EReference ctrClass = ref(constructorResult, "cwmClass", cwm.cwmClass, 1, 1,
                "The class whose constructor is invoked.");
        oppositeRole(ctrClass, "ormConstructorResult");
        cont(constructorResult, "columnResult", columnResult, 0, -1, true,
                "Constructor arguments in declaration order.");

        attrUnsettableBool(namedEntityGraph, "includeAllAttributes",
                "Includes all attributes of the root entity.");
        cont(namedEntityGraph, "namedAttributeNode", namedAttributeNode, 0, -1, false,
                "Nodes of the graph.");
        cont(namedEntityGraph, "subgraph", namedSubgraph, 0, -1, false,
                "Subgraphs referenced by nodes.");
        cont(namedEntityGraph, "subclassSubgraph", namedSubgraph, 0, -1, false,
                "Subgraphs for subclasses of the root entity.");

        EReference nanFeature = ref(namedAttributeNode, "cwmFeature", cwm.structuralFeature, 1, 1,
                "The fetched attribute or association end.");
        oppositeRole(nanFeature, "ormAttributeNode");
        EReference nanSub = ref(namedAttributeNode, "subgraph", namedSubgraph, 0, 1,
                "The subgraph fetched for the value of the feature.");
        oppositeRole(nanSub, "ormAttributeNodeSubgraph");
        EReference nanKeySub = ref(namedAttributeNode, "keySubgraph", namedSubgraph, 0, 1,
                "The subgraph fetched for the map key of the feature.");
        oppositeRole(nanKeySub, "ormAttributeNodeKeySubgraph");

        EReference nsgClass = ref(namedSubgraph, "cwmClass", cwm.cwmClass, 1, 1,
                "The type the subgraph applies to.");
        oppositeRole(nsgClass, "ormSubgraph");
        cont(namedSubgraph, "namedAttributeNode", namedAttributeNode, 0, -1, false,
                "Nodes of the subgraph.");

        return p;
    }

    /** {@code Property.oppositeRoleName} pair (convention for hidden association ends). */
    private void oppositeRole(EReference r, String roleName) {
        EAnnotation a1 = ef.createEAnnotation();
        a1.setSource(EMOF);
        a1.getDetails().put("Property.oppositeRoleName", roleName);
        r.getEAnnotations().add(a1);
        EAnnotation a2 = ef.createEAnnotation();
        a2.setSource(EMOF + "#Property.oppositeRoleName");
        a2.getDetails().put("body", roleName);
        r.getEAnnotations().add(a2);
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        if (!doc.isEmpty()) {
            annotate(c, doc);
        }
        return c;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper,
            String doc) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        annotate(a, doc);
        return a;
    }

    /** Optional enum attribute with tri-state semantics (unset is detectable). */
    private EAttribute attrUnsettable(EClass owner, String name, EEnum type, String doc) {
        EAttribute a = attr(owner, name, type, 0, 1, doc);
        a.setUnsettable(true);
        return a;
    }

    /** Optional boolean attribute with tri-state semantics (unset is detectable). */
    private EAttribute attrUnsettableBool(EClass owner, String name, String doc) {
        EAttribute a = attr(owner, name, cwm.booleanType, 0, 1, doc);
        a.setUnsettable(true);
        return a;
    }

    /** Optional integer attribute with tri-state semantics (unset is detectable). */
    private EAttribute attrUnsettableInt(EClass owner, String name, String doc) {
        EAttribute a = attr(owner, name, cwm.integerType, 0, 1, doc);
        a.setUnsettable(true);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper,
            String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(false);
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    /**
     * Containment reference. {@code ordered} controls the list semantics: true where the
     * order carries meaning (join-column lists, parameters, constructor arguments).
     */
    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper,
            boolean ordered, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(true);
        if (upper != 1) {
            r.setOrdered(ordered);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    private record Lit(String name, String literal) {
    }

    private static Lit lit(String name) {
        return new Lit(name, null);
    }

    private static Lit lit(String name, String literal) {
        return new Lit(name, literal);
    }

    private EEnum enm(EPackage pkg, String name, String doc, Lit... literals) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        int value = 0;
        for (Lit l : literals) {
            EEnumLiteral el = ef.createEEnumLiteral();
            el.setName(l.name());
            if (l.literal() != null) {
                el.setLiteral(l.literal());
            }
            el.setValue(value++);
            e.getELiterals().add(el);
        }
        pkg.getEClassifiers().add(e);
        annotate(e, doc);
        return e;
    }

    private EAnnotation annotate(EModelElement el, String doc) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(GENMODEL);
        a.getDetails().put("documentation", doc);
        el.getEAnnotations().add(a);
        return a;
    }
}
