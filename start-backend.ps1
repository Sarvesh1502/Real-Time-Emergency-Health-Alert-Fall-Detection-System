# Set Telegram environment variables
$env:TELEGRAM_BOT_TOKEN = "YOUR_BOT_TOKEN"
$env:TELEGRAM_CHAT_ID = "5809650172"  # Your chat ID
$env:USE_TELEGRAM = "true"

# Start the backend
Set-Location "$PSScriptRoot\backend"
& "C:\Users\Sarvesh B\maven\apache-maven-3.9.9\bin\mvn.cmd" -q spring-boot:run
