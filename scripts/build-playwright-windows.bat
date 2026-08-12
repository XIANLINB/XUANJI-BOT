@echo off
setlocal
chcp 65001 >nul
REM ============================================================
REM  XuanJi Build - Windows package (with Playwright render)
REM  Output:  xuanji-starter\target\xuanji-starter-1.0.0-SNAPSHOT.jar
REM  Usage:   double-click this script (JDK 25+ required)
REM  产物:    slim jar with only Windows Playwright driver
REM ============================================================

set "PROJ=%~dp0.."
cd /d "%PROJ%"

echo [XuanJi] Building Windows package (with Playwright)...

REM ---- detect JDK ----
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" goto jdk_ok
if exist "D:\Program\Java\jdk-25\bin\java.exe" set "JAVA_HOME=D:\Program\Java\jdk-25"
if "%JAVA_HOME%"=="" if exist "D:\Program\Java\jdk-21\bin\java.exe" set "JAVA_HOME=D:\Program\Java\jdk-21"
if "%JAVA_HOME%"=="" for /d %%d in ("C:\Program Files\Java\*") do if exist "%%d\bin\java.exe" set "JAVA_HOME=%%d"
if "%JAVA_HOME%"=="" (
    echo [XuanJi] ERROR: JDK not found. Install JDK 25 or set JAVA_HOME.
    exit /b 1
)
:jdk_ok
echo [XuanJi] JDK: %JAVA_HOME%

REM ---- detect python ----
set "PY=python"
where python >nul 2>nul || (
    echo [XuanJi] ERROR: python not found. Install Python 3 and add to PATH.
    exit /b 1
)

REM ---- maven package (all platforms) ----
echo [XuanJi] Step 1/2: mvn clean package ...
call mvn.cmd -q clean package -DskipTests
if errorlevel 1 (
    echo [XuanJi] ERROR: maven build failed.
    exit /b 1
)

REM ---- slim driver to windows platform ----
set "JAR=xuanji-starter\target\xuanji-starter-1.0.0-SNAPSHOT.jar"
echo [XuanJi] Step 2/2: slimming Playwright driver for Windows...
python scripts\slim-jar.py "%JAR%" --platform win
if errorlevel 1 (
    echo [XuanJi] ERROR: driver slimming failed.
    exit /b 1
)

echo.
echo [XuanJi] BUILD OK!
echo [XuanJi] Jar: %JAR%
echo [XuanJi] Run: java -jar %JAR%
echo.
if /I not "%XJ_NO_PAUSE%"=="1" pause
