import xml._
import sbt.{ FileUtilities => FU, _}

/**
 *    @version 0.15, 11-Aug-10
 */
class SoundProcessesProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val scalaCollider    = "de.sciss" %% "scalacollider" % "0.24"
   val scalaSTM         = "org.scala-tools" %% "scala-stm" % "0.3"

   // ---- publishing ----

   override def managedStyle  = ManagedStyle.Maven
   val publishTo              = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
//   val publishTo              = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/snapshots/"

   override def packageDocsJar= defaultJarPath( "-javadoc.jar" )
   override def packageSrcJar = defaultJarPath( "-sources.jar" )
   val sourceArtifact         = Artifact.sources( artifactID )
   val docsArtifact           = Artifact.javadoc( artifactID )
   override def packageToPublishActions = super.packageToPublishActions ++ Seq( packageDocs, packageSrc )

   override def pomExtra =
      <licenses>
        <license>
          <name>GPL v2+</name>
          <url>http://www.gnu.org/licenses/gpl-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>

   Credentials( Path.userHome / ".ivy2" / ".credentials", log )
}