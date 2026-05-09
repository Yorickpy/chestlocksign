@echo off
setlocal

cd /d "%~dp0"

set "SERVER_MODS_DIR=..\..\mods"
set "MOD_JAR=build\libs\chest-locksign-0.1.1.jar"

if defined JAVA_HOME (
  set "Path=%JAVA_HOME%\bin;%Path%"
)

call gradlew.bat build
if errorlevel 1 (
  echo.
  echo Build echoue, installation annulee.
  pause
  exit /b 1
)

if not exist "%SERVER_MODS_DIR%" (
  echo Dossier mods serveur introuvable: %SERVER_MODS_DIR%
  pause
  exit /b 1
)

copy /Y "%MOD_JAR%" "%SERVER_MODS_DIR%\"

echo.
echo Mod installe dans %SERVER_MODS_DIR%.
echo Redemarre le serveur pour le charger.
pause
