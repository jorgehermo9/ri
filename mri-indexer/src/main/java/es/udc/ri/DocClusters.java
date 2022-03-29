package es.udc.ri;
import es.udc.ri.kmeans.*;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class DocClusters{
	//Créditos a la implementación de k-means: https://github.com/xetorthio/kmeans
	//Esta clase utiliza enums y clases definidas en SimilarDocs, para no volver a definir todo.
	public static void main(String[] args) {
	
		String usage = "java es.udc.ri.DocsCluster"
		+ " [-index INDEX_PATH] [-docID ID] [-field FIELD] [-top N] \n"
		+ "[-rep {bin|tf|tfxidf}] [-k K]\n\n";
		
		String indexPath = null;
		Integer targetDocId = null;
		String field = null;
		//Default top 10
		Integer top = 10;
		Integer k=null;
		//Default order TF
		Representation rep = Representation.tf;

		for ( int i = 0; i < args.length; i++ ) {
			switch ( args[i] ) {
				case "-index":
					indexPath = args[++i];
					break;
				case "-field":
					field = args[++i];
					break;
				case "-rep":
					String order_arg = args[++i];
					if ( order_arg.equals("tf") )
						rep = Representation.tf;
					else if ( order_arg.equals("bin") )
						rep = Representation.bin;
					else if ( order_arg.equals("tfxidf") )
						rep = Representation.tfxidf;
					else
						throw new IllegalArgumentException("Representation not supported: " + order_arg);
					break;
				case "-docID":
					targetDocId = Integer.parseInt(args[++i]);
					if ( targetDocId < 0 )
						throw new IllegalArgumentException("docID cannot be negative");
					break;
				case "-top":
					top = Integer.parseInt(args[++i]);
					if ( top < 0 )
						throw new IllegalArgumentException("top cannot be negative");
					break;
				case "-k":
					k = Integer.parseInt(args[++i]);
					if ( k < 0 )
						throw new IllegalArgumentException("K cannot be negative");
					break;
				default:
					System.err.println("Usage: " + usage);
					throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}
		if ( indexPath == null ) {
			System.err.println("Must specify index path. Usage: " + usage);
			System.exit(1);
		}
		if ( field == null ) {
			System.err.println("Must specify field. Usage: " + usage);
			System.exit(1);
		}
		if ( targetDocId == null ) {
			System.err.println("Must specify docId. Usage: " + usage);
			System.exit(1);
		}
		if ( k == null ) {
			System.err.println("Must specify K. Usage: " + usage);
			System.exit(1);
		}

		// No hay garantía de que el HashSet mantenga el orden en consecutivas
		// llamadas del iterator (ver https://docs.oracle.com/javase/7/docs/api/java/util/HashSet.html)
		LinkedHashSet<String> allTerms = new LinkedHashSet<String>();
		HashMap<Integer,HashMap<String,MyTermInfo>> allDocsTerms = new HashMap<>();
		try {
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);
			
			// Preguntar si evitar iterar sobre docs borrados
			// Bits liveDocs = MultiFields.getLiveDocs(reader);
			// for (int i=0; i<reader.maxDoc(); i++) {
			// 	if (liveDocs != null && !liveDocs.get(i))
			// 		continue;

			int numDocs = indexReader.numDocs();

			if(targetDocId > numDocs){
				System.err.println("Target doc id does not exist");
				System.exit(1);
			}
			for ( int docId = 0; docId < numDocs; docId++ ) {
				Terms termVector = indexReader.getTermVector(docId, field);
				if ( termVector == null ) {
					if ( docId == targetDocId ) {
						System.err.println("There is no term vector in target Doc. Exiting...");
						System.exit(1);
					} else {
						System.err.println("There is no term vector in document " + docId + " for field " + field);
						continue;
					}
				}
				HashMap<String, MyTermInfo> termInfos = new HashMap<>();
				BytesRef text = null;
				TermsEnum termsEnum = termVector.iterator();
				while ( (text = termsEnum.next()) != null ) {
					String term = text.utf8ToString();
					int tf = (int)termsEnum.totalTermFreq();
					int df = indexReader.docFreq(new Term(field, term));
					double idf = Math.log10((double)numDocs/(double)df);

					termInfos.put(term, new MyTermInfo(term, tf, idf));
					allTerms.add(term);
				}
				allDocsTerms.put(docId, termInfos);
			}

			ArrayList<DocSimilarity> allDocsSimilarity = new ArrayList<>();
			HashMap<Integer,RealVector> allDocsVector = new HashMap<>();
			RealVector targetVector = rep.getVector(allDocsTerms.get(targetDocId), allTerms);
			allDocsVector.put(targetDocId, targetVector);
			for ( int docId = 0; docId < numDocs; docId++ ) {
				if ( !allDocsTerms.containsKey(docId) || docId == targetDocId ) {
					// Si el campo en el docId no tenía term vector o si es el targetDocId
					continue;
				}
				RealVector docVector = rep.getVector(allDocsTerms.get(docId), allTerms);
				allDocsVector.put(docId, docVector);
				allDocsSimilarity.add(new DocSimilarity(docId, getCosineSimilarity(targetVector, docVector)));
			}
			
			allDocsSimilarity.sort((doc1, doc2) -> Double.compare(doc2.getSimilarity(), doc1.getSimilarity()));
			List<DocSimilarity> topSimilarity = allDocsSimilarity.subList(0, Math.min(top, allDocsSimilarity.size()));

			System.out.println("\nTop " + Math.min(top, allDocsSimilarity.size()) + " SimilarDocs for docID "
				+targetDocId+" ("+indexReader.document(targetDocId).get("path")+")\n");
			System.out.printf("%-7s", "DocId");
			System.out.printf("%-18s", "Similarity (" + rep + ")");
			System.out.printf("%-90s\n", "Path");

			for ( DocSimilarity doc : topSimilarity ) {
				Document d = indexReader.document(doc.getDocId());
				String path = d.get("path");

				System.out.printf("%-7d", doc.getDocId());
				System.out.printf("%-18f", doc.getSimilarity());
				System.out.printf("%-90s\n", path);
			}

			//Una vez printeados los top documents, crear los puntos para los cluster

			ArrayList<Punto> puntos = new ArrayList<Punto>();
			
			for ( int docId = 0; docId < numDocs; docId++ ) {
				if ( !allDocsTerms.containsKey(docId)){
					// Si el campo en el docId no tenía term vector
					continue;
				}
				Document d = indexReader.document(docId);
				String path = d.get("path");
				puntos.add(new Punto(allDocsVector.get(docId),docId,path));
			}

			KMeans kmeans = new KMeans();
			KMeansResultado result = kmeans.calcular(puntos,k);
			List<Cluster> clusters = result.getClusters();

			System.out.println("-------------------------------");
			System.out.println(clusters.size()+ " Clusters");
			System.out.println("docId => path");
			System.out.println("-------------------------------");

			int count = 0;
			for (Cluster cluster : clusters) {
				count++;
				System.out.println("Cluster " + count+"\n");
				for (Punto punto : cluster.getPuntos()) {
					System.out.println(punto.getDocId()+" => "+punto.getPath());
				}
				System.out.println("-------------------------------");
			}




			dir.close();
			indexReader.close();
		} catch (Exception e) {
			System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	static double getCosineSimilarity(RealVector v1,RealVector v2) {
		return (v1.dotProduct(v2)) / (v1.getNorm() * v2.getNorm());
	}
}
