package org.intermine.r2rmlmapping.vocabulary;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class R2RML
{
	public static String 	uri = "http://www.w3.org/ns/r2rml#";
	public static Resource TriplesMap = ResourceFactory.createProperty(uri, "TriplesMap");
}
