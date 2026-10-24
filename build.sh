#!/bin/sh
# Builds Unofficial Backport Voxy (Minecraft 1.20.1 Fabric) and puts the jar in the "output" folder
set -e
cd "$(dirname "$0")"

echo "Unofficial Backport Voxy - build for Minecraft 1.20.1 Fabric"
echo "The first build downloads everything it needs and takes about 5 minutes."

if ! command -v java >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
    echo "Java was not found. Install Java 21 first: https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi

chmod +x gradlew
./gradlew :1.20.1-fabric:build -x test

mkdir -p output
rm -f output/*.jar
for jar in versions/1.20.1-fabric/build/libs/voxy-*-fabric.jar; do
    cp "$jar" output/
done

echo "Done! The jar is in the output folder:"
ls output/*.jar
echo "Put it in the mods folder of your game AND of your server."
