$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
Write-Output "== uninstall =="
& $adb uninstall com.example.studentcodex 2>&1 | Select-Object -First 1
Start-Sleep -Seconds 5
Write-Output "== install =="
& $adb install "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk" 2>&1 | Select-Object -First 2
Start-Sleep -Seconds 10
Write-Output "== resolve =="
& $adb shell "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.example.studentcodex" 2>&1 | Select-Object -First 3
Write-Output "== launch =="
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 8
Start-Sleep -Seconds 15
Write-Output "== focus =="
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1