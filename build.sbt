ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "tech.l-zero"

val chiselVersion = "6.7.0"

lazy val lzero_pim = (project in file("."))
  .settings(
    name := "lzero_pim",
    Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala_user",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
  )