@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set "HOME=%HOMEDRIVE%%HOMEPATH%")

@REM Execute a user defined script before this one
if not "%MAVEN_SKIP_RC%" == "" goto skipRcPre
@REM check for pre script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_pre.bat" call "%USERPROFILE%\mavenrc_pre.bat" %*
if exist "%USERPROFILE%\mavenrc_pre.cmd" call "%USERPROFILE%\mavenrc_pre.cmd" %*
:skipRcPre

@setlocal

set ERROR_CODE=0

@REM To isolate internal variables from possible post scripts, we use another setlocal
@setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome
for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJCmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJCmd
if exist "%JAVACMD%" goto chkMHome

echo The JAVA_HOME environment variable is not defined, and this specific Java executable could not be found. >&2
echo Please set JAVA_HOME to the 64-bit JDK 17 installation directory. >&2
echo. >&2
echo ERROR: Could not find Java executable. >&2
exit /b 1

:chkMHome
set "MAVEN_HOME=%~dp0\.mvn\wrapper\maven"
if not exist "%MAVEN_HOME%" goto downloadMaven
goto :execute

:downloadMaven
echo Downloading Maven...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%MAVEN_HOME%\maven-wrapper.jar'}"
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%MAVEN_HOME%\maven.zip'; Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%'; Move-Item -Path '%MAVEN_HOME%\apache-maven-*'\* -DestinationPath '%MAVEN_HOME%' -Force}"
if %ERRORLEVEL% NEQ 0 goto error
goto :execute

:execute
set "MAVEN_OPTS=-Xmx1024m"
set "MAVEN_HOME=%MAVEN_HOME:\=/%"
set "MAVEN_CMD=%MAVEN_HOME%/bin/mvn"
if not exist "%MAVEN_CMD%" (
    echo Maven not found, trying system mvn...
    set "MAVEN_CMD=mvn"
)

set "MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%"
if not "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir
set "MAVEN_PROJECTBASEDIR=%~dp0"

:endDetectBaseDir
if exist "%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config" set "MAVEN_OPTS=%MAVEN_OPTS% -Dmaven.repo.local=%MAVEN_PROJECTBASEDIR%\.mvn\repository"
call "%MAVEN_CMD%" %MAVEN_OPTS% -B -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %*
set ERROR_CODE=%ERRORLEVEL%
goto end

:error
set ERROR_CODE=1
echo Maven download failed. Please install Maven manually.
echo Run: winget install Apache.Maven.3
echo Or download from: https://maven.apache.org/download.cgi

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
exit /b %ERROR_CODE%
