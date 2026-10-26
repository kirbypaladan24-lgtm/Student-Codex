$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$ErrorActionPreference = "Continue"
& $adb shell "monkey -p com.example.studentcodex -c android.intent.category.LAUNCHER 1" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\monkey.txt"
Get-Content "$env:TEMP\monkey.txt" | Select-Object -Last 8
Start-Sleep -Seconds 12
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1