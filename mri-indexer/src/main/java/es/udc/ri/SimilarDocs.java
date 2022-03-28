package es.udc.ri;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

enum Representation {
	tf {
		@Override 
		RealVector getVector(HashMap<String,MyTermInfo> termInfos,Set<String> allTerms) {
			RealVector vector = new ArrayRealVector(allTerms.size());
			int i = 0;
			for (String term : allTerms) {
				int value = termInfos.containsKey(term) ? termInfos.get(term).getTf() : 0;
				vector.setEntry(i++, value);
			}
			return vector;
		}
	},
	bin {
		@Override 
		RealVector getVector(HashMap<String,MyTermInfo> termInfos,Set<String> allTerms) {
			RealVector vector = new ArrayRealVector(allTerms.size());
			int i = 0;
			for (String term : allTerms) {
				int value = termInfos.containsKey(term) ? 1 : 0;
				vector.setEntry(i++, value);
			}
			return vector;
		}
	},
	tfxidf {
		@Override 
		RealVector getVector(HashMap<String,MyTermInfo> termInfos,Set<String> allTerms) {
			RealVector vector = new ArrayRealVector(allTerms.size());
			int i = 0;
			for (String term : allTerms) {
				double value = termInfos.containsKey(term) ? termInfos.get(term).getTfxidf()  : 0;
				vector.setEntry(i++, value);
			}
			return vector;
		}
	};

	abstract RealVector getVector(HashMap<String,MyTermInfo> termInfos,Set<String> allTerms);
}
class MyTermInfo {
	private String termName;
	private int tf;
	private double idf;

	MyTermInfo(String termName, int tf, double idf) {
		this.termName = termName;
		this.tf = tf;
		this.idf = idf;
	}
	String getTermName() {
		return this.termName;
	}
	int getTf() {
		return this.tf;
	}
	double getTfxidf() {
		return this.tf * this.idf;
	}

}

class DocSimilarity{
	private Integer docId;
	private double similarity;
	
	DocSimilarity(Integer docId, double similarity){
		this.docId = docId;
		this.similarity = similarity;
	}
	Integer getDocId() {
		return this.docId;
	}
	double getSimilarity(){
		return this.similarity;
	}
	@Override
	public String toString(){
		return "("+docId+","+similarity+")";
	}
}

public class SimilarDocs{

    public static void main(String[] args) {
	
		String usage = "java es.udc.ri.SimilarDocs"
		+ " [-index INDEX_PATH] [-docID ID] [-field FIELD] [-top N] \n"
		+ "[-rep {bin|tf|tfxidf}]\n\n";
		
		String indexPath = null;
		Integer targetDocId = null;
		String field = null;
		//Default top 10
		Integer top = 10;
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

		// No hay garantía de que el HashSet mantenga el orden en consecutivas
		//llamadas del iterator (ver https://docs.oracle.com/javase/7/docs/api/java/util/HashSet.html)

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

			// Document doc = reader.document(i);

			for ( int docId = 0; docId < indexReader.numDocs(); docId++ ) {
				Terms termVector = indexReader.getTermVector(docId, field);
				if ( termVector == null ) {
					if(docId == targetDocId){
						System.err.println("There is no term vector in target Doc. Exiting...");
						System.exit(1);
					}else{
						System.err.println("There is no term vector in document "+docId+" for field "+field);
						continue;
					}
				}
				TermsEnum termsEnum = termVector.iterator();

				HashMap<String,MyTermInfo> termInfos = new HashMap<>();

				int numDocs = indexReader.numDocs();
				BytesRef text = null;
				while ( (text = termsEnum.next()) != null ) {
					String term = text.utf8ToString();
					int tf = (int)termsEnum.totalTermFreq();
					int df = indexReader.docFreq(new Term(field, term));
					double idf = Math.log10((double)numDocs/(double)df);
					termInfos.put(term,new MyTermInfo(term, tf, idf));
					allTerms.add(term);
				}
				allDocsTerms.put(docId,termInfos);

			}

			ArrayList<DocSimilarity> allDocsSimilarity = new ArrayList<>();
			RealVector targetVector = rep.getVector(allDocsTerms.get(targetDocId), allTerms);
			for ( int docId = 0; docId < indexReader.numDocs(); docId++ ) {
				if (!allDocsTerms.containsKey(docId) || docId ==targetDocId){
					//Si el campo en el docId no tenía term vector o si es el targetDocId
					continue;
				}
				RealVector docVector = rep.getVector(allDocsTerms.get(docId), allTerms);
				allDocsSimilarity.add(new DocSimilarity(docId,getCosineSimilarity(targetVector,docVector)));
			}


			
			allDocsSimilarity.sort((doc1,doc2) -> Double.compare(doc2.getSimilarity(),doc1.getSimilarity()));
			

			System.out.println();
			System.out.println("Representation: "+rep+"\n");
			int toShow = Math.min(top,allDocsSimilarity.size());
			for( int i = 0; i < toShow; i++){
				DocSimilarity doc = allDocsSimilarity.get(i);
				System.out.println((i+1)+". "+"docId "+doc.getDocId()+": "+doc.getSimilarity());
			}


			dir.close();
			indexReader.close();
		} catch (Exception e) {
            System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }
	}

	static double getCosineSimilarity(RealVector v1,RealVector v2) {
		return (v1.dotProduct(v2)) / (v1.getNorm() * v2.getNorm());
	}
}
