$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb kill-server 2>&1 | Out-Null
Start-Sleep -Seconds 5
& $adb start-server 2>&1 | Out-Null
Start-Sleep -Seconds 5
& $adb devices
& $adb shell "echo ping" 2>&1 | Select-Object -First 2