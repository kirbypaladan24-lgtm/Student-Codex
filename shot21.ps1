param([string]$Name)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell screencap -p /sdcard/$Name.png
& $adb pull /sdcard/$Name.png "$env:TEMP\$Name.png" | Out-Null
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile("$env:TEMP\$Name.png")
$w = 540; $h = [int]($src.Height * 540 / $src.Width)
$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.DrawImage($src, 0, 0, $w, $h)
$bmp.Save("$env:TEMP\${Name}_small.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $src.Dispose()
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:TEMP\${Name}_small.png")) | Set-Content -NoNewline "$env:TEMP\$Name.b64"
Write-Output ("B64LEN=" + (Get-Item "$env:TEMP\$Name.b64").Length)
