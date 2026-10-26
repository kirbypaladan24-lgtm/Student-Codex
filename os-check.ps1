$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
Write-Output "== sdk =="
& $adb shell getprop ro.build.version.sdk
Write-Output "== targetSdk of installed pkg =="
& $adb shell "dumpsys package com.example.studentcodex | grep -i 'targetSdk' | head -2"
Write-Output "== existing launcher icons? =="
& $adb shell "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.android.settings" 2>&1 | Select-Object -First 3
Write-Output "== pm query =="
& $adb shell "pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" 2>&1 | Select-Object -First 10