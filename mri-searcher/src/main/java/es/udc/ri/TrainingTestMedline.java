package es.udc.ri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;

class TrainingTestMetrics {
	private List<QueryMetrics> queryMetrics;

	TrainingTestMetrics(List<QueryMetrics> queryMetrics) {
		this.queryMetrics = queryMetrics;
	}

	public List<QueryMetrics> getQueryMetrics() {
		return this.queryMetrics;
	}

	public double getScore(Metric scoreMetric) {
		return scoreMetric.getScore(this.queryMetrics);
	}
}

enum Metric {
	P {
		@Override
		double getScore(List<QueryMetrics> queryMetrics) {
			return queryMetrics.stream().mapToDouble(metric -> metric.getPrecision()).summaryStatistics()
					.getAverage();
		}

		double getScore(QueryMetrics queryMetrics) {
			return queryMetrics.getPrecision();
		}

	},
	R {
		@Override
		double getScore(List<QueryMetrics> queryMetrics) {
			return queryMetrics.stream().mapToDouble(metric -> metric.getRecall()).summaryStatistics()
					.getAverage();
		}

		double getScore(QueryMetrics queryMetrics) {
			return queryMetrics.getRecall();
		}

	},
	MAP {
		@Override
		double getScore(List<QueryMetrics> queryMetrics) {
			return queryMetrics.stream().mapToDouble(metric -> metric.getAp()).summaryStatistics()
					.getAverage();
		}

		double getScore(QueryMetrics queryMetrics) {
			return queryMetrics.getAp();
		}
	};

	abstract double getScore(List<QueryMetrics> queryMetrics);

	abstract double getScore(QueryMetrics queryMetrics);

}

public class TrainingTestMedline {
	public static void main(String[] args) {
		String usage = "java es.udc.ri.TrainingTestMedline"
				+ " [-{evaljm int1-int2 int3-int4 | evaltfidf int3-int4}s] \n"
				+ "[-docs DOCS_PATH]\n"
				+ " [-metrica {P|R|MAP}]\n"
				+ "[-cut n]\n\n";
		String indexPath = null;
		String docsPath = null;

		String trainQueriesString = null;
		String testQueriesString = null;

		Boolean trainTfidf = null;

		Integer trainLowerBound = null;
		Integer trainUpperBound = null;

		Integer testLowerBound = null;
		Integer testUpperBound = null;

		Metric metric = null;

		Integer cut = null;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-indexin":
					indexPath = args[++i];
					break;
				case "-docs":
					docsPath = args[++i];
					break;
				case "-metrica":
					String argMetrica = args[++i];
					if (argMetrica.equals("P")) {
						metric = Metric.P;
					} else if (argMetrica.equals("R")) {
						metric = Metric.R;
					} else if (argMetrica.equals("MAP")) {
						metric = Metric.MAP;
					} else {
						throw new IllegalArgumentException("Unknown metric: " + argMetrica);
					}
					break;
				case "-cut":
					cut = Integer.parseInt(args[++i]);
					if (cut <= 0)
						throw new IllegalArgumentException("Cut must be greater than 0");
					break;
				case "-evaltfidf":
					trainTfidf = true;
					testQueriesString = args[++i];
					String[] splitted = testQueriesString.split("-");
					if (splitted.length == 2) {
						// Si es un rango de queries
						try {
							testLowerBound = Integer.parseInt(splitted[0]);
							testUpperBound = Integer.parseInt(splitted[1]);
							if (testLowerBound > testUpperBound) {
								throw new IllegalArgumentException(
										"Queries upper bound must be greater than lower bound");
							}
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("Can't parse queries: " + e.getMessage());
						}

					} else {
						throw new IllegalArgumentException("Invalid query format");
					}
					break;

				case "-evaljm":
					trainTfidf = false;
					trainQueriesString = args[++i];
					testQueriesString = args[++i];

					String[] trainSplitted = trainQueriesString.split("-");
					if (trainSplitted.length == 2) {
						// Si es un rango de queries
						try {
							trainLowerBound = Integer.parseInt(trainSplitted[0]);
							trainUpperBound = Integer.parseInt(trainSplitted[1]);
							if (trainLowerBound > trainUpperBound) {
								throw new IllegalArgumentException(
										"Queries upper bound must be greater than lower bound");
							}
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("Can't parse queries: " + e.getMessage());
						}

					} else {
						throw new IllegalArgumentException("Invalid query format");
					}
					String[] testSplitted = testQueriesString.split("-");
					if (testSplitted.length == 2) {
						// Si es un rango de queries
						try {
							testLowerBound = Integer.parseInt(testSplitted[0]);
							testUpperBound = Integer.parseInt(testSplitted[1]);
							if (testLowerBound > testUpperBound) {
								throw new IllegalArgumentException(
										"Queries upper bound must be greater than lower bound");
							}
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("Can't parse queries: " + e.getMessage());
						}

					} else {
						throw new IllegalArgumentException("Invalid query format");
					}

					break;

				default:
					System.err.println("Usage: " + usage);
					throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}

		if (indexPath == null || docsPath == null || cut == null || metric == null || trainTfidf == null
				|| testLowerBound == null || testLowerBound == null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		}

		FileReader queryReader = null;
		FileReader relevanceReader = null;

		try {
			queryReader = new FileReader(docsPath + "/MED.QRY");
		} catch (FileNotFoundException e) {
			System.err.println("Medline queries file not found: " + docsPath);
			System.exit(1);
		}

		try {
			relevanceReader = new FileReader(docsPath + "/MED.REL");
		} catch (FileNotFoundException e) {
			System.err.println("Medline queries relevance file not found: " + docsPath);
			System.exit(1);
		}

		List<MedlineInfo> queriesInfo = MedlineInfo.parseMedline(queryReader);
		HashMap<Long, List<Long>> relevances = MedlineInfo.getRelevance(relevanceReader);

		// Tienen que ser final para poder usarlos en los closures...

		final int finalTestLowerBound = testLowerBound;
		final int finalTestUpperBound = testUpperBound;

		List<MedlineInfo> trainQueries = null;
		if (!trainTfidf) {
			final int finalTrainLowerBound = trainLowerBound;
			final int finalTrainUpperBound = trainUpperBound;
			trainQueries = queriesInfo.stream()
					.filter(query -> query.getId() >= finalTrainLowerBound && query.getId() <= finalTrainUpperBound)
					.collect(Collectors.toList());

		}

		List<MedlineInfo> testQueries = queriesInfo.stream()
				.filter(query -> query.getId() >= finalTestLowerBound && query.getId() <= finalTestUpperBound)
				.collect(Collectors.toList());

		// Empezamos a leer el índice

		IndexReader reader = null;
		IndexSearcher searcher = null;
		QueryParser parser;

		try {
			reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));

		} catch (Exception e) {
			System.err.println("Could not read index in " + indexPath);
			System.exit(1);
		}

		parser = new QueryParser("Contents", new StandardAnalyzer());
		searcher = new IndexSearcher(reader);

		List<TrainingTestMetrics> trainMetrics = null;
		Float bestLambda = null;
		if (!trainTfidf) {
			float step = 0.1f;
			trainMetrics = new ArrayList<>();
			// No se puede poner lambda=0...
			for (int i = 1; i <= 10; i++) {
				searcher.setSimilarity(new LMJelinekMercerSimilarity(i * step));
				trainMetrics.add(processQueries(reader, searcher, parser, trainQueries, relevances, cut));
			}

			double maxScore = trainMetrics.get(0).getScore(metric);
			int maxIndex = 0;
			for (int i = 0; i < trainMetrics.size(); i++) {
				double newScore = trainMetrics.get(i).getScore(metric);
				if (newScore > maxScore) {
					maxScore = newScore;
					maxIndex = i;
				}
			}

			bestLambda = step * (maxIndex + 1);
			searcher.setSimilarity(new LMJelinekMercerSimilarity(bestLambda));
		} else {
			searcher.setSimilarity(new ClassicSimilarity());
		}

		TrainingTestMetrics testMetrics = processQueries(reader, searcher, parser, testQueries, relevances, cut);

		String trainFile = null;
		String testFile = null;

		if (trainTfidf) {
			testFile = String.format("medline.tfidf.training.null.test.%d-%d.%s%d.test.csv",
					testLowerBound, testUpperBound, metric.toString().toLowerCase(), cut);
		} else {
			trainFile = String.format("medline.jm.training.%d-%d.test.%d-%d.%s%d.training.csv",
					trainLowerBound, trainUpperBound, testLowerBound, testUpperBound,
					metric.toString().toLowerCase(), cut);

			testFile = String.format("medline.jm.training.%d-%d.test.%d-%d.%s%d.test.csv",
					trainLowerBound, trainUpperBound, testLowerBound, testUpperBound,
					metric.toString().toLowerCase(), cut);
		}

		if (!trainTfidf) {
			writeTrainingCsv(trainFile, trainMetrics, metric, cut);
			System.out.println("Training results");
			printFile(trainFile);
		}
		writeTestCsv(testFile, bestLambda, testMetrics, metric, cut);
		System.out.println("Test results");
		printFile(testFile);

	}

	public static void writeTrainingCsv(String fileName, List<TrainingTestMetrics> trainingMetrics, Metric metric,
			int cut) {
		PrintWriter fileWriter = null;

		try {
			FileWriter fw = new FileWriter(fileName, false);
			BufferedWriter bw = new BufferedWriter(fw);
			fileWriter = new PrintWriter(bw);

		} catch (Exception e) {
			System.err.println("Error while creating writer: " + e.getMessage());
			System.exit(1);
		}
		fileWriter.print(metric + "@" + cut);
		double step = 0.1f;
		for (int i = 1; i <= 10; i++) {
			fileWriter.printf(",%.1f", step * i);
		}
		fileWriter.println();

		int numQueries = trainingMetrics.get(0).getQueryMetrics().size();
		for (int i = 0; i < numQueries; i++) {

			List<QueryMetrics> queryMetrics = trainingMetrics.get(0).getQueryMetrics();
			MedlineInfo targetQuery = queryMetrics.get(i).getQuery();

			fileWriter.print(targetQuery.getId());
			for (TrainingTestMetrics lambdaTrainingMetrics : trainingMetrics) {

				double lambdaScore = metric.getScore(lambdaTrainingMetrics.getQueryMetrics().get(i));
				fileWriter.printf(",%.5f", lambdaScore);
			}
			fileWriter.println();
		}
		fileWriter.print("mean");
		for (TrainingTestMetrics lambdaTrainingMetrics : trainingMetrics) {
			fileWriter.printf(",%.5f", lambdaTrainingMetrics.getScore(metric));
		}

		fileWriter.close();
	}

	public static void writeTestCsv(String fileName, Float bestLambda, TrainingTestMetrics testMetrics, Metric metric,
			int cut) {
		PrintWriter fileWriter = null;

		try {
			FileWriter fw = new FileWriter(fileName, false);
			BufferedWriter bw = new BufferedWriter(fw);
			fileWriter = new PrintWriter(bw);

		} catch (Exception e) {
			System.err.println("Error while creating writer: " + e.getMessage());
			System.exit(1);
		}

		if (bestLambda == null) {
			fileWriter.print("null");
		} else {
			fileWriter.printf("%.1f", bestLambda);
		}
		fileWriter.printf(",%s@%d\n", metric, cut);

		for (QueryMetrics queryMetrics : testMetrics.getQueryMetrics()) {

			MedlineInfo targetQuery = queryMetrics.getQuery();

			fileWriter.print(targetQuery.getId() + ",");
			fileWriter.printf("%.5f\n", metric.getScore(queryMetrics));
		}
		fileWriter.print("mean");
		fileWriter.printf(",%.5f", testMetrics.getScore(metric));

		fileWriter.close();
	}

	public static void printFile(String path) {
		System.out.println("-----------------------");
		System.out.println("Showing contents for: " + path + "\n");
		try (FileReader reader = new FileReader(path);
				BufferedReader bufferedReader = new BufferedReader(reader)) {

			bufferedReader.lines().forEachOrdered(line -> System.out.println(line));
			System.out.println("-----------------------");

		} catch (FileNotFoundException e) {
			System.err.println("File not found: " + path);
			return;
		} catch (Exception e) {
			System.err.println("Could not read file: " + path);
			return;
		}
	}

	public static TrainingTestMetrics processQueries(IndexReader reader, IndexSearcher searcher,
			QueryParser parser, List<MedlineInfo> queries, HashMap<Long, List<Long>> relevances, int cut) {

		List<QueryMetrics> metrics = new ArrayList<>();
		for (MedlineInfo query : queries) {
			List<Long> relevantDocs = relevances.get(query.getId());
			QueryMetrics queryMetrics = processQuery(reader, searcher, parser, query, relevantDocs, cut);
			// Si se ha podido computar los query metrics
			if (queryMetrics != null) {
				metrics.add(queryMetrics);
			}
		}
		return new TrainingTestMetrics(metrics);
	}

	public static QueryMetrics processQuery(IndexReader reader, IndexSearcher searcher,
			QueryParser parser, MedlineInfo query, List<Long> relevantDocs, int cut) {

		// Pasar a minúsculas y eliminar paréntesis
		String queryContents = query.getContents().toLowerCase().replace("(", "").replace(")", "");
		Query parsedQuery = null;
		try {
			// Preguntar si quitar paréntesis. Da problemas con paréntesis no cerrados
			parsedQuery = parser.parse(queryContents);
		} catch (Exception e) {
			System.err.println("Could not parse query with id " + query.getId() + ": " + e.getMessage());
			return null;
		}
		TopDocs topdocs = null;
		try {
			topdocs = searcher.search(parsedQuery, cut);
		} catch (Exception e) {
			System.err.println("Could not get top docs from query with id " + query.getId() + ": " + e.getMessage());
			return null;
		}

		long totalHits = topdocs.totalHits.value;
		ScoreDoc[] scoreDocs = topdocs.scoreDocs;
		int VP = 0;
		double apAux = 0;

		for (int i = 0; i < Math.min(totalHits, cut); i++) {
			int docId = scoreDocs[i].doc;
			try {
				long medlineId = Long.parseLong(reader.document(docId).get("DocIDMedline"));
				boolean relevant = relevantDocs.contains(medlineId);

				if (relevant) {
					VP++;
					apAux += (double) VP / (double) (i + 1);
				}
			} catch (Exception e) {
				System.err.println(
						"Error while reading index for document " + docId + " in query with id " + query.getId()
								+ ": " + e.getMessage());
				continue;
			}
		}
		// Si la query no tiene relevantes, no evaluar métricas
		if (relevantDocs.size() == 0) {
			System.err.println("Query " + query.getId() + " does not have any relevant document. Can't compute metrics.");
			return null;
		}

		double precision = (double) VP / (double) Math.min(cut, totalHits);
		double recall = (double) VP / (double) relevantDocs.size();
		// Esta métrica así sería parecida al precision, pero penalizando más a los
		// documentos
		// que tienen otros no relevantes encima. No sé si estaría bien usarla tambien
		// double ap = apAux/Math.min(cut,totalHits);
		double ap = apAux / (double) relevantDocs.size();
		return new QueryMetrics(query, precision, recall, ap);
	}
}
