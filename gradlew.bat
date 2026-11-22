@echo off
setlocal
where gradle >nul 2>nul
if %errorlevel%==0 (
  gradle %*
  exit /b %errorlevel%
)
set "ROOT=%~dp0"
set "CACHE=%ROOT%.gradle-bootstrap"
if not exist "%CACHE%\gradle-8.7\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' -OutFile '%CACHE%\gradle.zip'; Expand-Archive -Path '%CACHE%\gradle.zip' -DestinationPath '%CACHE%' -Force"
)
call "%CACHE%\gradle-8.7\bin\gradle.bat" %*
