$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -d -t 500 2>&1 | Out-File -Encoding utf8 "C:\Users\user\AndroidStudioProjects\StudentCodex\lc.txt"
$hits = Select-String -Path "C:\Users\user\AndroidStudioProjects\StudentCodex\lc.txt" -Pattern "studentcodex|AndroidRuntime|FATAL|ActivityManager.*start" | Select-Object -Last 20
$hits | ForEach-Object { Write-Output $_.Line }