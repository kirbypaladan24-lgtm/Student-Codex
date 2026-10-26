$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "cmd package set-enabled-setting com.example.studentcodex enabled" 2>&1
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 8
Start-Sleep -Seconds 12
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1