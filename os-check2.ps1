$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" 2>&1 | Select-String "studentcodex"
Write-Output "== nexuslauncher should be there too; list all =="
& $adb shell "pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" 2>&1 | Select-String "com\." | Select-Object -First 8