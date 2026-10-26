$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.example.studentcodex" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\qa.txt"
Get-Content "$env:TEMP\qa.txt" | Select-String "name=" | Select-Object -First 4