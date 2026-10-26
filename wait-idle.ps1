$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# Wait until the qemu CPU load settles (first-boot dexopt/packagemanager scans)
$deadline = (Get-Date).AddSeconds(100)
do {
  Start-Sleep -Seconds 20
  $r = & $adb shell "service list | grep -c package" 2>&1 | Select-Object -Last 1
  $boot = & $adb shell getprop sys.boot_completed 2>&1 | Select-Object -Last 1
  Write-Output "pkg=$r boot=$boot"
} until ("$boot".Trim() -eq "1" -and "$r".Trim() -ne "0" -or (Get-Date) -gt $deadline)
& $adb shell "service list | grep -c package" 2>&1