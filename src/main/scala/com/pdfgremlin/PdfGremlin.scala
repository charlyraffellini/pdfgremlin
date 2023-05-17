package com.pdfgremlin

import cats.effect.{IO, IOApp}
import cats.effect.kernel.Resource
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.python.util.PythonInterpreter
import cats.implicits._

import java.nio.file.Path
import scala.io.BufferedSource
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

object PdfGremlin extends IOApp.Simple {

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
  def fileOutputStream(filePath: String): Resource[IO, FileOutputStream] = Resource.make(IO {
    val byteOutputStream = new ByteArrayOutputStream
    new FileOutputStream(filePath)
  })(s => IO(s.close()))

  def pdfMerger(outPath: FileOutputStream): Resource[IO, PDFMergerUtility] = Resource.make(IO(new PDFMergerUtility()))(m => IO {
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

  def mdToHtml(markdown: String): String = {
    import org.commonmark.node._
    import org.commonmark.parser.Parser
    import org.commonmark.renderer.html.HtmlRenderer

    val parser: Parser = Parser.builder().build()
    val document: Node = parser.parse(markdown)
    val renderer: HtmlRenderer = HtmlRenderer.builder().build()
    renderer.render(document)
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

  def mergeFile(merger: PDFMergerUtility)(path: Path, htmlDoc: String): IO[Unit] = {
    IO.println("Merging " + path.toAbsolutePath.toString) >>
    IO(merger.addSource(new ByteArrayInputStream(titleToPdf(path.toString)))) >>
    IO(merger.addSource(new ByteArrayInputStream(htmlToPdf(htmlDoc))))
  }

  def toHtml(path: Path): IO[String] =
    file(path.toAbsolutePath.toString).use(buffer => IO(mdToHtml(buffer.mkString)))

  def toHtml(interpreter: PythonInterpreter, path: Path): IO[String] =
    file(path.toAbsolutePath.toString).use(buffer => IO(toHtml(interpreter, buffer.mkString)))

  def files(folder: String, supportedFiles: List[String]): IO[List[Path]] = IO {
    import scala.collection.JavaConverters._

    java.nio.file.Files.find(
      java.nio.file.Paths.get(folder),
      10,
      (p, a) => supportedFiles.count(p.toString.endsWith(_)) == 1 && a.isRegularFile
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

  def program(folderPath: String, outputFile: String) = (
    for {
      inter <- pynterpreter()
      pdfFile <- fileOutputStream(s"$outputFile${gitRev(folderPath).map("." + _).getOrElse("")}.pdf")
      merger <- pdfMerger(pdfFile)
    } yield (inter,merger)
  ).use { case (inter, merger) => {
      for {
        sFiles <- files(folderPath, ".scala" :: ".java" :: Nil)
        mdFiles <- files(folderPath, ".md" :: Nil)
        sHtml <- sFiles.traverse(f => toHtml(inter, f).map(f -> _))
        mdHtml <- mdFiles.traverse(f => toHtml(f).map(f -> _))
        _ <- IO.println(sFiles ++ mdFiles)
        v <- (mdHtml ++ sHtml).traverse(t => mergeFile(merger)(t._1, t._2))
      } yield v
    }
  }

  /***
   * How to run it:
   * 
   *  sbt assembly
   *
   * java -Dpdfgremlin.input.folder=/Users/carlos.raffellini/src/myproject -Dpdfgremlin.output.prefix=com.raffellini.myproject -cp target/scala-2.12/pdfgremlin-assembly-0.1.0-SNAPSHOT.jar com.pdfgremlin.PdfGremlin
   */
  override def run: IO[Unit] = {
    (IO(sys.props.get("pdfgremlin.input.folder").getOrElse(".")), IO(sys.props.get("pdfgremlin.output.prefix").getOrElse("com.pdfgremlin.out")))
      .flatMapN(program).void
  }
}
