$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "pm enable com.example.studentcodex" 2>&1 | Select-Object -First 1
Start-Sleep -Seconds 25
$ok = $false
for ($i = 0; $i -lt 5; $i++) {
  $out = & $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1
  $joined = $out -join " "
  if ($joined -match "Status: ok|Complete") { Write-Output "LAUNCHED at try $i"; $ok = $true; break }
  Start-Sleep -Seconds 15
}
if (-not $ok) { Write-Output ("failed: " + ($out -join " | ").Substring(0, [Math]::Min(300, ($out -join " | ").Length))) }
Start-Sleep -Seconds 12
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2