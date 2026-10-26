$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$r = & $adb install -r "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\outputs\apk\debug\app-debug.apk" 2>&1
Write-Output "INSTALL: $r"