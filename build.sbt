name := "datachannels-experiment"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.mavenLocal
resolvers += "Jitsi Maven Repository Releases" at "https://github.com/jitsi/jitsi-maven-repository/raw/master/releases/"
resolvers += "Jitsi Maven Repository Snapshots" at "https://github.com/jitsi/jitsi-maven-repository/raw/master/snapshots/"

javaOptions += "-Djava.compiler=NONE"

fork in run := true

val akkaVersion = "2.5.8"

scalaVersion := "2.12.4"

crossScalaVersions := Seq("2.11.12", "2.12.4")

libraryDependencies += guice
libraryDependencies += ws

libraryDependencies += "org.webjars" % "flot" % "0.8.3"
libraryDependencies += "org.webjars" % "bootstrap" % "3.3.6"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += "org.awaitility" % "awaitility" % "3.0.0" % Test

libraryDependencies += "javax.sdp" % "nist-sdp" % "1.0"
libraryDependencies += "net.java.dev.jna" % "jna-platform" % "4.5.1"


libraryDependencies += "com.bitbreeds.webrtc" % "webrtc-peerconnection" % "1.0-SNAPSHOT"
libraryDependencies += "com.bitbreeds.webrtc" % "webrtc-signaling" % "1.0-SNAPSHOT"
libraryDependencies += "org.jitsi" % "jitsi-util" % "2.13.77e7a10" exclude("org.jitsi", "libjitsi")
unmanagedBase := baseDirectory.value / "lib"
