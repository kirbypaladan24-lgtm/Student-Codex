$merged = Get-ChildItem "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\intermediates\merged_manifests\debug" -Filter AndroidManifest.xml -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
$content = Get-Content $merged.FullName -Raw
$idx = $content.IndexOf("com.example.studentcodex.MainActivity")
if ($idx -ge 0) { $content.Substring([Math]::Max(0,$idx-400), 700) }