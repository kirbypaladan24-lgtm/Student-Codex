$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "pm path com.example.studentcodex"
& $adb shell "cmd package resolve-activity --brief com.example.studentcodex | tail -2"
& $adb shell getprop sys.boot_completed
& $adb shell "service list | grep -c package"