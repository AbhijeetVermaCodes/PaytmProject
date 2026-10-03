@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Executable for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set "DIRNAME=%~dp0"
if "%DIRNAME%" == "" set "DIRNAME=."
set "MAVEN_CMD=%DIRNAME%.mvn\maven\apache-maven-3.9.6\bin\mvn.cmd"

if exist "%MAVEN_CMD%" (
    "%MAVEN_CMD%" %*
) else (
    mvn %*
)
