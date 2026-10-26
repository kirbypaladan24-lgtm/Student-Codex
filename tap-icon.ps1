$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# Launch by tapping the app icon on the launcher grid instead of am start.
# Open the app drawer first (swipe up from bottom center).
& $adb shell "input swipe 540 2200 540 1200 300" 2>&1 | Out-Null
Start-Sleep -Seconds 4
& $adb shell "uiautomator dump /data/local/tmp/ui_d.xml" 2>&1 | Select-Object -First 1
& $adb shell "cat /data/local/tmp/ui_d.xml" 2>&1 | Out-File -Encoding utf8 "C:\Users\user\AndroidStudioProjects\StudentCodex\ui_d.xml"
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\ui_d.xml" -ErrorAction SilentlyContinue).Length
Write-Output "drawer dump: $size"