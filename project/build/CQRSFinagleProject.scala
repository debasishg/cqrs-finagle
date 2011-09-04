import sbt._

class CQRSFinagleProject(info: ProjectInfo) extends DefaultProject(info) {
  val scalaToolsSnapshots = "Scala-Tools Maven2 Snapshot Repository" at "http://scala-tools.org/repo-snapshots"
  val scalaToolsReleases = "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases"
  val twitterRepo = "Twitter Repository" at "http://maven.twttr.com"

  val scalatest = "org.scalatest" % "scalatest_2.9.0-1" % "1.6.1" % "test"
  val twitterUtil = "com.twitter" % "util" % "1.11.4"
  val twitterFinagleCore = "com.twitter" % "finagle-core" % "1.9.0"
  val scalazDep = "org.scalaz" %% "scalaz-core" % "6.0.3"

  val junit = "junit" % "junit" % "4.8.1"

  override def packageSrcJar = defaultJarPath("-sources.jar")
  lazy val sourceArtifact = Artifact.sources(artifactID)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageSrc)

  override def managedStyle = ManagedStyle.Maven
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  lazy val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
//  lazy val publishTo = Resolver.file("Local Test Repository", Path fileProperty "java.io.tmpdir" asFile)
}
