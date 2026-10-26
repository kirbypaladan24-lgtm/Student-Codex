$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "dumpsys package com.example.studentcodex" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\pkg.txt"
Select-String -Path "$env:TEMP\pkg.txt" -Pattern "versionName|lastUpdateTime|firstInstallTime|pkgFlags|codePath" | ForEach-Object { Write-Output $_.Line.Trim() } | Select-Object -First 6
Select-String -Path "$env:TEMP\pkg.txt" -Pattern "Activity" | ForEach-Object { Write-Output $_.Line.Trim() } | Select-Object -First 8