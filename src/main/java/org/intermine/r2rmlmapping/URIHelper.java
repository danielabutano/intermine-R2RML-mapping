package org.intermine.r2rmlmapping;

import org.intermine.web.uri.ClassNameURIIdentifierMapper;

public class URIHelper {
    public final static String interMineNS = "http://intermine.org/biotestmine/";
    public final static String interMineVocNS = "http://intermine.org/vocabulary/";
    public final static String uniProtNS = "http://purl.uniprot.org/core/";
    public final static String uniProtKbNS = "http://purl.uniprot.org/uniprot/";

    private static final String DEFAULT_IDENTIFIER = "primaryIdentifier";
    private ClassNameURIIdentifierMapper classNameIdentifierMapper = null;

    public URIHelper() {
        classNameIdentifierMapper = ClassNameURIIdentifierMapper.getMapper();
    }

    private String getIdentifier(String type) {
        String identifier = classNameIdentifierMapper.getIdentifier(type);
        return ( identifier != null) ? identifier : DEFAULT_IDENTIFIER;
    }

    public boolean isURIIdentifier(String type, String attribute) {
        if (getIdentifier(type).equalsIgnoreCase(attribute)) {
            return true;
        }
        return false;
    }

    public String createURI(String type) {
        String identifier = getIdentifier(type);
        if (("Protein").equalsIgnoreCase(type)) {
            return uniProtKbNS + "{" + identifier +"}";
        } else {
            return interMineNS + type + "/{" + identifier +"}";
        }
    }
    
     public String createURI(String type, String allias) {
        if (("Protein").equalsIgnoreCase(type)) {
            return uniProtKbNS + "{" + allias +"}";
        } else {
            return interMineNS + type + "/{" + allias +"}";
        }
    }
}
