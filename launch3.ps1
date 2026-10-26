$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 5
Start-Sleep -Seconds 10
& $adb shell "dumpsys window windows | grep mCurrentFocus"