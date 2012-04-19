name := "SoundProcesses"

version := "0.34-SNAPSHOT"

organization := "de.sciss"

homepage := Some( url( "https://github.com/Sciss/SoundProcesses" ))

description := "A ScalaCollider extension for creating and managing sound processes"

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "0.34-SNAPSHOT",
   "org.scala-tools" %% "scala-stm" % "0.5"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )
