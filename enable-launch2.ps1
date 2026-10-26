$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "pm enable com.example.studentcodex" 2>&1 | Select-Object -First 1
Start-Sleep -Seconds 20
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 8
Start-Sleep -Seconds 15
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2