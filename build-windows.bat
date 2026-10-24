@echo off
rem Builds Unofficial Backport Voxy (Minecraft 1.20.1 Fabric) and puts the jar in the "output" folder
setlocal
cd /d "%~dp0"

echo.
echo  Unofficial Backport Voxy - build for Minecraft 1.20.1 Fabric
echo  The first build downloads everything it needs and takes about 5 minutes.
echo.

where java >nul 2>nul
if errorlevel 1 if not defined JAVA_HOME (
    echo  Java was not found. Install Java 21 first:
    echo  https://adoptium.net/temurin/releases/?version=21
    echo  ^(take the .msi installer and tick "Set JAVA_HOME"^)
    echo.
    pause
    exit /b 1
)

call "%~dp0gradlew.bat" :1.20.1-fabric:build -x test
if errorlevel 1 (
    echo.
    echo  The build failed, see the messages above.
    pause
    exit /b 1
)

if not exist output mkdir output
del /q output\*.jar >nul 2>nul
copy /y "versions\1.20.1-fabric\build\libs\voxy-*-fabric.jar" output\ >nul

echo.
echo  Done! The jar is in the "output" folder:
dir /b output\*.jar
echo  Put it in the "mods" folder of your game AND of your server.
echo.
start "" explorer output
pause
