$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "am start -W -n com.example.studentcodex/.MainActivity" 2>&1 | Out-File -Encoding utf8 "$env:TEMP\st.txt"
Get-Content "$env:TEMP\st.txt" | Select-Object -First 7
& $adb shell "ps -A | grep studentcodex" 2>&1 | Select-Object -First 2
& $adb shell "dumpsys activity activities | grep -E 'studentcodex|mFocusedApp' | head -4" 2>&1