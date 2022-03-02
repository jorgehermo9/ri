package es.udc.ri;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IndexFiles {


    private IndexFiles(){}

    public static void main(String[] args) throws Exception{
        String usage = "java org.apache.lucene.demo.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles\n";
        String indexPath ="/tmp/index";
        String docsPath = null;
        boolean create = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-docs":
                    docsPath = args[++i];
                    break;
                case "-update":
                    create = false;
                    break;
                case "-create":
                    create = true;
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath()
                    + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }


            try (IndexWriter writer = new IndexWriter(dir, iwc)) {
				final int numCores = Runtime.getRuntime().availableProcessors();
				final ExecutorService executor = Executors.newFixedThreadPool(numCores);
				try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(docDir)) {

					/* We process each subfolder in a new thread. */
					for (final Path path : directoryStream) {
						if (Files.isDirectory(path)) {
							final Runnable worker = new WorkerThread(path,writer);
							/*
							 * Send the thread to the ThreadPool. It will be processed eventually.
							 */
							executor.execute(worker);
						}//Pensar manera de guardar los paths que no recorro y llamar a un thread que los recorra al final
						//Por ejemplo, crear un nuevo worker sobre el docDir, con profundidad 0(o 1, la que funcione)
					}
		
				} catch (final IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
		
				/*
				 * Close the ThreadPool; no more jobs will be accepted, but all the previously
				 * submitted jobs will be processed.
				 */
				executor.shutdown();
		
				/* Wait up to 1 hour to finish all the previously submitted jobs */
				try {
					executor.awaitTermination(1, TimeUnit.HOURS);
				} catch (final InterruptedException e) {
					e.printStackTrace();
					System.exit(-2);
				}
		
				System.out.println("Finished all threads");

            }

            Date end = new Date();
			
            try (IndexReader reader = DirectoryReader.open(dir)) {
                System.out.println("Indexed " + reader.numDocs() + " documents in " + (end.getTime() - start.getTime())
                        + " milliseconds");
            }
        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }

    }


}

class WorkerThread implements Runnable{
	private Path docsFolder;
	private IndexWriter writer;

	public WorkerThread(Path docsFolder,MMapDirectory indexFolder, IndexWriterConfig iwc) {
		// El indexWriterConfig se debería poder compartir entre distintos writers
		this.docsFolder = docsFolder;
		try{
			this.writer = new IndexWriter(indexFolder, iwc);
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
	public WorkerThread(Path docsFolder,IndexWriter writer){
		this.docsFolder = docsFolder;
		this.writer = writer;
	}
	@Override
	public void run(){
		try{
			this.indexDocs();
		}catch (IOException e){
			e.printStackTrace();
		}
	}

	private void indexDocs() throws IOException {
		Path path = this.docsFolder;
		if (Files.isDirectory(path)) {
			// Este método tiene una  opción para el maxDepth
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					try {
						indexDoc(file, attrs);
					} catch (IOException ex) {
						ex.printStackTrace(System.err);
						// don't index files that can't be read.
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} else {//En teoría no se analizan los archivos normales del primer nivel. Esto es por si acaso.
			BasicFileAttributes fileAttrs = Files.readAttributes(path,BasicFileAttributes.class);
			indexDoc(path, fileAttrs);
		}
	}

	/** Indexes a single document */
	private void indexDoc(Path file, BasicFileAttributes attrs) throws IOException {
		IndexWriter writer = this.writer;

		try (InputStream stream = Files.newInputStream(file)) {
			Document doc = new Document();

			Field pathField = new StringField("path", file.toString(), Field.Store.YES);
			doc.add(pathField);

			doc.add(new LongPoint("modified", attrs.lastModifiedTime().toMillis()));

			String content = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
			doc.add(new TextField("contents",content, Field.Store.NO)); //Original contents field

			// Preguntar si tokenizar esto o no (En caso de que no, utilizar StringField)
			doc.add(new TextField("contentsStored",content, Field.Store.YES)); // Contents stored

			String hostname = InetAddress.getLocalHost().getHostName();
			doc.add(new StringField("hostname",hostname, Field.Store.YES));

			String thread = Thread.currentThread().getName();
			doc.add(new StringField("thread",thread, Field.Store.YES));

			String type;
			if(attrs.isRegularFile())
				type = "regular";
			else if(attrs.isDirectory())
				type = "directory";
			else if(attrs.isSymbolicLink())
				type = "symbolic";
			else //if(attrs.isOther())
				type = "other";

			doc.add(new StringField("type",type, Field.Store.YES));

			// El nombre debería ser sizeKB en el field...son bytes, no bits
			// Divido entre 1000 y no entre 1024. Para que fuese entre 1024, deberían pedirse Kibibytes.
			long sizeKb = attrs.size()/1000;
			doc.add(new LongPoint("sizeKb", sizeKb));


			FileTime creationTime = attrs.creationTime();
			doc.add(new StringField("creationTime",creationTime.toString(), Field.Store.YES));

			FileTime lastAccessTime = attrs.lastAccessTime();
			doc.add(new StringField("lastAccessTime",lastAccessTime.toString(), Field.Store.YES));

			FileTime lastModifiedTime = attrs.lastModifiedTime();
			doc.add(new StringField("lastModifiedTime",lastModifiedTime.toString(), Field.Store.YES));


			String creationTimeLucene = DateTools.dateToString(new Date(creationTime.toMillis()),Resolution.MILLISECOND);
			doc.add(new StringField("creationTimeLucene",creationTimeLucene, Field.Store.YES));

			String lastAccessTimeLucene = DateTools.dateToString(new Date(lastAccessTime.toMillis()),Resolution.MILLISECOND);
			doc.add(new StringField("lastAccessTimeLucene",lastAccessTimeLucene, Field.Store.YES));

			String lastModifiedTimeLucene = DateTools.dateToString(new Date(lastModifiedTime.toMillis()),Resolution.MILLISECOND);
			doc.add(new StringField("lastModifiedTimeLucene",lastModifiedTimeLucene, Field.Store.YES));
			


			if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
				System.out.println("adding " + file);
				writer.addDocument(doc);
			} else {
				System.out.println("updating " + file);
				writer.updateDocument(new Term("path", file.toString()), doc);
			}
		}
	}
}