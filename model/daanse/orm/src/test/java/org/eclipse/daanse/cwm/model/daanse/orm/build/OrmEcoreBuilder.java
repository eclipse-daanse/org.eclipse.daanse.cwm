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
 * Programmatischer Builder fuer das {@code orm}-Metamodell — das plane
 * CWM-Objekt/Relational-Mapping-Modell der Daanse-Welt.
 *
 * <p>Das Modell traegt den vollen funktionalen Umfang der Jakarta-Persistence-3.1-Mappings
 * (orm.xml), verzichtet aber bewusst darauf, das XML selbst schreiben zu wollen: keine
 * ExtendedMetaData, kein DocumentRoot, keine abgeleiteten JPA-Namensstrings und keine
 * Ableitungs-OCL. Die einzige Wahrheit sind die {@code cwm*}-Referenzen — Klassenseite
 * {@code core::Class}/{@code core::Attribute}/{@code relationships::AssociationEnd},
 * Tabellenseite {@code relational::NamedColumnSet}/{@code Column}/{@code PrimaryKey}/
 * {@code ForeignKey}/{@code Procedure}. Die Namensmaterialisierung (FQCN, Schema/Catalog,
 * columnList, ...) lebt im fennec-eorm-Konverter, nicht im Modell.</p>
 *
 * <p>Alle konkreten Klassen erben von {@code OrmElement ⊂ core::ModelElement} (Name,
 * Sichtbarkeit, TaggedValues geerbt); {@code EntityMappings} und {@code ManagedType} sind
 * zusaetzlich {@code core::Namespace} (Praezedenz {@code core::Subsystem}). Nicht-Containment-
 * Referenzen sind versteckte Assoziationsenden mit {@code Property.oppositeRoleName}-Paaren
 * (Praefix {@code orm}); Rueckwaertsnavigation via InverseReferences.</p>
 *
 * <p>Aufbau und Serialisierung nach dem Vorbild {@code model/daanse/dcat/.../DcatEcoreBuilder}.</p>
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

    /** Haelt die in den CWM-Ecores aufgeloesten Ziel-Klassifizierer fuer Cross-Resource-Bezuege. */
    public static final class CwmRefs {
        final EClass modelElement;
        final EClass namespace;
        final EClass cwmClass;
        final EClass classifier;
        final EClass cwmPackage;
        final EClass attribute;
        final EClass structuralFeature;
        final EClass associationEnd;
        final EClass column;
        final EClass namedColumnSet;
        final EClass catalog;
        final EClass schema;
        final EClass primaryKey;
        final EClass foreignKey;
        final EClass procedure;
        final EClass sqlParameter;
        final EDataType stringType;
        final EDataType booleanType;
        final EDataType integerType;

        CwmRefs(EPackage core, EPackage relationships, EPackage relational) {
            this.modelElement = eClass(core, "ModelElement");
            this.namespace = eClass(core, "Namespace");
            this.cwmClass = eClass(core, "Class");
            this.classifier = eClass(core, "Classifier");
            this.cwmPackage = eClass(core, "Package");
            this.attribute = eClass(core, "Attribute");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.associationEnd = eClass(relationships, "AssociationEnd");
            this.column = eClass(relational, "Column");
            this.namedColumnSet = eClass(relational, "NamedColumnSet");
            this.catalog = eClass(relational, "Catalog");
            this.schema = eClass(relational, "Schema");
            this.primaryKey = eClass(relational, "PrimaryKey");
            this.foreignKey = eClass(relational, "ForeignKey");
            this.procedure = eClass(relational, "Procedure");
            this.sqlParameter = eClass(relational, "SQLParameter");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.integerType = eDataType(core, "Integer");
        }
    }

    public OrmEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    // ---------------------------------------------------------------------
    // CWM laden
    // ---------------------------------------------------------------------

    /**
     * Laedt die benoetigten CWM-Einheiten und loest die Ziel-Klassifizierer auf. Die
     * Resource-URI wird auf die nsURI gesetzt, damit Cross-Refs als
     * {@code <nsURI>#//Klasse} serialisieren.
     */
    public static CwmRefs loadCwmRefs(ResourceSet rs, URI coreEcoreUri, URI relationshipsEcoreUri,
            URI relationalEcoreUri) {
        EPackage core = loadPackage(rs, coreEcoreUri);
        EPackage relationships = loadPackage(rs, relationshipsEcoreUri);
        EPackage relational = loadPackage(rs, relationalEcoreUri);
        return new CwmRefs(core, relationships, relational);
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

    // ---------------------------------------------------------------------
    // Serialisierung
    // ---------------------------------------------------------------------

    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        injectLicenseHeader(outUri);
    }

    /** Fuegt den EPL-2.0-XML-Kommentarheader nach der XML-Deklaration ein. */
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

    public static ResourceSet newResourceSet() {
        ResourceSet rs = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    // ---------------------------------------------------------------------
    // Modellaufbau
    // ---------------------------------------------------------------------

    /** Erzeugt das EPackage {@code orm}: 57 Klassen (9 abstrakt), 12 Enums, keine Subpakete. */
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
                        + "side core::Class / core::Attribute / relationships::AssociationEnd, table "
                        + "side relational::NamedColumnSet / Column / PrimaryKey / ForeignKey / "
                        + "Procedure. Name materialization (FQCN, schema/catalog, column lists, ...) "
                        + "lives in the fennec eorm converter, not in the model. All concrete classes "
                        + "extend OrmElement (core::ModelElement); non-containment references are "
                        + "hidden association ends (Property.oppositeRoleName, prefix orm) — backward "
                        + "navigation via InverseReferences.");
        pa.getDetails().put("modelName", "CWMORM");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
        pa.getDetails().put("fileExtensions", "cwmorm");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "CWMORM");

        // Nicht-Containment-Referenzen sind versteckte Assoziationsenden (Konvention wie dcat).
        EAnnotation ho = ef.createEAnnotation();
        ho.setSource(ECORE_OCL);
        ho.getDetails().put("hiddenOpposites", "true");
        p.getEAnnotations().add(ho);

        // -- Enums --------------------------------------------------------

        EEnum accessType = enm(p, "AccessType",
                "How the persistence provider accesses the state of an entity or embedded object.",
                lit("PROPERTY"), lit("FIELD"));
        EEnum constraintMode = enm(p, "ConstraintMode",
                "Whether a physical foreign-key constraint is generated for a join.",
                lit("CONSTRAINT"), lit("NOCONSTRAINT", "NO_CONSTRAINT"),
                lit("PROVIDERDEFAULT", "PROVIDER_DEFAULT"));
        EEnum discriminatorType = enm(p, "DiscriminatorType",
                "Value type of the discriminator column.",
                lit("STRING"), lit("CHAR"), lit("INTEGER"));
        EEnum enumType = enm(p, "EnumType",
                "How a Java enum value maps to its column: by ordinal or by name.",
                lit("ORDINAL"), lit("STRING"));
        EEnum fetchType = enm(p, "FetchType",
                "Whether the value is fetched lazily or eagerly.",
                lit("LAZY"), lit("EAGER"));
        EEnum generationType = enm(p, "GenerationType",
                "Primary-key generation strategy.",
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
        EEnum parameterMode = enm(p, "ParameterMode",
                "Direction of a stored-procedure parameter.",
                lit("IN"), lit("INOUT"), lit("OUT"), lit("REFCURSOR", "REF_CURSOR"));
        EEnum temporalType = enm(p, "TemporalType",
                "SQL temporal precision of a java.util.Date/Calendar value.",
                lit("DATE"), lit("TIME"), lit("TIMESTAMP"));
        EEnum cascadeKind = enm(p, "CascadeKind",
                "Cascadable operation of a relationship; replaces the former CascadeType wrapper "
                        + "with its EmptyType children (XML artifact).",
                lit("ALL"), lit("PERSIST"), lit("MERGE"), lit("REMOVE"), lit("REFRESH"),
                lit("DETACH"));
        EEnum callbackKind = enm(p, "CallbackKind",
                "Lifecycle event of a callback method; replaces the seven former one-per-event "
                        + "classes (PrePersist ... PostLoad).",
                lit("PREPERSIST", "PRE_PERSIST"), lit("POSTPERSIST", "POST_PERSIST"),
                lit("PREREMOVE", "PRE_REMOVE"), lit("POSTREMOVE", "POST_REMOVE"),
                lit("PREUPDATE", "PRE_UPDATE"), lit("POSTUPDATE", "POST_UPDATE"),
                lit("POSTLOAD", "POST_LOAD"));

        // -- Klassenruempfe (wegen zyklischer Bezuege zuerst anlegen) ------

        EClass ormElement = cls(p, "OrmElement", true,
                "Abstract local root of the mapping world: every concrete mapping class is a "
                        + "core::ModelElement (inherited name, visibility, taggedValue). The "
                        + "inherited name is informative where a cwm reference names the element "
                        + "authoritatively.");
        ormElement.getESuperTypes().add(cwm.modelElement);

        EClass managedType = cls(p, "ManagedType", true,
                "A persistently managed Java type (entity, mapped superclass or embeddable): the "
                        + "cwmClass anchor plus the attribute mappings, held in typed containments "
                        + "(no Attributes wrapper).");
        managedType.getESuperTypes().add(ormElement);
        managedType.getESuperTypes().add(cwm.namespace);

        EClass identifiableType = cls(p, "IdentifiableType", true,
                "A managed type with identity: id/embeddedId/version mappings, an optional id "
                        + "class, entity listeners and lifecycle callbacks.");
        identifiableType.getESuperTypes().add(managedType);

        EClass attributeMapping = cls(p, "AttributeMapping", true,
                "One mapped attribute of a managed type. Two branches: ValueMapping (a "
                        + "core::Attribute) and RelationshipMapping (a relationships::AssociationEnd).");
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
                        + "owningSide replaces the former mappedBy string — the opposite end is "
                        + "computable from the association, only the choice of owner is mapping "
                        + "information.");
        ownableRelationship.getESuperTypes().add(relationshipMapping);

        EClass collectionSupport = cls(p, "CollectionSupport", true,
                "Mixin for collection-valued mappings (element collection, one-to-many, "
                        + "many-to-many): ordering, map key and map-key column details.");

        EClass generator = cls(p, "Generator", true,
                "A primary-key value generator; referenced by GeneratedValue.generator (typed "
                        + "reference instead of a name lookup).");
        generator.getESuperTypes().add(ormElement);

        EClass column = cls(p, "Column", false,
                "The universal column wrapper: cwmColumn is the catalog column (name, type, "
                        + "length, precision, nullability live there); insertable/updatable are the "
                        + "only genuine mapping flags. Also used for order and map-key columns — the "
                        + "role follows from the containing reference.");
        column.getESuperTypes().add(ormElement);

        EClass joinColumn = cls(p, "JoinColumn", false,
                "A join column as a pair of catalog columns: cwmColumn is the foreign-key column, "
                        + "cwmReferencedColumn the referenced (usually primary-key) column. Also used "
                        + "for primary-key join columns, where insertable/updatable are meaningless.");
        joinColumn.getESuperTypes().add(ormElement);

        EClass foreignKey = cls(p, "ForeignKey", false,
                "Mapping policy for a physical foreign key: whether a constraint is generated "
                        + "(constraintMode) and, if materialized, the catalog relational::ForeignKey.");
        foreignKey.getESuperTypes().add(ormElement);

        EClass discriminatorColumn = cls(p, "DiscriminatorColumn", false,
                "The discriminator column of a SINGLE_TABLE/JOINED hierarchy: catalog column plus "
                        + "value type.");
        discriminatorColumn.getESuperTypes().add(ormElement);

        EClass secondaryTable = cls(p, "SecondaryTable", false,
                "An additional table of an entity, joined by primary-key join columns.");
        secondaryTable.getESuperTypes().add(ormElement);

        EClass collectionTable = cls(p, "CollectionTable", false,
                "The table holding an element collection, joined to the owner by join columns.");
        collectionTable.getESuperTypes().add(ormElement);

        EClass joinTable = cls(p, "JoinTable", false,
                "An intersection table mapping a relationship: join columns towards the owning "
                        + "side, inverse join columns towards the target side.");
        joinTable.getESuperTypes().add(ormElement);

        EClass attributeOverride = cls(p, "AttributeOverride", false,
                "Overrides the column of an inherited or embedded value mapping; the attribute "
                        + "path (e.g. address.street) is the inherited name.");
        attributeOverride.getESuperTypes().add(ormElement);

        EClass associationOverride = cls(p, "AssociationOverride", false,
                "Overrides the join columns or join table of an inherited or embedded "
                        + "relationship mapping; the attribute path is the inherited name.");
        associationOverride.getESuperTypes().add(ormElement);

        EClass convert = cls(p, "Convert", false,
                "Applies or disables an attribute converter for an attribute (path in "
                        + "attributeName); converter is the Java class name — a runtime artifact, "
                        + "deliberately without CWM anchor.");
        convert.getESuperTypes().add(ormElement);

        EClass converter = cls(p, "Converter", false,
                "Registers an attribute converter class (Java runtime artifact, no CWM anchor).");
        converter.getESuperTypes().add(ormElement);

        EClass entityListener = cls(p, "EntityListener", false,
                "A listener class receiving lifecycle callbacks for an entity (Java runtime "
                        + "artifact, no CWM anchor).");
        entityListener.getESuperTypes().add(ormElement);

        EClass lifecycleCallback = cls(p, "LifecycleCallback", false,
                "One lifecycle callback method: which event (kind) invokes which method "
                        + "(methodName). Replaces the seven former one-per-event classes.");
        lifecycleCallback.getESuperTypes().add(ormElement);

        EClass persistenceUnitMetadata = cls(p, "PersistenceUnitMetadata", false,
                "Metadata that applies to the whole persistence unit.");
        persistenceUnitMetadata.getESuperTypes().add(ormElement);

        EClass persistenceUnitDefaults = cls(p, "PersistenceUnitDefaults", false,
                "Defaults for the persistence unit. Default schema and catalog are the "
                        + "EntityMappings cwmSchema/cwmCatalog references — not repeated as strings.");
        persistenceUnitDefaults.getESuperTypes().add(ormElement);

        EClass entityMappings = cls(p, "EntityMappings", false,
                "Root of an object/relational mapping set: the managed types, generators, queries "
                        + "and result-set mappings, anchored to the object package "
                        + "(cwmObjectPackage) and the default relational catalog/schema.");
        entityMappings.getESuperTypes().add(ormElement);
        entityMappings.getESuperTypes().add(cwm.namespace);

        EClass entity = cls(p, "Entity", false,
                "An entity: cwmClass (inherited) is the Java class, cwmTable its primary table. "
                        + "The JPA entity name is the inherited name; the fully qualified class name "
                        + "is materialized by the converter from the core package chain.");
        entity.getESuperTypes().add(identifiableType);

        EClass mappedSuperclass = cls(p, "MappedSuperclass", false,
                "A mapped superclass: contributes mappings to subclasses, has no table of its own.");
        mappedSuperclass.getESuperTypes().add(identifiableType);

        EClass embeddable = cls(p, "Embeddable", false,
                "An embeddable type: its attribute mappings apply within the owning entity's "
                        + "table(s).");
        embeddable.getESuperTypes().add(managedType);

        EClass base = cls(p, "Base", true,
                "Value mapping with a column: the optional Column wrapper carries the catalog "
                        + "column and the insert/update flags.");
        base.getESuperTypes().add(valueMapping);

        EClass simpleBase = cls(p, "SimpleBase", true,
                "Value mapping with fetch, conversion and LOB/enum/temporal options.");
        simpleBase.getESuperTypes().add(base);

        EClass basic = cls(p, "Basic", false,
                "A basic value mapping.");
        basic.getESuperTypes().add(simpleBase);

        EClass id = cls(p, "Id", false,
                "An id attribute mapping; cwmPrimaryKey anchors the catalog primary key the id "
                        + "column belongs to.");
        id.getESuperTypes().add(base);

        EClass version = cls(p, "Version", false,
                "An optimistic-locking version attribute mapping.");
        version.getESuperTypes().add(base);

        EClass embedded = cls(p, "Embedded", false,
                "Maps an attribute whose type is an embeddable; columns may be overridden per "
                        + "path.");
        embedded.getESuperTypes().add(valueMapping);

        EClass embeddedId = cls(p, "EmbeddedId", false,
                "A composite id held in an embeddable; cwmPrimaryKey anchors the catalog primary "
                        + "key.");
        embeddedId.getESuperTypes().add(valueMapping);

        EClass transientCls = cls(p, "Transient", false,
                "Marks an attribute as not persistent.");
        transientCls.getESuperTypes().add(valueMapping);

        EClass elementCollection = cls(p, "ElementCollection", false,
                "A collection of basic or embeddable values, stored in a collection table.");
        elementCollection.getESuperTypes().add(simpleBase);
        elementCollection.getESuperTypes().add(collectionSupport);

        EClass manyToOne = cls(p, "ManyToOne", false,
                "A many-to-one relationship mapping, joined by join columns.");
        manyToOne.getESuperTypes().add(relationshipMapping);

        EClass oneToOne = cls(p, "OneToOne", false,
                "A one-to-one relationship mapping, joined by join columns or primary-key join "
                        + "columns.");
        oneToOne.getESuperTypes().add(ownableRelationship);

        EClass oneToMany = cls(p, "OneToMany", false,
                "A one-to-many relationship mapping, joined by foreign-key columns on the target "
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
                "A table-based id generator: generator table, pk and value columns are catalog "
                        + "references; pkColumnValue is the row selector (a real value, not a name).");
        tableGenerator.getESuperTypes().add(generator);

        EClass generatedValue = cls(p, "GeneratedValue", false,
                "Id generation for an id attribute: strategy plus a typed reference to the "
                        + "generator.");
        generatedValue.getESuperTypes().add(ormElement);

        EClass queryHint = cls(p, "QueryHint", false,
                "A provider hint of a named query; the hint name is the inherited name.");
        queryHint.getESuperTypes().add(ormElement);

        EClass namedQuery = cls(p, "NamedQuery", false,
                "A named JPQL query; the query name is the inherited name.");
        namedQuery.getESuperTypes().add(ormElement);

        EClass sqlResultSetMapping = cls(p, "SqlResultSetMapping", false,
                "Maps a native query result set to entities, constructor calls and scalar "
                        + "columns; the mapping name is the inherited name.");
        sqlResultSetMapping.getESuperTypes().add(ormElement);

        EClass namedNativeQuery = cls(p, "NamedNativeQuery", false,
                "A named native SQL query; resultSetMapping is a typed reference (no name "
                        + "lookup).");
        namedNativeQuery.getESuperTypes().add(ormElement);

        EClass namedStoredProcedureQuery = cls(p, "NamedStoredProcedureQuery", false,
                "A named stored-procedure query; cwmProcedure anchors the catalog procedure (the "
                        + "procedure name is not repeated as a string).");
        namedStoredProcedureQuery.getESuperTypes().add(ormElement);

        EClass storedProcedureParameter = cls(p, "StoredProcedureParameter", false,
                "A parameter of a named stored-procedure query: the Java-side class string plus "
                        + "optional catalog anchors (cwmParameter, cwmType). Aliases may prevent "
                        + "resolution, so the strings stay settable.");
        storedProcedureParameter.getESuperTypes().add(ormElement);

        EClass entityResult = cls(p, "EntityResult", false,
                "Maps result columns to an entity; entityClass stays settable (alias case), "
                        + "cwmClass is the additive anchor and takes precedence when set.");
        entityResult.getESuperTypes().add(ormElement);

        EClass fieldResult = cls(p, "FieldResult", false,
                "Maps one result column to a field; the field name is the inherited name, "
                        + "cwmFeature the additive anchor.");
        fieldResult.getESuperTypes().add(ormElement);

        EClass columnResult = cls(p, "ColumnResult", false,
                "A scalar result column; the column alias is the inherited name, cwmColumn the "
                        + "additive anchor (aliases may prevent resolution).");
        columnResult.getESuperTypes().add(ormElement);

        EClass constructorResult = cls(p, "ConstructorResult", false,
                "Maps result columns to a constructor invocation of targetClass; column order is "
                        + "the argument order.");
        constructorResult.getESuperTypes().add(ormElement);

        EClass namedEntityGraph = cls(p, "NamedEntityGraph", false,
                "A named entity graph (fetch plan); the graph name is the inherited name.");
        namedEntityGraph.getESuperTypes().add(ormElement);

        EClass namedAttributeNode = cls(p, "NamedAttributeNode", false,
                "A node of an entity graph; the attribute name is the inherited name, subgraph "
                        + "and keySubgraph refer to subgraphs by their graph-local names.");
        namedAttributeNode.getESuperTypes().add(ormElement);

        EClass namedSubgraph = cls(p, "NamedSubgraph", false,
                "A subgraph of an entity graph; the subgraph name is the inherited name.");
        namedSubgraph.getESuperTypes().add(ormElement);

        // -- OrmElement ---------------------------------------------------

        attr(ormElement, "description", cwm.stringType, 0, 1,
                "Free-text description of the mapping element.");

        // -- ManagedType / IdentifiableType --------------------------------

        EReference mtClass = ref(managedType, "cwmClass", cwm.cwmClass, 1, 1,
                "The mapped Java type as core::Class. The fully qualified class name is "
                        + "materialized by the converter from the owning core::Package chain.");
        oppositeRole(mtClass, "ormManagedType");
        attrUnsettable(managedType, "access", accessType,
                "Default access strategy for the attributes of this type.");
        attrUnsettableBool(managedType, "metadataComplete",
                "Whether this metadata is complete (annotations on the class are ignored).");
        cont(managedType, "basic", basic, 0, -1, false,
                "Basic value mappings.");
        cont(managedType, "embedded", embedded, 0, -1, false,
                "Embedded value mappings.");
        cont(managedType, "transient", transientCls, 0, -1, false,
                "Attributes excluded from persistence.");
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
                "The composite id class (replaces the former IdClass wrapper).");
        oppositeRole(idClass, "ormIdClassUser");
        cont(identifiableType, "entityListener", entityListener, 0, -1, false,
                "Entity listeners of this type.");
        attrUnsettableBool(identifiableType, "excludeDefaultListeners",
                "Excludes the persistence unit's default listeners for this type.");
        attrUnsettableBool(identifiableType, "excludeSuperclassListeners",
                "Excludes superclass listeners for this type.");
        cont(identifiableType, "lifecycleCallback", lifecycleCallback, 0, -1, false,
                "Lifecycle callback methods declared on the type itself.");

        // -- AttributeMapping-Hierarchie -----------------------------------

        attrUnsettable(attributeMapping, "access", accessType,
                "Access strategy override for this attribute.");

        EReference vmAttr = ref(valueMapping, "cwmAttribute", cwm.attribute, 1, 1,
                "The mapped object attribute (core::Attribute) — the single truth for the "
                        + "attribute name and type.");
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
        cont(relationshipMapping, "foreignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the join columns.");
        cont(relationshipMapping, "joinTable", joinTable, 0, 1, true,
                "Maps the relationship over an intersection table.");

        EAttribute owningSide = attr(ownableRelationship, "owningSide", cwm.booleanType, 0, 1,
                "Whether this side owns the mapping (the former mappedBy: the opposite end is "
                        + "computable from the association; exactly one side of a bidirectional "
                        + "relationship is the owner).");
        owningSide.setDefaultValueLiteral("true");
        owningSide.setUnsettable(true);

        // -- CollectionSupport --------------------------------------------

        attr(collectionSupport, "orderBy", cwm.stringType, 0, 1,
                "Ordering clause over attributes of the target type.");
        cont(collectionSupport, "orderColumn", column, 0, 1, true,
                "A dedicated order column maintaining the list order.");
        EReference mapKeyAttr = ref(collectionSupport, "cwmMapKeyAttribute", cwm.structuralFeature, 0, 1,
                "The attribute of the target type acting as map key (the former MapKey wrapper).");
        oppositeRole(mapKeyAttr, "ormMapKeyUser");
        EReference mapKeyClass = ref(collectionSupport, "cwmMapKeyClassifier", cwm.classifier, 0, 1,
                "The type of the map key (the former MapKeyClass wrapper).");
        oppositeRole(mapKeyClass, "ormMapKeyClassUser");
        attrUnsettable(collectionSupport, "mapKeyTemporal", temporalType,
                "Temporal precision of a temporal map key.");
        attrUnsettable(collectionSupport, "mapKeyEnumerated", enumType,
                "Enum mapping of an enum map key.");
        cont(collectionSupport, "mapKeyAttributeOverride", attributeOverride, 0, -1, false,
                "Column overrides for an embeddable map key.");
        cont(collectionSupport, "mapKeyConvert", convert, 0, -1, false,
                "Converter applications for the map key.");
        cont(collectionSupport, "mapKeyColumn", column, 0, 1, true,
                "The column holding a basic map key.");
        cont(collectionSupport, "mapKeyJoinColumn", joinColumn, 0, -1, true,
                "Join columns for an entity map key.");
        cont(collectionSupport, "mapKeyForeignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the map-key join columns.");

        // -- Column / JoinColumn / ForeignKey / DiscriminatorColumn --------

        EReference colRef = ref(column, "cwmColumn", cwm.column, 1, 1,
                "The catalog column (relational::Column) — the single truth for name, type, "
                        + "length, precision, scale and nullability.");
        oppositeRole(colRef, "ormColumn");
        attrUnsettableBool(column, "insertable",
                "Whether the column appears in provider-generated INSERT statements.");
        attrUnsettableBool(column, "updatable",
                "Whether the column appears in provider-generated UPDATE statements.");

        EReference jcCol = ref(joinColumn, "cwmColumn", cwm.column, 1, 1,
                "The foreign-key column.");
        oppositeRole(jcCol, "ormJoinColumn");
        EReference jcRef = ref(joinColumn, "cwmReferencedColumn", cwm.column, 0, 1,
                "The referenced column (defaults to the primary-key column of the target table).");
        oppositeRole(jcRef, "ormReferencingJoinColumn");
        attrUnsettableBool(joinColumn, "insertable",
                "Whether the column appears in provider-generated INSERT statements.");
        attrUnsettableBool(joinColumn, "updatable",
                "Whether the column appears in provider-generated UPDATE statements.");

        EReference fkRef = ref(foreignKey, "cwmForeignKey", cwm.foreignKey, 0, 1,
                "The materialized catalog constraint (relational::ForeignKey), when it exists.");
        oppositeRole(fkRef, "ormForeignKey");
        attrUnsettable(foreignKey, "constraintMode", constraintMode,
                "Whether a physical constraint is generated.");

        EReference dcCol = ref(discriminatorColumn, "cwmColumn", cwm.column, 1, 1,
                "The catalog discriminator column.");
        oppositeRole(dcCol, "ormDiscriminatorColumn");
        attrUnsettable(discriminatorColumn, "discriminatorType", discriminatorType,
                "Value type of the discriminator column.");

        // -- SecondaryTable / CollectionTable / JoinTable ------------------

        EReference stTable = ref(secondaryTable, "cwmTable", cwm.namedColumnSet, 1, 1,
                "The secondary table (relational::NamedColumnSet).");
        oppositeRole(stTable, "ormSecondaryTable");
        cont(secondaryTable, "primaryKeyJoinColumn", joinColumn, 0, -1, true,
                "Join columns to the primary table (insertable/updatable are meaningless here).");
        cont(secondaryTable, "primaryKeyForeignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the primary-key join.");

        EReference ctTable = ref(collectionTable, "cwmTable", cwm.namedColumnSet, 1, 1,
                "The collection table (relational::NamedColumnSet).");
        oppositeRole(ctTable, "ormCollectionTable");
        cont(collectionTable, "joinColumn", joinColumn, 0, -1, true,
                "Join columns to the owning entity's table.");
        cont(collectionTable, "foreignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the join columns.");

        EReference jtTable = ref(joinTable, "cwmTable", cwm.namedColumnSet, 1, 1,
                "The intersection table (relational::NamedColumnSet).");
        oppositeRole(jtTable, "ormJoinTable");
        cont(joinTable, "joinColumn", joinColumn, 0, -1, true,
                "Join columns towards the owning side.");
        cont(joinTable, "foreignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the owning-side join columns.");
        cont(joinTable, "inverseJoinColumn", joinColumn, 0, -1, true,
                "Join columns towards the target side.");
        cont(joinTable, "inverseForeignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the target-side join columns.");

        // -- Overrides / Convert / Converter / Listener / Callback ---------

        cont(attributeOverride, "column", column, 1, 1, true,
                "The overriding column.");

        cont(associationOverride, "joinColumn", joinColumn, 0, -1, true,
                "The overriding join columns.");
        cont(associationOverride, "foreignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the overriding join columns.");
        cont(associationOverride, "joinTable", joinTable, 0, 1, true,
                "The overriding join table.");

        attr(convert, "attributeName", cwm.stringType, 0, 1,
                "Path of the attribute the conversion applies to (empty for the annotated "
                        + "attribute itself).");
        attr(convert, "converter", cwm.stringType, 0, 1,
                "Fully qualified Java class name of the converter (runtime artifact).");
        attrUnsettableBool(convert, "disableConversion",
                "Disables an otherwise auto-applied converter.");

        attr(converter, "class", cwm.stringType, 1, 1,
                "Fully qualified Java class name of the converter (runtime artifact).");
        attrUnsettableBool(converter, "autoApply",
                "Whether the converter applies automatically to all matching attributes.");

        attr(entityListener, "class", cwm.stringType, 1, 1,
                "Fully qualified Java class name of the listener (runtime artifact).");
        cont(entityListener, "lifecycleCallback", lifecycleCallback, 0, -1, false,
                "Callback methods of the listener.");

        attr(lifecycleCallback, "kind", callbackKind, 1, 1,
                "The lifecycle event invoking the method.");
        attr(lifecycleCallback, "methodName", cwm.stringType, 1, 1,
                "Name of the callback method.");

        // -- PersistenceUnit ----------------------------------------------

        attrUnsettableBool(persistenceUnitMetadata, "xmlMappingMetadataComplete",
                "Whether the XML mapping metadata is complete for the unit.");
        cont(persistenceUnitMetadata, "persistenceUnitDefaults", persistenceUnitDefaults, 0, 1, true,
                "Defaults for the persistence unit.");

        attrUnsettableBool(persistenceUnitDefaults, "delimitedIdentifiers",
                "Whether database identifiers are treated as delimited.");
        attrUnsettableBool(persistenceUnitDefaults, "cascadePersist",
                "Adds cascade-persist to all relationships of the unit.");
        attrUnsettable(persistenceUnitDefaults, "access", accessType,
                "Default access strategy of the unit.");
        cont(persistenceUnitDefaults, "entityListener", entityListener, 0, -1, false,
                "Default entity listeners of the unit.");

        // -- EntityMappings ------------------------------------------------

        cont(entityMappings, "persistenceUnitMetadata", persistenceUnitMetadata, 0, 1, true,
                "Persistence-unit level metadata.");
        attrUnsettable(entityMappings, "access", accessType,
                "Default access strategy of this mapping set.");
        cont(entityMappings, "sequenceGenerator", sequenceGenerator, 0, -1, false,
                "Globally defined sequence generators.");
        cont(entityMappings, "tableGenerator", tableGenerator, 0, -1, false,
                "Globally defined table generators.");
        cont(entityMappings, "namedQuery", namedQuery, 0, -1, false,
                "Globally defined named queries.");
        cont(entityMappings, "namedNativeQuery", namedNativeQuery, 0, -1, false,
                "Globally defined named native queries.");
        cont(entityMappings, "namedStoredProcedureQuery", namedStoredProcedureQuery, 0, -1, false,
                "Globally defined stored-procedure queries.");
        cont(entityMappings, "sqlResultSetMapping", sqlResultSetMapping, 0, -1, false,
                "Globally defined result-set mappings.");
        cont(entityMappings, "mappedSuperclass", mappedSuperclass, 0, -1, false,
                "Mapped superclasses of this set.");
        cont(entityMappings, "entity", entity, 0, -1, false,
                "Entities of this set.");
        cont(entityMappings, "embeddable", embeddable, 0, -1, false,
                "Embeddables of this set.");
        cont(entityMappings, "converter", converter, 0, -1, false,
                "Registered converters.");
        EReference emPkg = ref(entityMappings, "cwmObjectPackage", cwm.cwmPackage, 0, 1,
                "The core::Package holding the mapped classes; its package chain is the Java "
                        + "package (converter convention: package names are the Java package "
                        + "segments).");
        oppositeRole(emPkg, "ormEntityMappings");
        EReference emCat = ref(entityMappings, "cwmCatalog", cwm.catalog, 0, 1,
                "The default relational::Catalog of this mapping set.");
        oppositeRole(emCat, "ormEntityMappingsCatalog");
        EReference emSchema = ref(entityMappings, "cwmSchema", cwm.schema, 0, 1,
                "The default relational::Schema of this mapping set.");
        oppositeRole(emSchema, "ormEntityMappingsSchema");

        // -- Entity --------------------------------------------------------

        EReference entTable = ref(entity, "cwmTable", cwm.namedColumnSet, 0, 1,
                "The primary table (relational::NamedColumnSet; a table or updatable view). "
                        + "Replaces the former Table wrapper — schema and catalog follow from the "
                        + "table's namespace chain.");
        oppositeRole(entTable, "ormEntity");
        attrUnsettableBool(entity, "cacheable",
                "Whether the entity is cacheable (shared-cache mode ENABLE_SELECTIVE/"
                        + "DISABLE_SELECTIVE).");
        attrUnsettable(entity, "inheritanceStrategy", inheritanceType,
                "Inheritance strategy rooted at this entity (replaces the former Inheritance "
                        + "wrapper).");
        attr(entity, "discriminatorValue", cwm.stringType, 0, 1,
                "Discriminator value of this entity in a SINGLE_TABLE/JOINED hierarchy.");
        cont(entity, "discriminatorColumn", discriminatorColumn, 0, 1, true,
                "The discriminator column of the hierarchy rooted here.");
        cont(entity, "secondaryTable", secondaryTable, 0, -1, false,
                "Additional tables of the entity.");
        cont(entity, "primaryKeyJoinColumn", joinColumn, 0, -1, true,
                "Join columns to the superclass table in a JOINED hierarchy (insertable/"
                        + "updatable are meaningless here).");
        cont(entity, "primaryKeyForeignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the JOINED primary-key join.");
        cont(entity, "sequenceGenerator", sequenceGenerator, 0, 1, true,
                "Entity-local sequence generator.");
        cont(entity, "tableGenerator", tableGenerator, 0, 1, true,
                "Entity-local table generator.");
        cont(entity, "namedQuery", namedQuery, 0, -1, false,
                "Entity-local named queries.");
        cont(entity, "namedNativeQuery", namedNativeQuery, 0, -1, false,
                "Entity-local named native queries.");
        cont(entity, "namedStoredProcedureQuery", namedStoredProcedureQuery, 0, -1, false,
                "Entity-local stored-procedure queries.");
        cont(entity, "sqlResultSetMapping", sqlResultSetMapping, 0, -1, false,
                "Entity-local result-set mappings.");
        cont(entity, "attributeOverride", attributeOverride, 0, -1, false,
                "Column overrides for inherited value mappings.");
        cont(entity, "associationOverride", associationOverride, 0, -1, false,
                "Join overrides for inherited relationship mappings.");
        cont(entity, "convert", convert, 0, -1, false,
                "Converter applications for inherited attributes.");
        cont(entity, "namedEntityGraph", namedEntityGraph, 0, -1, false,
                "Named entity graphs of the entity.");

        // -- Base / SimpleBase / Basic / Id / Version ----------------------

        cont(base, "column", column, 0, 1, true,
                "The mapped column with its insert/update flags.");
        attrUnsettable(base, "temporal", temporalType,
                "Temporal precision of a temporal value.");

        attrUnsettable(simpleBase, "fetch", fetchType,
                "Fetch strategy of the value.");
        attrUnsettable(simpleBase, "enumerated", enumType,
                "Enum mapping of an enum value.");
        attrUnsettableBool(simpleBase, "lob",
                "Maps the value as a large object (replaces the former empty Lob marker class).");
        cont(simpleBase, "convert", convert, 0, -1, false,
                "Converter applications for the value (paths for embeddable elements).");

        attrUnsettableBool(basic, "optional",
                "Whether the value may be null.");

        cont(id, "generatedValue", generatedValue, 0, 1, true,
                "Id generation for this id attribute.");
        cont(id, "sequenceGenerator", sequenceGenerator, 0, 1, true,
                "Id-local sequence generator.");
        cont(id, "tableGenerator", tableGenerator, 0, 1, true,
                "Id-local table generator.");
        EReference idPk = ref(id, "cwmPrimaryKey", cwm.primaryKey, 0, 1,
                "The catalog primary key the id column belongs to.");
        oppositeRole(idPk, "ormIdMapping");

        // -- Embedded / EmbeddedId / ElementCollection ---------------------

        cont(embedded, "attributeOverride", attributeOverride, 0, -1, false,
                "Column overrides per embeddable path.");
        cont(embedded, "associationOverride", associationOverride, 0, -1, false,
                "Join overrides per embeddable path.");
        cont(embedded, "convert", convert, 0, -1, false,
                "Converter applications per embeddable path.");

        cont(embeddedId, "attributeOverride", attributeOverride, 0, -1, false,
                "Column overrides per embeddable path.");
        EReference eidPk = ref(embeddedId, "cwmPrimaryKey", cwm.primaryKey, 0, 1,
                "The catalog primary key the composite id maps to.");
        oppositeRole(eidPk, "ormEmbeddedIdMapping");

        cont(elementCollection, "attributeOverride", attributeOverride, 0, -1, false,
                "Column overrides for embeddable elements.");
        cont(elementCollection, "associationOverride", associationOverride, 0, -1, false,
                "Join overrides for embeddable elements.");
        cont(elementCollection, "collectionTable", collectionTable, 0, 1, true,
                "The collection table.");

        // -- Relationships -------------------------------------------------

        cont(manyToOne, "joinColumn", joinColumn, 0, -1, true,
                "Join columns of the relationship.");
        attrUnsettableBool(manyToOne, "id",
                "Whether the relationship is part of the id (derived identity).");
        attr(manyToOne, "mapsId", cwm.stringType, 0, 1,
                "Maps the relationship onto an id attribute of an embedded id (derived "
                        + "identity).");
        attrUnsettableBool(manyToOne, "optional",
                "Whether the reference may be null.");

        cont(oneToOne, "primaryKeyJoinColumn", joinColumn, 0, -1, true,
                "Primary-key join columns (shared primary key).");
        cont(oneToOne, "primaryKeyForeignKey", foreignKey, 0, 1, true,
                "Foreign-key policy for the primary-key join.");
        cont(oneToOne, "joinColumn", joinColumn, 0, -1, true,
                "Join columns of the relationship.");
        attrUnsettableBool(oneToOne, "id",
                "Whether the relationship is part of the id (derived identity).");
        attr(oneToOne, "mapsId", cwm.stringType, 0, 1,
                "Maps the relationship onto an id attribute of an embedded id (derived "
                        + "identity).");
        attrUnsettableBool(oneToOne, "optional",
                "Whether the reference may be null.");
        attrUnsettableBool(oneToOne, "orphanRemoval",
                "Removes the target when it is dereferenced.");

        cont(oneToMany, "joinColumn", joinColumn, 0, -1, true,
                "Foreign-key columns on the target table (unidirectional without a join table).");
        attrUnsettableBool(oneToMany, "orphanRemoval",
                "Removes targets when they leave the collection.");

        // -- Generatoren ---------------------------------------------------

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

        EReference tgTable = ref(tableGenerator, "cwmTable", cwm.namedColumnSet, 0, 1,
                "The generator table.");
        oppositeRole(tgTable, "ormTableGenerator");
        EReference tgPk = ref(tableGenerator, "cwmPkColumn", cwm.column, 0, 1,
                "The column holding the generator names.");
        oppositeRole(tgPk, "ormTableGeneratorPk");
        EReference tgVal = ref(tableGenerator, "cwmValueColumn", cwm.column, 0, 1,
                "The column holding the last allocated value.");
        oppositeRole(tgVal, "ormTableGeneratorValue");
        attr(tableGenerator, "pkColumnValue", cwm.stringType, 0, 1,
                "Row selector value in the pk column (a real value in the data, not a model "
                        + "name).");

        attrUnsettable(generatedValue, "strategy", generationType,
                "Generation strategy.");
        EReference gvGen = ref(generatedValue, "generator", generator, 0, 1,
                "The generator producing the values (typed reference instead of the former name "
                        + "lookup).");
        oppositeRole(gvGen, "ormGeneratedValue");

        // -- Queries -------------------------------------------------------

        attr(queryHint, "value", cwm.stringType, 1, 1,
                "Value of the hint.");

        attr(namedQuery, "query", cwm.stringType, 1, 1,
                "The JPQL query text.");
        attrUnsettable(namedQuery, "lockMode", lockModeType,
                "Lock mode applied when the query executes.");
        cont(namedQuery, "hint", queryHint, 0, -1, false,
                "Provider hints.");

        attr(namedNativeQuery, "query", cwm.stringType, 1, 1,
                "The native SQL query text.");
        attr(namedNativeQuery, "resultClass", cwm.stringType, 0, 1,
                "Fully qualified Java class name of the result entity (runtime artifact).");
        EReference nnqRsm = ref(namedNativeQuery, "resultSetMapping", sqlResultSetMapping, 0, 1,
                "The result-set mapping shaping the result.");
        oppositeRole(nnqRsm, "ormNativeQueryUser");
        cont(namedNativeQuery, "hint", queryHint, 0, -1, false,
                "Provider hints.");

        EReference nspqProc = ref(namedStoredProcedureQuery, "cwmProcedure", cwm.procedure, 0, 1,
                "The catalog procedure (relational::Procedure) — the procedure name is not "
                        + "repeated as a string.");
        oppositeRole(nspqProc, "ormStoredProcedureQuery");
        cont(namedStoredProcedureQuery, "parameter", storedProcedureParameter, 0, -1, true,
                "Parameters in call order.");
        EAttribute nspqRc = attr(namedStoredProcedureQuery, "resultClass", cwm.stringType, 0, -1,
                "Result entity class names in result order (runtime artifacts).");
        nspqRc.setOrdered(true);
        EReference nspqRsm = ref(namedStoredProcedureQuery, "resultSetMapping", sqlResultSetMapping, 0, -1,
                "Result-set mappings in result order.");
        nspqRsm.setOrdered(true);
        oppositeRole(nspqRsm, "ormStoredProcedureQueryUser");
        cont(namedStoredProcedureQuery, "hint", queryHint, 0, -1, false,
                "Provider hints.");

        attr(storedProcedureParameter, "class", cwm.stringType, 0, 1,
                "Java-side parameter class name (runtime artifact; stays settable).");
        attrUnsettable(storedProcedureParameter, "mode", parameterMode,
                "Parameter direction (stays settable; cwmParameter is additive).");
        EReference sppParam = ref(storedProcedureParameter, "cwmParameter", cwm.sqlParameter, 0, 1,
                "The catalog parameter (relational::SQLParameter), when resolvable.");
        oppositeRole(sppParam, "ormParameter");
        EReference sppType = ref(storedProcedureParameter, "cwmType", cwm.classifier, 0, 1,
                "The catalog parameter type, when resolvable.");
        oppositeRole(sppType, "ormParameterType");

        cont(sqlResultSetMapping, "entityResult", entityResult, 0, -1, false,
                "Entity results.");
        cont(sqlResultSetMapping, "constructorResult", constructorResult, 0, -1, false,
                "Constructor results.");
        cont(sqlResultSetMapping, "columnResult", columnResult, 0, -1, false,
                "Scalar column results.");

        attr(entityResult, "entityClass", cwm.stringType, 0, 1,
                "Fully qualified Java class name of the result entity (stays settable; cwmClass "
                        + "is additive and takes precedence when set).");
        attr(entityResult, "discriminatorColumn", cwm.stringType, 0, 1,
                "Alias of the discriminator column in the result set.");
        EReference erClass = ref(entityResult, "cwmClass", cwm.cwmClass, 0, 1,
                "The result entity class as core::Class (additive anchor).");
        oppositeRole(erClass, "ormEntityResult");
        cont(entityResult, "fieldResult", fieldResult, 0, -1, false,
                "Field results of the entity.");

        attr(fieldResult, "column", cwm.stringType, 1, 1,
                "Alias of the column in the result set (aliases may prevent catalog "
                        + "resolution).");
        EReference frFeature = ref(fieldResult, "cwmFeature", cwm.structuralFeature, 0, 1,
                "The mapped object feature (additive anchor).");
        oppositeRole(frFeature, "ormFieldResult");

        attr(columnResult, "class", cwm.stringType, 0, 1,
                "Java type the scalar value converts to (runtime artifact).");
        EReference crCol = ref(columnResult, "cwmColumn", cwm.column, 0, 1,
                "The catalog column, when the alias is resolvable (additive anchor).");
        oppositeRole(crCol, "ormColumnResult");

        attr(constructorResult, "targetClass", cwm.stringType, 1, 1,
                "Fully qualified Java class name whose constructor is invoked (runtime "
                        + "artifact).");
        EReference crCols = cont(constructorResult, "columnResult", columnResult, 0, -1, true,
                "Constructor arguments in declaration order.");
        crCols.setOrdered(true);

        // -- Entity graphs -------------------------------------------------

        attrUnsettableBool(namedEntityGraph, "includeAllAttributes",
                "Includes all attributes of the root entity.");
        cont(namedEntityGraph, "namedAttributeNode", namedAttributeNode, 0, -1, false,
                "Nodes of the graph.");
        cont(namedEntityGraph, "subgraph", namedSubgraph, 0, -1, false,
                "Subgraphs referenced by nodes.");
        cont(namedEntityGraph, "subclassSubgraph", namedSubgraph, 0, -1, false,
                "Subgraphs for subclasses of the root entity.");

        attr(namedAttributeNode, "subgraph", cwm.stringType, 0, 1,
                "Graph-local name of the subgraph fetched for this attribute.");
        attr(namedAttributeNode, "keySubgraph", cwm.stringType, 0, 1,
                "Graph-local name of the subgraph fetched for the map key.");

        attr(namedSubgraph, "class", cwm.stringType, 0, 1,
                "Fully qualified Java class name the subgraph applies to (runtime artifact).");
        cont(namedSubgraph, "namedAttributeNode", namedAttributeNode, 0, -1, false,
                "Nodes of the subgraph.");

        return p;
    }

    // -- Hilfsmethoden -----------------------------------------------------

    /** {@code Property.oppositeRoleName}-Paar (Konvention fuer versteckte Assoziationsenden). */
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

    /** Optionales Enum-Attribut mit Tri-State (unset erkennbar). */
    private EAttribute attrUnsettable(EClass owner, String name, EEnum type, String doc) {
        EAttribute a = attr(owner, name, type, 0, 1, doc);
        a.setUnsettable(true);
        return a;
    }

    /** Optionales Boolean-Attribut mit Tri-State (unset erkennbar). */
    private EAttribute attrUnsettableBool(EClass owner, String name, String doc) {
        EAttribute a = attr(owner, name, cwm.booleanType, 0, 1, doc);
        a.setUnsettable(true);
        return a;
    }

    /** Optionales Integer-Attribut mit Tri-State (unset erkennbar). */
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
     * Containment-Referenz. {@code ordered} steuert die Listensemantik: true, wo die
     * Reihenfolge Bedeutung traegt (JoinColumn-Listen, Parameter, Konstruktor-Argumente).
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
