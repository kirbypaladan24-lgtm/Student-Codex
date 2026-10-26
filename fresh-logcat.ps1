$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -c 2>&1 | Out-Null
& $adb shell "am start -n com.example.studentcodex/.MainActivity" 2>&1 | Out-Null
Start-Sleep -Seconds 18
& $adb logcat -d 2>&1 | Out-File -Encoding utf8 "C:\Users\user\AndroidStudioProjects\StudentCodex\lc3.txt"
Select-String -Path "C:\Users\user\AndroidStudioProjects\StudentCodex\lc3.txt" -Pattern "studentcodex" | Select-Object -Last 12 | ForEach-Object { Write-Output $_.Line }