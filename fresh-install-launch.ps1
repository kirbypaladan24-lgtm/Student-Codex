$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# wait for package service to be registered
for ($i = 0; $i -lt 8; $i++) {
  $svc = & $adb shell "service list | grep -c package" 2>&1 | Select-Object -Last 1
  if ("$svc".Trim() -ge "1") { break }
  Start-Sleep -Seconds 10
}
Write-Output "package svc ready"
& $adb install -r "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk" 2>&1 | Select-Object -First 2
Start-Sleep -Seconds 8
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 8
Start-Sleep -Seconds 15
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2