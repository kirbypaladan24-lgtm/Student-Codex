$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -n com.example.studentcodex/.MainActivity
Start-Sleep -Seconds 6
& $adb shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2"