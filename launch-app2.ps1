$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -W -n "com.example.studentcodex/com.example.studentcodex.MainActivity"
Start-Sleep -Seconds 10
& $adb shell "dumpsys window | grep -E 'mCurrentFocus' | head -1"