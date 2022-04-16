package es.udc.ri;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.similarities.ClassicSimilarity;


class MedlineInfo{
	private long id;
	private String contents;
	MedlineInfo(long id, String contents){
		this.id = id;
		this.contents = contents;
	}
	
	long getId(){return id;}
	String getContents(){return contents;}

	public static List<MedlineInfo> parseMedline(FileReader collectionReader){
		BufferedReader bufferedReader = new BufferedReader(collectionReader);
		String line;
		Long medlineId=null;
		String medlineContent="";
		List<MedlineInfo> docs = new ArrayList<>();
		try{
			while ((line = bufferedReader.readLine()) != null){
				if(line.startsWith(".I")){
					if(medlineId != null){
						// Hacemos trim para eliminar el último salto de línea
						docs.add(new MedlineInfo(medlineId, medlineContent.trim()));
					}
					medlineId = Long.parseLong(line.split(" ")[1]);
				}else if(line.startsWith(".W")){
					medlineContent="";
				}else{
					medlineContent+=line+"\n";
				}
			}

			//Al acabar, guardar el último documento
			if (medlineId !=null){
				docs.add(new MedlineInfo(medlineId, medlineContent.trim()));
			}

		}catch (Exception e){
			System.err.println("Exception while parsing medline: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		return docs;
	}

	public static HashMap<Long,List<Long>> getRelevance(FileReader relevanceReader){
		BufferedReader bufferedReader = new BufferedReader(relevanceReader);
		HashMap<Long,List<Long>> relevances = new HashMap<>();
		bufferedReader.lines().forEach((line) -> {
			String[] splitted = line.split(" ");
			if (splitted.length != 4){
				System.err.println("Error while parsing line in query relevances: invalid line format");
				return;
			}
			long query;
			long doc;
			try{
				query = Long.parseLong(splitted[0]);
				doc = Long.parseLong(splitted[2]);
			}catch (NumberFormatException e){
				System.err.println("Error parsing line in query relevances: "+e.getMessage());
				return;
			}
			if (!relevances.containsKey(query)){
				relevances.put(query,new ArrayList<Long>());
			}
			relevances.get(query).add(doc);
		});
		return relevances;
	}


	public String toString(){
		return "Id: " + id + "\nContents:\n"+contents;
	}
}

public class IndexMedline{

	
	public static void main(String[] args) {

		String usage = "java es.udc.ri.IndexMedline"
			+ " [-index INDEX_PATH] [-docs DOCS_PATH \n"
			+ "[-openmode {create|append|create_or_append}]\n"
			+" [-indexingmodel {jm lambda | tfidf}]\n\n"
			+ "This indexes the documents in DOCS_PATH, creating a Lucene index"
			+ "in INDEX_PATH\n";
		String indexPath = null;
		String docsPath = null;
		OpenMode openmode = OpenMode.CREATE;
		Similarity similarity=null;

		for ( int i = 0; i < args.length; i++ ) {
			switch ( args[i] ) {
				case "-index":
					indexPath = args[++i];
					break;
				case "-docs":
					docsPath = args[++i];
					break;
				case "-openmode":
					String openmode_arg = args[++i];
					if ( openmode_arg.equals("create") )
						openmode = OpenMode.CREATE;
					else if ( openmode_arg.equals("append") )
						openmode = OpenMode.APPEND;
					else if ( openmode_arg.equals("create_or_append") )
						openmode = OpenMode.CREATE_OR_APPEND;
					else
						throw new IllegalArgumentException("Open mode not supported: " + openmode_arg);
					break;
				case "-indexingmodel":
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
						throw new IllegalArgumentException("indexing model not supported: " + indexingmodel);
					}
					break;

				default:
					System.err.println("Usage: " + usage);
					throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}
			
		if (indexPath == null || docsPath ==null || similarity == null){
			System.err.println("Usage: " +usage);
			System.exit(1);
		}

		FileReader reader = null;
		try{
			reader = new FileReader(docsPath+"/MED.ALL");
		}catch(FileNotFoundException e){
			System.err.println("Medline file not found: " + docsPath);
			System.exit(1);
		}
		List<MedlineInfo> medlineDocs = MedlineInfo.parseMedline(reader);

		IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
		iwc.setSimilarity(similarity);
		iwc.setOpenMode(openmode);

		IndexWriter writer = null;

		try{
			writer = new IndexWriter(FSDirectory.open(Paths.get(indexPath)),iwc);
		}catch(Exception e){
			System.err.println("Could not create index writer: " + e.getMessage());
			System.exit(1);
		}

		

		for (MedlineInfo medlineDoc : medlineDocs){
			Document doc = new Document();
			
			doc.add(new StringField("DocIDMedline", Long.toString(medlineDoc.getId()), Field.Store.YES));
			doc.add(new TextField("Contents", medlineDoc.getContents(), Field.Store.YES));

			try {
				writer.addDocument(doc);
				System.out.println("Added document with DocIDMedline "+medlineDoc.getId());
			} catch (Exception e){
				System.err.println("Error while adding to index document with DocIDMedline "+medlineDoc.getId()
				+": "+e.getMessage());
			}
		}

		try{
			writer.commit();
			writer.close();
		}catch (Exception e){
			System.err.println("Error while closing index: "+e.getMessage());
			System.exit(1);
		}

		System.out.println("Succesfully added all documents to index in "+indexPath);

	}

}