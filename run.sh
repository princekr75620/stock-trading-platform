#!/usr/bin/env bash
set -e
if [ ! -d "out" ] || [ -z "$(ls -A out 2>/dev/null)" ]; then
    echo "Compiled classes not found. Compiling first..."
    ./compile.sh
fi
echo "Starting Online Stock Trading Platform Console..."
java -cp out main.Main
