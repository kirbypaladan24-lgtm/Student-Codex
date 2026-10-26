$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# Disable DMA read on the emulator's graphics to work around the screencap assertion
& $adb shell "setprop debug.hwui.read_dma 0" 2>&1 | Out-Null
& $adb shell "settings put global debug_view_attributes 0" 2>&1 | Out-Null
& $adb shell "screencap /sdcard/shot1.png" 2>&1 | Select-Object -First 2
& $adb pull /sdcard/shot1.png "C:\Users\user\AndroidStudioProjects\StudentCodex\shot1.png" 2>&1 | Select-Object -First 1
$size = (Get-Item "C:\Users\user\AndroidStudioProjects\StudentCodex\shot1.png" -ErrorAction SilentlyContinue).Length
Write-Output "size: $size"