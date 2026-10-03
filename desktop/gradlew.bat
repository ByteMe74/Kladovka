@echo off
REM Gradle wrapper script for Windows
set GRADLE_USER_HOME=%GRADLE_USER_HOME%~\gradle

if exist "%GRADLE_USER_HOME%\wrapper\gradle-wrapper.jar" (
    call "%GRADLE_USER_HOME%\wrapper\gradle-wrapper.jar" %*
) else (
    echo Gradle not found. Please install Gradle 8.10 or later.
    exit /b 1
)