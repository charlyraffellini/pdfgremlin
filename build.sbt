import Dependencies._

ThisBuild / scalaVersion     := "2.12.17"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "pdfgremlin",
    libraryDependencies += munit % Test,
    libraryDependencies += cloudify,
    libraryDependencies += "org.python" % "jython-standalone" % "2.7.2",
    libraryDependencies += "org.pygments" % "pygments" % "2.5.2" % "runtime",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.4.8",
    libraryDependencies += "org.apache.pdfbox" % "pdfbox" % "2.0.27"
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
