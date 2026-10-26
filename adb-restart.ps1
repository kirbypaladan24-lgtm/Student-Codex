$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb kill-server
Start-Sleep -Seconds 3
& $adb start-server
Start-Sleep -Seconds 3
& $adb devices
& $adb install -r "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -W -n com.example.studentcodex/.MainActivity