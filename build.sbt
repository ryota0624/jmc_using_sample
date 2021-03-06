import sbt.Keys.javaOptions

version := "0.1"

scalaVersion := "2.13.4"

resolvers += Classpaths.typesafeReleases

val AkkaVersion = "2.6.8"
val AkkaHttpVersion = "10.2.1"

val akkaLibraries = Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion
)

val jvmDebugOptions = Seq(
  // IntelliJ debug
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
  // JMC
  "-Dcom.sun.management.jmxremote.rmi.port=7091",
  "-Dcom.sun.management.jmxremote.port=7091",
  "-Djava.rmi.server.hostname=127.0.0.1",
  "-Dcom.sun.management.jmxremote=true",
  "-Dcom.sun.management.jmxremote.authenticate=false",
  "-Dcom.sun.management.jmxremote.ssl=false"
)

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
    libraryDependencies ++= akkaLibraries,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime,
      "com.github.pureconfig" %% "pureconfig" % "0.14.0"
    ),
    javaOptions ++= jvmDebugOptions,
    javaOptions in Universal ++= jvmDebugOptions
  )

val client = (project in file("./client"))
  .settings(
    name := "fun-jmc-client",
    libraryDependencies ++= akkaLibraries,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime,
      "com.github.pureconfig" %% "pureconfig" % "0.14.0"
    )
  )
