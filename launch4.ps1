$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "monkey -p com.example.studentcodex -c android.intent.category.LAUNCHER 1" 2>&1 | Select-Object -First 6
Start-Sleep -Seconds 10
& $adb shell "dumpsys window windows | grep mCurrentFocus"
& $adb shell "uiautomator dump /sdcard/zz1.xml" 
& $adb shell "cat /sdcard/zz1.xml" | Out-File -Encoding utf8 "C:\Users\user\AndroidStudioProjects\StudentCodex\zz1.xml"
Write-Output "dump saved"