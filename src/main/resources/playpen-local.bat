@echo off
java -Dlog4j.configurationFile=logging-local.xml -Xmx512M -Xms512M -jar "%~dp0${project.build.finalName}.jar" local %*