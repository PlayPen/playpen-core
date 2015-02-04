@echo off
java -Dlog4j.configurationFile=logging.xml -jar "%~dp0${project.build.finalName}.jar" %*