

# Indexar 

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.IndexMedline" -Dexec.args="-index /tmp/index -docs /home/jorge/github/ri/docs/medline/ -indexingmodel tfidf"
```

# Buscar

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.SearchEvalMedline" -Dexec.args="-indexin /tmp/index -docs /home/jorge/github/ri/docs/medline/ -search tfidf -top 10 -cut 10 -queries all"
```

# Training 
## jm
```console
mvn exec:java -Dexec.mainClass="es.udc.ri.TrainingTestMedline" -Dexec.args="-indexin /tmp/index -docs /home/jorge/github/ri/docs/medline -cut 10 -evaljm 1-20 21-30 -metrica MAP"
```

## tfidf 
```console
mvn exec:java -Dexec.mainClass="es.udc.ri.TrainingTestMedline" -Dexec.args="-indexin /tmp/index -docs /home/jorge/github/ri/docs/medline -cut 10 -evaltfidf 21-30 -metrica MAP"
```

# Compare

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.Compare" -Dexec.args="-test t 0.05 -results medline.jm.training.1-20.test.21-30.map10.test.csv medline.tfidf.training.null.test.21-30.map10.test.csv"
```