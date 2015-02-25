#!/bin/sh
rm -rf src/main/java/net/thechunk/playpen/protocol
protoc -Isrc/main/proto --java_out=src/main/java src/main/proto/*.proto