$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 3
& $adb shell "dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity' | head -3" 2>&1