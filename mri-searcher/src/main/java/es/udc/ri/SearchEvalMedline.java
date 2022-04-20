package es.udc.ri;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;


class QueryMetrics{
	MedlineInfo query;
	double precision;
	double recall;
	double ap;

	QueryMetrics(MedlineInfo query, double precision, double recall, double ap){
		this.query = query;
		this.precision = precision;
		this.recall = recall;
		this.ap = ap;
	}
	public MedlineInfo getQuery() { return this.query; }
	public double getPrecision() { return this.precision; }
	public double getRecall() { return this.recall; }
	public double getAp() { return this.ap; }
}
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
			//Hay que declararlas finale sino da error con el removeIf
			final int lower = lowerBound;
			final int upper = upperBound;
			queriesInfo.removeIf(query -> query.getId() < lower || query.getId() > upper);
		}

		//Empezamos a leer el índice

		IndexReader reader = null;
		IndexSearcher searcher = null;
		QueryParser parser;
		
		try {
			reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));

		} catch (Exception e){
			System.err.println("Could not read index in " + indexPath);
			System.exit(1);
		}
		
		searcher = new IndexSearcher(reader);
		searcher.setSimilarity(similarity);
		parser = new QueryParser("Contents", new StandardAnalyzer());

		List<QueryMetrics> metrics = new ArrayList<>();

		for(MedlineInfo query : queriesInfo){
			List<Long> relevantDocs = relevances.get(query.getId());
			QueryMetrics queryMetrics = processQuery(reader,searcher,parser,query,relevantDocs,top,cut);
			//Si se ha podido computar los query metrics
			if (queryMetrics != null){
				metrics.add(queryMetrics);
			}
		}


		double meanPrecision = metrics.stream().mapToDouble(metric -> metric.getPrecision()).summaryStatistics().getAverage();
		double meanRecall = metrics.stream().mapToDouble(metric -> metric.getRecall()).summaryStatistics().getAverage();
		double meanAp = metrics.stream().mapToDouble(metric -> metric.getAp()).summaryStatistics().getAverage();
		System.out.println("----------------------------------------\n");
		String message;
		if (queries.equals("all")){
			message = "all queries";
		}else if(queries.contains("-")){
			message = "queries " + queries;
		}else{
			message = "query " + queries;
		}
		System.out.println("Summary for "+message+":");
		System.out.println("Mean P@"+cut+": "+meanPrecision);
		System.out.println("Mean Recall@"+cut+": "+meanRecall);
		System.out.println("MAP@"+cut+": "+meanAp);


	}

	public static QueryMetrics processQuery(IndexReader reader, IndexSearcher searcher, QueryParser parser, MedlineInfo query,List<Long> relevantDocs,int top,int cut){

		//Pasar a minúsculas y eliminar paréntesis
		String queryContents = query.getContents().toLowerCase().replace("(", "").replace(")","");
		int numDocs = Math.max(top,cut);
		Query parsedQuery = null;
		try{
			//Preguntar si quitar paréntesis. Da problemas con paréntesis no cerrados
			parsedQuery = parser.parse(queryContents);
		}catch (Exception e){
			System.err.println("Could not parse query with id " + query.getId()+": "+e.getMessage());
			return null;
		}
		TopDocs topdocs = null;
		try{
			topdocs = searcher.search(parsedQuery,numDocs);
		}catch (Exception e){
			System.err.println("Could not get top docs from query with id "+ query.getId()+": "+e.getMessage());
			return null;
		}

		long totalHits = topdocs.totalHits.value;
		ScoreDoc[] scoreDocs= topdocs.scoreDocs;
		int VP = 0;
		double apAux=0;

		System.out.println("-------------------------");
		System.out.println("Ranking for query "+query.getId()+": "+queryContents);

		System.out.printf("%-37s\n", "-".repeat(37));

		System.out.printf("|%-4s | ", "Rank");
		System.out.printf("%-9s | ", "MedlineID");
		System.out.printf("%-9s | ", "Score");
		System.out.printf("%-4s|\n", "Rel");
		System.out.printf("%-37s\n", "-".repeat(37));
		
		for (int i = 0; i < Math.min(totalHits,numDocs); i++) {
			int docId = scoreDocs[i].doc;
			try{
				long medlineId = Long.parseLong(reader.document(docId).get("DocIDMedline"));
				boolean relevant = relevantDocs.contains(medlineId);
				
				if (i<top){
					System.out.printf("|%-4d | ", i+1);
					System.out.printf("%-9d | ", medlineId);
					System.out.printf("%-9.6f | ", scoreDocs[i].score);
					System.out.printf("%-4s|\n", relevant?" R":"");
				}

				
				if (relevant && i<cut){
					VP++;
					apAux += (double) VP/ (double) (i+1);
				}
			}catch (Exception e){
				System.err.println("Error while reading index for document "+ docId+" in query with id "+query.getId()+": "+e.getMessage());
				continue;
			}
		}
		System.out.printf("%-37s\n", "-".repeat(37));

		for(int i =0;i<Math.min(top,totalHits);i++){
			ScoreDoc doc = scoreDocs[i];
			int docId = doc.doc;
			try{
				long medlineId = Long.parseLong(reader.document(docId).get("DocIDMedline"));
				String medlineContents = reader.document(docId).get("Contents");
				System.out.println("Contents for document "+ medlineId+":");
				System.out.println("\t"+medlineContents.replace("\n","\n\t"));

			}catch(Exception e){
				System.err.println("Error while reading index for document "+ docId+" in query with id "+query.getId()+": "+e.getMessage());
				continue;
			}
		}


		System.out.println("-------------------------");
		System.out.println("Metrics for query "+query.getId()+"\n");

		//Si la query no tiene relevantes, no evaluar métricas
		if (relevantDocs.size() == 0){
			System.err.println("Query " + query.getId()+" does not have any relevant document. Can't compute metrics.");
			return null;
		}
		double precision = (double) VP / (double) Math.min(cut,totalHits);
		double recall = (double) VP / (double) relevantDocs.size();
		// Esta métrica así sería parecida al precision, pero penalizando más a los documentos
		// que tienen otros no relevantes encima. No sé si estaría bien usarla tambien
		// double ap = apAux/Math.min(cut,totalHits);
		double ap = apAux/ (double)relevantDocs.size();

		System.out.println("P@"+cut+": "+precision);
		System.out.println("Recall@"+cut+": "+recall);
		System.out.println("AP@"+cut+": "+ap);
		System.out.println("----------------------------------------\n");

		return new QueryMetrics(query,precision, recall, ap);
	}
}