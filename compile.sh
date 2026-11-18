#!/usr/bin/env bash
set -e

if ! command -v javac >/dev/null 2>&1; then
  for candidate in /usr/lib/jvm/java-17-openjdk-*/bin /usr/lib/jvm/default-java/bin; do
    if [ -x "$candidate/javac" ]; then
      export PATH="$candidate:$PATH"
      break
    fi
  done
fi

if ! command -v javac >/dev/null 2>&1; then
  if [ "$(id -u)" -eq 0 ] && command -v apt-get >/dev/null 2>&1; then
    echo "JDK not found on PATH; provisioning openjdk-17-jdk-headless..."
    apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends openjdk-17-jdk-headless
  else
    echo "Error: javac not found. Please install JDK 17+ and ensure javac is on PATH." >&2
    exit 1
  fi
fi

echo "Compiling Online Stock Trading Platform (Java 17+)..."
rm -rf out
mkdir -p out
javac -cp "lib/*:out" -d out $(find src -name "*.java")
echo "Compilation successful! Output classes generated in ./out"
