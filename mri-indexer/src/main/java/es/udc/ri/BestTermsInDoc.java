package es.udc.ri;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

enum Order {
	TF {
		@Override 
		List<MyTerm> sortTerms(List<MyTerm> terms) {
			Collections.sort(terms, (a,b) -> b.getTf() - a.getTf());
			return terms;
		}
	},
	DF {
		@Override 
		List<MyTerm> sortTerms(List<MyTerm> terms) {
			Collections.sort(terms, (a,b) -> b.getDf() - a.getDf());
			return terms;
		}
	},
	IDF {
		//Preguntar si ordenar esto creciente o decreciente
		@Override 
		List<MyTerm> sortTerms(List<MyTerm> terms) {
			Collections.sort(terms, (a,b) -> Double.compare(b.getIdf(), a.getIdf()));
			return terms;
		}
	},
	TFXIDF {
		@Override 
		List<MyTerm> sortTerms(List<MyTerm> terms) {
			Collections.sort(terms, (a,b) -> Double.compare(b.getTfxidf(), a.getTfxidf()));
			return terms;
		}
	};

	String orderMark(String actual){
		if ( actual.toLowerCase().equals(this.toString().toLowerCase()) )
			return actual+"*";
		else
			return actual;
	}

	abstract List<MyTerm> sortTerms(List<MyTerm> terms);
}
class MyTerm {
	private String termName;
	private int tf;
	private int df;
	private double idf;

	MyTerm(String termName, int tf, int df, double idf) {
		this.termName = termName;
		this.tf = tf;
		this.df = df;
		this.idf = idf;
	}
	String getTermName() {
		return this.termName;
	}
	int getTf() {
		return this.tf;
	}
	int getDf() {
		return this.df;
	}
	double getIdf()  {
		return this.idf;
	}
	double getTfxidf() {
		return this.tf * this.idf;
	}
}
public class BestTermsInDoc{

    public static void main(String[] args) {
	
		String usage = "java es.udc.ri.BestTermsInDoc"
		+ " [-index INDEX_PATH] [-docID ID] [-field FIELD] [-top N] \n"
		+ "[-order {tf|df|idf|tfxidf}]\n\n";
		
		String indexPath = null;
		String outputFile = null;
		Integer docId = null;
		String field = null;
		//Default top 10
		Integer top = 10;
		//Default order TF
		Order order = Order.TF;

		for ( int i = 0; i < args.length; i++ ) {
			switch ( args[i] ) {
				case "-index":
					indexPath = args[++i];
					break;
				case "-field":
					field = args[++i];
					break;
				case "-outputfile":
					outputFile = args[++i];
					break;
				case "-order":
					String order_arg = args[++i];
					if ( order_arg.equals("tf") )
						order = Order.TF;
					else if ( order_arg.equals("df") )
						order = Order.DF;
					else if ( order_arg.equals("idf") )
						order = Order.IDF;
					else if ( order_arg.equals("tfxidf") )
						order = Order.TFXIDF;
					else
						throw new IllegalArgumentException("Order not supported: " + order_arg);
					break;
				case "-docID":
					docId = Integer.parseInt(args[++i]);
					if ( docId < 0 )
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
		if ( docId == null ) {
			System.err.println("Must specify docId. Usage: " + usage);
			System.exit(1);
		}

		try {
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);
			PrintWriter out = null;
			
			if ( outputFile == null ) {
				out = new PrintWriter(System.out);
			} else {
				FileWriter fw = new FileWriter(outputFile, false);
				BufferedWriter bw = new BufferedWriter(fw);
				out = new PrintWriter(bw);
			}
			Terms termVector = indexReader.getTermVector(docId, field);

			if ( termVector == null ) {
				System.out.println("There is no term vector in document "+docId+" for field "+field);
				System.exit(0);
			}

			TermsEnum termsEnum = termVector.iterator();

			ArrayList<MyTerm> terms = new ArrayList<>();
			int numDocs = indexReader.numDocs();
			BytesRef text = null;
			while ( (text = termsEnum.next()) != null ) {
				String term = text.utf8ToString();
				int tf = (int)termsEnum.totalTermFreq();
				int df = indexReader.docFreq(new Term(field, term));
				double idf = Math.log10((double)numDocs/(double)df);
				terms.add(new MyTerm(term, tf, df, idf));
			}

			List<MyTerm> sortedTerms = order.sortTerms(terms);
			List<MyTerm> topTerms = sortedTerms.subList(0, Math.min(top, sortedTerms.size()));

			out.println("\nBestTermsInDoc - " + field + " (top " + Math.min(top, sortedTerms.size()) + ")");

			out.printf("%-77s\n", "-".repeat(77));

			out.printf("|%-23s | ", "Term");
			out.printf("%-10s | ", order.orderMark("tf"));
			out.printf("%-10s | ", order.orderMark("df"));
			out.printf("%-10s | ", order.orderMark("idf"));
			out.printf("%-10s|\n", order.orderMark("tfxidf"));

			out.printf("%-77s\n", "-".repeat(77));
			for ( MyTerm term : topTerms ) {
				// Si no hay estadísticas para ese campo, por ejemplo, los LongPoint no indexan términos
				out.printf("|%-23s | ", term.getTermName());
				out.printf("%-10d | ", term.getTf());
				out.printf("%-10d | ", term.getDf());
				out.printf("%-10f | ", term.getIdf());
				out.printf("%-10f|\n", term.getTfxidf());
			}
			out.printf("%-77s\n", "-".repeat(77));

			dir.close();
			indexReader.close();
			out.close();
		} catch (Exception e) {
            System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }
	}
}
