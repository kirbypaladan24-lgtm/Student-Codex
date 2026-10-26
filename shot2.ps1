$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb exec-out "screencap -p" > "C:\Users\user\AndroidStudioProjects\StudentCodex\shot1.png" 2>$null
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\shot1.png" -ErrorAction SilentlyContinue).Length
Write-Output "shot1 size: $size"