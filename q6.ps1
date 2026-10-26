$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# try the long-form component with the process-start path
& $adb shell "am start -n com.example.studentcodex/com.example.studentcodex.MainActivity" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\ls.txt"
Get-Content "$env:TEMP\ls.txt" | Select-Object -First 6
Start-Sleep -Seconds 14
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1