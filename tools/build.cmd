@echo off
REM Build helper for the CinemaHQ replica (Windows).
setlocal
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Java\jdk-27.0.1"
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=C:\Users\abuza\AppData\Local\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
cd /d "%~dp0.."
if "%~1"=="" (
  call gradlew.bat :app:assembleDebug --no-daemon
) else (
  call gradlew.bat %* --no-daemon
)
