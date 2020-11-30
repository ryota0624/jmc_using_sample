version := "0.1"

scalaVersion := "2.13.4"

resolvers += Classpaths.typesafeReleases

val AkkaVersion = "2.6.8"
val AkkaHttpVersion = "10.2.1"

val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    dockerExposedPorts ++= Seq(8080, 5005, 7091),
    dockerBaseImage := "openjdk:11-jdk",
    fork in run := true,
    connectInput := true
  )
  .settings(
    name := "fun-jmc",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime,
      "com.github.pureconfig" %% "pureconfig" % "0.14.0"
    ),
    javaOptions in Universal ++= Seq(
      // -J params will be added as jvm parameters
      // IntelliJ debug
      "-J-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
      // JMC
      "-J-Dcom.sun.management.jmxremote.rmi.port=7091",
      "-J-Dcom.sun.management.jmxremote.port=7091",
      "-J-Djava.rmi.server.hostname=127.0.0.1",
      "-J-Dcom.sun.management.jmxremote=true",
      "-J-Dcom.sun.management.jmxremote.authenticate=false",
      "-J-Dcom.sun.management.jmxremote.ssl=false"
    )
  )
