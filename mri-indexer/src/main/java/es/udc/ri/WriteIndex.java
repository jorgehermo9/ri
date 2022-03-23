package es.udc.ri;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class WriteIndex {
	public static void main(String[] args) {
		String usage = "java es.udc.ri.WriteIndex"
            + " [-index INDEX_PATH] [-outputfile OUTPUT_FILE] \n\n";
		String indexPath = null;
		String outputFile = null;
		for ( int i = 0; i < args.length; i++ ) {
            switch ( args[i] ) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-outputfile":
                    outputFile = args[++i];
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
		if ( outputFile == null ) {
			System.err.println("Must specify output file. Usage: " + usage);
			System.exit(1);
        }

		try(FileWriter fw = new FileWriter(outputFile, false);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw)) {

			//Preguntar si hace falta describir las exepciones que pueden pasar
			//o simplemente un catch general
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);
			
			for ( int i = 0; i < indexReader.numDocs(); i++ ) {
				Document doc = indexReader.document(i);
				out.println("Documento " + i);
				List<IndexableField> fields = doc.getFields();
	
				for ( IndexableField field : fields ) {
					String fieldName = field.name();
					String content = doc.get(fieldName);
					//Aux para printear el contenido en otra línea si el contenido
					//tiene más de una línea
					String aux = "";
					if ( content.contains("\n") )
						aux = "\n";
					out.println(fieldName + ": "+aux+content);
				}
				out.println("-".repeat(30));
			}

			dir.close();
			indexReader.close();
		} catch (Exception e) {
            System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }
	}
}
