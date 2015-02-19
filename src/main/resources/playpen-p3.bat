@echo off
java -Dlog4j.configurationFile=logging-p3.xml -jar "%~dp0${project.build.finalName}.jar" p3 %*