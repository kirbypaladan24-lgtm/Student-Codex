$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# Try a cold reboot of the framework by uninstall + reinstall
& $adb uninstall com.example.studentcodex 2>&1 | Select-Object -First 2
Start-Sleep -Seconds 4
& $adb install "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk" 2>&1 | Select-Object -First 3
Start-Sleep -Seconds 6
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 8