package es.udc.ri;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IndexFiles {

    private IndexFiles(){}

    public static void main(String[] args) throws Exception {
        String usage = "java org.apache.lucene.demo.IndexFiles"
            + " [-index INDEX_PATH] [-docs DOCS_PATH] [-deep N] [-partialIndexes] \n"
            + "[-openmode {create|append|create_or_append}] [-numThreads T]\n\n"
            + "This indexes the documents in DOCS_PATH, creating a Lucene index"
            + "in INDEX_PATH\n";
            
        String indexPath = "/tmp/index";
        String docsPath = null;
		OpenMode openmode = OpenMode.CREATE;
		int numThreads = Runtime.getRuntime().availableProcessors(); // Default threads to available cores
		boolean partialIndexes = false;
		int depth = Integer.MAX_VALUE; // If no depth speified, not depth limit
		boolean update = false;

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
                case "-update":
                    update = true;
                    break;
				case "-numThreads":
					numThreads = Integer.parseInt(args[++i]);
					break;
				case "-partialIndexes":
					partialIndexes = true;
					break;
				case "-deep":
					depth = Integer.parseInt(args[++i]);
					if ( depth < 0 )
						throw new IllegalArgumentException("deep cannot be negative");
					break;
                default:
                    System.err.println("Usage: " + usage);
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if ( docsPath == null ) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        Path docDir = Paths.get(docsPath);
        if ( !Files.isReadable(docDir) ) {
            System.err.println("Document directory '" + docDir.toAbsolutePath()
                + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        ArrayList<String> onlyFiles = null;
		Integer onlyTopLines = null;
		Integer onlyBottomLines = null;
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(new File("src/main/resources/config.properties")));

            if ( properties.getProperty("onlyFiles") != null ) {
                onlyFiles = new ArrayList<>(Arrays.asList(properties.getProperty("onlyFiles").split(" ")));
            }
            if ( properties.getProperty("onlyTopLines") != null ) {
                onlyTopLines = Integer.parseInt(properties.getProperty("onlyTopLines"));
                if ( onlyTopLines < 0 )
                    throw new IllegalArgumentException("onlyTopLines cannot be negative");
            }
            if ( properties.getProperty("onlyBottomLines") != null ) {
                onlyBottomLines = Integer.parseInt(properties.getProperty("onlyBottomLines"));
                if ( onlyBottomLines < 0 )
                    throw new IllegalArgumentException("onlyBottomLines cannot be negative");
            }
        } catch (IOException e) {
            System.err.println("Error while reading config file in src/main/resources/config.properties");
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            iwc.setOpenMode(openmode);
			
            IndexWriter mainWriter = new IndexWriter(dir, iwc);

            List<IndexWriter> partialWriterList = null;
            if ( partialIndexes )
                partialWriterList = new ArrayList<IndexWriter>();
            
            final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(docDir)) {
                for ( final Path path : directoryStream ) {
                    if ( depth <= 0 ) break;
                    if ( !Files.isDirectory(path) ) continue;

                    if ( partialIndexes ) {
                        Path partialPath = Paths.get(indexPath + "_" + path.getFileName());
                        Directory partialDir = FSDirectory.open(partialPath);
                        IndexWriterConfig partialIwc = new IndexWriterConfig(new StandardAnalyzer());
                        partialIwc.setOpenMode(openmode);

                        IndexWriter partialWriter = new IndexWriter(partialDir,partialIwc);
                        partialWriterList.add(partialWriter);
                        // Esto se hace así y no con una variable que guarde el writer que va a usar el worker,
                        // ya que sale un warning de recurso sin cerrar, debido a que metemos el writer en una
                        // lista y lo cerramos fuera del scope.

                        Runnable worker = new WorkerThread(path,partialWriter,update,depth,onlyFiles,onlyTopLines,onlyBottomLines);
                        executor.execute(worker);
                    } else {
                        Runnable worker = new WorkerThread(path,mainWriter,update,depth,onlyFiles,onlyTopLines,onlyBottomLines);
                        executor.execute(worker);
                    }
                }
				// Always index root files 
				Runnable worker = new WorkerThread(docDir,mainWriter,update,1,onlyFiles,onlyTopLines,onlyBottomLines);
				executor.execute(worker);

            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
    
            executor.shutdown();
    
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(2);
            } finally {
                if ( partialWriterList != null ) {
                    for ( IndexWriter partialWriter : partialWriterList ) {
                        partialWriter.commit();
                        partialWriter.close();
                    }
                }
            }
    
            // System.out.println("Finished all threads");
            
            if ( partialIndexes ) {
                Directory[] partialDirs = new Directory[partialWriterList.size()];
                for ( int i = 0; i < partialWriterList.size(); i++ )
                    partialDirs[i] = partialWriterList.get(i).getDirectory();
            
                mainWriter.addIndexes(partialDirs);
            }

            mainWriter.commit();
            mainWriter.close();
            System.out.println("Finished writing index to: " + indexPath);

            Date end = new Date();
            try (IndexReader reader = DirectoryReader.open(dir)) {
                System.out.println("Indexed " + reader.numDocs() + " documents in " + (end.getTime() - start.getTime())
                    + " milliseconds");
            }
            
        } catch (IOException e) {
            System.err.println("Caught a " + e.getClass() + "\n with message: " + e.getMessage());
            System.exit(1);
        }
    }
}

class WorkerThread implements Runnable {
	private Path docsFolder;
	private IndexWriter writer;
	private boolean update;
	private int depth;
	private ArrayList<String> onlyFiles;
	private Integer	onlyTopLines;
	private Integer	onlyBottomLines;
	private FieldType myFieldTypeStored;

	public WorkerThread(Path docsFolder, IndexWriter writer,boolean update, int depth, List<String> onlyFiles, Integer onlyTopLines, Integer onlyBottomLines) {
		this.docsFolder = docsFolder;
		this.writer = writer;
		this.update = update;
		this.depth = depth;
		this.onlyFiles = onlyFiles != null ? new ArrayList<>(onlyFiles) : null;
		this.onlyTopLines = onlyTopLines;
		this.onlyBottomLines = onlyBottomLines;

		this.myFieldTypeStored = new FieldType();

		IndexOptions options = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
		myFieldTypeStored.setIndexOptions(options);
		myFieldTypeStored.setTokenized(true);
		myFieldTypeStored.setStored(true);
		myFieldTypeStored.setStoreTermVectors(true);
		myFieldTypeStored.setStoreTermVectorPositions(true);
		myFieldTypeStored.freeze();

	}

	@Override
	public void run() {
		try {
			this.indexDocs();
		} catch (IOException e){
			e.printStackTrace();
		}
	}

	private void indexDocs() throws IOException {
		Path path = this.docsFolder;
		if ( !Files.isDirectory(path) ) {
			BasicFileAttributes fileAttrs = Files.readAttributes(path,BasicFileAttributes.class);
			indexDoc(path, fileAttrs);
            return;
        }

        Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), depth, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Con la limitación de depth, las carpetas que no se sigan explorando
                // van a visitarse con este método y no se iterará sobre sus subarchivos,
                // por lo que es una carpeta y no un archivo, no se va a indexar
                if ( Files.isDirectory(file) ) return FileVisitResult.CONTINUE;

                // En este punto se que el file es un archivo y no una carpeta
                String fileName = file.getFileName().toString();
                int i = fileName.lastIndexOf(".");
                String extension = i >= 0 ? fileName.substring(i) : "";
                if ( onlyFiles != null && !onlyFiles.contains(extension) )
                    return FileVisitResult.CONTINUE;
                    
                try {
                    indexDoc(file, attrs);
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
                return FileVisitResult.CONTINUE;
            }
        });
	}

	private void indexDoc(Path file, BasicFileAttributes attrs) throws IOException {
		IndexWriter writer = this.writer;
		

		try (InputStream stream = Files.newInputStream(file)) {
			Document doc = new Document();

			Field pathField = new StringField("path", file.toString(), Field.Store.YES);
			doc.add(pathField);

			doc.add(new LongPoint("modified", attrs.lastModifiedTime().toMillis()));

			String content = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
			doc.add(new TextField("contents", content, Field.Store.NO)); // Original contents field

			doc.add(new Field("contentsStored", content, this.myFieldTypeStored)); // Contents stored

			String hostname = InetAddress.getLocalHost().getHostName();
			doc.add(new StringField("hostname", hostname, Field.Store.YES));

			String thread = Thread.currentThread().getName();
			doc.add(new StringField("thread", thread, Field.Store.YES));

			String type;
			if ( attrs.isRegularFile() )
				type = "regular";
			else if ( attrs.isDirectory() )
				type = "directory";
			else if ( attrs.isSymbolicLink() )
				type = "symbolic";
			else //if(attrs.isOther())
				type = "other";

			doc.add(new StringField("type", type, Field.Store.YES));

			// El nombre debería ser sizeKB en el field...son bytes, no bits
			// Divido entre 1000 y no entre 1024. Para que fuese entre 1024, deberían pedirse Kibibytes.
			Double sizeKb = attrs.size()/1000.0;
			// Campo para query por rangos
			doc.add(new DoublePoint("sizeKbNumeric", sizeKb));
			//Campo para ver el valor del size
			doc.add(new StringField("sizeKb", sizeKb.toString(),Field.Store.YES));

			FileTime creationTime = attrs.creationTime();
			doc.add(new StringField("creationTime", creationTime.toString(), Field.Store.YES));

			FileTime lastAccessTime = attrs.lastAccessTime();
			doc.add(new StringField("lastAccessTime", lastAccessTime.toString(), Field.Store.YES));

			FileTime lastModifiedTime = attrs.lastModifiedTime();
			doc.add(new StringField("lastModifiedTime", lastModifiedTime.toString(), Field.Store.YES));

			String creationTimeLucene = DateTools.dateToString(new Date(creationTime.toMillis()), Resolution.MILLISECOND);
			doc.add(new StringField("creationTimeLucene", creationTimeLucene, Field.Store.YES));

			String lastAccessTimeLucene = DateTools.dateToString(new Date(lastAccessTime.toMillis()), Resolution.MILLISECOND);
			doc.add(new StringField("lastAccessTimeLucene", lastAccessTimeLucene, Field.Store.YES));

			String lastModifiedTimeLucene = DateTools.dateToString(new Date(lastModifiedTime.toMillis()), Resolution.MILLISECOND);
			doc.add(new StringField("lastModifiedTimeLucene", lastModifiedTimeLucene, Field.Store.YES));

			if ( this.onlyTopLines != null ) {
				List<String> splittedContent = Arrays.asList(content.split("\n"));
				int totalLines= splittedContent.size();
				int linesToRead = totalLines < onlyTopLines ? totalLines : onlyTopLines;
				List<String> lines = splittedContent.subList(0, linesToRead);
				StringBuilder sb = new StringBuilder();
				for ( String line : lines )
					sb.append(line+"\n");
				//Borar último \n
				sb.deleteCharAt(sb.length()-1);
				String onlyTopContent = new String(sb);
				doc.add(new Field("onlyTopLines", onlyTopContent, this.myFieldTypeStored));
			}

			if ( this.onlyBottomLines != null ) {
				List<String> splittedContent = Arrays.asList(content.split("\n"));
				int totalLines= splittedContent.size();
				int linesToRead = totalLines < onlyBottomLines ? totalLines : onlyBottomLines;
				List<String> lines = splittedContent.subList(totalLines-linesToRead, totalLines);
				StringBuilder sb = new StringBuilder();
				for ( String line : lines )
					sb.append(line+"\n");
				//Borar último \n
				sb.deleteCharAt(sb.length()-1);
				String onlyBottomContent = new String(sb);
				doc.add(new Field("onlyBottomLines", onlyBottomContent, this.myFieldTypeStored));
			}

			if ( writer.getConfig().getOpenMode() == OpenMode.CREATE ) {
				System.out.println("adding " + file);
				writer.addDocument(doc);
			} else {
				if(this.update){
					System.out.println("updating " + file);
					writer.updateDocument(new Term("path", file.toString()), doc);
				}else{
					System.out.println("adding " + file);
					writer.addDocument(doc);
				}
			}
		}
	}
}
