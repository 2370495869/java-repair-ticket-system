@echo off
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo JDK 21 or later is required. Install a supported JDK and retry.
  exit /b 1
)
call mvnw.cmd -B -ntp verify
if errorlevel 1 exit /b %errorlevel%
java -jar "target\java-repair-ticket-system.jar" --spring.profiles.active=local %*
