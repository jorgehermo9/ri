package es.udc.ri;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class StatsField{
	public static void main(String[] args) {
		String usage = "java es.udc.ri.StatsField"
            + " [-index INDEX_PATH] [-field FIELD] \n\n";
		String indexPath = null;
		String field = null;
		for ( int i = 0; i < args.length; i++ ) {
            switch ( args[i] ) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
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

		try{
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);

			IndexSearcher searcher = new IndexSearcher(indexReader);
			
			CollectionStatistics stats = searcher.collectionStatistics(field);

			System.out.println("Index Statistics\n");
			System.out.println("-----------------------");
			System.out.println("field = "+stats.field()+", docCount = "+stats.docCount()
				+ ", maxDoc = "+stats.maxDoc()+ ", sumDocFreq = "+stats.sumDocFreq()
				+", sumTotalFreq = "+stats.sumTotalTermFreq()
			);


		}catch (IOException e) {
            System.err.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }

	}
}