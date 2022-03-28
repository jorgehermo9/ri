package es.udc.ri.kmeans;
import java.util.ArrayList;
import java.util.List;

public class KMeansResultado {
    private List<Cluster> clusters = new ArrayList<Cluster>();
    private Double ofv;

    public KMeansResultado(List<Cluster> clusters, Double ofv) {
	super();
	this.ofv = ofv;
	this.clusters = clusters;
    }

    public List<Cluster> getClusters() {
	return clusters;
    }

    public Double getOfv() {
	return ofv;
    }

	//Cambiar
	public void print() {
		System.out.println("-------------------------------");
	    System.out.println(clusters.size()+ " Clusters");
		System.out.println("docId => path");
		System.out.println("-------------------------------");

		int count = 0;
		for (Cluster cluster : this.clusters) {
			count++;
			System.out.println("Cluster " + count+"\n");
			for (Punto punto : cluster.getPuntos()) {
				System.out.println(punto.getDocId()+" => "+punto.getPath());
			}
			System.out.println("-------------------------------");
		}
	}
}
