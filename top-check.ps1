$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "dumpsys activity top" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\top.txt"
Get-Content "$env:TEMP\top.txt" -TotalCount 4
Select-String -Path "$env:TEMP\top.txt" -Pattern "studentcodex" | Select-Object -First 3 | ForEach-Object { Write-Output $_.Line.Trim().Substring(0, [Math]::Min(120, $_.Line.Trim().Length)) }