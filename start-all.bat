@echo off
echo Starting Fall Detection System...

:: Start Backend with Telegram
echo Starting Backend...
start "" "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -NoExit -File "%~dp0start-backend.ps1"

:: Start Frontend Server
echo Starting Frontend Server...
start "" "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -NoExit -Command "cd '%~dp0frontend' ; python -m http.server 5500"

echo Waiting for servers to start...
timeout /t 10 >nul

:: Start ngrok Tunnels
echo Starting ngrok Tunnels...
start "" "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -NoExit -Command "cd '%~dp0' ; ngrok http 5500"
start "" "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -NoExit -Command "cd '%~dp0' ; ngrok http 8081"

echo.
echo ===================================
echo System is starting up...
echo 1. Backend: http://localhost:8081
if exist "C:\Windows\System32\curl.exe" (
    timeout /t 2 >nul
    curl -s http://localhost:5500 >nul && (
        echo 2. Frontend: http://localhost:5500
    ) || echo 2. Frontend: Starting... (refresh in browser)
) else (
    echo 2. Frontend: http://localhost:5500 (check if running)
)
echo.
echo Note: It may take a moment for all services to start.
echo Check the PowerShell windows for status and ngrok URLs.
echo ===================================

timeout /t 2 >nul
start "" "http://localhost:5500"
