
# Prácticas de Recuperación de Información UDC

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

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.DocClusters" -Dexec.args="-index /tmp/index -docID 9 -field contentsStored -top 10 -rep tf -k 4"
```

Para separar entre idiomas en los clusters, funciona muy bien con el tf. En cambio, para ver la similitud entre dos noticias del mismo idioma, funciona muy bien el tfxidf.

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.IndexFiles" -Dexec.args="-index /tmp/index -docs /home/jorge/github/ri/docs/news/ -openmode create -partialIndexes -numThreads 20"

mvn exec:java -Dexec.mainClass="es.udc.ri.DocClusters" -Dexec.args="-index /tmp/index -docID 9 -field contentsStored -top 10 -rep tfxidf -k 2"
```

Por ejemplo, podemos ver que el más similar a russia.txt (frances) es ukraine.txt y ukraine2.txt ( en frances)

```console
mvn exec:java -Dexec.mainClass="es.udc.ri.DocClusters" -Dexec.args="-index /tmp/index -docID 0 -field contentsStored -top 10 -rep tfxidf -k 2"
```

/tmp/news -> noticias
/tmp/news_spanish_french
/tmp/index -> my_docs
