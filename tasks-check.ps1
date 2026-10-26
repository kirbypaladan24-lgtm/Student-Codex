$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "dumpsys activity recents" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\rec.txt"
Select-String -Path "$env:TEMP\rec.txt" -Pattern "studentcodex" | Select-Object -First 4 | ForEach-Object { Write-Output $_.Line.Trim().Substring(0, [Math]::Min(140, $_.Line.Trim().Length)) }
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2