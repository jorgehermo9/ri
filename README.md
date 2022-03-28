# Executing main 

## Example

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.IndexFiles" -Dexec.args="args"
```

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.IndexFiles" -Dexec.args="-index /tmp/index3 -docs /home/jorge/apuntes/current -openmode create -partialIndexes -numThreads 20"
```
```console
mvn exec:java -Dexec.mainClass="es.udc.ri.IndexFiles" -Dexec.args="-index /tmp/index -docs /home/jorge/github/ri/docs -openmode create -partialIndexes -numThreads 20"
```

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.StatsField" -Dexec.args="-index /tmp/index4"
```
```console
mvn exec:java -Dexec.mainClass="es.udc.ri.WriteIndex" -Dexec.args="-index /tmp/index -outputfile file.txt"
```
```console
mvn exec:java -Dexec.mainClass="es.udc.ri.BestTermsInDoc" -Dexec.args="-index /tmp/index -docID 9 -field contentsStored -top 10 -order tf"
```
```console
mvn exec:java -Dexec.mainClass="es.udc.ri.BestTermsInColl" -Dexec.args="-index /tmp/index -field contentsStored -top 10"
```

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.SimilarDocs" -Dexec.args="-index /tmp/index -docID 9 -field contentsStored -top 10 -rep tf"
```