# The system_server on the emulator is flapping (services deregistering and
# re-registering repeatedly). Kill the emulator and cold-boot it fresh.
$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb emu kill 2>&1 | Out-Null
Start-Sleep -Seconds 12
$emu = "C:\Users\user\AppData\Local\Android\Sdk\emulator\emulator.exe"
Start-Process -FilePath $emu -ArgumentList "-avd","Pixel_8","-no-snapshot","-no-boot-anim","-netdelay","none","-netspeed","full" -WindowStyle Hidden
Write-Output "cold reboot requested"