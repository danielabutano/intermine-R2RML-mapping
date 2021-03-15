package org.intermine.r2rmlmapping;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.intermine.metadata.AttributeDescriptor;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.r2rmlmapping.vocabulary.R2RML;
import org.intermine.sql.DatabaseUtil;


public class R2RMLMapping
{
	/** The version number of the database format */
	static final int FORMAT_VERSION = 1;

	public static void main(String[] args)
	{
		Model model = Model.getInstanceByName("genomic");
		Set<ClassDescriptor> classDescriptors = model.getClassDescriptors();
		final org.apache.jena.rdf.model.Model jenaModel = ModelFactory.createDefaultModel();
		setKnownPrefixes(jenaModel);
		URIHelper uriHelper = new URIHelper();

		for (ClassDescriptor cd : classDescriptors)
		{
			if (isExportable(cd)) {
				mapBasicFields(cd, jenaModel, uriHelper);
				mapJoinToOtherTable(cd, jenaModel, uriHelper);
			}
		}
		try (PrintWriter out = new PrintWriter(new FileWriter("mapping.ttl"))){
			jenaModel.write(out, "turtle");
		} catch (IOException ex) {
			ex.printStackTrace();
                        System.exit(1);
		}
	}

	private static boolean isExportable(ClassDescriptor classDescriptor) {
		String dataType = classDescriptor.getSimpleName();
		if ("Annotatable".equalsIgnoreCase(dataType) || "BioEntity".equalsIgnoreCase(dataType)
				|| "SequenceFeature".equalsIgnoreCase(dataType)) {
			return false;
		}
		return true;
	}

	private static void setKnownPrefixes(final org.apache.jena.rdf.model.Model jenaModel)
	{
		jenaModel.setNsPrefix("rr", R2RML.uri);
		jenaModel.setNsPrefix("rdfs", RDFS.uri);
		jenaModel.setNsPrefix("up", URIHelper.uniProtNS);
	}

	private static void mapJoinToOtherTable(ClassDescriptor cd,
	    org.apache.jena.rdf.model.Model model, URIHelper uriHelper)
	{
		for (CollectionDescriptor collection : cd.getAllCollectionDescriptors())
		{
			if (FieldDescriptor.M_N_RELATION == collection.relationType())
			{
                String indirectionTable = DatabaseUtil.getIndirectionTableName(collection);
                System.err.println("JOINING TABLE: " + indirectionTable);
                mapManyToMany(model, cd, uriHelper, collection);
			}
		}
	}

	private static void mapBasicFields(ClassDescriptor cd, org.apache.jena.rdf.model.Model model, URIHelper uriHelper)
	{
		final String tableName = DatabaseUtil.getTableName(cd);
		System.err.println("TABLE: " + tableName);
		final Resource basicTableMapping = createMappingNameForTable(model, tableName);
		
		final Resource logicalTable = model.createResource();
		
		final AttributeDescriptor subjectMap = generateSubjectMap(cd, model, tableName, basicTableMapping, uriHelper);
		if (subjectMap != null) {
				model.add(basicTableMapping, RDF.type, R2RML.TriplesMap);
				model.add(basicTableMapping, R2RML.logicalTable, logicalTable);
				model.add(logicalTable, R2RML.tableName, tableName);
		}
		for (FieldDescriptor fd : cd.getAllFieldDescriptors())
		{
			String columnName = DatabaseUtil.getColumnName(fd);

			if (fd instanceof AttributeDescriptor)
			{
				mapPrimitiveObjects(model, tableName, basicTableMapping, (AttributeDescriptor) fd);
				System.err.println(columnName +
				    (columnName.equalsIgnoreCase("id") ? ": PRIMARY KEY" : ": column")
				    + " with type " + ((AttributeDescriptor) fd).getType());
			} else if (fd.isCollection() && ((CollectionDescriptor) fd).relationType() == FieldDescriptor.ONE_N_RELATION) {
				//Gene->Synonyms
				if (isExportable(cd)) {
					createOneToManyResources(model, cd, (CollectionDescriptor) fd, basicTableMapping, uriHelper);
				}
			} else if (!fd.isCollection()) {
				mapManyToOne(model, basicTableMapping, fd, uriHelper);
			}
		}
		System.err.println();
	}

	public static void createOneToManyResources(org.apache.jena.rdf.model.Model model,
			ClassDescriptor cd, CollectionDescriptor collection, Resource basicTableMapping, URIHelper uriHelper) {
		System.err.println("ONE_N_RELATION");
		System.err.println(cd.getSimpleName());//Gene
		ReferenceDescriptor reverse = collection.getReverseReferenceDescriptor();

		String jointTable = collection.getReferencedClassDescriptor().getSimpleName();
		if (findSubjectMap(collection.getReferencedClassDescriptor(),collection.getReferencedClassDescriptor().getSimpleName(), uriHelper) != null
				&& isExportable(collection.getReferencedClassDescriptor())) {//e.g strain -> Sequencefeature
			Resource objectMap = model.createResource();
			Resource objectPredicateMap = model.createResource();
			Resource joinCondition = model.createResource();
			model.add(basicTableMapping, R2RML.predicateObjectMap, objectPredicateMap);
			model.add(objectPredicateMap, R2RML.predicate, R2RML.createIMProperty(collection.getReferencedClassDescriptor().getSimpleName()));
			model.add(objectPredicateMap, R2RML.objectMap, objectMap);
			model.add(objectMap, R2RML.parentTriplesMap, createMappingNameForTable(model, jointTable));
			model.add(objectMap, R2RML.joinCondition, joinCondition);
			model.add(joinCondition, R2RML.child, "id");
			model.add(joinCondition, R2RML.parent, reverse.getName() + "id");
		}

	}

	private static AttributeDescriptor generateSubjectMap(ClassDescriptor cd, org.apache.jena.rdf.model.Model model,
	    final String tableName, final Resource basicTableMapping, URIHelper uriHelper)
	{
		for (FieldDescriptor fd : cd.getAllFieldDescriptors())
		{
			String columnName = DatabaseUtil.getColumnName(fd);

			if (fd instanceof AttributeDescriptor)
			{
				AttributeDescriptor ad = (AttributeDescriptor) fd;
				if (uriHelper.isURIIdentifier(tableName, columnName))
				{
					
					Resource subjectMap = model.createResource();
					model.add(basicTableMapping, R2RML.subjectMap, subjectMap);

					model.add(subjectMap, R2RML.template, uriHelper.createURI(tableName));

					if (cd.getFairTerm() != null && !cd.getFairTerm().isEmpty()){
						Resource classInOutsideWorld = ResourceFactory.createProperty(cd.getFairTerm());
						model.add(subjectMap, R2RML.classProperty, classInOutsideWorld);
					}
					return ad;
				}
			}
		}
		return null;
	}

	private static AttributeDescriptor findSubjectMap(ClassDescriptor cd,
	    final String tableName, URIHelper uriHelper)
	{
		for (FieldDescriptor fd : cd.getAllFieldDescriptors())
		{
			String columnName = DatabaseUtil.getColumnName(fd);

			if (fd instanceof AttributeDescriptor)
			{
				AttributeDescriptor ad = (AttributeDescriptor) fd;
				if (uriHelper.isURIIdentifier(tableName, columnName))
				{
					return ad;
				}
			}
		}
		return null;
	}

	/***
	 * Here we map two tables to each other.
	 * Normally one would follow https://www.w3.org/TR/r2rml/#example-m2m
	 * 
	 * However, that does not apply here as we the intermine primary keys are internal only.
	 * We need to expose the relation as it is in the outside world.
	 * 
	 * This means building a 'view' that links the two tables together via their external known
	 * URIs. 
	 * 
	 * 
	 * @param model
	 * @param fromTableDescription 
	 * @param uriHelper
	 * @param collection 
	 */
	private static void mapManyToMany(org.apache.jena.rdf.model.Model model,
	    ClassDescriptor fromTableDescription, URIHelper uriHelper, CollectionDescriptor collection) {

		final ClassDescriptor toTableDescription = collection.getReferencedClassDescriptor();

		Model imModel = Model.getInstanceByName("genomic");
		Set<ClassDescriptor> toSubDescriptors = imModel.getAllSubs(toTableDescription);
		if (!toSubDescriptors.isEmpty()) {
			for (ClassDescriptor toSubDescriptor : toSubDescriptors) {
				if (isExportable(toSubDescriptor)) {
					createManytoManyResources(model, fromTableDescription, toSubDescriptor, uriHelper, collection);
				}
			}
		} else {
			if (isExportable(toTableDescription)) {
				createManytoManyResources(model, fromTableDescription, toTableDescription, uriHelper, collection);
			}
		}
	}

	public static void createManytoManyResources(org.apache.jena.rdf.model.Model model,
										  ClassDescriptor fromTableDescription,
										  ClassDescriptor toTableDescription,
										  URIHelper uriHelper,
										  CollectionDescriptor collection) {
		final String fromTableName = DatabaseUtil.getTableName(fromTableDescription);
		final String joinTableName = DatabaseUtil.getIndirectionTableName(collection);
		final String toTableName = DatabaseUtil.getTableName(toTableDescription);
		String fromJoinColumn = DatabaseUtil.getInwardIndirectionColumnName(collection, FORMAT_VERSION);
		String toJoinColumn = DatabaseUtil.getOutwardIndirectionColumnName(collection, FORMAT_VERSION);
		Resource table = model.createResource();
		if (findSubjectMap(fromTableDescription, fromTableName, uriHelper) != null)
		{
			Resource jointTriplesMap = createMappingNameForJoinTable(model, fromTableName, joinTableName, toTableName);
			final AttributeDescriptor toColumnName = findSubjectMap(toTableDescription, toTableName, uriHelper);
			if (toColumnName != null) {
				final AttributeDescriptor fromColumnname = generateSubjectMap(fromTableDescription, model,
						fromTableName,
						jointTriplesMap,
						uriHelper);
				Resource objectPredicateMap = model.createResource();
				Resource objectMap = model.createResource();
				model.add(jointTriplesMap, RDF.type, R2RML.TriplesMap);
				model.add(jointTriplesMap, R2RML.logicalTable, table);
				model.add(table, RDF.type, R2RML.R2RMLView);
				final Stream<String> distinct = Stream.of(fromTableName, joinTableName, toTableName).distinct();
				String tables = distinct.collect(Collectors.joining(","));
				// We build a big sql query to join internally via the intermine id's.
				// But expose the external identifiers only.
				// We need the "AS" fromColumnname because that column name is used in the
				// GenerateSubjectsMap method.
				model.add(table, R2RML.sqlQuery,
						"SELECT " + fromTableName + "." + fromColumnname.getName()
								+ ", "
								+ toTableName
								+ "." + toColumnName.getName() + " AS toColumnName  FROM "
								+ tables
								+ " WHERE " + fromTableName + ".id = " + joinTableName + "." + fromJoinColumn + " AND " + toTableName + ".id = " + joinTableName + "." + toJoinColumn);
				model.add(jointTriplesMap, R2RML.predicateObjectMap, objectPredicateMap);
				//TODO figure out what predicate to use. Maybe for now just use intermine
				model.add(objectPredicateMap, R2RML.predicate, R2RML.createIMProperty(toTableName));
				model.add(objectPredicateMap, R2RML.objectMap, objectMap);
				model.add(objectMap, RDF.type, R2RML.TermMap);
				model.add(objectMap, RDF.type, R2RML.ObjectMap);
				// We need to disambiguate here as the toColumnName and fromColumnName might be lexically the same.
				model.add(objectMap, R2RML.column, toColumnName.getName());
				model.add(objectMap, R2RML.termType, R2RML.IRI);
				model.add(objectMap, R2RML.template, uriHelper.createURI(toTableName, "toColumnName"));
			}
		}

	}

	/**
	 * We generate a simple join condition here as in <a href="https://www.w3.org/TR/r2rml/#example-fk"> the example</a>
	 * of the R2RML spec
	 *  
	 * @param model
	 * @param basicTableMapping
	 * @param fd
	 */
	private static void mapManyToOne(org.apache.jena.rdf.model.Model model, final Resource basicTableMapping,
	    FieldDescriptor fd, URIHelper uriHelper) {
		final ClassDescriptor refClassDescriptor = ((ReferenceDescriptor) fd).getReferencedClassDescriptor();
		Model imModel = Model.getInstanceByName("genomic");
		Set<ClassDescriptor> toSubDescriptors = imModel.getAllSubs(refClassDescriptor);
		if (!toSubDescriptors.isEmpty()) {
			for (ClassDescriptor toSubDescriptor : toSubDescriptors) {
				if (isExportable(toSubDescriptor)) {
					createManytoOneResources(model, basicTableMapping, fd, toSubDescriptor, uriHelper);
				}
			}
		} else {
			if (isExportable(refClassDescriptor)) {
				createManytoOneResources(model, basicTableMapping, fd, refClassDescriptor, uriHelper);
			}
		}

	}

	private static void createManytoOneResources(org.apache.jena.rdf.model.Model model,
			final Resource basicTableMapping, FieldDescriptor fieldDescriptor,
			ClassDescriptor referencedClassDescriptor, URIHelper uriHelper) {
		String jointTable = DatabaseUtil.getTableName(referencedClassDescriptor);
		if (findSubjectMap(referencedClassDescriptor,jointTable, uriHelper) != null) {
			Resource objectMap = model.createResource();
			Resource objectPredicateMap = model.createResource();
			Resource joinCondition = model.createResource();
			model.add(basicTableMapping, R2RML.predicateObjectMap, objectPredicateMap);
			model.add(objectPredicateMap, R2RML.predicate, R2RML.createIMProperty(referencedClassDescriptor.getSimpleName()));
			model.add(objectPredicateMap, R2RML.objectMap, objectMap);
			model.add(objectMap, R2RML.parentTriplesMap, createMappingNameForTable(model, jointTable));
			model.add(objectMap, R2RML.joinCondition, joinCondition);
			model.add(joinCondition, R2RML.child, fieldDescriptor.getName() + "id");
			model.add(joinCondition, R2RML.parent, "id");
		}
	}

	/**
	 * A primitive object is field that is just a value such as true, false or 1 or "lala"
	 * @param model
	 * @param tableName
	 * @param basicTableMapping
	 * @param ad
	 */
	private static void mapPrimitiveObjects(org.apache.jena.rdf.model.Model model, final String tableName,
	    final Resource basicTableMapping, AttributeDescriptor ad)
	{
		String columnName = DatabaseUtil.getColumnName(ad);
		System.err.println(tableName + '.' + columnName + " is primitive");
		Resource predicateObjectMap = model.createResource();
		Resource objectMap = model.createResource();
		model.add(basicTableMapping, R2RML.predicateObjectMap, predicateObjectMap);
		model.add(predicateObjectMap, R2RML.objectMap, objectMap);
		model.add(objectMap, RDF.type, R2RML.TermMap);
		model.add(objectMap, RDF.type, R2RML.ObjectMap);
		model.add(objectMap, R2RML.termType, R2RML.Literal);

		model.add(objectMap, R2RML.datatype, getXsdForFullyQualifiedClassName(ad));
		model.add(objectMap, R2RML.column, columnName);
		model.add(predicateObjectMap, R2RML.predicate, R2RML.createIMProperty(columnName));
	}

	private static Resource createMappingNameForTable(org.apache.jena.rdf.model.Model model, final String tableName)
	{
		return model.createResource("urn:intermine-table:" + tableName);
	}

	private static Resource createMappingNameForJoinTable(org.apache.jena.rdf.model.Model model, final String tableName,
	    final String joinTableName, final String otherTableName)
	{
		return model.createResource("urn:intermine-join-tables:" + tableName + '/'+joinTableName+'/' + otherTableName);
	}

	/**
	 * We need to map the java fully qualified object name to the right kind
	 * of XSD type.
	 * @param ad
	 * @return an XSD for the value
	 */
	private static Resource getXsdForFullyQualifiedClassName(AttributeDescriptor ad)
	{
		switch (ad.getType())
		{
                        case "org.intermine.objectstore.query.ClobAccess":
			case "java.lang.String":
				return XSD.xstring;
			case "java.lang.Boolean":
				return XSD.xboolean;
                        case "int":
			case "java.lang.Integer":
				return XSD.integer;
                        case "double":
			case "java.lang.Double":
				return XSD.decimal;        
			default:
				{
					System.err.println("Unknown primitive datatype: "+ad.getType());
					System.exit(2);
					return null;
				}
		}
	}
}
