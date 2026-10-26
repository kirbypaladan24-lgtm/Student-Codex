$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.example.studentcodex" 2>&1 | Select-Object -First 4
& $adb shell "pm list packages -d" 2>&1 | Select-String studentcodex
& $adb shell "pm list packages -e" 2>&1 | Select-String studentcodex
& $adb shell "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 5