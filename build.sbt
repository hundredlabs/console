import CommonSettings._
import com.typesafe.sbt.packager.MappingsHelper._
import sbt._
import com.typesafe.sbt.packager.linux._

lazy val `gigahex-ce` = (project in file("."))
  .settings(buildSettings)
  .settings(baseSettings)
  .settings(
    organization := "com.gigahex",
    moduleName := "gigahex-ce"
  )

lazy val buildSettings = Seq(
  scalaVersion := "2.13.3",
  assemblyOutputPath in assembly := file(s"${baseDirectory.value.getAbsolutePath}/target/${moduleName.value}-${scalaVersion.value}.jar"),
  assemblyJarName in assembly := s"${moduleName.value}.jar",
  assemblyMergeStrategy in assembly := {
    case PathList(ps @ _*) if ps.last endsWith ".html"       => MergeStrategy.first
    case "application.conf"                                  => MergeStrategy.concat
    case "unwanted.txt"                                      => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith ".properties" => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith "BUILD"       => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith ".default"    => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith "class"       => MergeStrategy.first
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  scalaModuleInfo := scalaModuleInfo.value.map(_.withOverrideScalaVersion(true)),
  fork in Test := true
)

lazy val projectSettings = baseSettings ++ buildSettings ++ Seq(
  organization := "com.gigahex",
  resolvers ++= Seq("lazylibsodium" at "https://dl.bintray.com/terl/lazysodium-maven", "jcenter" at "https://jcenter.bintray.com"),
  javaOptions ++= Seq(
    "-Dcom.sun.management.jmxremote",
    "-Dcom.sun.management.jmxremote.port=5678",
    "-Dcom.sun.management.jmxremote.local.only=true",
    "-Dcom.sun.management.jmxremote.ssl=false",
    "-Dcom.sun.management.jmxremote.authenticate=false"
  )
)

lazy val sparkProjectSettings = buildSettings ++ Seq(
  organization := "com.gigahex"
)

lazy val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "org.mockito"                % "mockito-core"    % versions.mockito % Test,
    "org.scalacheck"             %% "scalacheck"     % versions.scalaCheck % Test,
    "org.scalatest"              %% "scalatest"      % versions.scalaTest % Test,
    "org.specs2"                 %% "specs2-core"    % versions.specs2 % Test,
    "org.specs2"                 %% "specs2-junit"   % versions.specs2 % Test,
    "org.specs2"                 %% "specs2-mock"    % versions.specs2 % Test,
    "ch.qos.logback"             % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "Atlassian Releases" at "https://maven.atlassian.com/public/"
  ),
  scalaCompilerOptions,
  javacOptions in (Compile, compile) ++= Seq("-source", "11")
)

lazy val scalaCompilerOptions = scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xlint",
  "-Ywarn-unused:imports"
)

lazy val models = (project in file("modules/common"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-models",
    moduleName := "gigahex-models",
    libraryDependencies ++= Seq(
      "org.scalikejdbc"            %% "scalikejdbc"                     % "3.3.5",
    )
  )


lazy val aws = (project in file("modules/aws"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-aws",
    moduleName := "gigahex-aws",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % "1.12.180" withSources (),
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.2"
    )
  ).dependsOn(models)

lazy val cassandra = (project in file("modules/cassandra"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-cassandra",
    moduleName := "gigahex-cassandra",
    libraryDependencies ++= Seq(
      "com.datastax.oss" % "java-driver-core" % "4.14.0"
    )
  ).dependsOn(models)

lazy val cockroachdb = (project in file("modules/cockroachdb"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-cockroachdb",
    moduleName := "gigahex-cockroachdb"
  ).dependsOn(postgres)


lazy val postgres = (project in file("modules/postgres"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-postgres",
    moduleName := "gigahex-postgres",
    libraryDependencies ++= Seq(
      "org.postgresql"             % "postgresql"                       % "42.2.16"
    )
  ).dependsOn(models)

lazy val mysql = (project in file("modules/mysql"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-mysql",
    moduleName := "gigahex-mysql",
    libraryDependencies ++= Seq(
      "mysql" % "mysql-connector-java" % "8.0.27"
    )
  ).dependsOn(models)

lazy val mariadb = (project in file("modules/mariadb"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-mysql",
    moduleName := "gigahex-mysql",
    libraryDependencies ++= Seq(
      "org.mariadb.jdbc" % "mariadb-java-client" % "3.0.3"
    )
  ).dependsOn(models)

lazy val `gigahex-server`  = (project in file("server"))
  .settings(projectSettings)
  .settings(
    name := "gigahex-server",
    moduleName := "gigahex-server",
    maintainer in Linux := "Gigahex Support <support@gigahex.com>",
    packageSummary in Linux := "Gigahex Web Server",
    mappings in Universal ++= directory(baseDirectory.value / "public"),
    mappings in Universal ++= directory(baseDirectory.value / "lib"),
    mappings in Universal ++= directory(baseDirectory.value / "sbin"),
    defaultLinuxInstallLocation := "/opt/gigahex",
    packageDescription := """Gigahex Data Platform Server""",
    linuxStartScriptTemplate in Debian := {
      println((resourceDirectory in Compile).value.toPath.toAbsolutePath.toString)
      ((resourceDirectory in Compile).value / "server-start-template").toURI.toURL
    },
    serverLoading in Debian := Some(ServerLoader.Systemd),
    rpmLicense := Some("AGPL3"),
    libraryDependencies ++= Seq(
      guice,
      caffeine, // or cacheApi
      ws,
      filters,
      "net.jcazevedo"              %% "moultingyaml"                    % "0.4.2",
      "org.jsoup" % "jsoup" % "1.14.3",
      "com.mohiva"                 %% "play-silhouette"                 % versions.silhouette withSources (),
      "com.mohiva"                 %% "play-silhouette-password-bcrypt" % versions.silhouette,
      "com.mohiva"                 %% "play-silhouette-persistence"     % versions.silhouette,
      "com.mohiva"                 %% "play-silhouette-crypto-jca"      % versions.silhouette,
      "com.typesafe.play"          %% "play-mailer"                     % versions.playMailer,
      "com.typesafe.play"          %% "play-mailer-guice"               % versions.playMailer,
      "net.java.dev.jna"           % "jna-platform"                     % "5.9.0",
      "org.postgresql"             % "postgresql"                       % "42.2.16",
      "net.codingwell"             %% "scala-guice"                     % "4.2.5",
      "org.apache.commons"         % "commons-compress"                 % "1.21",
      "org.flywaydb"               %% "flyway-play"                     % versions.flywayPlay,
      "com.goterl.lazycode"        % "lazysodium-java"                  % "4.3.2" excludeAll (ExclusionRule(organization = "org.slf4j")),
      "net.java.dev.jna"           % "jna"                              % "5.5.0",
      "com.iheart"                 %% "ficus"                           % "1.5.1",
      "com.lightbend.akka"         %% "akka-stream-alpakka-file"        % "2.0.1" excludeAll (ExclusionRule(organization = "com.typesafe.akka")),
      "org.scalikejdbc"            %% "scalikejdbc"                     % "3.3.5",
      "org.scalikejdbc"            %% "scalikejdbc-config"              % "3.3.5",
      "org.scalikejdbc"            %% "scalikejdbc-play-initializer"    % "2.7.1-scalikejdbc-3.3",
      "com.auth0"                  % "java-jwt"                         % "3.4.0" exclude ("com.fasterxml.jackson.core", "jackson-databind"),
      "org.apache.kafka"           % "kafka-clients"                    % versions.kafka
    )
  )
  .enablePlugins(PlayScala, DebianPlugin, JavaServerAppPackaging, SystemdPlugin)
  .aggregate(models, aws,postgres, mysql, mariadb, cassandra)
  .dependsOn(models, aws, postgres, mysql, mariadb, cassandra)
