package es.udc.ri;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

class MyCollTerm{
	private String termName;
	private int df;
	private double idf;

	MyCollTerm(String termName, int df, double idf) {
		this.termName = termName;
		this.df = df;
		this.idf = idf;
	}
	String getTermName() {
		return this.termName;
	}
	int getDf() {
		return this.df;
	}
	double getIdf()  {
		return this.idf;
	}
}

public class BestTermsInColl{

    public static void main(String[] args) {
	
		String usage = "java es.udc.ri.BestTermsInDoc"
		+ " [-index INDEX_PATH] [-docID ID] [-field FIELD] [-top N] \n"
		+ "[-order {tf|df|idf|tfxidf}]\n\n";
		
		String indexPath = null;
		String field=null;
		//Default top 10
		Integer top=10;
		boolean dfOrder = true;

		for ( int i = 0; i < args.length; i++ ) {
			switch ( args[i] ) {
				case "-index":
					indexPath = args[++i];
					break;
				case "-field":
					field = args[++i];
					break;
				case "-top":
					top = Integer.parseInt(args[++i]);
					if ( top < 0 )
						throw new IllegalArgumentException("top cannot be negative");
					break;
				case "-rev":
					dfOrder = false;
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

		try{

			//Preguntar si hace falta describir las exepciones que pueden pasar
			//o simplemente un catch general
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);
			
			Terms indexTerms  = MultiTerms.getTerms(indexReader, field);

			if ( indexTerms == null){
				System.err.println("Error, there is no field with name: "+field);
				System.exit(1);
			}
			TermsEnum termsEnum = indexTerms.iterator();

			ArrayList<MyCollTerm> terms = new ArrayList<>();

			int numDocs = indexReader.numDocs();
			BytesRef text = null;
			while ((text = termsEnum.next()) != null) {
				String term = text.utf8ToString();
				int df = indexReader.docFreq(new Term(field,term));
				double idf = Math.log10((double)numDocs/(double)df);
				terms.add(new MyCollTerm(term,df,idf));
			}

			List<MyCollTerm> sortedTerms = terms;
			if(dfOrder){
				Collections.sort(sortedTerms, (a,b) -> b.getDf() - a.getDf());
			}else{
				Collections.sort(sortedTerms, (a,b) -> Double.compare(b.getIdf(),a.getIdf()));
			}

			List<MyCollTerm> topTerms = sortedTerms.subList(0,Math.min(top, sortedTerms.size()));
	
			System.out.println("\nBestTermsInDoc - " + field + " (top " + Math.min(top, sortedTerms.size()) + ")");

			System.out.printf("%-79s\n", "-".repeat(79));

			System.out.printf("|%-50s | ", "Term");
			System.out.printf("%-10s | ", dfOrder?"df*":"df");
			System.out.printf("%-10s |\n", dfOrder?"idf":"idf*");

			System.out.printf("%-79s\n", "-".repeat(79));
			for ( MyCollTerm term : topTerms ) {
				// Si no hay estadísticas para ese campo, por ejemplo, los LongPoint no indexan términos
				System.out.printf("|%-50s | ", term.getTermName());
				System.out.printf("%-10d | ", term.getDf());
				System.out.printf("%-10f |\n", term.getIdf());
			}
			System.out.printf("%-79s\n", "-".repeat(79));

		}catch (Exception e) {
            System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }

	}

}