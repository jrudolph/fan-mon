val scalaV = "2.13.3"
val scalaTestV = "3.2.1"

libraryDependencies ++= Seq(
  "net.java.dev.jna" % "jna" % "5.6.0",
  "org.graalvm.nativeimage" % "svm" % "20.1.0" % "provided",
  "org.scalatest" %% "scalatest" % scalaTestV % "test"
)

scalaVersion := scalaV

// docs

enablePlugins(ParadoxMaterialThemePlugin)

paradoxMaterialTheme in Compile := {
  ParadoxMaterialTheme()
    // choose from https://jonas.github.io/paradox-material-theme/getting-started.html#changing-the-color-palette
    .withColor("light-green", "amber")
    // choose from https://jonas.github.io/paradox-material-theme/getting-started.html#adding-a-logo
    .withLogoIcon("cloud")
    .withCopyright("Copyleft © Johannes Rudolph")
    .withRepository(uri("https://github.com/jrudolph/xyz"))
    .withSocial(
      uri("https://github.com/jrudolph"),
      uri("https://twitter.com/virtualvoid")
    )
}

paradoxProperties ++= Map(
  "github.base_url" -> (paradoxMaterialTheme in Compile).value.properties.getOrElse("repo", "")
)