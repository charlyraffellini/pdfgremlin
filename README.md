# PdfGremlin

## Utility app that highlight and convert to pdf an entire folder

### Currently only supports

- scala
- java
- markdown

How to run it:

```bash
sbt assembly

java -Dpdfgremlin.input.folder=/Users/carlos.raffellini/src/myproject -Dpdfgremlin.output.prefix=com.raffellini.myproject -cp target/scala-2.12/pdfgremlin-assembly-0.1.0-SNAPSHOT.jar com.pdfgremlin.PdfGremlin
```
