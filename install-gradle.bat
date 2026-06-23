@echo off
setlocal enabledelayedexpansion

set "GRADLE_VERSION=9.6.0"
set "GRADLE_DIR=C:\Gradle\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"
set "DOWNLOADS=%USERPROFILE%\Downloads"
set "LOCAL_ZIP=%DOWNLOADS%\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_DIR%\bin\gradle.bat" (
    echo Gradle already exists at %GRADLE_DIR%
    goto usegradle
)

if not exist "C:\Gradle" mkdir "C:\Gradle"

if exist "%LOCAL_ZIP%" (
    echo Using local ZIP from Downloads...
    copy /Y "%LOCAL_ZIP%" "%GRADLE_ZIP%" >nul
) else (
    echo Local ZIP not found in Downloads. Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
    if errorlevel 1 (
        echo Download failed.
        exit /b 1
    )
)

echo Extracting Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath 'C:\Gradle' -Force"
if errorlevel 1 (
    echo Extraction failed.
    exit /b 1
)

:usegradle
set "PATH=%GRADLE_DIR%\bin;%PATH%"
echo.
echo Gradle installed locally at:
echo %GRADLE_DIR%
echo.
echo Testing Gradle...
"%GRADLE_DIR%\bin\gradle.bat" -v
if errorlevel 1 (
    echo Gradle test failed.
    exit /b 1
)

echo.
echo Build the plugin with:
echo "%GRADLE_DIR%\bin\gradle.bat" build
endlocal
