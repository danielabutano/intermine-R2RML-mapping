package org.intermine.r2rmlmapping;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
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
			String table = cd.getSimpleName();
			mapBasicFields(cd, jenaModel, uriHelper);
			mapJoinToOtherTable(cd, table, jenaModel, uriHelper);
		}
		try (PrintWriter out = new PrintWriter(new FileWriter("mapping.ttl"))){
			jenaModel.write(out, "turtle");
		} catch (IOException ex) {
			ex.printStackTrace();
                        System.exit(1);
		}
	}

	private static void setKnownPrefixes(final org.apache.jena.rdf.model.Model jenaModel)
	{
		jenaModel.setNsPrefix("rr", R2RML.uri);
		jenaModel.setNsPrefix("rdfs", RDFS.uri);
		jenaModel.setNsPrefix("up", URIHelper.uniProtNS);
	}

	private static void mapJoinToOtherTable(ClassDescriptor cd, String table,
	    org.apache.jena.rdf.model.Model jenaModel, URIHelper uriHelper)
	{
		for (CollectionDescriptor collection : cd.getCollectionDescriptors())
		{
			if (FieldDescriptor.M_N_RELATION == collection.relationType())
			{
                String indirectionTable = DatabaseUtil.getIndirectionTableName(collection);
                System.err.println("JOINING TABLE: " + indirectionTable);
                String column1 = DatabaseUtil.getInwardIndirectionColumnName(collection, FORMAT_VERSION);
                String column2 = DatabaseUtil.getOutwardIndirectionColumnName(collection, FORMAT_VERSION);
                System.err.println(column1 + ": FOREIGN KEY with type java.lang.Integer referring to "
                    + table + ".id");
                System.err.println(column2 + ": FOREIGN KEY with type java.lang.Integer referring to "
                    + collection.getReferencedClassDescriptor().getName() + (".id"));
                System.err.println();
                final Resource basicTableMapping = createMappingNameForTable(jenaModel, table);
                mapManyToMany(jenaModel, basicTableMapping, cd, uriHelper, collection);
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
				AttributeDescriptor ad = (AttributeDescriptor) fd;
				mapPrimitiveObjects(model, tableName, basicTableMapping, columnName, ad);
				System.err.println(columnName +
				    (columnName.equalsIgnoreCase("id") ? ": PRIMARY KEY" : ": column")
				    + " with type " + ((AttributeDescriptor) fd).getType());
			}
			else if (!fd.isCollection())
			{
				mapOneToMany(model, basicTableMapping, fd, columnName, uriHelper);
			}
		}
		System.err.println();
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
	 * @param basicTableMapping
	 * @param fromTableDescription 
	 * @param uriHelper
	 * @param collection 
	 */
	private static void mapManyToMany(org.apache.jena.rdf.model.Model model, Resource basicTableMapping,
	    ClassDescriptor fromTableDescription, URIHelper uriHelper, CollectionDescriptor collection)
	{
		 
		final ClassDescriptor toTableDescription = collection.getReferencedClassDescriptor(); 
		final String fromTableName = DatabaseUtil.getTableName(fromTableDescription);
		final String joinTableName = DatabaseUtil.getIndirectionTableName(collection);
		final String toTableName = DatabaseUtil.getTableName(toTableDescription);
//		
		String fromJoinColumn = DatabaseUtil.getInwardIndirectionColumnName(collection, FORMAT_VERSION);
		String toJoinColumn = DatabaseUtil.getOutwardIndirectionColumnName(collection, FORMAT_VERSION);
		Resource table = model.createResource();
		if (findSubjectMap(fromTableDescription, fromTableName, uriHelper) != null)
		{
			Resource jointTriplesMap = createMappingNameForJoinTable(model, fromTableName, joinTableName, toTableName);
			final AttributeDescriptor toColumnName = findSubjectMap(toTableDescription, toTableName, uriHelper);
			if (toColumnName != null)
			{
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
				        + " WHERE " + fromTableName + ".id = " + joinTableName + "."+fromJoinColumn +" AND " + toTableName + ".id = " + joinTableName + "."+toJoinColumn);
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
	 * @param columnName
	 */
	private static void mapOneToMany(org.apache.jena.rdf.model.Model model, final Resource basicTableMapping,
	    FieldDescriptor fd, String columnName, URIHelper uriHelper)
	{
		final ClassDescriptor rfc = ((ReferenceDescriptor) fd).getReferencedClassDescriptor();
		String jointTable = DatabaseUtil.getTableName(rfc);
		System.err.println(columnName + ": FOREIGN KEY with type java.lang.Integer referring to "
		    + jointTable + ".id");
                if (findSubjectMap(rfc,jointTable, uriHelper) != null) {
					Resource objectMap = model.createResource();
					Resource objectPredicateMap = model.createResource();
					Resource joinCondition = model.createResource();
					model.add(basicTableMapping, R2RML.predicateObjectMap, objectPredicateMap);
					model.add(objectPredicateMap, R2RML.predicate, R2RML.createIMProperty(rfc.getSimpleName()));
					model.add(objectPredicateMap, R2RML.objectMap, objectMap);
					model.add(objectMap, R2RML.parentTriplesMap, createMappingNameForTable(model, jointTable));
					model.add(objectMap, R2RML.joinCondition, joinCondition);
					model.add(joinCondition, R2RML.child, fd.getName() + "id");
					model.add(joinCondition, R2RML.parent, "id");
                }
	}

	/**
	 * A primitive object is field that is just a value such as true, false or 1 or "lala"
	 * @param model
	 * @param tableName
	 * @param basicTableMapping
	 * @param columnName
	 * @param ad
	 */
	private static void mapPrimitiveObjects(org.apache.jena.rdf.model.Model model, final String tableName,
	    final Resource basicTableMapping, String columnName, AttributeDescriptor ad)
	{
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
