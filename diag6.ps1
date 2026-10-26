$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "dumpsys package com.example.studentcodex" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\pkg2.txt"
$lines = Get-Content "$env:TEMP\pkg2.txt"
$idx = 0
foreach ($l in $lines) {
  if ($l -match "Schemes|Non-Data Actions|android.intent.action.MAIN|Action|Category|Filter|MainActivity") { }
}
# print the resolver table region
$start = ($lines | Select-String -Pattern "Activity Resolver Table:").LineNumber
$end = [Math]::Min($start + 40, $lines.Count)
$lines[($start)..($end)] | ForEach-Object { Write-Output $_ }