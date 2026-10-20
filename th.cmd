@echo off
rem TestHarness CLI launcher:  th --help
rem Builds the CLI on first use (or when the jar is missing), then runs it.
setlocal
set "ROOT=%~dp0"
set "JAR=%ROOT%harness\harness-cli\target\harness-cli-1.1.0.jar"
if not exist "%JAR%" (
  echo Building the harness CLI ^(first run^)... 1>&2
  call "%ROOT%mvnw.cmd" -q -B install -DskipTests -pl harness/harness-cli -am 1>&2 || exit /b 1
)
java -jar "%JAR%" %*
