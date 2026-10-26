$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$out = & $adb shell "monkey -p com.example.studentcodex -c android.intent.category.LAUNCHER 1" 2>&1
$out | ForEach-Object { Write-Output $_ } | Select-Object -First 10
Start-Sleep -Seconds 12
& $adb shell "dumpsys window windows | grep mCurrentFocus"