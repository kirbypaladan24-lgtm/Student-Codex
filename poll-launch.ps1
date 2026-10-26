$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$ok = $false
for ($i = 0; $i -lt 10; $i++) {
  $out = & $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1
  $joined = $out -join " "
  if ($joined -match "Status: ok") { Write-Output "LAUNCHED at try $i"; $ok = $true; break }
  Start-Sleep -Seconds 12
}
if (-not $ok) { Write-Output "launch failed all tries: $($out -join ' | ')" }
Start-Sleep -Seconds 12
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1