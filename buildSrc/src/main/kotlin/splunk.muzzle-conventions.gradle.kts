// a separate, optional muzzle conventions file is used because some projects (tomee) fail with a mysterious
// "java.util.zip.ZipException: zip END header not found" error when muzzle is turned on everywhere by default

plugins {
  id("splunk.java-conventions")

  id("io.opentelemetry.instrumentation.muzzle-generation")
  id("io.opentelemetry.instrumentation.muzzle-check")
}

dependencies {
  add("muzzleTooling", platform(project(":dependencyManagement")))
  add("muzzleBootstrap", platform(project(":dependencyManagement")))
  add("codegen", platform(project(":dependencyManagement")))
  // dependencies needed to make muzzle-check work
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleBootstrap", project(":bootstrap"))
  add("muzzleBootstrap", "io.opentelemetry:opentelemetry-api")
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-muzzle")
}
