import sbt._

class SoundProcessesProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val scalaCollider    = "de.sciss" %% "scalacollider" % "0.30-SNAPSHOT"
   val scalaSTM         = "org.scala-tools" %% "scala-stm" % "0.3"
}