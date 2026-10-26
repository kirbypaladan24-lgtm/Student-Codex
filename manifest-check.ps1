$apk = "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$entry = $zip.Entries | Where-Object { $_.FullName -eq "AndroidManifest.xml" }
$ms = New-Object System.IO.MemoryStream
$entry.Open().CopyTo($ms)
$bytes = $ms.ToArray()
$zip.Dispose()
# crude binary string search for the activity name
$text = [System.Text.Encoding]::ASCII.GetString($bytes)
$found = ($text -match [char]0x1 + [char]0xc)
# Instead: search the merged manifest in build intermediates
$merged = Get-ChildItem "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\intermediates\merged_manifests\debug" -Filter AndroidManifest.xml -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($merged) { Get-Content $merged.FullName | Select-String -Pattern "activity|MainActivity" | Select-Object -First 6 }