package org.intermine.r2rmlmapping.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


public class R2RML
{
	public static final String uri = "http://www.w3.org/ns/r2rml#";
	public static final Resource TriplesMap = ResourceFactory.createProperty(uri, "TriplesMap");
	public static final Property logicalTable = ResourceFactory.createProperty(uri, "logicalTable");
	public static final Property tableName = ResourceFactory.createProperty(uri, "tableName");
	public static final Property subjectMap = ResourceFactory.createProperty(uri, "subjectMap");
	public static final Property template = ResourceFactory.createProperty(uri, "template");
	public static final Property classProperty = ResourceFactory.createProperty(uri, "class");
	public static final Property objectMap = ResourceFactory.createProperty(uri, "objectMap");
	public static final RDFNode TermMap = ResourceFactory.createProperty(uri, "TermMap");
	public static final RDFNode ObjectMap = ResourceFactory.createProperty(uri, "ObjectMap");
	public static final RDFNode Literal = ResourceFactory.createProperty(uri, "Literal");
	public static final Property termType = ResourceFactory.createProperty(uri, "termType");
	public static final Property datatype = ResourceFactory.createProperty(uri, "datatype");
	public static final Property column = ResourceFactory.createProperty(uri, "column");
	public static final Property predicate = ResourceFactory.createProperty(uri, "predicate");
	public static final Property parentTriplesMap = ResourceFactory.createProperty(uri, "parentTriplesMap");
	public static final Property joinCondition = ResourceFactory.createProperty(uri, "joinCondition");
	public static final Property child = ResourceFactory.createProperty(uri, "child");
	public static final Property parent = ResourceFactory.createProperty(uri, "parent");
	public static final Property R2RMLView = ResourceFactory.createProperty(uri, "R2RMLView");
	public static final Property sqlQuery = ResourceFactory.createProperty(uri, "sqlQuery");
	public static final Property predicateObjectMap = ResourceFactory.createProperty(uri, "predicateObjectMap");
	public static final RDFNode IRI = ResourceFactory.createProperty(uri, "IRI");
}
