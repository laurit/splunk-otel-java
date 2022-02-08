import com.google.protobuf.gradle.*

plugins {
  id("java")
  id("java-library")
  id("com.google.protobuf") version "0.8.18"
}

val protobufVersion = "3.19.4"

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.19.4"
  }
}

dependencies {
  api("com.google.protobuf:protobuf-java:$protobufVersion")
  implementation(project(":profiler"))
}
