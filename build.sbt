name := "soundprocesses"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "0.30-SNAPSHOT",
   "org.scala-tools" %% "scala-stm" % "0.3"
)

retrieveManaged := true
