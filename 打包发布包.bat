@echo off
setlocal
chcp 437 >nul
REM ============================================================
REM  XuanJi Bot - Release Package Builder (Windows)
REM  Output: release\xuanji-dist\  (portable, no Java needed)
REM  Usage: double-click this script (JDK 21+ required on build machine only)
REM  NOTE: keep this file pure ASCII to avoid cmd encoding issues.
REM ============================================================

set "PROJ=%~dp0"
cd /d "%PROJ%"

REM ---- environment check: auto-detect JDK ----
REM 1) use JAVA_HOME if it points to a real JDK
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" goto :jdk_ok
REM 2) probe common install locations
set "JDK_HINT="
if exist "D:\Program\Java\jdk-25\bin\java.exe" set "JDK_HINT=D:\Program\Java\jdk-25"
if "%JDK_HINT%"=="" if exist "D:\Program\Java\jdk-21\bin\java.exe" set "JDK_HINT=D:\Program\Java\jdk-21"
if "%JDK_HINT%"=="" if exist "C:\Program Files\Java\jdk-25\bin\java.exe" set "JDK_HINT=C:\Program Files\Java\jdk-25"
if "%JDK_HINT%"=="" if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set "JDK_HINT=C:\Program Files\Java\jdk-21"
if "%JDK_HINT%"=="" for /d %%d in ("C:\Program Files\Java\*") do if exist "%%d\bin\java.exe" set "JDK_HINT=%%d"
if "%JDK_HINT%"=="" for /d %%d in ("C:\Program Files\Eclipse Adoptium\*") do if exist "%%d\bin\java.exe" set "JDK_HINT=%%d"
if "%JDK_HINT%"=="" for /d %%d in ("D:\Program\Java\*") do if exist "%%d\bin\java.exe" set "JDK_HINT=%%d"
if not "%JDK_HINT%"=="" (
  echo NOTE: JAVA_HOME not usable ^(%JAVA_HOME%^), auto-detected JDK: %JDK_HINT%
  set "JAVA_HOME=%JDK_HINT%"
  goto :jdk_ok
)
echo ERROR: No JDK found. Set JAVA_HOME to your JDK 21+ dir, e.g.:
echo   set JAVA_HOME=D:\Program\Java\jdk-25
pause
exit /b 1
:jdk_ok
echo JDK: %JAVA_HOME% ^(%JAVA_HOME%\bin\java.exe^)

where mvn >nul 2>nul
if errorlevel 1 (
  echo ERROR: mvn not found in PATH. Add Maven bin to PATH, e.g.:
  echo   set PATH=D:\Program\Maven\apache-maven-3.9.11\bin;%%PATH%%
  pause
  exit /b 1
)

echo [1/5] Building all modules (mvn clean package) ...
call mvn -o clean package -DskipTests
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Check output above.
  echo Need: Maven in PATH or M2_HOME set; JAVA_HOME = JDK 21+.
  pause
  exit /b 1
)

set "JAR=xuanji-starter\target\xuanji-starter-1.0.0-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo ERROR: %JAR% not found.
  pause
  exit /b 1
)

echo [2/5] Analyzing JDK module deps (jdeps) ...
"%JAVA_HOME%\bin\jdeps" --ignore-missing-deps --print-module-deps "%JAR%" > _mods.txt 2>nul
set /p MODS=<_mods.txt
if "%MODS%"=="java.base" set "MODS="
if "%MODS%"=="" set "MODS=java.base,java.logging,java.naming,java.management,java.security.jgss,java.instrument,java.sql,java.xml,jdk.unsupported,java.net.http,java.desktop"
echo      modules: %MODS%

echo [3/5] jlink minimal runtime (approx 60MB) ...
if exist "release\dist\runtime" rmdir /s /q "release\dist\runtime"
"%JAVA_HOME%\bin\jlink" --add-modules %MODS% --strip-debug --no-header-files --no-man-pages --output "release\dist\runtime"
if errorlevel 1 (
  echo.
  echo JLINK FAILED. Check output above.
  pause
  exit /b 1
)

echo [4/5] Assembling dist folder ...
if exist "release\dist\plugins" rmdir /s /q "release\dist\plugins"
mkdir "release\dist\plugins" 2>nul
copy /y "%JAR%" "release\dist\xuanji-bot.jar" >nul
if exist "plugins\README.txt" copy /y "plugins\README.txt" "release\dist\plugins\README.txt" >nul
if exist "xuanji-plugin-demo\target\xuanji-plugin-demo-1.0.0-SNAPSHOT.jar" (
  copy /y "xuanji-plugin-demo\target\xuanji-plugin-demo-1.0.0-SNAPSHOT.jar" "release\dist\plugins\" >nul
)
if exist "USAGE-dist.txt" copy /y "USAGE-dist.txt" "release\dist\USAGE.txt" >nul

echo [5/5] Generating launcher scripts ...
call :write_win_script
call :write_sh_script

echo.
echo ============================================================
echo  DONE! Package at: release\dist\
echo  Zip the folder and ship it. User double-clicks:
echo    Windows: start.bat     Linux/macOS: ./start.sh
echo  No Java install needed.
echo ============================================================
pause
exit /b 0

:write_win_script
(
echo @echo off
echo chcp 65001 ^>nul
echo cd /d "%%~dp0"
echo echo Starting XuanJi Bot ... open: http://localhost:8668/xuanji/console/
echo start "" "http://localhost:8668/xuanji/console/"
echo "%%~dp0runtime\bin\java.exe" -jar "%%~dp0xuanji-bot.jar"
echo pause
) > "release\dist\start.bat"
echo      start.bat generated
exit /b 0

:write_sh_script
(
echo #!/usr/bin/env bash
echo cd "$(dirname "$0")"
echo echo "Starting XuanJi Bot ... open: http://localhost:8668/xuanji/console/"
echo "$$(pwd)/runtime/bin/java" -jar "$(pwd)/xuanji-bot.jar" "$@"
) > "release\dist\start.sh"
echo      start.sh generated
exit /b 0
