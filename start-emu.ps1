$emu = "C:\Users\user\AppData\Local\Android\Sdk\emulator\emulator.exe"
Start-Process -FilePath $emu -ArgumentList "-avd","Pixel_8","-no-snapshot-save","-no-boot-anim","-netdelay","none","-netspeed","full" -WindowStyle Hidden
Write-Output "emulator launch requested"