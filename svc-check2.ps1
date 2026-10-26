$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "service list | grep -E 'package|activity' | head -4"
& $adb shell "pm list packages | grep studentcodex"