$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "pm list packages | grep studentcodex" 2>&1 | Select-Object -First 2
& $adb shell "dumpsys package com.example.studentcodex | grep -E 'installed=|enabled=|stopped=' | head -2" 2>&1