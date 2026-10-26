$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "screencap -p /sdcard/shot1.png" 2>&1 | Select-Object -First 2
& $adb pull /sdcard/shot1.png "C:\Users\user\AndroidStudioProjects\StudentCodex\shot1.png" 2>&1 | Select-Object -First 1
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\shot1.png" -ErrorAction SilentlyContinue).Length
Write-Output "shot1 size: $size"
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1