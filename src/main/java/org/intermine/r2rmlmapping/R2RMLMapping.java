package org.intermine.r2rmlmapping;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
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
		Set<CollectionDescriptor> indirections = new HashSet<CollectionDescriptor>();
		final org.apache.jena.rdf.model.Model jenaModel = ModelFactory.createDefaultModel();
		for (ClassDescriptor cd : classDescriptors)
		{
			String table = cd.getSimpleName();
			if (//table.equalsIgnoreCase("Gene") ||
			table.equalsIgnoreCase("Protein") ||
			    table.equalsIgnoreCase("Organism")
			//table.equalsIgnoreCase("DataSource") ||
			//table.equalsIgnoreCase("DataSet")
			//
			)
			{

				mapBasicFields(cd, jenaModel);

				//joining table
				mapJoinToOtherTable(indirections, cd, table, jenaModel);
			}
		}
		jenaModel.write(System.out, "turtle");
	}

	private static void mapJoinToOtherTable(Set<CollectionDescriptor> indirections, ClassDescriptor cd, String table,
	    org.apache.jena.rdf.model.Model jenaModel)
	{
		for (CollectionDescriptor collection : cd.getCollectionDescriptors())
		{
			if (FieldDescriptor.M_N_RELATION == collection.relationType())
			{
				if (!indirections.contains(collection.getReverseReferenceDescriptor()))
				{
					indirections.add(collection);
					String indirectionTable = DatabaseUtil.getIndirectionTableName(collection);
					System.err.println("JOINING TABLE: " + indirectionTable);
					String column1 = DatabaseUtil.getInwardIndirectionColumnName(collection, FORMAT_VERSION);
					String column2 = DatabaseUtil.getOutwardIndirectionColumnName(collection, FORMAT_VERSION);
					System.err.println(column1 + ": FOREIGN KEY with type java.lang.Integer referring to "
					    + table + ".id");
					System.err.println(column2 + ": FOREIGN KEY with type java.lang.Integer referring to "
					    + collection.getReferencedClassDescriptor().getName() + (".id"));
					System.err.println();
				}
			}

		}
	}

	private static void mapBasicFields(ClassDescriptor cd, org.apache.jena.rdf.model.Model model)
	{
		final String tableName = DatabaseUtil.getTableName(cd);
		System.err.println("TABLE: " + tableName);
		final Resource basicTableMapping = model.createResource();
		model.add(basicTableMapping, RDF.type, R2RML.TriplesMap);
		final Resource logicalTable = model.createResource();
		model.add(basicTableMapping, R2RML.logicalTable, logicalTable);
		model.add(logicalTable, R2RML.tableName, tableName);
		URIHelper uriHelper = new URIHelper();
		for (FieldDescriptor fd : cd.getAllFieldDescriptors()) {
		    String columnName = DatabaseUtil.getColumnName(fd);
		    
		    if (fd instanceof AttributeDescriptor) {
		    	AttributeDescriptor ad = (AttributeDescriptor) fd;
		    	if (uriHelper.isURIIdentifier(tableName, columnName))
		    	{
		    		Resource classInOutsideWorld = ResourceFactory.createProperty(cd.getFairTerm());
		    		Resource subjectMap= model.createResource();
		    		model.add(basicTableMapping, R2RML.subjectMap, subjectMap);

					model.add(subjectMap, R2RML.template, uriHelper.createURI(tableName));
		    
		    		model.add(subjectMap, R2RML.classProperty, classInOutsideWorld);
				    
		    	}
				else if (! ad.isReference() && ad.getFairTerm() != null)
				{
					System.err.println(tableName +'.'+columnName + " is primitive");
					Resource objectMap = model.createResource();
					model.add(basicTableMapping, R2RML.objectMap, objectMap);
					model.add(objectMap, RDF.type, R2RML.TermMap);
					model.add(objectMap, RDF.type, R2RML.ObjectMap);
					model.add(objectMap, R2RML.termType, R2RML.Literal);

					model.add(objectMap, R2RML.datatype, getXsdForFullyQualifiedClassName(ad));
					model.add(objectMap, R2RML.column, columnName);
					model.add(objectMap, R2RML.predicate, model.createProperty(ad.getFairTerm()));
				}
				System.err.println(columnName +
				    (columnName.equalsIgnoreCase("id") ? ": PRIMARY KEY" : ": column")
				    + " with type " + ((AttributeDescriptor) fd).getType());
			}
			else if (!fd.isCollection())
			{ //n to one relation
				System.err.println(columnName + ": FOREIGN KEY with type java.lang.Integer referring to "
				    + ((ReferenceDescriptor) fd).getReferencedClassDescriptor().getSimpleName() + ".id");
			}
		}
		System.err.println();
	}

	private static Resource getXsdForFullyQualifiedClassName(AttributeDescriptor ad)
	{
		switch (ad.getType())
		{
			case "java.lang.String":
				return XSD.xstring;
			case "java.lang.Boolean":
				return XSD.xboolean;
			case "java.lang.Integer":
				return XSD.integer;
			case "java.lang.Double":
				return XSD.decimal;
			default:
				return null;
		}
	}
}
