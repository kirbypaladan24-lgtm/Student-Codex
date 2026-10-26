$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb wait-for-device
& $adb install -r -d "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk"
& $adb shell am start -W -n com.example.studentcodex/.MainActivity