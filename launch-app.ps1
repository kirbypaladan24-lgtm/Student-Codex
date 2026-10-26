$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -W -n "com.example.studentcodex/com.example.studentcodex.MainActivity"
Start-Sleep -Seconds 8
& $adb shell "dumpsys window | grep -E 'mCurrentFocus' | head -1"
& $adb exec-out screencap -p /sdcard/e2e_1.png
& $adb pull /sdcard/e2e_1.png "C:\Users\user\AndroidStudioProjects\StudentCodex\e2e_1.png" | Out-Null
Write-Output "screenshot saved"