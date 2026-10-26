$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "input keyevent KEYCODE_WAKEUP" 2>&1 | Out-Null
& $adb shell "am start -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 4
Start-Sleep -Seconds 8
& $adb shell "dumpsys activity top" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\top2.txt"
Get-Content "$env:TEMP\top2.txt" -TotalCount 3