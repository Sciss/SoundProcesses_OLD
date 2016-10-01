name         := "SoundProcesses"
version      := "0.34"
organization := "de.sciss"
homepage     := Some(url("https://github.com/Sciss/SoundProcesses_OLD"))
description  := "A ScalaCollider extension for creating and managing sound processes"
licenses     := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
   "de.sciss"      %% "scalacollider" % "0.34",
   "org.scala-stm" %% "scala-stm"     % "0.7"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

// ---- publishing ----

publishMavenStyle := true

publishTo :=
   Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/SoundProcesses_OLD.git</url>
  <connection>scm:git:git@github.com:Sciss/SoundProcesses_OLD.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
