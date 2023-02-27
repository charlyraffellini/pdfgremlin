package example

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.python.util.PythonInterpreter

import java.nio.file.Path
import scala.io.BufferedSource
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

object Hello extends App {

  import java.io.FileOutputStream
  import io.github.cloudify.scala.spdf._
  import java.io._

  val pdfForCode = Pdf(new PdfConfig {
    orientation := Landscape
    pageSize := "A4"
    marginTop := "5mm"
    marginBottom := "5mm"
    marginLeft := "5mm"
    marginRight := "5mm"
    disableSmartShrinking := true
    zoom := 0.75f
  })

  val pdfForFileSeparator = Pdf(new PdfConfig {
    orientation := Landscape
    pageSize := "A4"
    marginTop := "5cm"
    marginBottom := "20mm"
    marginLeft := "20mm"
    marginRight := "20mm"
    disableSmartShrinking := true
    zoom := 1.0f
  })

  def file(path: String): Resource[IO, BufferedSource] = Resource.make(IO(scala.io.Source.fromFile(path)))(b => IO(b.close()))
  def pynterpreter(): Resource[IO, PythonInterpreter] = Resource.make(IO(new PythonInterpreter()))(i => IO(i.close()))
  def fileOutputStream(filePath: String) = Resource.make(IO {
    val byteOutputStream = new ByteArrayOutputStream
    new FileOutputStream(filePath)
  })(s => IO(s.close()))

  def pdfMerger(outPath: FileOutputStream) = Resource.make(IO(new PDFMergerUtility()))(m => IO {
    m.setDestinationStream(outPath)
    m.mergeDocuments()
  })

  def toHtml(interpreter: PythonInterpreter, code: String): String = {
    val codeVarName = "code"
    val resultValName = "result"

    interpreter.set(codeVarName, code)

    interpreter.exec(
      s"""
        |from pygments import highlight
        |from pygments.lexers.jvm import ScalaLexer
        |from pygments.formatters import HtmlFormatter
        |from pygments.styles import get_style_by_name
        |$resultValName = highlight($codeVarName, ScalaLexer(), HtmlFormatter(full=True,style='xcode'))
        |""".stripMargin
    )

    interpreter.get(resultValName, classOf[String])
  }

  def htmlToPdf(html: String) = {
    val b = new ByteArrayOutputStream
    pdfForCode.run(html, b)
    val r = b.toByteArray
    b.close()
    r
  }

  def titleToPdf(title: String): Array[Byte] = {
    val page: String = {
      s"""
         |<html>
         |  <body>
         |    <div class="title">
         |      <h1>$title</h1>
         |    </div>
         |  </body>
         |</html>
         |
         |""".stripMargin
    }

    htmlToPdf(page)
  }

  def mergeFile(inter: PythonInterpreter, merger: PDFMergerUtility)(path: Path): IO[Unit] = {
    IO {
      println(path.toAbsolutePath.toString)
    } >>
    IO {
      val pdf = titleToPdf(path.toString)
      merger.addSource(new ByteArrayInputStream(pdf))
    } >>
    file(path.toAbsolutePath.toString).use(codeFile =>
      IO {
        val htmlDoc = toHtml(inter, codeFile.mkString)
        val pdf = htmlToPdf(htmlDoc)
        merger.addSource(new ByteArrayInputStream(pdf))
      }
    )
  }

  def codeFiles(folder: String): IO[List[Path]] = IO {
    import scala.collection.JavaConverters._

    java.nio.file.Files.find(
      java.nio.file.Paths.get(folder),
      10,
      (p, a) => p.toString.endsWith(".scala") && a.isRegularFile
    ).iterator.asScala.toList.sortBy(_.toString.toUpperCase)
  }

  def gitRev(path: String): Option[String] =
    Try {
      import scala.sys.process._
      Process("git rev-parse --short HEAD", new File(path)).!!.trim
    } match {
      case Failure(_) => None
      case Success(v) => Option(v)
    }

  val folderPath: String = "/Users/carlos.raffellini/src/circe-generic-extras/generic-extras/src/main/scala/io/circe/generic/extras/"
  val outputFile: String = "io.circe.generic.extras"

  import cats.implicits._
  (
    for {
      inter <- pynterpreter()
      pdfFile <- fileOutputStream(s"$outputFile${gitRev(folderPath).map("." + _).getOrElse("")}.pdf")
      merger <- pdfMerger(pdfFile)
    } yield (inter,merger)
  ).use { case (inter, merger) => {
      for {
        fs <- codeFiles(folderPath)
        _ <- IO.println(fs)
        v <- fs.map(mergeFile(inter, merger)).sequence
      } yield v
    }
  }.unsafeRunSync()

}



/*
val code = scala.io.Source.fromFile("/Users/carlos.raffellini/src/pdfgremlin/src/main/scala/example/Hello.scala").mkString

val interpreter = new PythonInterpreter()

// Set a variable with the content you want to work with
interpreter.set("code", code)

interpreter.exec(
  "from pygments import highlight\n" +
  "from pygments.lexers.jvm import ScalaLexer\n" +
  "from pygments.formatters import HtmlFormatter\n" +
  "from pygments.styles import get_style_by_name\n" +
  "\nresult = highlight(code, ScalaLexer(), HtmlFormatter(full=True,style='xcode'))")

// Get the result that has been set in a variable
val highlightedHtml: String = interpreter.get("result", classOf[String])
System.out.println(highlightedHtml)


// Create a new Pdf converter with a custom configuration
// run `wkhtmltopdf --extended-help` for a full list of options

val page = scala.io.Source.fromFile("/Users/carlos.raffellini/src/circe/exportToHTML/io/circe/ACursor.scala.html").mkString

// Save the PDF generated from the above HTML into a Byte Array
val byteOutputStream = new ByteArrayOutputStream
pdf.run(highlightedHtml, byteOutputStream)


try {
  val outputStream = new FileOutputStream("google.pdf")
  try byteOutputStream.writeTo(outputStream)
  finally if (outputStream != null) outputStream.close()
}

// Save the PDF of Google's homepage into a file
//  pdf.run(new URL("http://www.google.com"), new File("google.pdf"))

println(greeting)
 */


/*
file("/Users/carlos.raffellini/src/pdfgremlin/src/main/scala/example/Hello.scala").use(codeFile =>
  IO {
    val htmlDoc = toHtml(inter, codeFile.mkString)
    val pdf = htmlToPdf(htmlDoc)
    merger.addSource(new ByteArrayInputStream(pdf))
  }
) >> IO {
  val pdf = titleToPdf("pdfgremlin/src/main/scala/example/Hello.scala")
  merger.addSource(new ByteArrayInputStream(pdf))
} >> file("/Users/carlos.raffellini/src/pdfgremlin/src/main/scala/example/Hello.scala").use(codeFile =>
  IO {
    val htmlDoc = toHtml(inter, codeFile.mkString)
    val pdf = htmlToPdf(htmlDoc)
    merger.addSource(new ByteArrayInputStream(pdf))
  }
)
 */