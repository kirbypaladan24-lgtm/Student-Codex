$q1 = (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Select-Object -First 1).CPU
Start-Sleep -Seconds 25
$q2 = (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Select-Object -First 1).CPU
Write-Output "cpu delta over 25s: $([math]::Round($q2 - $q1, 1)) cores-seconds"
& "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell "echo ping" 2>&1 | Select-Object -First 2