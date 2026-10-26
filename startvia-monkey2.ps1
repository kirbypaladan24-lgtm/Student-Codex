$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# monkey uses the PackageManager query path (which WORKS), not am's resolver
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $adb
$psi.Arguments = "shell monkey -p com.example.studentcodex -c android.intent.category.LAUNCHER 1"
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$p = [System.Diagnostics.Process]::Start($psi)
$stdout = $p.StandardOutput.ReadToEnd()
$stderr = $p.StandardError.ReadToEnd()
$p.WaitForExit()
Write-Output "monkey exit: $($p.ExitCode)"
$stdout.Split("`n") | Select-Object -Last 5
Start-Sleep -Seconds 15
& $adb shell "dumpsys activity activities | grep topResumedActivity | head -1" 2>&1
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2