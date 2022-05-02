package es.udc.ri;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

public class Compare {
	public static void main(String[] args) {

		String usage = "java es.udc.ri.Compare\n"
				+ " [-test {t|wilcoxon alpha}] [-results results1 results2]\n\n";

		String results1 = null;
		String results2 = null;
		Double alpha = null;
		Boolean t_test = null;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-results":
					results1 = args[++i];
					results2 = args[++i];

					break;
				case "-test":
					String testString = args[++i];

					if (testString.contains("wilcoxon")) {
						t_test = false;
					} else if (testString.equals("t")) {
						t_test = true;
					} else {
						throw new IllegalArgumentException("Test not supported: " + testString);
					}

					try {
						alpha = Double.parseDouble(args[++i]);
					} catch (Exception e) {
						throw new IllegalArgumentException("Error while parsing alpha: " + e.getMessage());
					}
					break;

				default:
					System.err.println("Usage: " + usage);
					throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}

		if (results1 == null || results2 == null || alpha == null || t_test == null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		}
		String[] splittedResults1 = results1.split("\\.");

		String testResults1 = splittedResults1[5];
		String metricResults1 = splittedResults1[6];

		String[] splittedResults2 = results2.split("\\.");
		String testResults2 = splittedResults2[5];
		String metricResults2 = splittedResults2[6];

		if (!testResults1.equals(testResults2)) {
			System.err.println("Test queries does not match: " + testResults1 + " and " + testResults2);
			System.exit(1);
		}

		if (!metricResults1.equals(metricResults2)) {
			System.err.println("Test metrics does not match: " + metricResults1 + " and " + metricResults2);
			System.exit(1);
		}

		double[] x = parseCsv(results1);
		double[] y = parseCsv(results2);

		double p_value;
		if (t_test)
			p_value = new TTest().pairedTTest(x, y);
		else
			p_value = new WilcoxonSignedRankTest().wilcoxonSignedRankTest(x, y, true);

		if (p_value < alpha) {
			System.out.println("Se rechaza la hipótesis nula (Hay uno mejor que el otro)");
			System.out.println("p-value = " + p_value + " < " + alpha);
		} else {
			System.out.println("No se rechaza hipótesis nula (No hay pruebas de que uno sea mejor que el otro)");
			System.out.println("p-value = " + p_value + " >= " + alpha);
		}
	}

	public static double[] parseCsv(String path) {
		List<Double> metricValues = new ArrayList<>();
		try (FileReader reader = new FileReader(path);
				BufferedReader bufferedReader = new BufferedReader(reader)) {

			bufferedReader.lines().skip(1).forEachOrdered(line -> {
				String valueString = line.split(",")[1];
				metricValues.add(Double.parseDouble(valueString));
			});
		} catch (FileNotFoundException e) {
			System.err.println("File not found: " + path);
			System.exit(1);
		} catch (Exception e) {
			System.err.println("Could not read file: " + path);
			System.exit(1);
		}
		// Hasta size-1 para no meter en el array la media
		double[] array = new double[metricValues.size() - 1];
		for (int i = 0; i < metricValues.size() - 1; i++) {
			array[i] = metricValues.get(i);
		}
		return array;
	}
}
