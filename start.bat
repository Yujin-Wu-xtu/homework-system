@echo off
title Online Homework System

:: ============================================
::  在线作业系统 一键启动（v2.2）
::  自动探测 JDK 17 与 Maven（tools\maven 优先，其次系统 mvn）
:: ============================================

:: ---------- 1. 探测 JDK 17 ----------
if not defined JAVA_HOME (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%d"
)
if not defined JAVA_HOME (
    for /d %%d in ("C:\Program Files\Java\jdk-17*") do set "JAVA_HOME=%%d"
)
if not defined JAVA_HOME (
    echo [ERROR] 未探测到 JDK 17，请先安装: https://adoptium.net/temurin/releases/?version=17
    echo         然后设置系统环境变量 JAVA_HOME 指向 JDK 17 目录
    pause
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JAVA_HOME 指向的目录无效: %JAVA_HOME%
    pause
    exit /b 1
)
echo [OK] JDK 17: %JAVA_HOME%

:: ---------- 2. 探测 Maven（tools\maven 优先，其次系统 mvn）----------
set "MAVEN_DIR=%~dp0tools\maven\apache-maven-3.9.9"
set "MAVEN_FOUND="
if exist "%MAVEN_DIR%\bin\mvn.cmd" (
    set "MAVEN_FOUND=1"
) else (
    for /d %%d in ("%~dp0tools\maven\apache-maven-*") do (
        if exist "%%d\bin\mvn.cmd" set "MAVEN_DIR=%%d" & set "MAVEN_FOUND=1"
    )
)
if defined MAVEN_FOUND (
    set "PATH=%MAVEN_DIR%\bin;%PATH%"
    echo [OK] Maven: %MAVEN_DIR%
) else (
    where mvn >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] 未找到 Maven，请检查 tools\maven 是否存在或系统已安装 mvn
        pause
        exit /b 1
    )
    echo [OK] Maven: 使用系统 mvn
)

:: ---------- 3. AI 出题代理 ----------
:: DeepSeek API 需外网访问，本机经 Clash 代理（默认 7890）；已设置过则跳过
:: AI 出题功能还需环境变量 DEEPSEEK_API_KEY（请自行配置系统环境变量，勿写入本文件以免泄露）
if not defined HTTPS_PROXY set "HTTPS_PROXY=http://127.0.0.1:7890"
echo [OK] HTTPS_PROXY=%HTTPS_PROXY%

:: ---------- 4. 启动 ----------
echo.
echo ========================================
echo   在线作业系统启动中...
echo   访问地址: http://localhost:8080
echo   停止: 关闭本窗口 / Ctrl+C
echo ========================================
echo.
cd /d "%~dp0"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8"
call mvn spring-boot:run -s .mvn\settings.xml
pause
