package es.udc.ri;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
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

		try {
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			DirectoryReader indexReader = DirectoryReader.open(dir);

			IndexSearcher searcher = new IndexSearcher(indexReader);
			
			HashSet<String> fields = new HashSet<>();

			if ( field == null ) {
				// for(LeafReaderContext leaf : indexReader.leaves()){
				// 	LeafReader leafReader = leaf.reader();
				// 	FieldInfos fieldInfos = leafReader.getFieldInfos();
				// 	// System.out.println("Leaf "+leaf.ord);
				// 	// System.out.println("Numero de campos devuelto por leafReader.getFieldInfos() = " + fieldInfos.size());
				// 	// for(FieldInfo fieldInfo: fieldInfos){
				// 	// 	System.out.print(fieldInfo.name+",");
				// 	// }
				// 	for(FieldInfo fieldInfo: fieldInfos){
				// 		fields.add(fieldInfo.name);
				// 	}
				// }

				FieldInfos fieldInfos = FieldInfos.getMergedFieldInfos(indexReader);
				for ( FieldInfo fieldInfo : fieldInfos )
					fields.add(fieldInfo.name);
				
			} else {
				fields.add(field);
			}

			System.out.println("\nIndex Statistics");

			System.out.printf("%-80s\n", "-".repeat(80));

			System.out.printf("|%-23s | ", "field");
			System.out.printf("%-8s | ", "docCount");
			System.out.printf("%-8s | ", "maxDoc");
			System.out.printf("%-10s | ", "sumDocFreq");
			System.out.printf("%-17s|\n", "sumCollectionFreq");

			System.out.printf("%-80s\n", "-".repeat(80));
			for ( String f : fields ) {
				CollectionStatistics stats = searcher.collectionStatistics(f);
				// Si no hay estadísticas para ese campo, por ejemplo, los LongPoint no indexan términos
				if ( stats == null ) continue;
				System.out.printf("|%-23s | ", stats.field());
				System.out.printf("%-8d | ", stats.docCount());
				System.out.printf("%-8d | ", stats.maxDoc());
				System.out.printf("%-10d | ", stats.sumDocFreq());
				System.out.printf("%-17d|\n", stats.sumTotalTermFreq());
			}
			System.out.printf("%-80s\n", "-".repeat(80));

			dir.close();
			indexReader.close();
		} catch (IOException e) {
            System.err.println("Caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }

	}
}
