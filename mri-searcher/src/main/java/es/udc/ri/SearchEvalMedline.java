package es.udc.ri;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;

public class SearchEvalMedline{
	public static void main(String[] args) {
		String usage = "java es.udc.ri.SearchEvalMedline"
			+ " [-indexin INDEX_PATH] [-docs DOCS_PATH]\n"
			+" [-search {jm lambda | tfidf}]\n"
			+ "[-cut n] [-top m] [-search {all | int1 | int1-int2}]";
		String indexPath = null;
		String docsPath = null;

		String queries = null;
		Integer lowerBound = null;
		Integer upperBound = null;

		Similarity similarity = null;
		Integer cut = null;
		Integer top = null;

		for ( int i = 0; i < args.length; i++ ) {
			switch ( args[i] ) {
				case "-indexin":
					indexPath = args[++i];
					break;
				case "-docs":
					docsPath = args[++i];
					break;
				case "-search":
					String indexingmodel = args[++i];
					if (indexingmodel.contains("jm")){
						Float lambda;
						try{
							lambda = Float.parseFloat(args[++i]);
						}catch (Exception e){
							throw new IllegalArgumentException("Error while parsing lambda: "+e.getMessage());
						}
						similarity = new LMJelinekMercerSimilarity(lambda);
					} else if (indexingmodel.equals("tfidf")){
						similarity = new ClassicSimilarity();
					} else {
						throw new IllegalArgumentException("search model not supported: " + indexingmodel);
					}
					break;
				case "-cut":
					cut = Integer.parseInt(args[++i]);
					if ( cut <= 0 )
						throw new IllegalArgumentException("Cut must be greater than 0");
					break;
				case "-top":
					top = Integer.parseInt(args[++i]);
					if ( top <= 0 )
						throw new IllegalArgumentException("Top must be greater than 0");
					break;
				case "-queries":
					queries = args[++i];
					if (!queries.equals("all")){
						//Si es igual a all, está bien.
						String[] splitted = queries.split("-");
						if (splitted.length == 1){
							try{
								lowerBound = Integer.parseInt(splitted[0]);
								upperBound = lowerBound;
							}catch(NumberFormatException e){
								throw new IllegalArgumentException("Can't parse queries: "+e.getMessage());
							}
						}else if(splitted.length ==2){
							//Si es un rango de queries
							try{
								lowerBound = Integer.parseInt(splitted[0]);
								upperBound = Integer.parseInt(splitted[1]);
								if (lowerBound > upperBound){
									throw new IllegalArgumentException("Queries upper bound must be greater than lower bound");
								}
							}catch(NumberFormatException e){
								throw new IllegalArgumentException("Can't parse queries: "+e.getMessage());
							}
							
						}else{
							throw new IllegalArgumentException("Invalid query format");
						}
					}
					break;
				

				default:
					System.err.println("Usage: " + usage);
					throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}
			
		if (indexPath == null || top ==null || similarity == null || cut ==null || docsPath == null || queries == null){
			System.err.println("Usage: " +usage);
			System.exit(1);
		}

		FileReader queryReader = null;
		FileReader relevanceReader = null;

		try{
			queryReader = new FileReader(docsPath+"/MED.QRY");
		}catch(FileNotFoundException e){
			System.err.println("Medline queries file not found: " + docsPath);
			System.exit(1);
		}

		try{
			relevanceReader = new FileReader(docsPath+"/MED.REL");
		}catch(FileNotFoundException e){
			System.err.println("Medline queries relevance file not found: " + docsPath);
			System.exit(1);
		}
		
		List<MedlineInfo> queriesInfo = MedlineInfo.parseMedline(queryReader);
		HashMap<Long,List<Long>> relevances = MedlineInfo.getRelevance(relevanceReader);

		if( lowerBound !=null && upperBound !=null){
			//No hacer un sublist, porque no se asegura que la lista tenga las queries ordenadas
			//por ID, se podría ordenar antes y hacer un sublist, pero no es tan eficiente.
			List<MedlineInfo> auxList = new ArrayList<MedlineInfo>();
			for (MedlineInfo query : queriesInfo){
				if (query.getId() >= lowerBound && query.getId() <= upperBound){
					auxList.add(query);
				}
			}
			queriesInfo = auxList;
		}

		System.out.println(queriesInfo);
		System.out.println(relevances.get((long) 1));

	}
}