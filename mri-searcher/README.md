

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.IndexMedline" -Dexec.args="-index /tmp/index -docs /home/jorge/github/ri/docs/medline/ -indexingmodel tfidf"
````

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.SearchEvalMedline" -Dexec.args="-indexin /tmp/index -docs /home/jorge/github/ri/docs/medline/ -search tfidf -top 10 -cut 10 -queries all"
```