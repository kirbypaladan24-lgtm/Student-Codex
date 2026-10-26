$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "dumpsys package com.example.studentcodex" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\pkg3.txt"
$lines = Get-Content "$env:TEMP\pkg3.txt"
$start = ($lines | Select-String -Pattern "Non-Data Actions:" | Select-Object -First 1).LineNumber
$lines[($start-2)..($start+12)] | ForEach-Object { Write-Output $_ }
Write-Output "--- hidden/suspended? ---"
Select-String -Path "$env:TEMP\pkg3.txt" -Pattern "hidden=|suspended=|stopped=|enabled=" | ForEach-Object { Write-Output $_.Line.Trim() } | Select-Object -First 6