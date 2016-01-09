#!/bin/sh
rm -rf src/main/java/io/playpen/core/protocol
protoc -Isrc/main/proto --java_out=src/main/java src/main/proto/*.proto