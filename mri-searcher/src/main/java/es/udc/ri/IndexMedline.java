package es.udc.ri;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;


class MedlineDoc{
	private long docIdMedline;
	private String contents;
	MedlineDoc(long docIdMedline, String contents){
		this.docIdMedline = docIdMedline;
		this.contents = contents;
	}
	
	long getDocIdMedline(){return docIdMedline;}
	String getContents(){return contents;}

	public String toString(){
		return "Doc Id: " + docIdMedline + "\n Contents:\n"+contents;
	}
}

public class IndexMedline{

	public static List<MedlineDoc> parseMedline(FileReader collectionReader){
		BufferedReader bufferedReader = new BufferedReader(collectionReader);
		String line;
		Long medLineId=null;
		String medLineContent="";
		List<MedlineDoc> docs = new ArrayList<>();
		try{
			while ((line = bufferedReader.readLine()) != null){
				if(line.startsWith(".I")){
					if(medLineId != null){
						// Hacemos trim para eliminar el último salto de línea
						docs.add(new MedlineDoc(medLineId, medLineContent.trim()));
					}
					medLineId = Long.parseLong(line.split(" ")[1]);
				}else if(line.startsWith(".W")){
					medLineContent="";
				}else{
					medLineContent+=line+"\n";
				}
			}

			//Al acabar, guardar el último documento
			if (medLineId !=null){
				docs.add(new MedlineDoc(medLineId, medLineContent.trim()));
			}

		}catch (Exception e){
			System.err.println("Exception while parsing medline: " + e.getMessage());
			e.printStackTrace();
		}

		return docs;
	}
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
							throw new IllegalArgumentException("Error while parsing lambda. "+e.getMessage());
						}
						similarity = new LMJelinekMercerSimilarity(lambda);
					} else if (indexingmodel.equals("tfidf")){
						similarity = new ClassicSimilarity();
					} else {
						throw new IllegalArgumentException("Open mode not supported: " + indexingmodel);
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
		try{
			FileReader reader = new FileReader(docsPath+"/MED.ALL");
			System.out.println(parseMedline(reader));

		}catch(FileNotFoundException e){
			System.err.println("File not found: " + docsPath);
		}

				
	}

}