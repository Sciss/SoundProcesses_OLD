import xml._
import sbt.{ FileUtilities => FU, _}

/**
 *    @version 0.15, 11-Aug-10
 */
class SoundProcessesProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val scalaCollider    = "de.sciss" %% "scalacollider" % "0.24"
   val scalaSTM         = "org.scala-tools" %% "scala-stm" % "0.3"
}