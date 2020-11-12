package org.intermine.r2rmlmapping;

import java.util.Arrays;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.federated.FedXFactory;

public class FederatedQuery {

  public static void main (String args[]) throws Exception {
    Repository repo = FedXFactory.createSparqlFederation(Arrays.asList(
        "https://sparql.uniprot.org/sparql",
        "http://localhost:8081/sparql"
        ));

    String q = "PREFIX up: <http://purl.uniprot.org/core/> \n"
        +"PREFIX voc: <http://intermine.org/vocabulary/> \n"
        + "SELECT ?protein ?citation ?sequence  WHERE {\n"
        + "?protein a up:Protein .\n"
        + "?protein up:citation ?citation .\n"
        + "?protein voc:hasSequence ?sequence . "
        + "FILTER(?protein = <http://purl.uniprot.org/uniprot/Q8ILG7>)}";


    try (RepositoryConnection conn = repo.getConnection()) {
      TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, q);
      try (TupleQueryResult res = query.evaluate()) {

        while (res.hasNext()) {
          System.out.println(res.next());
        }
      }
    }

    repo.shutDown();
    System.out.println("Done.");
    System.exit(0);


  }

}
