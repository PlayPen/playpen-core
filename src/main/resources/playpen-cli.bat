@echo off
java -Dlog4j.configurationFile=logging-cli.xml -jar "%~dp0${project.build.finalName}.jar" cli %*