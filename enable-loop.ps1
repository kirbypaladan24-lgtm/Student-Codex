$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "pm enable com.example.studentcodex" 2>&1 | Select-Object -First 1
Start-Sleep -Seconds 8
for ($i = 0; $i -lt 4; $i++) {
  $r = & $adb shell "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.example.studentcodex" 2>&1 | Select-Object -Last 1
  Write-Output "resolve[$i]: $r"
  if ($r -like "*MainActivity*") {
    & $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Select-Object -First 7
    break
  }
  Start-Sleep -Seconds 10
}
Start-Sleep -Seconds 15
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2