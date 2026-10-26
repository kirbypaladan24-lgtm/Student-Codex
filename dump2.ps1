$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "uiautomator dump /sdcard/ui_a.xml" 2>&1 | Select-Object -First 1
& $adb shell "ls -la /sdcard/ui_a.xml" 2>&1 | Select-Object -First 1
& $adb pull /sdcard/ui_a.xml "C:\Users\user\AndroidStudioProjects\StudentCodex\ui_a.xml" 2>&1 | Select-Object -First 1