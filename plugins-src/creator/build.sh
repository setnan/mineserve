#!/bin/sh
# Builds Creator.jar with a JDK in Docker against the paper-api in the server's libraries/ and puts it in data/plugins/.
set -e
cd "$(dirname "$0")"
L=../../data/libraries
CP=$(find $L -name "paper-api-26.3*.jar" -o -name "adventure-api-*.jar" -o -name "adventure-key-*.jar" -o -name "bungeecord-chat-*.jar" -o -name "brigadier-*.jar" -o -name "annotations-*.jar" -o -name "jspecify-*.jar" -o -name "examination-*.jar" -o -name "adventure-text-*.jar" -o -name "guava-*.jar" -o -name "gson-*.jar" -o -name "joml-*.jar" | tr '\n' ':' | sed "s#$L#/plib#g")
docker run --rm --entrypoint "" -v "$PWD":/src -v "$(cd $L && pwd)":/plib:ro -w /src eclipse-temurin:25-jdk /bin/sh -c \
  "rm -rf out && mkdir out && javac -encoding UTF-8 --release 25 -proc:none -cp '$CP' -d out src/dev/etnan/creator/*.java && cp plugin.yml out/ && cp -r supers out/ && cd out && jar cf ../Creator.jar ."
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
