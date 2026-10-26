$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1
Start-Sleep -Seconds 5
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1
& $adb shell "uiautomator dump /sdcard/zzz1.xml" 2>&1 | Select-Object -First 1
& $adb pull /sdcard/zzz1.xml "C:\Users\user\AndroidStudioProjects\StudentCodex\zzz1.xml" 2>&1 | Select-Object -First 1