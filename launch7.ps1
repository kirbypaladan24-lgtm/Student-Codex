$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
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
Write-Output "EXIT: $($p.ExitCode)"
$stdout.Split("`n") | Select-Object -Last 6
Start-Sleep -Seconds 12
& $adb shell "dumpsys window windows | grep mCurrentFocus" 2>&1