package org.intermine.r2rmlmapping.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class R2RML
{
	public static final String 	uri = "http://www.w3.org/ns/r2rml#";
	public static final Resource TriplesMap = ResourceFactory.createProperty(uri, "TriplesMap");
	public static final Property logicalTable = ResourceFactory.createProperty(uri, "logicalTable");
	public static final Property tableName = ResourceFactory.createProperty(uri, "tableName");
}
