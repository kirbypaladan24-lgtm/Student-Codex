$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "rm -f /data/local/tmp/ui_a.xml; uiautomator dump /data/local/tmp/ui_a.xml" 2>&1 | Select-Object -First 2
& $adb shell "cat /data/local/tmp/ui_a.xml" 2>&1 | Out-File -Encoding utf8 "C:\Users\user\AndroidStudioProjects\StudentCodex\ui_a.xml"
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\ui_a.xml" -ErrorAction SilentlyContinue).Length
Write-Output "dump size: $size"