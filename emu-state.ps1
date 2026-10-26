$procs = Get-Process | Where-Object { $_.Name -like "qemu*" -or $_.Name -like "emulator*" } | Select-Object -First 3
if ($procs) { $procs | ForEach-Object { Write-Output "proc: $($_.Name) cpu=$($_.CPU)" } } else { Write-Output "no emulator process" }
& "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices