$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -d 2>&1 | Out-File -Encoding utf8 "C:\Users\user\AndroidStudioProjects\StudentCodex\lc2.txt"
$hits = Select-String -Path "C:\Users\user\AndroidStudioProjects\StudentCodex\lc2.txt" -Pattern "studentcodex" | Select-Object -Last 25
$hits | ForEach-Object { Write-Output $_.Line }