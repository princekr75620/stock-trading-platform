#!/usr/bin/env bash
set -e
echo "Compiling Online Stock Trading Platform (Java 17+)..."
mkdir -p out
javac -cp "lib/*:out" -d out $(find src -name "*.java")
echo "Compilation successful! Output classes generated in ./out"
