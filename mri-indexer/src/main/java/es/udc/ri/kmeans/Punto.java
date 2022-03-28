package es.udc.ri.kmeans;

import org.apache.commons.math3.linear.RealVector;

public class Punto {
    private double[] data;
	private Integer docId;
	private String path;
    public Punto(double[] data) {
	this.data = data;
    }
	public Punto(RealVector data,int docId,String path) {
		this.data = data.toArray();
		this.docId = docId;
		this.path=path;
	}

    public Double get(int dimension) {
	return data[dimension];
    }

    public int getGrado() {
	return data.length;
    }
	public Integer getDocId() {
		return this.docId;
	}
	public String getPath() {
		return this.path;
	}

    @Override
    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append(data[0]);
	for (int i = 1; i < data.length; i++) {
	    sb.append(", ");
	    sb.append(data[i]);
	}
	return "("+sb.toString()+") => "+ (docId!=null?docId.toString():"centroide") ;
    }

    public Double distanciaEuclideana(Punto destino) {
	Double d = 0d;
	for (int i = 0; i < data.length; i++) {
	    d += Math.pow(data[i] - destino.get(i), 2);
	}
	return Math.sqrt(d);
    }

    @Override
    public boolean equals(Object obj) {
	Punto other = (Punto) obj;
	for (int i = 0; i < data.length; i++) {
	    if (data[i] != other.get(i)) {
		return false;
	    }
	}
	return true;
    }
}