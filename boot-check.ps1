$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell getprop sys.boot_completed
& $adb shell getprop init.svc.bootanim