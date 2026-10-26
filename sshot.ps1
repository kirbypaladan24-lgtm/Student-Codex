$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "input keyevent KEYCODE_WAKEUP" 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $adb shell "input keyevent 82" 2>&1 | Out-Null
Start-Sleep -Seconds 3
& $adb exec-out screencap -p 2>$null | Set-Content -Encoding Byte -Path "C:\Users\user\AndroidStudioProjects\StudentCodex\s1.png"
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\s1.png" -ErrorAction SilentlyContinue).Length
Write-Output "shot: $size bytes"