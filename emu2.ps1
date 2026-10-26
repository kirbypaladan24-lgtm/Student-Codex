$q = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($q) { Write-Output "qemu cpu=$($q.CPU) ws=$([math]::Round($q.WorkingSet64/1MB))MB" } else { Write-Output "qemu gone" }
& "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices