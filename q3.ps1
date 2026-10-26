$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices 2>&1
& $adb shell getprop sys.boot_completed 2>&1
& $adb shell "service list | grep -c package" 2>&1