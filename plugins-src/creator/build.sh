#!/bin/sh
# Builds Creator.jar (Java and Kotlin, with Gradle) in Docker against the paper-api in the server's libraries/ and puts it in data/plugins/.
set -e
cd "$(dirname "$0")"
L=../../data/libraries
# Gradle (Java and Kotlin) in a JDK container; the gradle cache lives in the Docker volume creator-gradle.
docker run --rm --entrypoint "" -v "$PWD":/src -v "$(cd $L && pwd)":/plib:ro -v creator-gradle:/root/.gradle -e PLIB=/plib -w /src eclipse-temurin:25-jdk \
  ./gradlew --quiet --console=plain jar
# Never overwrite the jar of a running server: Java reads classes from the open jar as they are first needed, and a
# changed file gives NoClassDefFoundError (Helper$2 and the like). While it runs, the jar goes to plugins/update/,
# which Paper moves into place on the next start.
if docker ps --format '{{.Names}}' | grep -qx mc; then
  mkdir -p ../../data/plugins/update && cp Creator.jar ../../data/plugins/update/Creator.jar
else
  cp Creator.jar ../../data/plugins/Creator.jar
fi
# The plugin used to be called Skaper: two copies would register the same commands. The running server keeps its
# open jar, so moving it aside is safe; Creator migrates the old data folder on its first start.
[ -f ../../data/plugins/Skaper.jar ] && mv ../../data/plugins/Skaper.jar ../../data/plugins/Skaper.jar.old
echo "Creator.jar built (active after the next restart)"
