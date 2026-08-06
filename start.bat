@echo off
title Online Homework System

:: ============================================
::  在线作业系统 一键启动（v2.1）
::  自动检测 JDK 17 与 Maven（内置 tools\maven 优先，其次系统 mvn）
:: ============================================

:: ---------- 1. 检测 JDK 17 ----------
if not defined JAVA_HOME (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%d"
)
if not defined JAVA_HOME (
    for /d %%d in ("C:\Program Files\Java\jdk-17*") do set "JAVA_HOME=%%d"
)
if not defined JAVA_HOME (
    echo [ERROR] 未检测到 JDK 17，请先安装: https://adoptium.net/temurin/releases/?version=17
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

:: ---------- 2. 检测 Maven（内置 tools\maven 优先，其次系统 mvn）----------
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
        echo [ERROR] 未找到 Maven（内置 tools\maven 缺失，且系统环境无 mvn）
        pause
        exit /b 1
    )
    echo [OK] Maven: 使用系统环境 mvn
)

:: ---------- 3. 清理旧的 H2 数据文件（内存模式，可安全删除）----------
if exist "%~dp0data" rd /s /q "%~dp0data" 2>nul

:: ---------- 4. 启动 ----------
echo.
echo ========================================
echo   在线作业系统启动中...
echo   浏览器打开: http://localhost:8080
echo   停止: 关闭本窗口 / Ctrl+C
echo ========================================
echo.
cd /d "%~dp0"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8"
call mvn spring-boot:run -s .mvn\settings.xml
pause
