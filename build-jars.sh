#!/bin/sh
# Recompile les deux JAR (Java 17) dans ./jars
set -e
cd "$(dirname "$0")"
for app in produce-pdf consume-pdf; do
  main=$(echo "$app" | sed -E 's/(^|-)([a-z])/\U\2/g')
  tmp=$(mktemp -d)
  javac --release 17 -d "$tmp" "src/$app/$main.java"
  jar --create --file "jars/$app.jar" --main-class "$main" -C "$tmp" .
  rm -rf "$tmp"
done
