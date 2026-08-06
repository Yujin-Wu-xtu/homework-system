@echo off
title Online Homework System

:: Auto-detect JDK 17
if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
    ) else if exist "%JAVA_HOME%" (
        rem use existing
    ) else (
        echo [ERROR] JDK 17 not found!
        echo Please install JDK 17 from: https://adoptium.net/
        echo Then set JAVA_HOME and try again.
        pause
        exit /b 1
    )
)
echo [OK] JAVA_HOME = %JAVA_HOME%

:: Set bundled Maven
set "MAVEN_DIR=%~dp0tools\maven\apache-maven-3.9.9"
if not exist "%MAVEN_DIR%\bin\mvn.cmd" (
    echo [ERROR] Maven not found at %MAVEN_DIR%
    pause
    exit /b 1
)
set "PATH=%MAVEN_DIR%\bin;%PATH%"
echo [OK] Maven ready

:: Clean old H2 data (memory mode, safe to remove)
if exist "%~dp0data" (
    echo [CLEAN] Removing old database files...
    rd /s /q "%~dp0data" 2>nul
)

:: Start
echo.
echo ========================================
echo   Starting Online Homework System...
echo   Open: http://localhost:8080
echo   Stop: Ctrl+C
echo ========================================
echo.

cd /d "%~dp0"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8"
call mvn spring-boot:run -s .mvn\settings.xml

pause
