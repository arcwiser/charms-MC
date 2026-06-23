@echo off
setlocal enabledelayedexpansion

echo [1/4] Checking for Java...
where java >nul 2>&1
if errorlevel 1 (
    echo Java not found. Trying to install Temurin JDK 25...
    winget install --id EclipseAdoptium.Temurin.25.JDK -e --silent
    if errorlevel 1 (
        echo Failed to install Java automatically.
        echo Install Java 25 manually from https://adoptium.net/temurin/releases/?version=25
        exit /b 1
    )
)

echo [2/4] Checking Java version...
for /f "tokens=2 delims==" %%A in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%A"
echo Detected Java: !JAVA_VER!

echo [3/4] Installing Gradle locally...
set "GRADLE_VERSION=9.6.0"
set "GRADLE_DIR=C:\Gradle\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "C:\Gradle" mkdir "C:\Gradle"

powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
if errorlevel 1 (
    echo Failed to download Gradle.
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath 'C:\Gradle' -Force"
if errorlevel 1 (
    echo Failed to extract Gradle.
    exit /b 1
)

set "PATH=%GRADLE_DIR%\bin;%PATH%"
setx PATH "%PATH%" >nul

echo [4/4] Building the plugin...
call "%GRADLE_DIR%\bin\gradle.bat" build
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo Done. Build finished successfully.
endlocal
