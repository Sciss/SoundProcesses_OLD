name := "soundprocesses"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

// crossScalaVersions := Seq("2.9.1", "2.9.0", "2.8.1")

// fix sbt issue #85 (https://github.com/harrah/xsbt/issues/85)
// unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist"))

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "0.30-SNAPSHOT",
   "org.scala-tools" %% "scala-stm" % "0.3"
)

retrieveManaged := true