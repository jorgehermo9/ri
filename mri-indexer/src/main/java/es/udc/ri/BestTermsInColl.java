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
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class BestTermsInColl{

    public static void main(String[] args) {
	
		String usage = "java es.udc.ri.BestTermsInDoc"
		+ " [-index INDEX_PATH] [-docID ID] [-field FIELD] [-top N] \n"
		+ "[-order {tf|df|idf|tfxidf}]\n\n";
		
		String indexPath = null;
		String outputFile = null;
		Integer docId =null;
		String field=null;
		//Default top 10
		Integer top=10;
		//Default order TF
		Order order=Order.TF;

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
					if (order_arg.equals("tf") )
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

		try{

			//Preguntar si hace falta describir las exepciones que pueden pasar
			//o simplemente un catch general
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);
			PrintWriter out = null;
			
			
		}catch (Exception e) {
            System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }

	}

}