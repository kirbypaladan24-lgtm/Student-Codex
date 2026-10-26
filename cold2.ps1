# Kill and cold-restart the emulator completely (device stuck offline)
$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb emu kill 2>&1 | Out-Null
Start-Sleep -Seconds 15
Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 5
$emu = "C:\Users\user\AppData\Local\Android\Sdk\emulator\emulator.exe"
Start-Process -FilePath $emu -ArgumentList "-avd","Pixel_8","-no-snapshot","-no-boot-anim","-gpu","swiftshader_indirect","-netdelay","none","-netspeed","full" -WindowStyle Hidden
Write-Output "cold restart (swiftshader) requested"