package com.pdfgremlin
import com.pdfgremlin.LangInstances._

class LangSpec extends munit.FunSuite {
  test("can instanciate a maker for scala") {
    val actual = implicitly[Maker[Scala]].instance("import com.carlos.gremlins", new java.io.File("."))
    assertEquals(actual.txt, "import com.carlos.gremlins")
  }
}