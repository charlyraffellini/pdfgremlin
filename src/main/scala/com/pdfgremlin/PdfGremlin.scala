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

  /***
   * Supported Lexers https://pygments.org/languages/
   *
   * @param interpreter
   * @param lang
   * @tparam T
   * @return
   */
  def toHtml[T <: Lang : scala.reflect.ClassTag](interpreter: PythonInterpreter, lang: T): Html = {
    val codeVarName = "code"
    val resultValName = "result"

    println(s"Converting to html ${lang.path}")

    interpreter.set(codeVarName, lang.txt)

    val (lexerPackage, lexer) = lang match {
      case _: Java => ("pygments.lexers.jvm", "JavaLexer")
      case _: Markdown => ("pygments.lexers.markup", "MarkdownLexer")
      case _: Python => ("pygments.lexers.python", "PythonLexer")
      case _: Scala => ("pygments.lexers.jvm", "ScalaLexer")
      case _ => throw new IllegalArgumentException(s"Lexer not supported for language ${implicitly[scala.reflect.ClassTag[T]].toString()}")
    }

    val interpreted = Try {
      interpreter.exec(
        s"""
          |import sys
          |sys.setrecursionlimit(8000)
          |from pygments import highlight
          |from $lexerPackage import $lexer
          |from pygments.formatters import HtmlFormatter
          |from pygments.styles import get_style_by_name
          |$resultValName = highlight($codeVarName, $lexer(), HtmlFormatter(full=True,style='xcode'))
          |""".stripMargin
      )
      interpreter.get(resultValName, classOf[String])
    }.toEither

    val html = interpreted match {
      case Left(value) => {
        println(s"There was an error coloring ${lang.path}. Will continue without coloring: ${value.getMessage}")
        s"""
           |<html>
           |  <body>
           |    <div class="title">
           |      ${lang.txt}
           |    </div>
           |  </body>
           |</html>
           |
           |""".stripMargin
      }
      case Right(value) => value
    }

    Html(html, lang.path)
  }

//  def mdToHtml(markdown: String): String = {
//    import org.commonmark.node._
//    import org.commonmark.parser.Parser
//    import org.commonmark.renderer.html.HtmlRenderer
//
//    val parser: Parser = Parser.builder().build()
//    val document: Node = parser.parse(markdown)
//    val renderer: HtmlRenderer = HtmlRenderer.builder().build()
//    renderer.render(document)
//  }

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

  def mergeFile(merger: PDFMergerUtility)(html: Html): IO[Unit] = {
    IO.println("Merging " + html.file) >>
    IO(merger.addSource(new ByteArrayInputStream(titleToPdf(html.file)))) >>
    IO(merger.addSource(new ByteArrayInputStream(htmlToPdf(html.txt))))
  }

//  def toHtml(path: Path): IO[String] =
//    file(path.toAbsolutePath.toString).use(buffer => IO(mdToHtml(buffer.mkString)))

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

  def toLang[T <: Lang : Maker](path: File): IO[T] =
    file(path.getAbsolutePath).use(b => IO(b.mkString)).map(t => implicitly[Maker[T]].instance(t, path))

  import com.pdfgremlin.LangInstances._
  def program(folderPath: String, outputFile: String) = (
    for {
      inter <- pynterpreter()
      pdfFile <- fileOutputStream(s"$outputFile${gitRev(folderPath).map("." + _).getOrElse("")}.pdf")
      merger <- pdfMerger(pdfFile)
    } yield (inter,merger)
  ).use { case (inter, merger) => {
      for {
        sFiles <- files(folderPath, ".scala" :: ".java" :: Nil)
        pyFiles <- files(folderPath, ".py" :: Nil)
        mdFiles <- files(folderPath, ".md" :: Nil)
        scalas <- sFiles.traverse(f => toLang[Scala](f.toFile))
        pys <- pyFiles.traverse(f => toLang[Python](f.toFile))
        mds <- mdFiles.traverse(f => toLang[Markdown](f.toFile))
        sHtmls <- scalas.traverse(l => IO(toHtml(inter, l)))
        pyHtmls <- pys.traverse(l => IO(toHtml(inter, l)))
        mdHtmls <- mds.traverse(l => IO(toHtml(inter, l)))
        _ <- IO.println(pyFiles ++ sFiles ++ mdFiles)
        v <- (mdHtmls ++ sHtmls ++ pyHtmls).traverse(mergeFile(merger))
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


sealed trait Lang {
  def txt: String
  def path: String
}
case class Scala(txt: String, path: String) extends Lang
case class Markdown(txt: String, path: String) extends Lang
case class Python(txt: String, path: String) extends Lang
case class Java(txt: String, path: String) extends Lang

object LangInstances {
  implicit lazy val scalaMakerInstance: Maker[Scala] = (txt: String, file: java.io.File) => Scala(txt, file.getAbsolutePath)

  implicit lazy val pythonMakerInstance: Maker[Python] = (txt: String, file: java.io.File) => Python(txt, file.getAbsolutePath)

  implicit lazy val markdownMakerInstance: Maker[Markdown] = (txt: String, file: java.io.File) => Markdown(txt, file.getAbsolutePath)
}

abstract class Maker[+T <: Lang] {
  def instance(txt: String, file: java.io.File): T
}

case class Html(txt: String, file: String)
