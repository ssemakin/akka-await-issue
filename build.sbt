name := "akka-await-issue"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq (
  "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT",
  "ch.qos.logback" % "logback-classic" % "1.0.13"
)

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
