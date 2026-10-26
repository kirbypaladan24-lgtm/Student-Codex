$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "uiautomator dump /sdcard/zzz2.xml" 2>&1 | Select-Object -First 2
& $adb shell "ls -la /sdcard/zzz2.xml" 2>&1 | Select-Object -First 2
& $adb pull /sdcard/zzz2.xml "C:\Users\user\AndroidStudioProjects\StudentCodex\zzz2.xml" 2>&1 | Select-Object -First 1
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\zzz2.xml" -ErrorAction SilentlyContinue).Length
Write-Output "dump size: $size"
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1