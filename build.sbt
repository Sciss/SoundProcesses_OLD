name := "SoundProcesses"

version := "0.35.0"

organization := "de.sciss"

homepage := Some( url( "https://github.com/Sciss/SoundProcesses_OLD" ))

description := "A ScalaCollider extension for creating and managing sound processes"

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.10.0"

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "1.3.1+",
   "org.scala-stm" %% "scala-stm" % "0.7"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

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

// // ---- ls.implicit.ly ----
//
// seq( lsSettings :_* )
//
// (LsKeys.tags in LsKeys.lsync) := Seq( "sound-synthesis", "stm", "sound", "music", "supercollider" )
//
// (LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )
//
// (LsKeys.ghRepo in LsKeys.lsync) := Some( "SoundProcesses" )
//
// // bug in ls -- doesn't find the licenses from global scope
// (licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
