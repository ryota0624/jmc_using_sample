name := "fun-jmc"

version := "0.1"

scalaVersion := "2.13.4"

resolvers += Classpaths.typesafeReleases

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

enablePlugins(ScalatraPlugin)

val ScalatraVersion = "2.7.0"

dockerExposedPorts ++= Seq(8080, 5005, 7091)
dockerBaseImage := "openjdk:11-jdk"
fork in run := true

val Http4sVersion = "0.21.11"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.28.v20200408" % "container",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
  "org.scalatra" %% "scalatra-json" % ScalatraVersion,
  "org.json4s" %% "json4s-jackson" % "3.6.10"
)
javaOptions in Universal ++= Seq(
  // -J params will be added as jvm parameters

  // IntelliJ debug
  "-J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
  // JMC
  "-J-Dcom.sun.management.jmxremote.rmi.port=7091",
  "-J-Dcom.sun.management.jmxremote.port=7091",
  "-J-Djava.rmi.server.hostname=127.0.0.1",
  "-J-Dcom.sun.management.jmxremote=true",
  "-J-Dcom.sun.management.jmxremote.authenticate=false",
  "-J-Dcom.sun.management.jmxremote.ssl=false"
)
