$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.example.studentcodex" 2>&1 | Select-Object -First 6
Write-Output "--- user 0 ---"
& $adb shell "am get-current-user" 2>&1
& $adb shell "pm list users" 2>&1 | Select-Object -First 4